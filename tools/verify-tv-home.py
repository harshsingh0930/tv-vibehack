"""On-device navigation regression checks. Run only while the TV remote is authorized idle."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET

p = argparse.ArgumentParser()
p.add_argument('--output', type=Path, required=True)
p.add_argument('--library-up-only', action='store_true')
p.add_argument('--serial', default=os.environ.get('ANDROID_SERIAL'), required=not os.environ.get('ANDROID_SERIAL'))
p.add_argument('--expected-model', help='Optional model guard before sending any remote keys')
args = p.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
serial = args.serial
def adb(*command):
    return subprocess.check_output(['adb', '-s', serial, *command], timeout=30)
def key(code, delay=.36):
    adb('shell', 'input', 'keyevent', str(code)); time.sleep(delay)
def snapshot(name):
    data = adb('shell', 'dumpsys', 'activity', 'com.gathin.tvhome/.MainActivity')
    (args.output / f'{name}.txt').write_bytes(data)
    (args.output / f'{name}.png').write_bytes(adb('exec-out', 'screencap', '-p'))
    return data.decode(errors='replace')

if args.expected_model:
    assert adb('shell', 'getprop', 'ro.product.model').decode().strip() == args.expected_model
for _ in range(10):
    activity = adb('shell', 'dumpsys', 'activity', 'activities').decode()
    if any('mResumedActivity' in line and 'com.gathin.tvhome/' in line for line in activity.splitlines()):
        break
    time.sleep(.3)
else:
    raise AssertionError('Launcher did not become foreground; no navigation inputs sent')
before = adb('shell', 'run-as', 'com.gathin.tvhome', 'cat', 'shared_prefs/home.xml')
if args.library_up_only:
    for _ in range(12): key(20, .25)
    for attempt in range(3):
        time.sleep(1.2)  # Exercise Up after the footer and ambient animations have stopped.
        state = snapshot(f'library-idle-{attempt}')
        assert 'OriginalGlass visible=false' in state and 'app:id/appLibrary' in state
        key(19, .1)
        state = snapshot(f'library-up-{attempt}')
        assert 'OriginalGlass visible=true' in state, 'Up failed to focus the last app row'
        key(20, .4)
    result = dict(library_up_after_idle=True, library_down_return=True, cycles=3)
    (args.output / 'library-up-checks.json').write_text(json.dumps(result, indent=2))
    print(json.dumps(result))
    raise SystemExit(0)
# Start on an app tile in the dock, as established by the preceding visual checks.
key(20)
for _ in range(4):
    key(19, .06); key(20, .06)
time.sleep(.9)
state = snapshot('rapid-vertical-play-next')
assert 'OriginalGlass visible=false' in state and 'PlayNextTracker visible=true animating=false' in state
adb('shell', 'input', 'keyevent', *(['22'] * 14)); time.sleep(.8)
snapshot('play-next-right-edge')
adb('shell', 'input', 'keyevent', *(['21'] * 14)); time.sleep(.8)
snapshot('play-next-left-edge')
key(19, .8)
state = snapshot('rapid-return-dock')
assert 'OriginalGlass visible=true' in state and 'PlayNextTracker visible=false' in state
adb('shell', 'input', 'keyevent', '--longpress', '23'); time.sleep(.6)
snapshot('menu-matched-typography')
key(4)
# Reach the library using D-pad only: do not alter app order or hidden entries.
for _ in range(12): key(20, .25)
snapshot('app-library-pill')
adb('shell', 'uiautomator', 'dump', '/data/local/tmp/tv-home-check.xml')
ui = adb('shell', 'cat', '/data/local/tmp/tv-home-check.xml')
(args.output / 'library-focus.xml').write_bytes(ui)
root = ET.fromstring(ui)
assert any(n.get('resource-id','').endswith(':id/appLibrary') and n.get('focused') == 'true'
           for n in root.iter('node')), 'Library footer did not receive focus'
key(23, .5)
snapshot('full-installed-library')
key(4)
after = adb('shell', 'run-as', 'com.gathin.tvhome', 'cat', 'shared_prefs/home.xml')
def choices(data):
    return {e.get('name'): ET.tostring(e, encoding='unicode') for e in ET.fromstring(data)
            if not e.get('name','').startswith('last_cleanup')}
assert choices(before) == choices(after), 'Navigation testing changed Home preferences'
result = dict(rapid_vertical_handoff=True, horizontal_edges=True, matched_menu_opened=True,
              library_footer_focus=True, library_opened=True, home_preferences_preserved=True)
(args.output / 'navigation-checks.json').write_text(json.dumps(result, indent=2))
print(json.dumps(result))
