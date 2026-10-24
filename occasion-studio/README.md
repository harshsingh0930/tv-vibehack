# Occasion Studio

An optional self-hosted dashboard for scheduling photographs, messages and native effects on the personal launcher. Phone/laptop access uses public HTTPS; WireGuard is optional.

## What it does

- Single wallpaper or six-photo albums with automatic 16:9 crop, focal-point selection and small-image warnings.
- Per-photo effects, explicit no-effects/inherit choices, India-time schedules, edit/delete and overlap protection.
- Home and screensaver integration, cached active/next events, local expiry while offline, and automatic return to the ordinary background.
- Owner login, short-lived pairing, separate read-only TV tokens and SSE updates.
- Original uploads, display JPEGs and thumbnails are deleted when an occasion expires or is deleted. Cleanup runs every minute and on startup; unused drafts expire too. TV event images are pruned by the visible Home/screensaver controller, or when next opened if the app/TV was off. Photo retention in external backups is an administrator responsibility.

## Build

Requires Node 24 and JDK 17/Android SDK 34 for the launcher.

```
cd occasion-studio/cloud
npm ci
npm run build
npm test
npm run test:browser
npm run password
```

Put the generated password hash in an untracked environment file along with `ADMIN_ORIGIN`, `DATA_DIR` and `PORT`. For local use, `ADMIN_ORIGIN=http://127.0.0.1:8787` and `PORT=8787`; run `npm run server`. Never commit environment files, passwords, private keys, pairing tokens, databases or uploaded media.

For production, use `NODE_ENV=production`, a public HTTPS `ADMIN_ORIGIN`, Caddy, and the provided systemd application/cleanup units. Set `PUBLIC_HOST` to your hostname, adapt user/paths to your server and install dependencies with `npm ci --omit=dev`. Read deployment templates before applying them. The server binds to loopback; the reverse proxy handles public HTTPS. If a VPN already uses UDP 443, keep HTTP/3 disabled as shown in the Caddy template.

Build the launcher from the repository's `launcher/` directory using its documented build workflow. Hold Settings, open Screensaver, then Cloud occasions. Enter your own server address and approve the displayed pairing code in the dashboard. Existing installations must be updated with the same signing key to retain preferences.

`Package-Oracle.ps1` prepares a server-only archive; it does not deploy it. Keep uploads/state on a persistent directory outside the application. Verify deletion and any cloud backup retention policy on your own server.

No installation-specific hostname, server address, credentials, private uploaded photographs or device reports are included. The personal screensaver's existing licensed fallback images are separate from occasion uploads. See their existing credits and font licences.
