import { createServer } from 'node:http';
import { createReadStream, existsSync, statSync } from 'node:fs';
import { rm } from 'node:fs/promises';
import { resolve, join, extname, sep } from 'node:path';
import { isIP } from 'node:net';
import { Store } from './store.mjs';
import { ApiError, TEMPLATES, requireValue, validPassword, normaliseEffect } from './domain.mjs';
import { preparePhoto } from './media.mjs';
import { validEffect } from '../effects.mjs';

async function body(req, limit = 65536, json = true) {
  requireValue(Number(req.headers['content-length'] ?? 0) <= limit, 413, 'Upload is too large.');
  const chunks = [];
  let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    requireValue(size <= limit, 413, 'Upload is too large.');
    chunks.push(chunk);
  }
  const bytes = Buffer.concat(chunks);
  if (!json) return bytes;
  try {
    return JSON.parse(bytes.toString('utf8'));
  } catch {
    throw new ApiError(400, 'Invalid JSON request.');
  }
}
function reply(res, value, status = 200) {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
  res.end(JSON.stringify(value));
}
const cookie = (req) => /(?:^|;\s*)studio_session=([A-Za-z0-9_-]+)/.exec(req.headers.cookie ?? '')?.[1];
const bearer = (req) => /^Bearer ([A-Za-z0-9_-]{43})$/.exec(req.headers.authorization ?? '')?.[1];

// Only the explicitly trusted local gateway may supply a client address.
// Caddy overwrites X-Real-IP; internet callers cannot reset their login limit.
export function requestAddress(req, trustLoopbackProxy = false) {
  const peer = req.socket.remoteAddress;
  const forwarded = req.headers['x-real-ip'];
  if (
    trustLoopbackProxy &&
    ['127.0.0.1', '::1', '::ffff:127.0.0.1'].includes(peer) &&
    typeof forwarded === 'string' &&
    isIP(forwarded)
  )
    return forwarded;
  return peer ?? 'unknown';
}

