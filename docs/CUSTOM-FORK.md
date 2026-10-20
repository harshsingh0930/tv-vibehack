# Custom launcher variant

This fork preserves the original spring animation and adds app-library/dock editing, Play Next,
on-demand artwork, a one-shot blurred menu and optional cached-process cleanup. The upstream
contribution guide intentionally excludes blur, network access and content feeds. These additions
are maintained on `custom/tv-home`; the upstream contribution is limited to app organisation.
The original contribution guide and licences remain unchanged.

Version 3.5 adds a **Welcome home** greeting in the dock's empty shelf space. Light, licensed
Outfit lettering uses a pastel gradient and a finite fade/rise: Welcome appears first, with
home following 220 ms later. Each word takes 900 ms to settle. It hides in Play Next
and the app grid. The small text layer is rasterised for older TV GPUs; there is no looping
animation, network request or background worker. The greeting is a nonfocusable decoration and
does not alter D-pad targets. Its text is `welcome_home` in `res/values/strings.xml`.
The entrance waits for the dock's vertical return to finish. Real-TV checks confirmed visible
gradient lettering, completion of the word animation, cancellation while hidden in Play Next,
return to the dock, and preservation of saved Home choices. A reduced-motion/disabled-animation
system setting leaves the words fully visible immediately.

## Build

Use JDK 17, SDK platform 34 and build-tools 34.0.0. Gradle downloads the project's pinned
dependencies on the first build. Set `JAVA_HOME` and `ANDROID_HOME` to your installations.

```powershell
.\launcher\build.ps1 -JavaHome 'C:\Java\jdk-17' -SdkPath 'C:\Android\Sdk'
```

On macOS/Linux, set those variables and run `cd launcher && ./gradlew assembleDebug`.
The APK is `launcher/app/build/outputs/apk/debug/app-debug.apk`. All build output, local SDK
paths, TV reports, signing files and ADB keys stay out of version control.

For a manual update of an already configured TV, specify its exact ADB serial:

```sh
adb -s YOUR_SERIAL install -r launcher/app/build/outputs/apk/debug/app-debug.apk
adb -s YOUR_SERIAL shell pm grant com.gathin.tvhome android.permission.READ_TV_LISTINGS
```

These commands do not switch the Home default or disable other apps. The original
`build-and-install.sh` does both; review `STOCK_LAUNCHER` before using it on a new TV.
An update signed with another key cannot replace an existing installation. Keep your own
signing key private; uninstalling to change signatures would erase launcher preferences.

The example dock and tuner defaults in `config.xml` match the tested Redmi configuration.
Change the defaults there for another model. Saved dock/hidden-app choices override defaults.
Vendor-specific cleanup protection and eligibility also live in that file.

## Validation on real hardware

Tested on Redmi MiTV-AXFR0, Android 11/API 30, firmware
`RTT0.210630.002.2318 release-keys`, on 18 October 2026. The custom 3.3 build passed 13 alignment
checks covering initial Home, horizontal navigation, rapid Play Next transitions, dock return,
three Settings/Home cycles and App library Up. Saved Home choices remained unchanged.
The test compares actual glass/tile geometry, rather than only checking highlight visibility.

![Custom launcher dock](custom-home.png)
![Play Next focus](custom-play-next.png)

Debug assembly passes. Lint currently reports inherited conditions: no Leanback app-launcher
entry for the Home activity, an API-28 font style with minimum API 26, and target API 29 flagged
by the Play-target check. This is an ADB-installed launcher, not a claim of Play Store readiness.
Changing target SDK can change package visibility and cleanup behavior and needs separate testing.

Play Next depends on apps publishing links to the Android TV provider. Artwork is loaded only
when needed, with time/size limits and a bounded memory cache. Home cleanup asks Android to close
eligible cached processes; it does not force-stop apps or guarantee that services disappear.
No additional TV apps were disabled as part of preparing these Git branches.


## Online wallpaper + clock screensaver (custom 3.9)

Hold Settings on Home, then choose **Screensaver** to set the weather city or select
Temples, Festivals, or both. The native Android dream service appears as **Wallpaper + clock**
in the TV's screensaver selector. Selecting it preserves the system's existing inactivity
settings. Selecting the old provider restores its screensaver; Mi Wallpaper is not patched.

The screensaver discovers additional photographs using Wikimedia Commons' public search API.
Topic searches use Quality Image/Featured Picture collections, with an explicit assessment check,
landscape aspect-ratio and original-resolution filters, JPEG-only decoding, and complete author/
licence metadata. Temple searches use explicit Hindu-temple categories and a subject-name
check to reject unrelated landmarks. Online discovery is community-reviewed rather than individually hand-curated;
these filters cannot guarantee the composition of every future result. Original photos must be
at least 2400×1400; 1920-pixel thumbnails are downloaded and decoded to the display size, capped
at 1920×1080. No image-search web pages, advertisements or arbitrary hosts are loaded.

