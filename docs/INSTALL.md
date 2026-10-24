# Install Redmi Glass Launcher

Android TV / Google TV running Android 8.0 (API 26) or newer is required.
The earlier debug preview was tested on a Redmi MiTV-AXFR0 running Android TV 11.
The signed 3.12.1 preview installed successfully on retry without changing app
verification settings. Its dock, search and theme-switching checks passed on
that TV. The final full APK also installed and launched; search launched YouTube
and returned to the launcher. Other TV models have not been verified.

## Choose an APK

See [GitHub Releases](https://github.com/harshsingh0930/tv-vibehack/releases).
Source archives are not installable APKs. A draft release is visible only to maintainers.

| File | Use |
|---|---|
| `redmi-glass-3.12.1.apk` | Full launcher, eligible to become default Home |
| `redmi-glass-preview-3.12.1.apk` | Try the same look alongside Home; cannot become default Home and skips process cleanup |
| `SHA256SUMS.txt` | SHA-256 checksums for both APKs |

Both packages use a dedicated signing key and install alongside the original
launcher. Keep the same package and signing key for future updates. Preferences
from the original launcher or older previews are not imported.

## From an Android phone

Download the chosen APK onto the phone. Transfer it to the TV with a USB drive
or a TV file-transfer app, then open the APK with the TV's file manager and follow
the install prompts. If asked, allow installation from that particular file manager.
Open Redmi Glass Launcher (or Mountain Glass Preview) from the TV app list.

For a phone-only wireless ADB route, [Bugjaeger's developer guide](https://sisik.eu/blog/android/bugjaeger/sideload-apk-files)
explains installing a local APK. Put the phone and TV on the same Wi-Fi, enable
the TV's network/wireless debugging, connect or pair as shown by the TV, and
approve the phone on the TV. In Bugjaeger's Packages tab choose the local APK
using its install action. Turn debugging off when finished. This phone route is
documented from the tool's guide, not tested end-to-end in this project.

The upstream [Clean Your TV phone app](https://github.com/udhaykumarbala/cleanyourtv-app)
provides its own cleanup and bundled upstream launcher. It does not automatically
install this fork. You can retain upstream's independent setup, then install our
APK separately. Installing Redmi Glass does not require debloating the TV.

## From a computer

Enable network/ADB debugging on the TV, connect to its address with ADB, and
approve the computer using the TV remote. Substitute the exact connected serial:

```sh
adb devices
adb -s YOUR_SERIAL install -r redmi-glass-3.12.1.apk
adb -s YOUR_SERIAL shell am start -n com.harshsingh.redmiglass/com.gathin.tvhome.MainActivity
```

For preview, install `redmi-glass-preview-3.12.1.apk` and launch
`com.harshsingh.redmiglass.preview/com.gathin.tvhome.MainActivity` instead.

The normal launcher can be selected under the TV's Default apps/Home settings
or its Home chooser. Android can clear the previous Home selection when a new
launcher is installed, so select your preferred Home again if prompted. Keep the
stock launcher enabled. Preview cannot be chosen
as Home. To return, choose the previous Home in TV settings.

After installing the full launcher, you can delete the downloaded APK installer.
If you tried the preview first, uninstall **Mountain Glass Preview** through TV
Settings > Apps when finished; installing the full app does not remove it or
copy its preferences. Keep your working stock Home installed.

Play Next starts off. Hold OK on the gear (Settings), then enable **Show Play Next**
in Launcher options. Apps must publish watch progress for entries to appear.
If the TV requires an
ADB permission grant to read its entries:

```sh
adb -s YOUR_SERIAL shell pm grant com.harshsingh.redmiglass android.permission.READ_TV_LISTINGS
```

If installation reports a signature mismatch, check which package/build you
already have; do not remove your working Home just to install this one. If the
TV's verifier rejects the APK, stop and retain its error for diagnosis.

## Verify a download

On Windows: `Get-FileHash .\redmi-glass-3.12.1.apk -Algorithm SHA256`.
Compare with `SHA256SUMS.txt`. The release signing-certificate SHA-256 is
`23601e907f8acb4cefea63daa91e7999313b68ec2f2b847fd420e83cd904f70c`.
