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
