import sharp from 'sharp';
import { mkdir, writeFile, rm } from 'node:fs/promises';
import { join } from 'node:path';
import { randomUUID } from 'node:crypto';
import { ApiError, PHOTO_LIMIT, SCENE_LIMIT, digest, requireValue } from './domain.mjs';
import { photoLayout } from '../photo-layout.mjs';
sharp.cache({ memory: 32, files: 0, items: 20 });
sharp.concurrency(1);
export async function preparePhoto(store, id, input, focusX = 0.5, focusY = 0.5) {
  const event = store.get(id);
  requireValue(
    event && event.status !== 'ended' && event.endsAt > store.clock(),
    410,
    'This occasion has ended.',
  );
  requireValue(event.assets.length < PHOTO_LIMIT, 409, 'An album holds up to six photographs.');
  requireValue(input.length > 0 && input.length <= 20 * 1024 * 1024, 413, 'Use a photo smaller than 20 MiB.');
  requireValue(
    Number.isFinite(focusX) &&
      focusX >= 0 &&
      focusX <= 1 &&
      Number.isFinite(focusY) &&
      focusY >= 0 &&
      focusY <= 1,
    400,
    'Invalid crop position.',
  );
  let jpeg, thumb, quality;
  try {
    const image = sharp(input, { limitInputPixels: 40000000, animated: false, failOn: 'error' });
    const meta = await image.metadata();
    requireValue(
      ['jpeg', 'png', 'webp', 'heif', 'avif'].includes(meta.format) && (meta.pages ?? 1) === 1,
      415,
      'Use a still JPEG, PNG, WebP or supported HEIC photo.',
    );
    const swapsAxes = [5, 6, 7, 8].includes(meta.orientation);
    const width = swapsAxes ? meta.height : meta.width;
    const height = swapsAxes ? meta.width : meta.height;
    quality = photoLayout(width, height, focusX, focusY);
    // One decode/encode pass: an intermediate JPEG would discard detail before resizing.
    const cropped = image
      .rotate()
      .extract({ left: quality.left, top: quality.top, width: quality.cropWidth, height: quality.cropHeight })
      .resize(1280, 720, { fit: 'cover', kernel: sharp.kernel.lanczos3 });
    // Enlarge on Oracle, never on the TV. Preserve proportions and avoid aggressive halos.
    if (quality.upscaled) cropped.sharpen({ sigma: 0.5, m1: 0.4, m2: 1 });
    jpeg = await cropped.clone().jpeg({ quality: 92, mozjpeg: true }).toBuffer();
    if (jpeg.length > 1024 * 1024)
      jpeg = await cropped.clone().jpeg({ quality: 85, mozjpeg: true }).toBuffer();
    requireValue(jpeg.length <= 1024 * 1024, 413, 'Photo is too complex for this TV. Try another image.');
    thumb = await sharp(jpeg).resize(320, 180).jpeg({ quality: 75 }).toBuffer();
  } catch (error) {
    if (error instanceof ApiError) throw error;
    throw new ApiError(415, 'This photo could not be read. Try JPEG, PNG or WebP.');
  }
  const latest = store.get(id);
  requireValue(
    latest && latest.endsAt > store.clock() && latest.status !== 'ended',
    410,
    'This occasion has ended.',
  );
  requireValue(
    latest.assets.length < PHOTO_LIMIT &&
      latest.assets.reduce((n, a) => n + a.bytes, 0) + jpeg.length <= SCENE_LIMIT,
    413,
    'This album exceeds the TV photo budget.',
  );
  const asset = { id: randomUUID(), bytes: jpeg.length, sha256: digest(jpeg), focusX, focusY, quality };
  const directory = join(store.media, id);
  await mkdir(directory, { recursive: true, mode: 0o700 });
  const files = [
    [asset.id + '.original', input],
    [asset.id + '.jpg', jpeg],
    [asset.id + '.thumb.jpg', thumb],
  ];
  try {
    for (const [name, data] of files)
      await writeFile(join(directory, name), data, { mode: 0o600, flag: 'wx' });
    const current = store.get(id);
    requireValue(
      current && current.endsAt > store.clock() && current.status !== 'ended',
      410,
      'This occasion has ended.',
    );
    store.put({ ...current, assets: [...current.assets, asset] });
  } catch (error) {
    for (const [name] of files) await rm(join(directory, name), { force: true });
    throw error;
  }
  return asset;
}
