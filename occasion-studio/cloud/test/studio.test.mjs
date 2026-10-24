import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  mkdtempSync,
  existsSync,
  readdirSync,
  readFileSync,
  mkdirSync,
  writeFileSync,
  rmSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import sharp from 'sharp';
import { Store } from '../server/store.mjs';
import { createApp, requestAddress } from '../server/app.mjs';
import { preparePhoto } from '../server/media.mjs';
import { digest, passwordHash, validPassword, validateEvent } from '../server/domain.mjs';
import { EFFECTS } from '../effects.mjs';
const baseTime = Date.parse('2026-10-23T18:30:00+05:30');
const event = (extra = {}) => ({
  title: 'Diwali at home',
  message: 'A home full of light',
  template: 'diwali',
  color: '#edb96e',
  effect: 'sparkles',
  mode: 'album',
  status: 'scheduled',
  startsAt: baseTime + 10000,
  endsAt: baseTime + 21600000,
  intervalSeconds: 90,
  ...extra,
});
function fixture(t) {
  const dir = mkdtempSync(join(tmpdir(), 'occasion-test-'));
  let now = baseTime;
  const store = new Store(dir, () => now);
  t.after(() => {
    store.close();
    rmSync(dir, { recursive: true, force: true });
  });
  return {
    store,
    dir,
    time: (value) => {
      now = value;
    },
  };
}
test('occasion text validation identifies the field instead of blaming the photograph', () => {
  assert.throws(
    () => validateEvent(event({ title: '   ' }), undefined, baseTime),
    /Enter an occasion name\./,
  );
  assert.throws(
    () => validateEvent(event({ title: 'a'.repeat(81) }), undefined, baseTime),
    /Occasion name must be 80/,
  );
  assert.throws(
    () => validateEvent(event({ message: 'a'.repeat(141) }), undefined, baseTime),
    /Words for the screen must be 140/,
  );
  assert.equal(validateEvent(event({ message: '' }), undefined, baseTime).message, '');
});

test('flowers replace bubbles while older saved occasions remain readable', (t) => {
  const { store } = fixture(t);
  const saved = store.save(event({ effect: 'bubbles' }));
  assert.equal(saved.effect, 'flowers');
  store.put({ ...saved, effect: 'bubbles' });
  assert.equal(store.manifest().scenes[0].effect, 'flowers');
  assert.equal(store.get(saved.id).effect, 'bubbles', 'Reading old events does not rewrite their other data');
  assert.equal(validateEvent(event({ effect: 'flowers' }), undefined, baseTime).effect, 'flowers');
});

test('every published effect is accepted and unknown effects are rejected', () => {
  for (const effect of EFFECTS)
    assert.equal(validateEvent(event({ effect: effect.id }), undefined, baseTime).effect, effect.id);
  for (const effect of ['unknown', '', {}, null])
    assert.throws(() => validateEvent(event({ effect }), undefined, baseTime));
});

test('schedule boundaries, overlap, offline late return and expired events', (t) => {
  const { store, time } = fixture(t),
    first = store.save(event());
  assert.equal(store.manifest().scenes[0].id, first.id);
  assert.throws(() => store.save(event()), /overlaps/);
  store.save(event({ title: 'Later', startsAt: first.endsAt, endsAt: first.endsAt + 3600000 }));
  time(first.startsAt);
  assert.equal(store.manifest().scenes.length, 2);
  time(first.endsAt);
  assert.equal(store.manifest().scenes.length, 1);
  store.cleanup();
  assert.equal(store.get(first.id).status, 'ended');
  assert.throws(() => store.save(event({ endsAt: first.endsAt + 9999999 }), first.id), /ended/);
  assert.throws(() => store.save(event({ endsAt: first.endsAt - 1 })), /future/);
});

test('editing respects adjacent boundaries and a conflict never mutates the saved occasion', (t) => {
  const { store } = fixture(t);
  const first = store.save(event());
  const second = store.save(
    event({ title: 'Evening', startsAt: first.endsAt, endsAt: first.endsAt + 3600000 }),
  );
  const before = store.get(second.id),
    revision = store.revision();
  assert.throws(() => store.save({ ...second, startsAt: first.endsAt - 1 }, second.id), /overlaps.*Diwali/);
  assert.deepEqual(store.get(second.id), before);
  assert.equal(
    store.revision(),
    revision,
    'Conflicting edit must not cancel or overwrite the original schedule',
  );
  assert.equal(store.save({ ...second, title: 'Edited evening' }, second.id).title, 'Edited evening');
});

