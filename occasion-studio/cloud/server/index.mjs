import { fileURLToPath } from 'node:url';
import { createApp } from './app.mjs';
const production = process.env.NODE_ENV === 'production';
const app = createApp({
  dataDir: process.env.DATA_DIR ?? './data',
  passwordHash: process.env.OWNER_PASSWORD_HASH,
  adminOrigin: process.env.ADMIN_ORIGIN ?? 'http://127.0.0.1:8787',
  production,
  trustLoopbackProxy: production,
  distDir: fileURLToPath(new URL('../dist', import.meta.url)),
});
const port = Number(process.env.PORT ?? 8787);
app.server.listen(port, '127.0.0.1', () =>
  console.log(`Occasion Studio listening on http://127.0.0.1:${port}`),
);
for (const signal of ['SIGTERM', 'SIGINT']) process.on(signal, () => app.close().then(() => process.exit(0)));
