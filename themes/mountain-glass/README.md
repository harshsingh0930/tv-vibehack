# Mountain Glass

An offline mountain wallpaper and seven-slot dock inspired by the supplied TV
reference. The public launcher adds a live red-hour clock, a date pill, round
Search and Settings buttons, a network pill, and a light glass dock. The centre
stays clear of the classic greeting and large focused-app title.

Hold OK on Settings to turn **Mountain Glass theme** off and return to the
classic app-colour Home. Dock and grid orders are saved separately for each look.
Play Next, app reordering, App Library and the existing app menus remain available
below the dock. Inputs is available by holding Search or through Launcher options.

The default order is Netflix, Disney+, Prime Video, YouTube, Apple TV, Spotify and
Play Store. All tiles open actual installed apps, using the apps' own artwork.
Unavailable defaults can use other installed apps; an intentionally emptied slot
stays an Add app slot. Android TV uses Play Store for the final store position;
Apple's App Store is not an Android TV app. No streaming apps or logos are bundled.

## Files

- [`config.json`](config.json): supported upstream theme keys. Edit `dock_apps`
  and `columns` here before building this fork.
- [`wallpaper.jpg`](wallpaper.jpg): original generated landscape, 1920×1080 JPEG.
- [`CREDITS.md`](CREDITS.md): artwork provenance and full generation prompt.

The public launcher bundles these assets and draws the clock, date, controls and
dock in Android views. Wallpaper decoding uses RGB_565, approximately 4 MiB at
1920×1080. It has no wallpaper download or rotation service. App search filters
installed app labels and package names locally.

On current upstream the config provides the supported wallpaper/dock look;
the multicolour clock, date pill and Search control require this fork's code.
See [theme compatibility](../README.md#upstream-compatibility).

## Preview without replacing Home

Build with JDK 17 and Android SDK 34:

```powershell
cd launcher
.\gradlew.bat -PmountainPreview=true assembleDebug
```

The result is `launcher/build/mountain-preview/outputs/apk/debug/app-debug.apk`.
It uses `com.harshsingh.redmiglass.preview`, has a separate data directory,
does not claim the HOME category and skips cached-process cleanup. Build only
while the TV is in use. Installation and explicit launch can be done later;
they are not part of the build. No default-home, accessibility or screensaver
settings need to change.

## Validation

Desktop checks cover dock fallback ordering, reserved pinned slots, intentional
empty slots, classic geometry and seven-column tile sizing. The normal and
preview APK manifests are checked separately.

Real-TV preview on Redmi MiTV-AXFR0 (Android TV 11) verified the Home layout, dock navigation and filtered search with installed app icons. See the [Home and search screenshots](../../README.md). Both the normal debug build and its 11 JVM tests pass. Broader checks such as 12/24-hour clock changes and every settings combination are not claimed here. This look is submitted as a launcher showcase, rather than an upstream theme-only contribution.
