# Optional desktop diagnostics

Use Python 3.10+ and Google's `adb` on PATH. Pass `--serial IP:PORT` (or set `ANDROID_SERIAL`);
these tools never select an arbitrary connected device. Authorize ADB on your own TV first.
Keep reports under the ignored `backup/` or `reports/` directories: reports contain installed-app
information and may include device details. No diagnostic output is needed in a public commit.

```sh
python tools/monitor-tv.py --serial YOUR_SERIAL --once
python tools/monitor-tv.py --serial YOUR_SERIAL
```

On Windows, `TV Task Manager.cmd YOUR_SERIAL` opens the same monitor. Without an argument or
`ANDROID_SERIAL`, it asks for the target. Metrics are read-only; Ctrl+C exits the monitor.

## Storage report

This experimental helper queries storage statistics as the existing ADB shell identity; it
does not install an APK, grant privileges, clear data or delete files. It audits Android user 0.
It uses a hidden system interface, verified on Android 11, which may differ on other firmware.
Compile it with JDK 17 and SDK platform/build-tools 34 (Bash example):

```sh
mkdir -p reports/storage-helper/classes reports/storage-helper/dex
javac --release 8 -classpath "$ANDROID_HOME/platforms/android-34/android.jar" \
  -d reports/storage-helper/classes tools/StorageReport.java
"$ANDROID_HOME/build-tools/34.0.0/d8" --min-api 26 \
  --lib "$ANDROID_HOME/platforms/android-34/android.jar" \
  --output reports/storage-helper/dex reports/storage-helper/classes/StorageReport.class
python tools/report-tv-storage.py --serial YOUR_SERIAL \
  --helper-dex reports/storage-helper/dex/classes.dex --output reports/storage
```

The reporter uploads that DEX to `/data/local/tmp/tv-storage-audit.dex` and runs it once; the
helper file remains there for inspection. App code/data/cache sizes are not a complete filesystem
inventory, and shared UIDs can duplicate attribution. Protected directories are not bypassed.

## Navigation and video diagnostics

`verify-tv-home.py` sends remote keys, opens menus and reads the debug launcher's preferences.
Run only with the remote idle, the custom launcher foregrounded and an app tile selected in the
dock. It assumes a four-column layout with Play Next present; it is a development scenario,
not a generic TV self-test. It compares saved choices before and after navigation.

```sh
python tools/verify-tv-home.py --serial YOUR_SERIAL --expected-model YOUR_MODEL --output reports/navigation
python -m pip install -r tools/requirements-video.txt
python tools/review-tv-video.py issue.mp4 reports/video
```

Video tools are optional desktop dependencies, separate from the original Python ADB client.
The contact sheet letterboxes portrait and landscape footage and uses temporary frame files.


The task manager also shows the custom screensaver's displayed-photo counter and three-photo
ready buffer while it is running. **Displayed this run** includes repeats and restarts from
zero with a new screensaver run. Downloaded file count and MiB show actual storage use, rather
than the number displayed. Those cache statistics require the custom debug build's `run-as`
access; they are reported as unavailable on builds that restrict it. The monitor remains read-only.