test('deleting an occasion removes metadata and all owned photos without touching another event', async (t) => {
  const { store } = fixture(t);
  const first = store.save(event({ status: 'draft' })),
    second = store.save(event());
  const image = await sharp({ create: { width: 1280, height: 720, channels: 3, background: '#335577' } })
    .jpeg()
    .toBuffer();
  await preparePhoto(store, first.id, image);
  const secondAsset = await preparePhoto(store, second.id, image);
  store.delete(first.id);
  assert.equal(store.get(first.id), undefined);
  assert.ok(!existsSync(join(store.media, first.id)));
  assert.ok(existsSync(join(store.media, second.id, secondAsset.id + '.jpg')));
  assert.throws(() => store.delete(first.id), /not found/);
  assert.equal(store.get(second.id).status, 'scheduled');
});
test('server deletes originals, derivatives and thumbnails, including never-displayed drafts', async (t) => {
  const { store, time } = fixture(t);
  const first = store.save(event({ status: 'draft' }));
  const photo = await sharp({ create: { width: 2400, height: 1600, channels: 3, background: '#ab8951' } })
    .jpeg()
    .toBuffer();
  const asset = await preparePhoto(store, first.id, photo, 0, 1);
  const directory = join(store.media, first.id);
  assert.equal(readdirSync(directory).length, 3);
  assert.equal((await sharp(join(directory, asset.id + '.jpg')).metadata()).width, 1280);
  assert.equal((await sharp(join(directory, asset.id + '.jpg')).metadata()).height, 720);
  assert.equal(digest(readFileSync(join(directory, asset.id + '.jpg'))), asset.sha256);
  time(first.endsAt);
  assert.equal(store.cleanup(), 1);
  assert.ok(!existsSync(directory));
  assert.equal(store.get(first.id).assets.length, 0);
  assert.equal(store.cleanup(), 0);
  await assert.rejects(preparePhoto(store, first.id, photo), /ended/);
});
test('end now purges photos and corrupt/oversized uploads never become assets', async (t) => {
  const { store } = fixture(t);
  const first = store.save(event());
  await assert.rejects(preparePhoto(store, first.id, Buffer.from('not an image')), /could not be read/);
  await assert.rejects(preparePhoto(store, first.id, Buffer.alloc(20 * 1024 * 1024 + 1)), /smaller/);
  const image = await sharp({ create: { width: 1280, height: 720, channels: 3, background: '#336688' } })
    .png()
    .toBuffer();
  await preparePhoto(store, first.id, image);
  store.end(first.id);
  assert.ok(!existsSync(join(store.media, first.id)));
  assert.equal(store.manifest().scenes.length, 0);
});
test('startup recovery removes only unreferenced event files', (t) => {
  const { store } = fixture(t);
  const first = store.save(event());
  const folder = join(store.media, first.id);
  mkdirSync(folder);
  writeFileSync(join(folder, 'partial-upload'), 'incomplete');
  store.reconcileMedia();
  assert.equal(readdirSync(folder).length, 0);
});
test('an expired event is purged after a full server restart', async (t) => {
  const dir = mkdtempSync(join(tmpdir(), 'occasion-restart-'));
  let store = new Store(dir, () => baseTime);
  const saved = store.save(event({ status: 'draft' }));
  const picture = await sharp({ create: { width: 1280, height: 720, channels: 3, background: '#668855' } })
    .png()
    .toBuffer();
  await preparePhoto(store, saved.id, picture);
  store.close();
  store = new Store(dir, () => saved.endsAt + 1);
  t.after(() => {
    store.close();
    rmSync(dir, { recursive: true, force: true });
  });
  store.cleanup();
  assert.equal(store.get(saved.id).status, 'ended');
  assert.ok(!existsSync(join(store.media, saved.id)));
});
test('pairing is expiring, one-time and requires its secret challenge; revocation works', (t) => {
  const { store, time } = fixture(t);
  const pair = store.pairing();
  assert.deepEqual(store.claim(pair.code, pair.challenge), { pending: true });
  assert.throws(() => store.claim(pair.code, 'wrong'), /expired/);
  const device = store.approve(pair.code, 'Redmi TV');
  const claim = store.claim(pair.code, pair.challenge);
  assert.equal(claim.id, device.id);
  assert.throws(() => store.claim(pair.code, pair.challenge), /expired/);
  assert.equal(store.device(claim.token).id, device.id);
  store.revoke(device.id);
  assert.throws(() => store.device(claim.token), /not paired/);
  const second = store.pairing();
  time(second.expires);
  assert.throws(() => store.approve(second.code), /expired/);
});
test('password/session verification and expiry', (t) => {
  const { store, time } = fixture(t);
  const hash = passwordHash('correct-password-123');
  assert.ok(validPassword('correct-password-123', hash));
  assert.ok(!validPassword('wrong', hash));
  assert.ok(!validPassword({}, hash));
  const token = store.session();
  assert.ok(store.authenticated(token));
  time(baseTime + 12 * 3600000);
  assert.ok(!store.authenticated(token));
});

