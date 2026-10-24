import { chromium } from '@playwright/test';
import { createApp } from '../server/app.mjs';
import { passwordHash } from '../server/domain.mjs';
import { mkdtempSync, mkdirSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import assert from 'node:assert/strict';
import sharp from 'sharp';
import { EFFECTS } from '../effects.mjs';
const dir = mkdtempSync(join(tmpdir(), 'occasion-browser-'));
const app = createApp({
  dataDir: dir,
  passwordHash: passwordHash('local-browser-test-123'),
  adminOrigin: 'http://127.0.0.1:18787',
  production: false,
  distDir: resolve('dist'),
});
const artifacts = resolve('../review');
mkdirSync(artifacts, { recursive: true });
let browser;
try {
  await new Promise((done) => app.server.listen(18787, '127.0.0.1', done));
  for (const name of ['Living room TV', 'Bedroom television with a deliberately long display name']) {
    const pairing = app.store.pairing();
    app.store.approve(pairing.code, name);
    const device = app.store.claim(pairing.code, pairing.challenge);
    app.store.device(device.token);
  }
  browser = await chromium.launch({ channel: process.env.BROWSER_CHANNEL ?? 'chrome', headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } });
  const errors = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await page.goto('http://127.0.0.1:18787');
  await page.getByLabel('Owner password').fill('local-browser-test-123');
  await page.getByRole('button', { name: 'Open studio' }).click();
  await page.getByRole('heading', { name: 'Send a little joy home.' }).waitFor();
  async function checkMessageSpacing() {
    const label = await page.locator('.message-field > .field-label').boundingBox();
    const box = await page.getByLabel('Words for the screen').boundingBox();
    assert.ok(box.y - label.y - label.height >= 8, 'Message label stays clearly above its text box');
  }
  await checkMessageSpacing();
  assert.ok(
    await page.getByLabel('Finishing touch').evaluate((select) => select.size > 1),
    'Effect choices remain open as a persistent list',
  );
  for (const text of [
    'Live design preview',
    'Home + screensaver',
    'A rich entrance, then a soft, unhurried loop. Your apps stay in front.',
    'Fuller on arrival.',
    'Gentle for the rest of the occasion.',
  ]) {
    assert.equal(
      await page.getByText(text, { exact: true }).count(),
      0,
      'Removed preview copy must stay absent',
    );
  }
  const deviceRows = page.locator('.device');
  assert.equal(await deviceRows.count(), 2);
  for (const row of await deviceRows.all()) {
    const detail = await row.locator('.device-details').boundingBox();
    const disconnect = row.getByRole('button', { name: 'Disconnect', exact: true });
    const bounds = await disconnect.boundingBox();
    assert.ok(bounds.y >= detail.y + detail.height - 1, 'Disconnect belongs below the device details');
    assert.notEqual(
      await disconnect.evaluate((el) => getComputedStyle(el).backgroundColor),
      'rgba(0, 0, 0, 0)',
    );
  }
  await page.evaluate(() => window.scrollTo(0, 900));
  await page.waitForTimeout(100);
  const sidebar = await page.locator('.sidebar').boundingBox();
  assert.ok(sidebar.y >= 20 && sidebar.y <= 28, 'Desktop sidebar remains pinned while scrolling');
  assert.ok(sidebar.y + sidebar.height <= 1100 - 20, 'Pinned sidebar stays inside the desktop viewport');
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.getByRole('button', { name: 'Pair a TV', exact: true }).click();
  await page.getByLabel('Code on your TV').fill('000000');
  await page.getByRole('button', { name: 'Connect TV', exact: true }).click();
  await page.getByRole('dialog').getByRole('alert').waitFor();
  await page.getByRole('button', { name: 'Close', exact: true }).click();
  await page.getByRole('button', { name: 'Diwali', exact: true }).click();
  await page.getByLabel('Occasion name').fill('');
  await page.getByLabel('Words for the screen').fill('A little light. A lot of love.');
  await page.getByRole('button', { name: 'Slowly changing album' }).click();
  const image = await sharp({ create: { width: 1920, height: 1080, channels: 3, background: '#624723' } })
    .composite([
      {
        input: Buffer.from(
          '<svg width="1920" height="1080"><defs><radialGradient id="g"><stop stop-color="#efb962"/><stop offset="1" stop-color="#111c1d"/></radialGradient></defs><rect width="1920" height="1080" fill="url(#g)"/><g fill="#f9d99a" opacity=".45"><circle cx="100" cy="100" r="7"/><circle cx="1400" cy="300" r="14"/><circle cx="1550" cy="150" r="8"/><circle cx="300" cy="550" r="12"/></g></svg>',
        ),
      },
    ])
    .png()
    .toBuffer();
  await page
    .locator('input[type=file]')
    .setInputFiles({ name: 'diwali-test.png', mimeType: 'image/png', buffer: image });
  const cropDialog = page.getByRole('dialog');
  await cropDialog.getByLabel('Occasion name').waitFor();
  const selectedPhoto = await cropDialog.getByAltText('Wallpaper crop preview').getAttribute('src');
  await cropDialog.getByRole('button', { name: 'Use this photograph' }).click();
  await cropDialog.getByRole('alert').filter({ hasText: 'Enter an occasion name.' }).waitFor();
  assert.equal(app.store.list().length, 0, 'Missing name must not create a partial event');
  assert.equal(
    await cropDialog.getByAltText('Wallpaper crop preview').getAttribute('src'),
    selectedPhoto,
    'Name validation must retain the selected photo',
  );
  await cropDialog.getByLabel('Occasion name').fill('Diwali, together at home');
  await page.getByRole('button', { name: 'Use this photograph' }).click();
  await page.getByRole('button', { name: 'Preview photo 1', exact: true }).waitFor();
  assert.equal(await page.getByText(/Photo ready at 1280/).count(), 0);
  assert.equal(app.store.list()[0].assets.length, 1);
  const smallPhoto = await sharp({ create: { width: 640, height: 360, channels: 3, background: '#998366' } })
    .png()
    .toBuffer();
  await page
    .locator('input[type=file]')
    .setInputFiles({ name: 'small-photo.png', mimeType: 'image/png', buffer: smallPhoto });
  await page.getByText('Small photo · enlargement needed', { exact: true }).waitFor();
  await page.getByRole('dialog').screenshot({ path: join(artifacts, 'studio-small-photo-warning.png') });
  await page.getByRole('button', { name: 'Use this photograph' }).click();
  await page.getByRole('button', { name: 'Preview photo 2', exact: true }).waitFor();
  assert.equal(app.store.list()[0].assets.length, 2);
  assert.equal(await page.getByRole('option', { name: 'Glass bubbles' }).count(), 0);
  for (const { id: effect } of EFFECTS.filter((effect) => effect.id !== 'none')) {
    await page.getByLabel('Finishing touch').selectOption(effect);
    await page.waitForTimeout(150);
    if (effect === 'peacock')
      await page.locator('.tv-preview').screenshot({ path: join(artifacts, 'peacock-feather-refined.png') });
    assert.ok(
      await page.locator('canvas.motion').evaluate((canvas) => {
        const ctx = canvas.getContext('2d');
        return (
          canvas.width > 0 &&
          [...ctx.getImageData(0, 0, canvas.width, canvas.height).data].some((v, i) => i % 4 === 3 && v > 0)
        );
      }),
      'Each richer effect must produce visible particles',
    );
    if (effect === 'diya')
      assert.ok(
        await page.locator('canvas.motion').evaluate((canvas) => {
          const dock = canvas.parentElement.querySelector('.mock-dock'),
            scale = canvas.width / canvas.clientWidth;
          const top = Math.floor(dock.offsetTop * scale),
            pixels = canvas.getContext('2d').getImageData(0, 0, canvas.width, canvas.height).data;
          let above = 0,
            behind = 0;
          for (let y = 0; y < canvas.height; y++)
            for (let x = 0; x < canvas.width; x++)
              if (pixels[(y * canvas.width + x) * 4 + 3] > 190) {
                if (y < top) above++;
                else behind++;
              }
          return above > 20 && behind === 0;
        }),
        'Diya artwork must be visible above the app dock, not hidden behind it',
      );
  }
  await page.getByLabel('Finishing touch').selectOption('sparkles');
  const photoEffect = page.getByLabel('Effect for selected photo');
  assert.ok(
    await photoEffect.evaluate((select) => select.size > 1),
    'Per-photo effect choices also remain open',
  );
  assert.equal(await photoEffect.inputValue(), '');
  await photoEffect.selectOption('diya');
  await page.waitForFunction(() => document.querySelector('canvas.motion').dataset.effect === 'diya');
  assert.equal(
    await page.getByLabel('Finishing touch').inputValue(),
    'sparkles',
    'Photo effect must retain unsaved occasion edits',
  );
  assert.equal(app.store.list()[0].assets[0].effect, 'diya');
  await page.getByRole('button', { name: 'Preview photo 2', exact: true }).click();
  await photoEffect.selectOption('none');
  await page.waitForFunction(() => document.querySelector('canvas.motion').dataset.effect === 'none');
  await page.waitForTimeout(100);
  assert.ok(
    await page
      .locator('canvas.motion')
      .evaluate(
        (canvas) =>
          ![...canvas.getContext('2d').getImageData(0, 0, canvas.width, canvas.height).data].some(
            (v, i) => i % 4 === 3 && v > 0,
          ),
      ),
    'No effects clears the overlay',
  );
  await page.getByLabel('Finishing touch').selectOption('flowers');
  assert.equal(
    await page.locator('canvas.motion').getAttribute('data-effect'),
    'none',
    'Explicit none overrides occasion changes',
  );
  await page.getByRole('button', { name: 'Preview photo 1', exact: true }).click();
  assert.equal(await photoEffect.inputValue(), 'diya');
  await photoEffect.selectOption('');
  await page.waitForFunction(() => document.querySelector('canvas.motion').dataset.effect === 'flowers');
  assert.equal(Object.hasOwn(app.store.list()[0].assets[0], 'effect'), false);
  await page.getByRole('button', { name: 'Preview photo 2', exact: true }).click();
  assert.equal(await photoEffect.inputValue(), 'none', 'Changing photos retains their independent choices');
  await photoEffect.selectOption('starlight');
  await page.waitForFunction(() => document.querySelector('canvas.motion').dataset.effect === 'starlight');
  assert.equal(app.store.list()[0].assets[1].quality.upscaled, true);
  await page.screenshot({ path: join(artifacts, 'studio-desktop.png'), fullPage: true });
  await Promise.all([
    page.waitForResponse(
      (response) =>
        response.url().includes('/api/occasions/') &&
        response.request().method() === 'PUT' &&
        response.status() === 200,
    ),
    page.getByRole('button', { name: 'Schedule occasion' }).click(),
  ]);
  assert.equal(await page.locator('.notice').count(), 0, 'Successful scheduling has no notification');
  assert.equal(app.store.list()[0].status, 'scheduled');
  await page.reload();
  await page.locator('.occasion-card').filter({ hasText: 'Diwali, together at home' }).click();
  await page.getByRole('button', { name: 'Preview photo 2', exact: true }).click();
  assert.equal(await photoEffect.inputValue(), 'starlight', 'Photo override survives scheduling and reload');
  const previewBounds = await page.locator('.tv-preview').boundingBox();
  const controlBounds = await page.getByLabel('Finishing touch').boundingBox();
  assert.ok(
    controlBounds.y >= previewBounds.y &&
      controlBounds.y + controlBounds.height <= previewBounds.y + previewBounds.height,
    'Desktop effect controls sit alongside the visible preview',
  );
  await page.getByRole('button', { name: 'Edit Diwali, together at home', exact: true }).click();
  await page.getByLabel('Words for the screen').fill('Updated together');
  await Promise.all([
    page.waitForResponse(
      (response) =>
        response.url().includes('/api/occasions/') &&
        response.request().method() === 'PUT' &&
        response.status() === 200,
    ),
    page.getByRole('button', { name: 'Schedule occasion' }).click(),
  ]);
  assert.equal(app.store.list()[0].message, 'Updated together');
  await Promise.all([
    page.waitForResponse(
      (response) =>
        response.url().endsWith('/start') &&
        response.request().method() === 'POST' &&
        response.status() === 200,
    ),
    page.getByRole('button', { name: 'Start now', exact: true }).click(),
  ]);
  assert.equal(await page.locator('.notice').count(), 0, 'Successful Start now has no notification');
  await page.route('**/api/occasions/*/start', (route) =>
    route.fulfill({
      status: 409,
      contentType: 'application/json',
      body: JSON.stringify({ error: 'This overlaps another occasion.' }),
    }),
  );
  await page.getByRole('button', { name: 'Start now', exact: true }).click();
  await page.getByRole('alert').filter({ hasText: 'This overlaps another occasion.' }).waitFor();
  await page.getByRole('button', { name: 'Dismiss', exact: true }).click();
  await page.unroute('**/api/occasions/*/start');
  await page.setViewportSize({ width: 390, height: 844 });
  await checkMessageSpacing();
  assert.equal(
    await page.locator('.sidebar').evaluate((el) => getComputedStyle(el).position),
    'static',
    'Mobile sidebar keeps normal page flow',
  );
  await page.screenshot({ path: join(artifacts, 'studio-mobile.png'), fullPage: true });
  const overflow = await page.evaluate(() => ({
    width: innerWidth,
    scroll: document.documentElement.scrollWidth,
    elements: [...document.querySelectorAll('body *')]
      .filter((el) => el.getBoundingClientRect().right > innerWidth + 1)
      .map((el) => ({ element: el.tagName, class: el.className, right: el.getBoundingClientRect().right }))
      .slice(0, 10),
  }));
  assert.ok(
    overflow.scroll <= overflow.width,
    'Mobile has no horizontal overflow: ' + JSON.stringify(overflow),
  );
  await page.getByRole('tab', { name: 'Screensaver' }).click();
  await page.screenshot({ path: join(artifacts, 'studio-screensaver-preview.png'), fullPage: true });
  page.once('dialog', (dialog) => dialog.accept());
  await page.getByRole('button', { name: 'End now', exact: true }).click();
  await page.getByText('This moment has finished.').waitFor();
  assert.equal(app.store.list()[0].assets.length, 0);
  page.once('dialog', (dialog) => dialog.dismiss());
  await page.getByRole('button', { name: 'Delete Diwali, together at home', exact: true }).click();
  assert.equal(app.store.list().length, 1, 'Cancel delete keeps the occasion');
  page.once('dialog', (dialog) => dialog.accept());
  await page.getByRole('button', { name: 'Delete Diwali, together at home', exact: true }).click();
  await page.getByText('Create your first occasion').waitFor();
  assert.equal(app.store.list().length, 0);
  assert.deepEqual(errors, []);
  console.log(
    'PASS: desktop/mobile login, edit, crop/upload, schedule, preview, end/purge and no browser exceptions.',
  );
} finally {
  if (browser) await browser.close();
  await app.close();
  rmSync(dir, { recursive: true, force: true });
}
