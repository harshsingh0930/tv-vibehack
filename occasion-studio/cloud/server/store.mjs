import { DatabaseSync } from 'node:sqlite';
import { mkdirSync, readdirSync, rmSync } from 'node:fs';
import { resolve, join } from 'node:path';
import { randomUUID, randomInt } from 'node:crypto';
import { checkOverlap, digest, manifest, requireValue, secret, validateEvent } from './domain.mjs';
export class Store {
  constructor(directory, clock = Date.now) {
    this.directory = resolve(directory);
    this.clock = clock;
    this.media = join(this.directory, 'media');
    mkdirSync(this.media, { recursive: true, mode: 0o700 });
    this.db = new DatabaseSync(join(this.directory, 'studio.sqlite'));
    this.db.exec(`PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000;
      CREATE TABLE IF NOT EXISTS occasions(id TEXT PRIMARY KEY, body TEXT NOT NULL);
      CREATE TABLE IF NOT EXISTS devices(id TEXT PRIMARY KEY, name TEXT NOT NULL, token TEXT UNIQUE NOT NULL, seen INTEGER, ack TEXT, revoked INTEGER DEFAULT 0);
      CREATE TABLE IF NOT EXISTS pairings(code TEXT PRIMARY KEY, challenge TEXT NOT NULL, expires INTEGER NOT NULL, device TEXT, token TEXT);
      CREATE TABLE IF NOT EXISTS sessions(token TEXT PRIMARY KEY, expires INTEGER NOT NULL);
      CREATE TABLE IF NOT EXISTS settings(key TEXT PRIMARY KEY, value INTEGER NOT NULL);
      INSERT OR IGNORE INTO settings VALUES('revision',1);`);
  }
  list() {
    return this.db
      .prepare('SELECT body FROM occasions')
      .all()
      .map((r) => JSON.parse(r.body));
  }
  get(id) {
    return this.list().find((e) => e.id === id);
  }
  revision() {
    return this.db.prepare("SELECT value FROM settings WHERE key='revision'").get().value;
  }
  bump() {
    this.db.exec("UPDATE settings SET value=value+1 WHERE key='revision'");
  }
  put(event) {
    this.db.prepare('INSERT OR REPLACE INTO occasions VALUES(?,?)').run(event.id, JSON.stringify(event));
    this.bump();
    return event;
  }
  save(input, id) {
    const previous = id ? this.get(id) : undefined;
    if (id) requireValue(previous, 404, 'Occasion not found.');
    else
      requireValue(
        this.list().filter((e) => e.status !== 'ended').length < 20,
        409,
        'Finish an occasion before adding more.',
      );
    const event = { ...validateEvent(input, previous, this.clock()), id: id ?? randomUUID() };
    checkOverlap(event, this.list(), id);
    return this.put(event);
  }
  end(id) {
    const event = this.get(id);
    requireValue(event, 404, 'Occasion not found.');
    this.put({ ...event, endsAt: Math.min(this.clock(), event.endsAt), status: 'ended' });
    this.cleanup();
  }
  delete(id) {
    // Withdraw from manifests first; a file deletion failure leaves an ended row for cleanup to retry.
    this.end(id);
    this.db.prepare('DELETE FROM occasions WHERE id=?').run(id);
    this.bump();
  }
  manifest() {
    return manifest(this.list(), this.revision(), this.clock());
  }
  cleanup() {
    const now = this.clock();
    let removed = 0;
    for (const event of this.list())
      if (event.endsAt <= now || event.status === 'ended') {
        // Files first: failed deletions are retried rather than silently marked complete.
        this.removeDirectory(event.id);
        if (!event.purgedAt) {
          this.put({ ...event, status: 'ended', assets: [], purgedAt: now });
          removed++;
        }
      }
    this.db.prepare('DELETE FROM sessions WHERE expires<=?').run(now);
    this.db.prepare('DELETE FROM pairings WHERE expires<=?').run(now);
    return removed;
  }
  reconcileMedia() {
    for (const entry of readdirSync(this.media, { withFileTypes: true })) {
      const event = this.get(entry.name);
      if (!event || !entry.isDirectory()) {
        rmSync(join(this.media, entry.name), { force: true, recursive: true });
        continue;
      }
      const keep = new Set(
        event.assets.flatMap((a) => [a.id + '.jpg', a.id + '.thumb.jpg', a.id + '.original']),
      );
      for (const file of readdirSync(join(this.media, entry.name)))
        if (!keep.has(file)) rmSync(join(this.media, entry.name, file), { force: true });
    }
  }
  removeDirectory(id) {
    requireValue(/^[a-f0-9-]{36}$/.test(id), 400, 'Invalid occasion identifier.');
    rmSync(join(this.media, id), { recursive: true, force: true });
  }
  session() {
    const token = secret();
    this.db.prepare('INSERT INTO sessions VALUES(?,?)').run(digest(token), this.clock() + 12 * 3600000);
    return token;
  }
  authenticated(token) {
    return (
      !!token &&
      !!this.db
        .prepare('SELECT token FROM sessions WHERE token=? AND expires>?')
        .get(digest(token), this.clock())
    );
  }
  logout(token) {
    this.db.prepare('DELETE FROM sessions WHERE token=?').run(digest(token ?? ''));
  }
  pairing() {
    this.cleanup();
    const challenge = secret();
    let code;
    do {
      code = String(randomInt(1000000)).padStart(6, '0');
    } while (this.db.prepare('SELECT code FROM pairings WHERE code=?').get(code));
    const expires = this.clock() + 5 * 60000;
    this.db
      .prepare('INSERT INTO pairings VALUES(?,?,?,?,?)')
      .run(code, digest(challenge), expires, null, null);
    return { code, challenge, expires };
  }
  approve(code, name) {
    const pair = this.db.prepare('SELECT * FROM pairings WHERE code=? AND expires>?').get(code, this.clock());
    requireValue(pair && !pair.device, 400, 'Code is invalid, expired or already approved.');
    requireValue(this.devices().filter((d) => !d.revoked).length < 5, 409, 'Revoke an unused TV first.');
    const id = randomUUID(),
      token = secret();
    this.db.exec('BEGIN IMMEDIATE');
    try {
      this.db
        .prepare('INSERT INTO devices(id,name,token) VALUES(?,?,?)')
        .run(id, String(name || 'Living room TV').slice(0, 60), digest(token));
      this.db.prepare('UPDATE pairings SET device=?,token=? WHERE code=?').run(id, token, code);
      this.db.exec('COMMIT');
    } catch (e) {
      this.db.exec('ROLLBACK');
      throw e;
    }
    return { id };
  }
  claim(code, challenge) {
    const row = this.db
      .prepare('SELECT * FROM pairings WHERE code=? AND challenge=? AND expires>?')
      .get(code, digest(challenge), this.clock());
    requireValue(row, 410, 'Pairing expired.');
    if (!row.token) return { pending: true };
    this.db.prepare('DELETE FROM pairings WHERE code=?').run(code);
    return { id: row.device, token: row.token };
  }
  device(token) {
    const row = this.db.prepare('SELECT * FROM devices WHERE token=? AND revoked=0').get(digest(token ?? ''));
    requireValue(row, 401, 'TV is not paired.');
    this.db.prepare('UPDATE devices SET seen=? WHERE id=?').run(this.clock(), row.id);
    return row;
  }
  ack(id, data) {
    requireValue(['ready', 'active', 'idle', 'error'].includes(data.state), 400, 'Invalid TV state.');
    this.db.prepare('UPDATE devices SET ack=?,seen=? WHERE id=?').run(
      JSON.stringify({
        state: data.state,
        revision: Number(data.revision) || 0,
        eventId: String(data.eventId ?? '').slice(0, 36),
        at: this.clock(),
      }),
      this.clock(),
      id,
    );
  }
  devices() {
    return this.db
      .prepare('SELECT id,name,seen,ack,revoked FROM devices')
      .all()
      .map((d) => ({ ...d, ack: d.ack ? JSON.parse(d.ack) : null }));
  }
  revoke(id) {
    this.db.prepare('UPDATE devices SET revoked=1 WHERE id=?').run(id);
    this.bump();
  }
  touch(id) {
    this.db.prepare('UPDATE devices SET seen=? WHERE id=? AND revoked=0').run(this.clock(), id);
  }
  close() {
    this.db.close();
  }
}
