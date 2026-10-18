"""Read-only Android TV dashboard using the PC's installed adb. Ctrl+C exits."""
import argparse
import datetime
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time


def shell(adb, serial, command):
    result = subprocess.run(
        [adb, "-s", serial, "shell", command], capture_output=True,
        text=True, encoding="utf-8", errors="replace", timeout=30,
        creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
    )
    if result.returncode:
        raise RuntimeError(result.stderr.strip() or result.stdout.strip() or "ADB command failed")
    return result.stdout


def parse_sample(raw):
    memory = {key: int(value) for key, value in re.findall(r"^(\w+):\s+(\d+) kB", raw, re.M)}
    cpus = {}
    for key, values in re.findall(r"^(cpu\d*)\s+([\d ]+)$", raw, re.M):
        ticks = list(map(int, values.split()))[:8]
        cpus[key] = (sum(ticks), ticks[3] + (ticks[4] if len(ticks) > 4 else 0))
    uptime = re.search(r"^(\d+\.\d+)\s+\d+\.\d+\s*$", raw, re.M)
    load = re.search(r"^(\d+\.\d+ \d+\.\d+ \d+\.\d+)\s+\d+/\d+", raw, re.M)
    disk = re.search(r"^/\S+\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)%\s+\S+", raw, re.M)
    frequency = re.search(r"^(\d{4,})\s*$", raw, re.M)
    return {
        "memory": memory, "cpus": cpus,
        "uptime": float(uptime[1]) if uptime else 0,
        "load": load[1] if load else "unavailable",
        "disk": tuple(map(int, disk.groups())) if disk else None,
        "frequency": int(frequency[1]) if frequency else None,
    }


def process_memory(raw):
    section = raw.split("Total PSS by process:", 1)
    if len(section) != 2:
        return []
    block = section[1].split("Total PSS by", 1)[0]
    rows = []
    for amount, name, pid in re.findall(r"^\s*([\d,]+)K:\s+(.+?)\s+\(pid (\d+)[^\n]*", block, re.M):
        rows.append((int(amount.replace(",", "")), name, pid))
    return sorted(rows, reverse=True)[:8]


def cpu_percent(current, previous, key):
    if key not in current or key not in previous:
        return None
    total = current[key][0] - previous[key][0]
    idle = current[key][1] - previous[key][1]
    return max(0, min(100, 100 * (total - idle) / total)) if total > 0 else None


def bar(percent, width=24):
    used = round(max(0, min(100, percent)) * width / 100)
    return "[" + "#" * used + "." * (width - used) + "]"


def mib(kib):
    return f"{kib / 1024:,.1f} MiB"


