import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import sharp from 'sharp';
import { Store } from '../server/store.mjs';
import { preparePhoto } from '../server/media.mjs';
import { photoLayout } from '../photo-layout.mjs';

function fixture(t) {
  const directory = mkdtempSync(join(tmpdir(), 'occasion-photo-'));
  const store = new Store(directory);
  const event = store.save({
    title: 'Photo checks',
    message: 'Welcome home',
    template: 'custom',
    color: '#b9cebe',
    effect: 'none',
    mode: 'album',
    status: 'draft',
    startsAt: Date.now(),
    endsAt: Date.now() + 6 * 3600000,
    intervalSeconds: 90,
  });
  t.after(() => {
    store.close();
    rmSync(directory, { recursive: true, force: true });
  });
  return { store, event };
}
test('large photos are cropped to the largest matching area; existing 16:9 art is not cut', () => {
  const landscape = photoLayout(4000, 3000);
  assert.deepEqual(
    [landscape.left, landscape.top, landscape.cropWidth, landscape.cropHeight],
    [0, 375, 4000, 2250],
  );
  assert.equal(landscape.upscaled, false);
  assert.equal(landscape.warning, '');
  assert.equal(photoLayout(2560, 1440).cropped, false);
});
test('small images and small portrait crops get an honest enlargement warning', () => {
  const small = photoLayout(640, 360);
  assert.equal(small.upscaleFactor, 2);
  assert.equal(small.severity, 'upscaled');
  assert.match(small.warning, /cannot recover missing detail/);
  const tiny = photoLayout(64, 36);
  assert.equal(tiny.severity, 'low-resolution');
  assert.equal(tiny.upscaleFactor, 20);
  const portrait = photoLayout(1080, 1920);
  assert.equal(portrait.upscaled, true);
  assert.equal(portrait.cropHeight, 608);
});
test('automatic enlargement produces a full TV-sized JPEG and persists its warning', async (t) => {
  const { store, event } = fixture(t);
  const input = await sharp({ create: { width: 640, height: 360, channels: 3, background: '#b8caa3' } })
    .png()
    .toBuffer();
  const asset = await preparePhoto(store, event.id, input);
  const output = readFileSync(join(store.media, event.id, asset.id + '.jpg'));
  const meta = await sharp(output).metadata();
  assert.equal(meta.width, 1280);
  assert.equal(meta.height, 720);
  assert.equal(asset.quality.upscaled, true);
  assert.equal(store.get(event.id).assets[0].quality.severity, 'upscaled');
});
test('automatic cropping honours the focal point instead of stretching a portrait', async (t) => {
  const { store, event } = fixture(t);
  const input = await sharp({ create: { width: 512, height: 1024, channels: 3, background: '#ff0000' } })
    .composite([
      {
        input: await sharp({ create: { width: 512, height: 512, channels: 3, background: '#0000ff' } })
          .png()
          .toBuffer(),
        top: 512,
        left: 0,
      },
    ])
    .png()
    .toBuffer();
  const top = await preparePhoto(store, event.id, input, 0.5, 0),
    bottom = await preparePhoto(store, event.id, input, 0.5, 1);
  const pixel = async (asset) =>
    sharp(join(store.media, event.id, asset.id + '.jpg'))
      .extract({ left: 640, top: 360, width: 1, height: 1 })
      .raw()
      .toBuffer();
  const a = await pixel(top),
    b = await pixel(bottom);
  assert.ok(a[0] > 200 && a[2] < 30, 'Top crop retains the red subject');
  assert.ok(b[2] > 200 && b[0] < 30, 'Bottom crop retains the blue subject');
});
test('photo orientation is applied before measuring quality and preparing the crop', async (t) => {
  const { store, event } = fixture(t);
  const input = await sharp({ create: { width: 1600, height: 900, channels: 3, background: '#668899' } })
    .withMetadata({ orientation: 6 })
    .jpeg()
    .toBuffer();
  const asset = await preparePhoto(store, event.id, input);
  assert.equal(asset.quality.sourceWidth, 900);
  assert.equal(asset.quality.sourceHeight, 1600);
  assert.equal(asset.quality.upscaled, true);
  const result = await sharp(join(store.media, event.id, asset.id + '.jpg')).metadata();
  assert.equal(result.orientation, undefined);
  assert.equal(result.width, 1280);
  assert.equal(result.height, 720);
});
