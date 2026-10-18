"""Android TV storage audit for user 0; no package installation or app data changes."""
import argparse
import csv
import datetime
import html
import json
import os
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--serial', default=os.environ.get('ANDROID_SERIAL'), required=not os.environ.get('ANDROID_SERIAL'))
parser.add_argument('--helper-dex', type=Path, required=True, help='classes.dex built from StorageReport.java; see tools/README.md')
parser.add_argument('--expected-model', help='Optional model guard before uploading the helper')
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)

def adb(*command, input=None):
    return subprocess.run(['adb', '-s', args.serial, *command], input=input,
                          capture_output=True, timeout=120, check=True).stdout

model = adb('shell', 'getprop', 'ro.product.model').decode().strip()
if args.expected_model:
    assert model == args.expected_model, f'Unexpected model: {model}'
if not args.helper_dex.is_file():
    parser.error('--helper-dex must name the compiled classes.dex file')
adb('push', str(args.helper_dex), '/data/local/tmp/tv-storage-audit.dex')
names = adb('shell', 'pm', 'list', 'packages', '--user', '0').decode()
packages = sorted(line.removeprefix('package:').strip() for line in names.splitlines() if line.startswith('package:'))
stats = json.loads(adb('shell', 'CLASSPATH=/data/local/tmp/tv-storage-audit.dex app_process /system/bin StorageReport',
                      input=('\n'.join(packages) + '\n').encode()))
(args.output / 'storage-live.json').write_text(json.dumps(stats, indent=2), encoding='utf-8')
disabled = set(adb('shell', 'pm', 'list', 'packages', '-d', '--user', '0').decode().replace('package:', '').split())
system = set(adb('shell', 'pm', 'list', 'packages', '-s', '--user', '0').decode().replace('package:', '').split())
paths = {}
for line in adb('shell', 'pm', 'list', 'packages', '-f', '--user', '0').decode().splitlines():
    if line.startswith('package:') and '=' in line:
        path, pkg = line.removeprefix('package:').rsplit('=', 1)
        paths[pkg.strip()] = path
df = adb('shell', 'df', '-k', '/data').decode()
(args.output / 'filesystem.txt').write_text(df, encoding='utf-8')
(args.output / 'diskstats.txt').write_bytes(adb('shell', 'dumpsys', 'diskstats'))
labels = {
 'com.google.android.gms': 'Google Play services', 'com.google.android.gsf': 'Google Services Framework',
 'com.android.vending': 'Google Play Store', 'com.google.android.apps.mediashell': 'Chromecast built-in',
 'com.lagradost.cloudstream3': 'CloudStream', 'com.netflix.ninja': 'Netflix',
 'org.videolan.vlc': 'VLC', 'com.google.android.webview': 'Android System WebView',
 'in.startv.hotstar': 'JioHotstar', 'com.mitv.tvhome.mitvplus': 'Xiaomi TV+',
 'com.mitv.tvhome.atv': 'PatchWall', 'org.smarttube.stable': 'SmartTube',
 'com.amazon.amazonvideo.livingroom': 'Prime Video', 'com.apple.atve.androidtv.appletv': 'Apple TV',
 'com.google.android.apps.tachyon': 'Google Duo', 'com.jio.media.jiotvplus': 'JioTV+',
 'com.jio.media.stb.ondemand': 'JioHotstar / former JioCinema', 'com.graymatrix.did': 'ZEE5',
 'com.mxtech.videoplayer.television': 'MX Player TV', 'com.dangal.androidtv': 'Dangal Play',
 'com.sonyliv': 'SonyLIV', 'com.tcl.browser': 'BrowseHere browser',
 'com.google.android.tvlauncher': 'Original Google TV Home', 'com.google.android.katniss': 'Google Assistant / voice search',
 'com.google.android.youtube.tv': 'YouTube', 'com.google.android.youtube.tvmusic': 'YouTube Music',
 'com.spotify.tv.android': 'Spotify', 'com.windscribe.vpn': 'Windscribe VPN',
 'io.github.krlvm.powertunnel.android': 'PowerTunnel', 'com.gathin.tvhome': 'Custom TV launcher',
 'com.xiaomi.mitv.tvmanager': 'TV Manager', 'com.xiaomi.mitv.updateservice': 'Xiaomi firmware updater',
 'com.google.android.tts': 'Google text-to-speech', 'com.google.android.inputmethod.latin': 'Gboard keyboard',
 'com.google.android.videos': 'Google Play Movies', 'com.offshore.pikachu': 'Pikachu',
 'org.koitharu.kotatsu': 'Kotatsu', 'com.amazon.minitv.androidtv.app': 'Amazon MX Player',
 'is.xyz.mpv': 'mpv', 'com.mitv.gallery': 'Gallery', 'com.xiaomi.mitv.mediaexplorer': 'Xiaomi media explorer',
}
rows = []
for row in stats['packages']:
    pkg = row['package']
    row.update(label=labels.get(pkg, pkg), enabled=pkg not in disabled,
               kind='System / preloaded' if pkg in system else 'Installed app', apk_path=paths.get(pkg, ''))
    code = row.get('app_bytes', 0)
    data = row.get('data_bytes_including_cache', 0)
    cache = row.get('cache_bytes', 0)
    row.update(total_bytes=code + data, personal_data_bytes=max(0, data - cache))
    rows.append(row)
rows.sort(key=lambda r: r['total_bytes'], reverse=True)
fields = ['label','package','kind','enabled','total_bytes','app_bytes','personal_data_bytes',
          'cache_bytes','data_bytes_including_cache','apk_path','error']
