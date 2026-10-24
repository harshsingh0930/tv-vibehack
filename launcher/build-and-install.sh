#!/usr/bin/env bash
# Build and install Redmi Glass without changing Home or disabling packages.
set -euo pipefail
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
export PATH="$JAVA_HOME/bin:$PATH"
[ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug -q --console=plain
PY=../tools/venv/bin/python
APK=app/build/outputs/apk/debug/app-debug.apk
"$PY" -I ../tools/b64push.py "$APK" /data/local/tmp/tvhome.apk
printf '%s\n' \
  "pm install -r /data/local/tmp/tvhome.apk" \
  "rm -f /data/local/tmp/tvhome.apk" \
  "pm grant com.harshsingh.redmiglass android.permission.ACCESS_FINE_LOCATION 2>/dev/null; echo location-granted" \
  "pm grant com.harshsingh.redmiglass android.permission.READ_TV_LISTINGS" \
  | "$PY" -I ../tools/tv.py --batch
echo "Installed. Open Redmi Glass Launcher from the TV app list; choose default Home in TV settings if wanted."
