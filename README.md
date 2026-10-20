# Redmi Glass Launcher

An Android TV launcher I customised for my **Redmi MiTV-AXFR0 running Android TV 11**.
It adds remote app organisation, Play Next and glass controls to
[Gathin23's original launcher](https://github.com/Gathin23/cleanyourtv).

## On the TV

| Home | Play Next |
|---|---|
| ![Home with the Welcome home greeting, app-coloured background and four-app dock](docs/custom-home.png) | ![Play Next with large programme cards and the floating glass selection](docs/custom-play-next.png) |

Screenshots are from the Redmi test TV. Streaming apps decide which entries appear in Play Next.

## What I changed

- **App and dock editing:** hold OK to move apps, replace dock favourites or remove Home shortcuts.
- **Horizontal App library:** browse installed TV and ordinary Android apps, including shortcuts removed from Home.
- **Play Next shelf:** resume links, programme artwork and progress from Android's app-published TV provider.
- **Glass controls:** frosted menus, a rounded Play Next panel and Inputs/Wi-Fi/Settings buttons that follow the background.
- **Navigation:** continuous Left/Right movement through app rows, a new highlight at row crossings, stable Play Next transitions and a held-Up stop at the dock.
- **Background and greeting:** colours sampled from the selected app or programme, plus a finite Welcome home word animation.
- **Desktop diagnostics:** a separate, read-only TV task manager for memory, CPU, storage and processes.

This is the public launcher-only version on `main`. My
[personal build](https://github.com/harshsingh0930/tv-vibehack/tree/custom/tv-home)
is maintained separately and includes additional screensaver experiments.

## Build this version

Use JDK 17, Android SDK platform 34 and build-tools 34.0.0.

Windows:

```powershell
.\launcher\build.ps1 -JavaHome 'C:\Java\jdk-17' -SdkPath 'C:\Android\Sdk'
```

macOS/Linux, with `JAVA_HOME` and `ANDROID_HOME` set:

```sh
cd launcher
./gradlew assembleDebug
```

The APK is `launcher/app/build/outputs/apk/debug/app-debug.apk`.
See [build, installation and permissions](docs/CUSTOM-FORK.md) and
[TV task manager setup](tools/README.md). The build command does not install an app.
The inherited debloat/install scripts can change TV packages and the default Home.

## Hardware and data use

Developed on Redmi MiTV-AXFR0, Android 11/API 30; other TVs have not been verified.
The public source builds independently of my personal checkout.

Play Next reads links and progress that installed apps publish locally. Programme
artwork can be downloaded on demand over HTTPS; unavailable art falls back to an app icon.
There is no analytics, account sign-in or persistent background service.
[Full details and limitations](docs/CUSTOM-FORK.md#permissions-and-data-use).

## Credits and upstream contributions

Original launcher and debloat tools by [Gathin23 and contributors](https://github.com/Gathin23/cleanyourtv).
My custom Home-screen additions are maintained in this fork. The code retains the
[MIT licence](LICENSE); Outfit retains its [SIL Open Font Licence](launcher/FONT-LICENSE-OFL.txt).

The smaller installed-app library was accepted upstream in
[upstream PR #305](https://github.com/Gathin23/cleanyourtv/pull/305).

---

## Original project README

The original guide below is retained from the fork's starting point (`19f3c22`).
It describes the original project. For this custom launcher, use the build notes above;
for the author's latest guide, visit [cleanyourtv](https://github.com/Gathin23/cleanyourtv).

# tv-vibehack

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

- Pitch-black background, like Apple TV 4K.
- A dock at the bottom in a lighter rounded band with the four apps you choose. The shelf above
  shows the focused dock app's name in large type with an ambient glow sampled from its banner.
  Press Down and the dock slides up to reveal the rest of your apps in a grid.
- One translucent glass highlight springs from tile to tile. The focused tile lifts and shows its
  name.
- Frosted pills top right: **Inputs** (a dropdown of HDMI ports, AV and Live TV), **Wi-Fi**
  (shows the network name) and **Settings**. Greeting, a large thin clock and the date top left,
  set in [Outfit](https://fonts.google.com/specimen/Outfit).
- Deterministic D-pad navigation, last focused tile remembered, staggered entrance animation.
  No blur, no video, no network, no background services.

Configure it in [`launcher/app/src/main/res/values/config.xml`](launcher/app/src/main/res/values/config.xml):
dock order, hidden apps, Live TV package and columns. Build and install with

```bash
TV_HOST=192.168.1.50 launcher/build-and-install.sh
```

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