def render(specs, sample, previous, processes, process_age, interval, error):
    mem = sample["memory"]
    total = mem.get("MemTotal", 0)
    if not total:
        raise RuntimeError("The TV did not return RAM statistics")
    available = mem.get("MemAvailable")
    used = total - available if available is not None else None
    swap_total = mem.get("SwapTotal", 0)
    swap_used = swap_total - mem.get("SwapFree", 0)
    cpu = cpu_percent(sample["cpus"], previous, "cpu")
    elapsed = int(sample["uptime"])
    days, elapsed = divmod(elapsed, 86400)
    hours, elapsed = divmod(elapsed, 3600)
    minutes, seconds = divmod(elapsed, 60)
    lines = [
        "TV TASK MANAGER | " + specs["serial"] + " | " + datetime.datetime.now().strftime("%H:%M:%S"),
        "=" * 78,
        f"{specs['model']} | Android {specs['android']} (API {specs['sdk']})",
        f"Build: {specs['build']} | Security patch: {specs['patch']}",
        f"Platform: {specs['platform']} | CPU: {specs['cores']} cores, max {specs['max_mhz']} MHz",
        f"App ABI: {specs['abi']} | Android UI: {specs['display']}",
        f"Uptime: {days}d {hours:02}:{minutes:02}:{seconds:02} | Load (1/5/15 min): {sample['load']}",
        "-" * 78,
    ]
    if used is not None:
        percent = 100 * used / total
        lines += [f"RAM       {bar(percent)} {percent:5.1f}% in use",
                  f"          {mib(used)} used / {mib(total)} total",
                  f"          {mib(available)} available | {mib(mem.get('MemFree', 0))} completely free"]
    else:
        lines.append(f"RAM total: {mib(total)} | Available: not reported")
    lines.append("          Used = total - available; available includes reclaimable memory.")
    if swap_total:
        lines += [f"SWAP      {bar(100 * swap_used / swap_total)} {100 * swap_used / swap_total:5.1f}% occupied",
                  f"          {mib(swap_used)} / {mib(swap_total)} logical swap (not extra physical RAM)"]
    if cpu is not None:
        lines.append(f"CPU       {bar(cpu)} {cpu:5.1f}% busy across all cores")
    else:
        lines.append("CPU       Waiting for second sample...")
    core_values = []
    for key in sorted(k for k in sample["cpus"] if k != "cpu"):
        value = cpu_percent(sample["cpus"], previous, key)
        core_values.append(f"{key}: {value:.0f}%" if value is not None else f"{key}: --")
    lines.append("          " + " | ".join(core_values))
    if sample["frequency"]:
        lines.append(f"          CPU0 clock: {sample['frequency'] / 1000:.0f} MHz")
    if sample["disk"]:
        disk_total, disk_used, disk_free, disk_percent = sample["disk"]
        lines += [f"APP DATA  {bar(disk_percent)} {disk_percent:5.1f}% full",
                  f"          {mib(disk_used)} used / {mib(disk_total)} total | {mib(disk_free)} free"]
        if disk_free / disk_total < 0.05:
            lines.append("          LOW STORAGE: less than 5% free on the app/data partition.")
    lines += ["-" * 78, f"LARGEST PROCESSES (PSS, proportional share of RAM; updated {process_age:.0f}s ago)",
              "   MiB      PID   Process"]
    for size, name, pid in processes:
        lines.append(f"{size / 1024:7.1f}  {pid:>7}   {name[:54]}")
    if not processes:
        lines.append("Process memory unavailable on this firmware.")
    if error:
        lines.append("Process refresh: " + error[:100])
    lines += ["-" * 78,
              f"Read-only | Metrics ~{interval:g}s; processes every 20s | Ctrl+C to stop",
              "CPU temperature is restricted by TV firmware; no root access requested."]
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL"),
                        required=not os.environ.get("ANDROID_SERIAL"), help="ADB device IP:port or serial; defaults to ANDROID_SERIAL")
    parser.add_argument("--interval", type=float, default=5, help="Metric refresh seconds (minimum 3)")
    parser.add_argument("--once", action="store_true", help="Print a single snapshot")
    parser.add_argument("--samples", type=int, default=0, help="Stop after this many frames; 0 means continuous")
    parser.add_argument("--no-clear", action="store_true", help="Append frames instead of clearing the screen")
    args = parser.parse_args()
    adb = shutil.which("adb")
    if not adb:
        raise SystemExit("adb is not on PATH")
    args.interval = max(3, args.interval)
    raw_props = shell(adb, args.serial, "getprop")
    props = dict(re.findall(r"^\[([^]]+)\]: \[([^]]*)\]", raw_props, re.M))
    hardware = shell(adb, args.serial, "cat /sys/devices/system/cpu/present /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq; wm size")
    cores_match = re.search(r"^0-(\d+)", hardware)
    max_match = re.search(r"^(\d{4,})\s*$", hardware, re.M)
    size_match = re.search(r"Physical size:\s*(\S+)", hardware)
    specs = {
        "serial": args.serial, "model": props.get("ro.product.model", "Unknown TV"),
        "android": props.get("ro.build.version.release", "?"),
        "sdk": props.get("ro.build.version.sdk", "?"),
        "build": props.get("ro.build.display.id", "?"),
        "patch": props.get("ro.build.version.security_patch", "?"),
        "platform": props.get("ro.board.platform", props.get("ro.hardware", "?")),
        "abi": props.get("ro.product.cpu.abilist", "?"),
        "cores": str(int(cores_match[1]) + 1) if cores_match else "?",
        "max_mhz": str(int(max_match[1]) // 1000) if max_match else "?",
        "display": size_match[1] if size_match else "?",
    }
    command = "cat /proc/meminfo /proc/stat /proc/uptime /proc/loadavg; df -k /data; cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq"
    previous = parse_sample(shell(adb, args.serial, command))["cpus"]
    time.sleep(0.5)
    processes = []
    process_updated = 0
    count = 0
    live = sys.stdout.isatty() and not args.no_clear and not args.once
    if live and os.name == "nt":
        import ctypes
        handle = ctypes.windll.kernel32.GetStdHandle(-11)
        mode = ctypes.c_ulong()
        if ctypes.windll.kernel32.GetConsoleMode(handle, ctypes.byref(mode)):
            ctypes.windll.kernel32.SetConsoleMode(handle, mode.value | 4)
    try:
        while True:
            started = time.monotonic()
            error = ""
            if started - process_updated >= 20:
                try:
                    processes = process_memory(shell(adb, args.serial, "dumpsys meminfo"))
                    process_updated = time.monotonic()
                except (RuntimeError, subprocess.TimeoutExpired) as exc:
                    error = str(exc)
            sample = parse_sample(shell(adb, args.serial, command))
            frame = render(specs, sample, previous, processes, time.monotonic() - process_updated, args.interval, error)
            if live:
                sys.stdout.write("\x1b[H\x1b[2J")
            print(frame, flush=True)
            previous = sample["cpus"]
            count += 1
            if args.once or (args.samples and count >= args.samples):
                break
            time.sleep(max(0, args.interval - (time.monotonic() - started)))
    except KeyboardInterrupt:
        print("\nMonitor stopped. No TV settings changed.")
    except (RuntimeError, subprocess.TimeoutExpired) as exc:
        raise SystemExit(f"TV connection interrupted: {exc}")


if __name__ == "__main__":
    main()