The **next three fully downloaded photographs** stay in a persistent queue. Displaying one
refills its slot from unseen search results. The downloaded queue, discovery cursors and up to
256 recent filenames survive restarts. Crop/edit variants are normalised to reduce duplicate
photographs. Metadata and search requests are bounded; a topic is queried at most once every
two minutes when more candidates are needed. A finite provider pool can eventually repeat:
when no unseen candidate is returned, the oldest eligible photo outside the last three is used.
Newly available eligible results can be discovered without installing a new APK.

The original **nine reviewed photographs are bundled inside the APK** as an offline collection,
so first use and slow/failed connections do not need a download to show a picture. They rotate
whenever an online image is not ready. A corrupt/evicted queued file falls back to the built-in
collection. Successful crossfades never clear the previous image before a replacement exists.

The online cache is capped at **8 MiB**, with a **2 MiB per-download** limit. The current and
next three files are protected from cache eviction. Prefetch stores compressed files, not
three additional decoded full-screen bitmaps. The APK's approximately 6 MiB of bundled JPEGs
are separate from that cache. Photos crossfade every minute; a slow refill can extend the
current photo's display. Clock/date use native TextClock updates without seconds.

Individual photographs retain their Creative Commons licences, rather than the code's MIT
licence. The built-in catalogue `assets/heritage-wallpapers.json` lists original source pages,
authors, licence URLs and display-crop information. See also `assets/heritage-fallback/README.md`.
Each image has an onscreen author/licence credit. **Photo credits** includes the built-ins and
the current/queued online photographs with source and licence links.

Weather uses [Open-Meteo](https://open-meteo.com/) (CC BY 4.0), with city lookup from
[GeoNames through Open-Meteo](https://open-meteo.com/en/docs/geocoding-api). A city is chosen
explicitly; no GPS or new location permission is requested. City/coordinates are private
app preferences, not public defaults. Weather refreshes at most every 15 minutes during
the dream. If a refresh fails it retries after a minute. Conditions older than 45 minutes
are hidden behind Weather unavailable; no cache-age text is shown.
Current conditions are weather-model estimates. This API configuration is for personal,
noncommercial use; check provider terms before distributing a commercial build.

Photo and weather connections use HTTPS, an exact provider-host allowlist, response limits
and timeouts. Work is on separate photo/weather workers and is cancelled/released when the
dream stops or detaches. There are no background jobs, root access, overlays or persistent
polling outside the screensaver. Any remote key exits through Android's standard dream behavior.

Dependency-free device checks live in `app/src/androidTest`. Build with
`./gradlew assembleDebug assembleDebugAndroidTest`, install both APKs on the intended test TV,
and run:

```sh
adb -s YOUR_SERIAL shell am instrument -w com.gathin.tvhome.test/com.gathin.tvhome.DreamFeedChecks
```

These checks use isolated temporary
state and deterministic network failures; they do not change saved Home choices or weather settings.

Version 3.8 preserves the visible Welcome home greeting behind translucent TV Settings.
Pausing Home finishes the greeting's finite entrance and stops its animators instead of
clearing it from the still-visible shelf. Returning Home retains the normal entrance;
Play Next and the lower app grid continue to hide the greeting.


Version 3.10 adds a compact rounded glass panel behind the screensaver's existing clock,
date and weather. The right edge follows the time/AM-PM row. Text sizes, alignment and
spacing are preserved. A translucent blur mix, reflective rims and a narrow magnified
wallpaper edge give the material a glass appearance. A quarter-resolution wallpaper sample
is blurred only on photo or
panel-layout changes; the small texture crossfades with the photo for 900 ms. There is no
idle capture loop. The texture, wallpaper reference and animator are released on exit.

TV Task Manager now reports **Displayed this run**, **Upcoming ready**, downloaded photo
file count and cache size. The displayed counter includes repeats and resets with a new
dream session. It is not a count of unique or stored photos. Cache size is read from the
debug app's private download directory; no root access or cleanup command is used by the
monitor. The app itself evicts old unprotected files at its 8 MiB cache limit. The nine
packaged fallback photographs remain separate from that downloaded cache.

Version 3.11 stops a held Up press at the four-app dock when climbing from Play Next,
the lower app grid or App Library. Release and press Up again to reach Inputs,
Wi-Fi and Settings. Repeats still move through the lower rows normally; the existing
glass appearance and movement animation are unchanged.
