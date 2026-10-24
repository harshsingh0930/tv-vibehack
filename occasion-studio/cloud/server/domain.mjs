import { createHash, randomBytes, scryptSync, timingSafeEqual } from 'node:crypto';
import { normaliseEffect, validEffect } from '../effects.mjs';
export { normaliseEffect } from '../effects.mjs';
export const SCENE_LIMIT = 7 * 1024 * 1024;
export const PHOTO_LIMIT = 6;
export const TEMPLATES = {
  birthday: { label: 'Birthday', color: '#e8b5bf', effect: 'confetti', message: 'Happy birthday' },
  anniversary: {
    label: 'Anniversary',
    color: '#efc2a6',
    effect: 'starlight',
    message: 'Another beautiful year together',
  },
  diwali: { label: 'Diwali', color: '#edb96e', effect: 'diya', message: 'A home full of light' },
  holi: { label: 'Holi', color: '#c7a9e9', effect: 'gulal', message: 'Let the colours in' },
  navratri: {
    label: 'Navratri',
    color: '#efad79',
    effect: 'marigold',
    message: 'Wishing you a joyful Navratri',
  },
  newyear: {
    label: 'New Year',
    color: '#a7c7dc',
    effect: 'confetti',
    message: 'Here’s to a wonderful new year',
  },
  custom: {
    label: 'Just because',
    color: '#b9cebe',
    effect: 'sparkles',
    message: 'Something worth celebrating',
  },
};
export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}
export function requireValue(condition, status, message) {
  if (!condition) throw new ApiError(status, message);
}
export const digest = (value) => createHash('sha256').update(value).digest('hex');
export const secret = () => randomBytes(32).toString('base64url');
export function passwordHash(password, salt = randomBytes(16).toString('hex')) {
  return `${salt}:${scryptSync(password, salt, 64).toString('hex')}`;
}
export function validPassword(password, hash) {
  try {
    if (typeof password !== 'string' || password.length > 1024) return false;
    const [salt, encoded] = hash.split(':');
    const expected = Buffer.from(encoded, 'hex'),
      actual = scryptSync(password, salt, 64);
    return expected.length === actual.length && timingSafeEqual(actual, expected);
  } catch {
    return false;
  }
}
export function validateEvent(input, previous, now = Date.now()) {
  requireValue(input && typeof input === 'object', 400, 'An occasion is required.');
  const title = String(input.title ?? '').trim(),
    message = String(input.message ?? '').trim();
  const startsAt = Number(input.startsAt),
    endsAt = Number(input.endsAt);
  requireValue(title.length > 0, 400, 'Enter an occasion name.');
  requireValue(title.length <= 80, 400, 'Occasion name must be 80 characters or fewer.');
  requireValue(message.length <= 140, 400, 'Words for the screen must be 140 characters or fewer.');
  requireValue(
    Number.isSafeInteger(startsAt) &&
      Number.isSafeInteger(endsAt) &&
      startsAt >= 0 &&
      endsAt > now &&
      endsAt > startsAt,
    400,
    'Choose a future end after the start.',
  );
  requireValue(endsAt - startsAt <= 7 * 86400000, 400, 'An occasion can last up to seven days.');
  requireValue(
    previous?.status !== 'ended' && (!previous || previous.endsAt > now),
    410,
    'This occasion has ended. Create a new one.',
  );
  requireValue(Object.hasOwn(TEMPLATES, input.template), 400, 'Choose an occasion template.');
  requireValue(/^#[a-fA-F0-9]{6}$/.test(input.color), 400, 'Choose a valid colour.');
  const effect = normaliseEffect(input.effect);
  requireValue(validEffect(effect), 400, 'Choose a supported effect.');
  requireValue(
    ['fixed', 'album'].includes(input.mode) && ['draft', 'scheduled'].includes(input.status),
    400,
    'Invalid wallpaper mode or occasion status.',
  );
  const intervalSeconds = Number(input.intervalSeconds ?? 90);
  requireValue(
    Number.isInteger(intervalSeconds) && intervalSeconds >= 30 && intervalSeconds <= 600,
    400,
    'Photo timing must be between 30 and 600 seconds.',
  );
  requireValue(!input.timezone || input.timezone === 'Asia/Kolkata', 400, 'This TV uses Asia/Kolkata time.');
  return {
    title,
    message,
    startsAt,
    endsAt,
    template: input.template,
    color: input.color,
    effect,
    mode: input.mode,
    status: input.status,
    intervalSeconds,
    timezone: 'Asia/Kolkata',
    assets: previous?.assets ?? [],
  };
}
export function checkOverlap(candidate, events, id) {
  if (candidate.status === 'scheduled') {
    const conflict = events.find(
      (e) =>
        e.id !== id &&
        e.status === 'scheduled' &&
        candidate.startsAt < e.endsAt &&
        candidate.endsAt > e.startsAt,
    );
    requireValue(
      !conflict,
      409,
      conflict
        ? `This overlaps "${conflict.title}". Choose a different time or end that occasion first.`
        : 'Schedule conflict.',
    );
  }
}
export function manifest(events, revision, now = Date.now()) {
  const scheduled = events
    .filter((e) => e.status === 'scheduled' && e.endsAt > now)
    .sort((a, b) => a.startsAt - b.startsAt);
  const scenes = [scheduled.find((e) => e.startsAt <= now), scheduled.find((e) => e.startsAt > now)]
    .filter(Boolean)
    .map((e) => ({
      id: e.id,
      title: e.title,
      message: e.message,
      startsAt: e.startsAt,
      endsAt: e.endsAt,
      color: e.color,
      effect: normaliseEffect(e.effect),
      mode: e.mode,
      intervalSeconds: e.intervalSeconds,
      assets: (e.mode === 'fixed' ? e.assets.slice(0, 1) : e.assets).map((a) => ({
        id: a.id,
        sha256: a.sha256,
        bytes: a.bytes,
        width: 1280,
        height: 720,
        path: `/api/tv/media/${e.id}/${a.id}`,
        ...(a.effect ? { effect: normaliseEffect(a.effect) } : {}),
      })),
    }));
  return { schema: 1, revision, serverTime: now, scenes };
}
