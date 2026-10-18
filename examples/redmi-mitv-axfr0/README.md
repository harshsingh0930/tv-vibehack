# Redmi MiTV-AXFR0

Initial hardware and compatibility notes recorded on 18 October 2026.

| Property | Tested value |
|---|---|
| Brand / model | Redmi (Xiaomi) / `MiTV-AXFR0` |
| Android / SDK | Android 11 / API 30 |
| Firmware display ID | `RTT0.210630.002.2318 release-keys` |
| Build incremental | `2318` |
| Build fingerprint | `Redmi/rango/rango:11/RTT0.210630.002/2318:user/release-keys` |
| Live TV / tuner package | `com.android.tv` |
| Google stock Home | `com.google.android.tvlauncher` |
| PatchWall | `com.mitv.tvhome.atv` |
| Xiaomi TV+ | `com.mitv.tvhome.mitvplus` |

The Live TV/tuner package differs from the Mi TV 4A example. For a build for this model, set
`live_tv_package` in `launcher/app/src/main/res/values/config.xml` to `com.android.tv`.
Keep any installed tuner/input services enabled while checking HDMI and live-TV behaviour.

## Initial compatibility observation

A reversible PatchWall disable trial was performed with `pm disable-user --user 0
com.mitv.tvhome.atv`. A fresh Xiaomi TV+ launch still played the Aajtak live channel with video
and audio on this firmware. Xiaomi TV+ itself remained enabled and its app data was preserved.
This is one same-day channel/playback test, not a guarantee for every TV+ feature or firmware.
PatchWall can be restored with `pm default-state --user 0 com.mitv.tvhome.atv` (or enabled from
the TV's app settings).

## Debloat list pending

There is deliberately no `debloat.txt` yet. The contribution guide requires packages to have
been disabled and lived with for a few days. Only same-day tests have been completed, so this
entry registers the model and records its package differences without presenting a tested
multi-day disable list. Follow-up validation should include a reboot, HDMI/input switching,
Xiaomi TV+ live playback, streaming apps, remote controls and any casting/voice features the
owner wants to retain. Record regressions and re-enabled packages before adding a list.

No serial number, Android ID, MAC address or private network address is included here.