with (args.output / 'tv-storage.csv').open('w', newline='', encoding='utf-8-sig') as file:
    writer = csv.DictWriter(file, fieldnames=fields, extrasaction='ignore')
    writer.writeheader(); writer.writerows(rows)
def mib(n): return f'{n / 1048576:,.1f}'
lines = df.splitlines()[-1].split()
total, used, free = [int(n) * 1024 for n in lines[1:4]]
captured = datetime.datetime.now().astimezone().strftime('%d %b %Y %H:%M %Z')
intro = (f'Captured {captured}. Internal app storage: {mib(total)} MiB total, {mib(used)} MiB used, '
         f'{mib(free)} MiB free ({used/total:.1%} used).')
table = ''.join('<tr>' + ''.join(f'<td>{html.escape(str(value))}</td>' for value in [
    r['label'], r['package'], mib(r['total_bytes']), mib(r.get('app_bytes',0)),
    mib(r['personal_data_bytes']), mib(r.get('cache_bytes',0)),
    'Enabled' if r['enabled'] else 'Disabled', r['kind']]) + '</tr>' for r in rows)
document = '''<!doctype html><html lang="en"><meta charset="utf-8"><title>Android TV storage audit</title>
<style>body{background:#11131b;color:#eceef5;font:15px system-ui;margin:36px}h1{font-size:28px}p{max-width:1050px;line-height:1.6;color:#bfc5d6}input{width:340px;padding:13px;border-radius:12px;background:#232736;color:white;border:1px solid #464d64;margin:14px 0}table{border-collapse:collapse;width:100%;font-size:13px}th{text-align:left;cursor:pointer;background:#272c3e;position:sticky;top:0}td,th{padding:12px;border-bottom:1px solid #2b3040}td:nth-child(n+3):nth-child(-n+6){text-align:right;font-variant-numeric:tabular-nums}tr:hover{background:#202536}a{color:#92d9e8}.bar{height:14px;border-radius:7px;background:#2a3042;overflow:hidden}.bar span{display:block;height:100%;background:#e4a363}</style>
<h1>Android TV — internal storage</h1><p>INTRO</p><div class="bar"><span style="width:PERCENT%"></span></div>
<p>Live, read-only Android storage queries for every installed package. All sizes are MiB (1 MiB = 1,048,576 bytes). Total = app/code + personal data + cache. Cache is already included in Android’s raw data figure, so it is counted once. Disabled apps still occupy space.</p>
<p>App/code includes installed updates, native libraries and compiled code on the data volume. Firmware APKs baked into separate read-only system partitions are excluded. Shared-UID apps may share accounting; app totals and filesystem usage need not match because system files, reserved space, metadata and shared files also use storage. This audit only reads storage statistics.</p>
<input id="filter" placeholder="Search app, package, enabled, system…" aria-label="Filter apps"><p><a href="tv-storage.csv">Download all rows as CSV</a> · <a href="storage-live.json">Raw measurements</a> · <a href="https://developer.android.com/reference/android/app/usage/StorageStats">Android measurement definitions</a></p>
<table><thead><tr><th>App</th><th>Package</th><th>Total MiB</th><th>App/code MiB</th><th>Data MiB</th><th>Cache MiB</th><th>Status</th><th>Type</th></tr></thead><tbody>ROWS</tbody></table>
<script>const body=document.querySelector('tbody');document.querySelector('#filter').oninput=e=>{const q=e.target.value.toLowerCase();for(const r of body.rows)r.hidden=!r.textContent.toLowerCase().includes(q)};document.querySelectorAll('th').forEach((h,i)=>{let asc=false;h.onclick=()=>{asc=!asc;const rows=[...body.rows];rows.sort((a,b)=>{const x=a.cells[i].textContent.replaceAll(',',''),y=b.cells[i].textContent.replaceAll(',','');return (i>=2&&i<=5?Number(x)-Number(y):x.localeCompare(y))*(asc?1:-1)});body.append(...rows)}})</script></html>'''
document = document.replace('INTRO', html.escape(intro)).replace('PERCENT',str(used/total*100)).replace('ROWS',table)
(args.output / 'tv-storage.html').write_text(document, encoding='utf-8')
markdown = ['# Android TV storage audit', '', intro, '', '| App | Total MiB | App/code | Personal data | Cache | Status |', '|---|---:|---:|---:|---:|---|']
for r in rows:
    markdown.append(f"| {r['label']} | {mib(r['total_bytes'])} | {mib(r.get('app_bytes',0))} | {mib(r['personal_data_bytes'])} | {mib(r.get('cache_bytes',0))} | {'Enabled' if r['enabled'] else 'Disabled'} |")
markdown += ['', 'Live StorageStatsManager queries. Cache is a subset of the raw data size and is counted only once. Firmware in separate system partitions is excluded. Shared UIDs and system overhead prevent exact reconciliation with filesystem used space. This audit only reads storage statistics.', '', '[Android definitions](https://developer.android.com/reference/android/app/usage/StorageStats)']
(args.output / 'tv-storage.md').write_text('\n'.join(markdown), encoding='utf-8')
print(intro)
for row in rows[:12]: print(f"{row['label']}: {mib(row['total_bytes'])} MiB ({mib(row.get('app_bytes',0))} code, {mib(row['personal_data_bytes'])} data, {mib(row.get('cache_bytes',0))} cache)")
print(f"Report: {args.output / 'tv-storage.html'}")
