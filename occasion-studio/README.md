# Occasion Studio

An optional self-hosted dashboard for scheduling photographs, messages and native effects on the personal launcher. Phone/laptop access uses public HTTPS; WireGuard is optional.

![Occasion Studio desktop editor with peacock feather preview](docs/screenshots/dashboard-desktop.png)

## Features

- Schedule a photo or six-photo album with a greeting; edit/delete occasions and reject overlapping schedules.
- Automatic 16:9 crop and focal-point controls, with warnings for small images.
- 13 animated effects, per-photo overrides and a no-effects option; live previews on desktop and mobile.
- Owner login, approved TV pairing and public HTTPS access through your own server.
- Home/screensaver support in this personal fork, cached current/next events and offline expiry.

Expired/deleted occasion originals, display JPEGs and thumbnails are removed on the server. TV copies are pruned by the active Home/screensaver controller, or when next opened if the TV/app was off. External backup retention is the server owner's responsibility.

## Screenshots

Browser captures use an isolated demo account and original demo artwork. They show the current website preview; they are not screenshots of the proposed standalone add-on.

| Peacock feather shimmer | Temple bells and aura |
|---|---|
| ![Peacock feather preview](docs/screenshots/effect-peacock.png) | ![Temple bell preview](docs/screenshots/effect-temple.png) |
| **Diya flames and sparks** | **Holi gulal drift** |
| ![Diya preview](docs/screenshots/effect-diya.png) | ![Gulal preview](docs/screenshots/effect-gulal.png) |
| **Floating flowers** | **Marigold and rose rain** |
| ![Flower preview](docs/screenshots/effect-flowers.png) | ![Marigold preview](docs/screenshots/effect-marigold.png) |

[All effects, mobile editor, schedule controls and real-TV captures](docs/screenshots/README.md).

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