export function createApp(config) {
  requireValue(
    config.passwordHash && /^[a-f0-9]{32}:[a-f0-9]{128}$/.test(config.passwordHash),
    500,
    'Set OWNER_PASSWORD_HASH using npm run password.',
  );
  const adminOrigin = new URL(config.adminOrigin).origin;
  requireValue(
    !config.production || adminOrigin.startsWith('https://'),
    500,
    'Production requires an HTTPS dashboard origin.',
  );
  const store = new Store(config.dataDir, config.clock);
  store.cleanup();
  store.reconcileMedia();
  const streams = new Map(),
    attempts = new Map();
  let uploadBusy = false,
    lastState = '';
  function rate(req, key, maximum, global = false) {
    const now = Date.now(),
      name = key + ':' + (global ? 'all' : requestAddress(req, config.trustLoopbackProxy));
    const row = attempts.get(name);
    if (!row || now > row.until) {
      // Bound memory even when a public client rotates through many addresses.
      if (!row && attempts.size >= 10000) {
        for (const [entry, value] of attempts) if (value.until <= now) attempts.delete(entry);
        requireValue(attempts.size < 10000, 429, 'Please wait a minute and try again.');
      }
      attempts.set(name, { count: 1, until: now + 60000 });
    } else {
      requireValue(++row.count <= maximum, 429, 'Please wait a minute and try again.');
    }
  }
  function notify() {
    const payload = store.manifest();
    const state = JSON.stringify([
      payload.revision,
      payload.scenes.map((e) => [e.id, e.startsAt <= payload.serverTime]),
    ]);
    if (state === lastState) return;
    lastState = state;
    for (const [id, stream] of streams) {
      if (store.devices().find((d) => d.id === id)?.revoked) {
        stream.end();
        streams.delete(id);
        continue;
      }
      if (!stream.write(`event: changed\ndata: ${payload.revision}\n\n`)) stream.destroy();
    }
  }
  const server = createServer(async (req, res) => {
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('Referrer-Policy', 'no-referrer');
    res.setHeader(
      'Content-Security-Policy',
      "default-src 'self'; img-src 'self' blob:; style-src 'self' 'unsafe-inline'; font-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'",
    );
    res.setHeader('Cache-Control', 'no-store');
    try {
      const url = new URL(req.url, 'http://localhost'),
        path = url.pathname,
        method = req.method;
      if (path === '/api/health' && method === 'GET') return reply(res, { ok: true });
      if (path.startsWith('/api/tv/')) {
        if (path === '/api/tv/pair' && method === 'POST') {
          rate(req, 'pair', 10);
          rate(req, 'pair-global', 30, true);
          await body(req);
          return reply(res, store.pairing());
        }
        if (path === '/api/tv/claim' && method === 'POST') {
          rate(req, 'claim', 90);
          const data = await body(req);
          requireValue(
            typeof data.code === 'string' && typeof data.challenge === 'string',
            400,
            'Invalid pairing request.',
          );
          return reply(res, store.claim(data.code, data.challenge));
        }
        const device = store.device(bearer(req));
        if (path === '/api/tv/manifest' && method === 'GET') return reply(res, store.manifest());
        if (path === '/api/tv/ack' && method === 'POST') {
          store.ack(device.id, await body(req));
          return reply(res, { ok: true });
        }
        if (path === '/api/tv/events' && method === 'GET') {
          streams.get(device.id)?.end();
          res.writeHead(200, {
            'Content-Type': 'text/event-stream',
            'Cache-Control': 'no-store',
            'X-Accel-Buffering': 'no',
          });
          res.write('retry: 3000\nevent: changed\ndata: connected\n\n');
          streams.set(device.id, res);
          req.on('close', () => {
            if (streams.get(device.id) === res) streams.delete(device.id);
          });
          return;
        }
        const media = /^\/api\/tv\/media\/([a-f0-9-]{36})\/([a-f0-9-]{36})$/.exec(path);
        if (media && method === 'GET') {
          const event = store.get(media[1]);
          requireValue(
            event &&
              event.status === 'scheduled' &&
              event.endsAt > store.clock() &&
              event.assets.some((a) => a.id === media[2]),
            410,
            'Photo is no longer available.',
          );
          return sendFile(res, join(store.media, event.id, media[2] + '.jpg'), 'image/jpeg');
        }
        throw new ApiError(404, 'Not found.');
      }
      if (path.startsWith('/api/')) {
        if (!['GET', 'HEAD'].includes(method))
          requireValue(
            req.headers.origin === adminOrigin,
            403,
            'Open the dashboard from its configured address.',
          );
        if (path === '/api/login' && method === 'POST') {
          rate(req, 'login', 8);
          rate(req, 'login-global', 30, true);
          const data = await body(req);
          requireValue(validPassword(data.password, config.passwordHash), 401, 'Password not recognised.');
          res.setHeader(
            'Set-Cookie',
            `studio_session=${store.session()}; Path=/api; HttpOnly; SameSite=Strict; Max-Age=43200${config.production ? '; Secure' : ''}`,
          );
          return reply(res, { ok: true });
        }
        requireValue(store.authenticated(cookie(req)), 401, 'Please sign in.');
        if (path === '/api/logout' && method === 'POST') {
          store.logout(cookie(req));
          res.setHeader(
            'Set-Cookie',
            `studio_session=; Path=/api; HttpOnly; SameSite=Strict; Max-Age=0${config.production ? '; Secure' : ''}`,
          );
          return reply(res, { ok: true });
        }
        if (path === '/api/state' && method === 'GET')
          return reply(res, {
            events: store.list().map((event) => ({
              ...event,
              effect: normaliseEffect(event.effect),
              assets: event.assets.map((asset) =>
                asset.effect ? { ...asset, effect: normaliseEffect(asset.effect) } : asset,
              ),
            })),
            devices: store.devices(),
            templates: TEMPLATES,
            revision: store.revision(),
            serverTime: store.clock(),
          });
        if (path === '/api/pair/approve' && method === 'POST') {
          rate(req, 'approve', 12);
          const data = await body(req);
          return reply(res, store.approve(data.code, data.name));
        }
        const revoke = /^\/api\/devices\/([a-f0-9-]{36})\/revoke$/.exec(path);
        if (revoke && method === 'POST') {
          store.revoke(revoke[1]);
          notify();
          return reply(res, { ok: true });
        }
        if (path === '/api/occasions/start' && method === 'POST') {
          const input = await body(req);
          requireValue(
            input && typeof input === 'object' && !Array.isArray(input),
            400,
            'An occasion is required.',
          );
          const now = store.clock(),
            duration = Number(input.endsAt) - Number(input.startsAt);
          const event = store.save({ ...input, status: 'scheduled', startsAt: now, endsAt: now + duration });
          notify();
          return reply(res, event, 201);
        }
        if (path === '/api/occasions' && method === 'POST') {
          const event = store.save(await body(req));
          notify();
          return reply(res, event, 201);
        }
        const eventRoute =
          /^\/api\/occasions\/([a-f0-9-]{36})(?:\/(end|start|photos)(?:\/([a-f0-9-]{36})(?:\/(thumb|image))?)?)?$/.exec(
            path,
          );
        if (eventRoute) {
          const [, id, action, assetId, kind] = eventRoute;
          if (!action && method === 'PUT') {
            const event = store.save(await body(req), id);
            notify();
            return reply(res, event);
          }
          if (!action && method === 'DELETE') {
            store.delete(id);
            notify();
            return reply(res, { ok: true });
          }
          if (action === 'end' && method === 'POST') {
            store.end(id);
            notify();
            return reply(res, { ok: true });
          }
          if (action === 'start' && method === 'POST') {
            const input = await body(req);
            const event = store.get(id);
            requireValue(event, 404, 'Occasion not found.');
            const candidate = { ...event, ...input };
            const duration = Number(candidate.endsAt) - Number(candidate.startsAt),
              now = store.clock();
            const updated = store.save(
              { ...candidate, status: 'scheduled', startsAt: now, endsAt: now + duration },
              id,
            );
            notify();
            return reply(res, updated);
          }
          if (action === 'photos' && !assetId && method === 'POST') {
            requireValue(!uploadBusy, 429, 'Another photo is being prepared. Try again shortly.');
            uploadBusy = true;
            try {
              const photo = await body(req, 20 * 1024 * 1024, false);
              const asset = await preparePhoto(
                store,
                id,
                photo,
                Number(url.searchParams.get('x') ?? 0.5),
                Number(url.searchParams.get('y') ?? 0.5),
              );
              notify();
              return reply(res, asset, 201);
            } finally {
              uploadBusy = false;
            }
          }
          if (action === 'photos' && assetId) {
            const event = store.get(id);
            requireValue(
              event &&
                event.endsAt > store.clock() &&
                event.status !== 'ended' &&
                event.assets.some((a) => a.id === assetId),
              410,
              'Photo is no longer available.',
            );
            if (method === 'GET' && kind)
              return sendFile(
                res,
                join(store.media, id, assetId + (kind === 'thumb' ? '.thumb.jpg' : '.jpg')),
                'image/jpeg',
              );
            if (method === 'PATCH' && !kind) {
              const data = await body(req);
              const effect = data?.effect;
              requireValue(
                effect === null || validEffect(effect),
                400,
                'Choose a supported photo effect or use the occasion effect.',
              );
              // Re-read after the request body: another request may have ended the event or removed this photo.
              const latest = store.get(id);
              const current = latest?.assets.find((a) => a.id === assetId);
              requireValue(
                latest && latest.endsAt > store.clock() && latest.status !== 'ended' && current,
                410,
                'Photo is no longer available.',
              );
              const updated = { ...current };
              if (effect === null) delete updated.effect;
              else updated.effect = normaliseEffect(effect);
              store.put({ ...latest, assets: latest.assets.map((a) => (a.id === assetId ? updated : a)) });
              notify();
              return reply(res, updated);
            }
            if (method === 'DELETE' && !kind) {
              // Remove from manifests before deleting the owned files.
              store.put({ ...event, assets: event.assets.filter((a) => a.id !== assetId) });
              notify();
              for (const suffix of ['.jpg', '.thumb.jpg', '.original'])
                await rm(join(store.media, id, assetId + suffix), { force: true });
              return reply(res, { ok: true });
            }
          }
        }
        throw new ApiError(404, 'Not found.');
      }
      requireValue(method === 'GET' || method === 'HEAD', 405, 'Method not allowed.');
      const root = resolve(config.distDir),
        target = resolve(root, '.' + decodeURIComponent(path));
      requireValue(target === root || target.startsWith(root + sep), 404, 'Not found.');
      const file = existsSync(target) && statSync(target).isFile() ? target : join(root, 'index.html');
      const types = {
        '.html': 'text/html; charset=utf-8',
        '.js': 'text/javascript',
        '.css': 'text/css',
        '.woff2': 'font/woff2',
        '.ttf': 'font/ttf',
        '.svg': 'image/svg+xml',
      };
      sendFile(res, file, types[extname(file)] ?? 'application/octet-stream', method === 'HEAD');
    } catch (error) {
      if (res.headersSent) {
        res.destroy();
        return;
      }
      if (!(error instanceof ApiError)) console.error('Request failed:', error.code ?? error.name);
      if (error.status === 429) res.setHeader('Retry-After', '60');
      reply(
        res,
        { error: error instanceof ApiError ? error.message : 'The server could not complete this request.' },
        error.status ?? 500,
      );
    }
  });
  server.requestTimeout = 30000;
  server.headersTimeout = 10000;
  const sweep = setInterval(() => {
    try {
      store.cleanup();
      notify();
    } catch {
      console.error('Photo cleanup failed; will retry.');
    }
  }, 60000);
  const update = setInterval(notify, 1000);
  const heartbeat = setInterval(() => {
    for (const [id, stream] of streams) {
      store.touch(id);
      if (!stream.write(': keepalive\n\n')) stream.destroy();
    }
  }, 15000);
  const trim = setInterval(() => {
    for (const [key, row] of attempts) if (row.until < Date.now()) attempts.delete(key);
  }, 60000);
  for (const timer of [sweep, update, heartbeat, trim]) timer.unref();
  function close() {
    for (const timer of [sweep, update, heartbeat, trim]) clearInterval(timer);
    for (const stream of streams.values()) stream.end();
    server.closeAllConnections();
    return new Promise((done) =>
      server.close(() => {
        store.close();
        done();
      }),
    );
  }
  return { server, store, close };
}
function sendFile(res, file, type, head = false) {
  requireValue(existsSync(file), 404, 'File not found.');
  const size = statSync(file).size;
  res.writeHead(200, { 'Content-Type': type, 'Content-Length': size, 'Cache-Control': 'no-store' });
  if (head) res.end();
  else
    createReadStream(file)
      .on('error', () => res.destroy())
      .pipe(res);
}