test('client addresses are trusted only from the configured loopback gateway', () => {
  const request = (peer, address) => ({ socket: { remoteAddress: peer }, headers: { 'x-real-ip': address } });
  assert.equal(requestAddress(request('127.0.0.1', '203.0.113.10'), true), '203.0.113.10');
  assert.equal(requestAddress(request('::ffff:127.0.0.1', '2001:db8::1'), true), '2001:db8::1');
  assert.equal(requestAddress(request('127.0.0.1', '203.0.113.10')), '127.0.0.1');
  assert.equal(requestAddress(request('203.0.113.20', '203.0.113.10'), true), '203.0.113.20');
  assert.equal(requestAddress(request('127.0.0.1', '203.0.113.10, 203.0.113.20'), true), '127.0.0.1');
  assert.equal(requestAddress(request('127.0.0.1', ['203.0.113.10']), true), '127.0.0.1');
});

test('public production dashboard requires login, protects sessions and throttles gateway clients', async (t) => {
  const dir = mkdtempSync(join(tmpdir(), 'occasion-public-'));
  const dist = join(dir, 'dist');
  mkdirSync(dist);
  writeFileSync(join(dist, 'index.html'), '<html><body>Sign in</body></html>');
  const config = {
    dataDir: dir,
    passwordHash: passwordHash('correct-password-123'),
    adminOrigin: 'https://studio.example',
    production: true,
    trustLoopbackProxy: true,
    distDir: dist,
  };
  assert.throws(() => createApp({ ...config, adminOrigin: 'http://studio.example' }), /HTTPS/);
  const app = createApp(config);
  await new Promise((done) => app.server.listen(0, '127.0.0.1', done));
  t.after(async () => {
    await app.close();
    rmSync(dir, { recursive: true, force: true });
  });
  const root = `http://127.0.0.1:${app.server.address().port}`;
  const request = (path, options = {}) => fetch(root + path, options);
  const login = (address, password = 'incorrect', origin = config.adminOrigin) =>
    request('/api/login', {
      method: 'POST',
      headers: { Origin: origin, 'X-Real-IP': address },
      body: JSON.stringify({ password }),
    });
  // The public login page is available without VPN membership or a session.
  assert.equal((await request('/')).status, 200);
  for (const path of [
    '/api/state',
    '/api/tv/manifest',
    '/api/occasions/00000000-0000-0000-0000-000000000000/photos/00000000-0000-0000-0000-000000000000/thumb',
  ]) {
    assert.equal((await request(path)).status, 401);
  }
  assert.equal((await login('203.0.113.10', 'correct-password-123', 'https://other.example')).status, 403);
  for (let i = 0; i < 8; i++) assert.equal((await login('203.0.113.10')).status, 401);
  const blocked = await login('203.0.113.10', 'correct-password-123');
  assert.equal(blocked.status, 429);
  assert.equal(blocked.headers.get('retry-after'), '60');
  // A different real client is not locked out by the first client's failures.
  const accepted = await login('203.0.113.11', 'correct-password-123');
  assert.equal(accepted.status, 200);
  const setCookie = accepted.headers.get('set-cookie');
  for (const flag of ['HttpOnly', 'SameSite=Strict', 'Secure']) assert.ok(setCookie.includes(flag));
  const cookie = setCookie.split(';')[0];
  assert.equal((await request('/api/state', { headers: { Cookie: cookie } })).status, 200);
  assert.equal(
    (
      await request('/api/logout', {
        method: 'POST',
        headers: { Cookie: cookie, Origin: 'https://other.example' },
      })
    ).status,
    403,
  );
  assert.equal(
    (
      await request('/api/logout', {
        method: 'POST',
        headers: { Cookie: cookie, Origin: config.adminOrigin },
      })
    ).status,
    200,
  );
  assert.equal((await request('/api/state', { headers: { Cookie: cookie } })).status, 401);
  // Rotating addresses cannot bypass the global expensive-password-check cap.
  for (let i = 0; i < 21; i++) assert.equal((await login(`198.51.100.${i + 1}`)).status, 401);
  assert.equal((await login('198.51.100.100')).status, 429);
});
test('HTTP ownership, CSRF, read-only TV routes, upload/expiry and SSE change notification', async (t) => {
  const dir = mkdtempSync(join(tmpdir(), 'occasion-http-'));
  let now = baseTime;
  const app = createApp({
    dataDir: dir,
    clock: () => now,
    passwordHash: passwordHash('correct-password-123'),
    adminOrigin: 'http://studio.test',
    production: false,
    distDir: join(dir, 'dist'),
  });
  await new Promise((done) => app.server.listen(0, '127.0.0.1', done));
  t.after(async () => {
    await app.close();
    rmSync(dir, { recursive: true, force: true });
  });
  const root = `http://127.0.0.1:${app.server.address().port}`;
  const request = (path, options = {}) => fetch(root + path, options);
  assert.equal((await request('/api/state')).status, 401);
  assert.equal((await request('/api/login', { method: 'POST', body: '{}' })).status, 403);
  const login = await request('/api/login', {
    method: 'POST',
    headers: { Origin: 'http://studio.test' },
    body: JSON.stringify({ password: 'correct-password-123' }),
  });
  assert.equal(login.status, 200);
  const cookie = login.headers.get('set-cookie').split(';')[0];
  const owner = { Origin: 'http://studio.test', Cookie: cookie };
  const created = await request('/api/occasions', {
    method: 'POST',
    headers: owner,
    body: JSON.stringify(event()),
  });
  assert.equal(created.status, 201);
  const occasion = await created.json();
  const pair = await (await request('/api/tv/pair', { method: 'POST', body: '{}' })).json();
  await request('/api/pair/approve', {
    method: 'POST',
    headers: owner,
    body: JSON.stringify({ code: pair.code }),
  });
  const claim = await (await request('/api/tv/claim', { method: 'POST', body: JSON.stringify(pair) })).json();
  const tv = { Authorization: 'Bearer ' + claim.token };
  app.store.put({ ...app.store.get(occasion.id), effect: 'bubbles' });
  const legacyState = await (await request('/api/state', { headers: owner })).json();
  assert.equal(legacyState.events.find((e) => e.id === occasion.id).effect, 'flowers');
  const legacyManifest = await (await request('/api/tv/manifest', { headers: tv })).json();
  assert.equal(legacyManifest.scenes[0].effect, 'flowers');
  assert.equal((await request('/api/state', { headers: tv })).status, 401);
  assert.equal((await request('/api/tv/manifest', { headers: { Cookie: cookie } })).status, 401);
  const picture = await sharp({ create: { width: 1800, height: 1200, channels: 3, background: '#ffaa66' } })
    .jpeg()
    .toBuffer();
  const uploaded = await request(`/api/occasions/${occasion.id}/photos`, {
    method: 'POST',
    headers: owner,
    body: picture,
  });
  assert.equal(uploaded.status, 201);
  const asset = await uploaded.json();
  const later = app.store.save(
    event({ title: 'Later', startsAt: occasion.endsAt, endsAt: occasion.endsAt + 3600000 }),
  );
  const laterBefore = app.store.get(later.id);
  const conflictingStart = await request(`/api/occasions/${later.id}/start`, {
    method: 'POST',
    headers: owner,
    body: JSON.stringify({ ...later, title: 'Do not save this' }),
  });
  assert.equal(conflictingStart.status, 409);
  assert.deepEqual(
    app.store.get(later.id),
    laterBefore,
    'Failed Start now leaves the saved schedule and edits intact',
  );
  assert.equal(
    (await request('/api/occasions/start', { method: 'POST', headers: owner, body: JSON.stringify(event()) }))
      .status,
    409,
  );
  const editConflict = await request(`/api/occasions/${later.id}`, {
    method: 'PUT',
    headers: owner,
    body: JSON.stringify({ ...later, startsAt: occasion.endsAt - 1 }),
  });
  assert.equal(editConflict.status, 409);
  assert.deepEqual(app.store.get(later.id), laterBefore);
  assert.equal(
    (
      await request(`/api/occasions/${later.id}`, {
        method: 'DELETE',
        headers: { ...owner, Cookie: '', ...tv },
      })
    ).status,
    401,
  );
  assert.equal(
    (await request(`/api/occasions/${later.id}`, { method: 'DELETE', headers: owner })).status,
    200,
  );
  assert.equal(app.store.get(later.id), undefined);
  const media = `/api/tv/media/${occasion.id}/${asset.id}`;
  assert.equal((await request(media)).status, 401);
  assert.equal((await request(media, { headers: tv })).status, 200);
  const effectPath = `/api/occasions/${occasion.id}/photos/${asset.id}`;
  const changeEffect = (effect, headers = owner) =>
    request(effectPath, { method: 'PATCH', headers, body: JSON.stringify({ effect }) });
  assert.equal(
    (await changeEffect('diya', { ...owner, Cookie: '', ...tv })).status,
    401,
    'TV cannot edit a photo',
  );
  assert.equal((await changeEffect('diya', { Cookie: cookie, Origin: 'https://other.example' })).status, 403);
  for (const effect of EFFECTS) {
    const response = await changeEffect(effect.id);
    assert.equal(response.status, 200);
    const updated = await response.json();
    assert.equal(updated.effect, effect.id);
    assert.equal(
      updated.sha256,
      asset.sha256,
      'Changing effects does not re-encode or download another photo',
    );
    assert.equal(updated.bytes, asset.bytes);
    const value = await (await request('/api/tv/manifest', { headers: tv })).json();
    assert.equal(
      value.scenes[0].assets[0].effect,
      effect.id,
      'Overrides reach the TV, including explicit none',
    );
  }
  assert.equal((await changeEffect('invalid')).status, 400);
  assert.equal((await changeEffect({})).status, 400);
  assert.equal(
    app.store.get(occasion.id).assets[0].effect,
    'none',
    'Invalid effect must not overwrite the saved choice',
  );
  assert.equal((await changeEffect(null)).status, 200);
  const inherited = await (await request('/api/tv/manifest', { headers: tv })).json();
  assert.equal(
    Object.hasOwn(inherited.scenes[0].assets[0], 'effect'),
    false,
    'No override inherits the occasion',
  );
  assert.equal(inherited.scenes[0].effect, 'flowers');
  await changeEffect('bubbles');
  assert.equal(app.store.get(occasion.id).assets[0].effect, 'flowers');
  const abort = new AbortController();
  const stream = await request('/api/tv/events', { headers: tv, signal: abort.signal });
  assert.match(stream.headers.get('content-type'), /event-stream/);
  const reader = stream.body.getReader();
  assert.match(new TextDecoder().decode((await reader.read()).value), /changed/);
  const ended = await request(`/api/occasions/${occasion.id}/end`, {
    method: 'POST',
    headers: owner,
    body: '{}',
  });
  assert.equal(ended.status, 200);
  assert.match(new TextDecoder().decode((await reader.read()).value), /changed/);
  abort.abort();
  assert.equal((await request(media, { headers: tv })).status, 410);
  assert.equal(
    (await changeEffect('diya')).status,
    410,
    'Ended photos cannot be resurrected by an effect update',
  );
  assert.equal((await (await request('/api/tv/manifest', { headers: tv })).json()).scenes.length, 0);
  assert.ok(!existsSync(join(app.store.media, occasion.id)));
});
