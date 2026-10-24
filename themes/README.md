# Themes

This public fork offers Mountain Glass and the original app-colour Home. Hold OK
on Settings and toggle **Mountain Glass theme** to switch. The two looks retain
separate dock and app-order preferences; installed apps and Home visibility are shared.

| Theme | What it does | Screenshot |
|---|---|---|
| `mountain-glass/` | A mountain valley at dusk, seven apps in a light glass dock, a red-hour clock, date pill and app search in this fork | ![Mountain Glass on the real TV](../docs/mountain-home.png) |

Each theme folder contains `config.json` and an optional `wallpaper.jpg`. The
build bundles this directory as assets. Mountain Glass is the default for this
public edition; set `mountain_glass_default` to `false` in
[`config.xml`](../launcher/app/src/main/res/values/config.xml) to keep the classic
look for a new installation.

## Upstream compatibility

The configuration uses only existing keys documented in upstream's
[`themes/README.md`](https://github.com/Gathin23/cleanyourtv/blob/main/themes/README.md).
`columns` and `dock_apps` configure this fork's Mountain Glass preset. The other
keys describe its portable appearance on current upstream: static wallpaper,
glass dock, no hero title or app-colour background, round controls and calm motion.
The fork's clock colours, date layout and app search are native launcher code;
stock upstream keeps its standard clock and controls when using this config.

## Before an upstream theme PR

Follow upstream's [theme workflow](https://github.com/Gathin23/cleanyourtv/blob/main/docs/workflows/theme.md):

- Use supported keys, generic streaming-app defaults, and artwork you can redistribute.
- Keep the wallpaper dark, 1920×1080 and under about 300 KB.
- Capture Home on a real TV, focused on the dock. Save `screenshot.jpg` at
  1280×720 and under 300 KB; show it in this table.
- Run upstream's theme and repository tests. A theme without that real-TV
  screenshot is incomplete for upstream; a generated picture or desktop render
  cannot replace the capture.
- Submit any launcher changes separately. This fork's full restyle is not an
  upstream theme-only contribution.

Real-TV Home and search captures are in the root README. The complete launcher is intended for SHOWCASE.md; no upstream theme-only submission is planned.

Validate the desktop assets with:

```sh
python tools/check-public-theme.py themes/mountain-glass
```

Pass `--upstream-doc /path/to/cleanyourtv/themes/README.md` to verify every key
against an upstream checkout, and `--require-tv-screenshot` before submission.
The latter correctly fails until the real-TV capture is added.
