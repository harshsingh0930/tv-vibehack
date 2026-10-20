# Redmi glass launcher

An Android TV launcher fork with remote app and dock editing, a horizontal App library,
Play Next, artwork-matched backgrounds and glass controls. Built and tested on a
**Redmi MiTV-AXFR0 running Android TV 11**. Other models have not been verified.

This is the `custom/tv-home` branch of [harshsingh0930/tv-vibehack](https://github.com/harshsingh0930/tv-vibehack/tree/custom/tv-home),
based on [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv). The original project's
MIT licence and Outfit font licence are retained. Start with the
[custom build and validation notes](docs/CUSTOM-FORK.md) to build just the launcher;
the inherited debloat/install scripts below can also change TV packages and the default Home.
The [TV task manager](tools/README.md) is a separate, read-only desktop tool.

The launcher reads app-published Play Next entries from Android's TV provider and can fetch
their artwork over HTTPS when those entries are displayed. It has no analytics, account
sign-in or persistent background service. See the build notes for permissions and data use.

**Your TV, minus the ads.** Debloat any Android TV / Google TV over ADB and give it a clean,
tvOS-inspired launcher. Hand the prompt to Claude Code or any coding assistant, answer a few
questions about which apps you use, and it does the rest on the real TV.

| Stock home screen | After |
|---|---|
| ![Stock Google TV home with a sponsored ad](docs/stock-home.jpg) | ![tvOS-style launcher](docs/new-home.jpg) |

Everything is reversible: packages are disabled, never uninstalled, and a restore script puts the
stock launcher back.

## Quick start

1. On the TV: Settings → Device Preferences → About → press OK on **Build** seven times → back →
   Developer options → turn on **USB debugging** (and Network/ADB debugging if your TV shows it).
   Keep the TV on and on the same Wi-Fi as your computer.
2. Open Claude Code (or your assistant of choice) on your computer and paste the prompt from
   [`PROMPT.md`](PROMPT.md), filling in your TV model.
3. Accept the "Allow USB debugging?" prompt on the TV when told to, then answer the checklist
   questions (which apps you use, which four go in the dock).

That's it. The assistant backs up, debloats, builds and installs the launcher, and verifies each
step with screenshots from the TV. If you'd rather do it by hand, the pieces are below.

## What's in the repo

| Path | What it is |
|---|---|
| [`PROMPT.md`](PROMPT.md) | The prompt. Brand-neutral; the assistant discovers your TV's bloat. |
| [`launcher/`](launcher/) | The tvOS-style launcher. Edit `app/src/main/res/values/config.xml` for your dock, hidden apps and Live TV package, then run `build-and-install.sh`. |
| [`tools/tv.py`](tools/tv.py) | Pure-Python ADB client (shell, batch, push, pull). Works where the stock `adb` is blocked. |
| [`tools/b64push.py`](tools/b64push.py) | Uploads larger files (APKs) as base64 chunks with an md5 check. |
| [`tools/disable.sh`](tools/disable.sh), [`tools/restore.sh`](tools/restore.sh) | Apply or undo a debloat list. |
| [`examples/`](examples/) | Per-TV debloat lists. Add yours, see [CONTRIBUTING.md](CONTRIBUTING.md). |

## The launcher

A small native Android TV home screen (`com.gathin.tvhome`), built for sets with 1 GB of RAM:

- A dark background with soft light sampled from the focused app or Play Next artwork.
- **Welcome home** sits above the dock shelf in light Outfit lettering, with a pastel
  blue–lilac–rose–peach gradient and a slow fade-and-rise entrance, staggered word by word.
  It rests after the entrance
  and fades away when scrolling into Play Next or the app grid.
- A dock at the bottom in a lighter rounded band with the four apps you choose. The shelf above
  shows the focused dock app's name in large type with an ambient glow sampled from its banner.
  Press Down to scroll to large **Play Next** cards, followed by the rest of your apps in a grid.
- Hold **OK** on an app and choose **Move** in the compact frosted menu. Use the arrows, then **OK** to save or **Back**
  to cancel. The four chosen dock apps can be reordered within the dock; other apps can be
  reordered within the grid. The order survives returning Home and launcher restarts.
- The menu's **In dock** toggle lets you replace a dock app with a grid app, or select which
  dock slot a grid app should replace. The displaced app remains in the grid; nothing is uninstalled.
  Menu blur is a small, downsampled snapshot captured only when opening a menu.
- **App library** at the bottom of Home lists installed apps with TV or standard Android
  launcher entries, including apps hidden from Home. Choose an app to add it back. An empty
  dock slot also opens this library to choose any installed app for that slot.
  Its Outfit type, icon and frosted pill match the Wi-Fi and Settings controls.
- **Remove from Home** in the long-press menu hides the shortcut without uninstalling the app
  or clearing its data. Removing a dock app leaves an **Add app** slot. Hidden apps remain in
  App library and can be restored there.
- **Play Next** reads the Android TV provider's app-published resume links and progress. It
  needs **Read TV listings** permission; apps control which shows/channels they share. It
  does not scrape streaming accounts or generate history. Artwork loads on demand over HTTPS
  with a 2 MiB memory cache; an app icon appears when artwork is unavailable.
- The original translucent glass highlight glides between app tiles and Play Next artwork. Rounded
  programme images and captions sit in a separate rounded glass panel, matching the dock.
- Frosted pills top right: **Inputs** (a dropdown of HDMI ports, AV and Live TV), **Wi-Fi**
  (shows the network name) and **Settings**. Greeting, a large thin clock and the date top left,
  set in [Outfit](https://fonts.google.com/specimen/Outfit).
- D-pad navigation uses the original floating-glass spring motion for the dock and app grid, with
  the original glass styling, tile bounce, entrance animation and shelf fades. Entering Play Next
  hands the visible glass bounds to a separate direct tracker, preventing its horizontal scroll
  from fighting the spring. The same bounded bridge handles the return to app tiles; once the
  vertical scroll settles, normal app navigation resumes with the original spring.
  Horizontal movement continues into the next app row; the highlight starts at the new tile
  instead of flying diagonally across the grid. Holding Up while climbing from lower rows
  stops at the dock: release and press Up again to reach Inputs, Wi-Fi and Settings.
  Glass controls follow the changing background; their small blurred textures update on
  backdrop changes rather than through an idle screenshot loop. No video previews or
  persistent background services.
- On Android 11, cached-process cleanup is enabled three seconds after Home gains focus,
  with one additional pass ten seconds later to let apps finish stopping.
  Android decides which background processes can close; this is not force-stop and active
  services can remain or restart. Music, VPN/tunnel, download, casting, input and system-UID
  apps are excluded. Google Play services, the Store and Xiaomi web content are protected.
  Cached PatchWall is eligible if enabled. On the tested Redmi MiTV-AXFR0, PatchWall is disabled
  after verifying a fresh Xiaomi TV+ launch still plays Aajtak; its app data is retained.
  A successful API request does not guarantee that a vendor's firmware closes a process.
  Hold **OK** on **Settings** for the cleanup switch. Android 14+ restricts
  this API to the launcher's own processes, so cleanup requests are skipped there.

Configure it in [`launcher/app/src/main/res/values/config.xml`](launcher/app/src/main/res/values/config.xml):
dock order, hidden apps, Live TV package and columns. Build and install with

```bash
TV_HOST=192.168.1.50 launcher/build-and-install.sh
```

For Play Next, grant the permission if it was not requested interactively:
`adb -s YOUR_TV_SERIAL shell pm grant com.gathin.tvhome android.permission.READ_TV_LISTINGS`.
On Windows, `launcher\build.ps1` uses `JAVA_HOME` and `ANDROID_HOME` (or explicit
`-JavaHome` and `-SdkPath` arguments), and builds without installing or changing TV packages.

Needs JDK 17 and the Android SDK (platform 34, build-tools 34.0.0). On macOS:
`brew install openjdk@17 android-commandlinetools && sdkmanager "platforms;android-34" "build-tools;34.0.0"`.

## Talking to the TV by hand

```bash
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
export TV_HOST=192.168.1.50                                   # your TV's IP
tools/venv/bin/python -I tools/tv.py --probe                 # model, Android version, firmware
tools/venv/bin/python -I tools/tv.py 'pm list packages'      # one command
printf 'cmd1\ncmd2\n' | tools/venv/bin/python -I tools/tv.py --batch
tools/disable.sh examples/xiaomi-mi-tv-4a/debloat.txt        # apply a list
tools/restore.sh examples/xiaomi-mi-tv-4a/debloat.txt        # undo it, restore stock launcher
```

### Gotchas we hit

- **macOS: `adb connect` says "No route to host"** while the port is clearly open. Recent macOS
  treats the Google-signed `adb` as its own app under Local Network privacy and silently blocks it.
  `tools/tv.py` avoids the binary entirely.
- **No "wireless debugging" toggle** on some TVs (Xiaomi among them). USB debugging alone opens
  network ADB on port 5555.
- **Chained `pm` commands** (`pm disable a; pm disable b`) may silently stop after the first. Run
  one per shell call; `--batch` does this.
- **Large pushes hang** through the Python client. Use `tools/b64push.py` for APKs.
- **Disable the stock launcher only after the new one is installed**, or the Home button lands on
  a factory test app.
- **Third-party launchers** often charge for basics like a custom wallpaper. Hence `launcher/`.
- **No on-screen keyboard** after switching launchers (seen on Xiaomi): the default input method
  pointed at a Baidu IME that isn't installed. Fix with
  `tools/tv.py 'ime set com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME'`.

## Supported TVs

Anything running Android TV or Google TV: Xiaomi/Redmi, TCL, Hisense, Sony, OnePlus, Realme,
Nvidia Shield and most others. Not Samsung Tizen, LG webOS or Amazon Fire TV (different OS, no ADB
launcher replacement). Tested so far on the models in [`examples/`](examples/).

## Contributing

Add your TV's debloat list, improve the launcher, or sharpen the prompt. See
[CONTRIBUTING.md](CONTRIBUTING.md).

## Licence

MIT, see [`LICENSE`](LICENSE). The Outfit typeface is under the SIL Open Font License, see
`launcher/FONT-LICENSE-OFL.txt`.
