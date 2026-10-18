@echo off
title Android TV Task Manager
cd /d "%~dp0"
set "TV_MONITOR_SERIAL=%~1"
if not defined TV_MONITOR_SERIAL set "TV_MONITOR_SERIAL=%ANDROID_SERIAL%"
if not defined TV_MONITOR_SERIAL set /p "TV_MONITOR_SERIAL=TV ADB serial or IP:port: "
if not defined TV_MONITOR_SERIAL exit /b 1
python tools\monitor-tv.py --serial "%TV_MONITOR_SERIAL%"
pause
