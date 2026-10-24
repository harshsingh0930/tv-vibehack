"""Validate a public theme without contacting a TV.

python tools/check-public-theme.py themes/mountain-glass
Add --upstream-doc PATH to validate keys against upstream's themes/README.md.
Add --require-tv-screenshot before submitting an upstream theme PR.
"""
import argparse
import json
from pathlib import Path
import re
import struct


def jpeg_size(data):
    if data[:2] != b"\xff\xd8":
        raise ValueError("not a JPEG")
    offset = 2
    while offset + 9 <= len(data):
        if data[offset] != 255:
            break
        marker = data[offset + 1]
        length = struct.unpack_from(">H", data, offset + 2)[0]
        if 0xC0 <= marker <= 0xCF and marker not in (0xC4, 0xC8, 0xCC):
            height, width = struct.unpack_from(">HH", data, offset + 5)
            return width, height
        if length < 2:
            break
        offset += 2 + length
    raise ValueError("JPEG has no readable size")


def check(folder, upstream_doc=None, require_screenshot=False):
    config = json.loads((folder / "config.json").read_text(encoding="utf-8"))
    if not isinstance(config, dict):
        raise ValueError("config.json must contain an object")
    if type(config.get("columns")) is not int or not 1 <= config["columns"] <= 8:
        raise ValueError("columns must be an integer from 1 to 8")
    apps = config.get("dock_apps")
    if not isinstance(apps, list) or not all(isinstance(app, str) and app.strip() for app in apps):
        raise ValueError("dock_apps must contain nonempty package names")
    if len(apps) != len(set(apps)):
        raise ValueError("dock_apps contains duplicates")
    if upstream_doc:
        keys = set()
        for line in upstream_doc.read_text(encoding="utf-8").splitlines():
            if line.startswith("| `"):
                keys.update(re.findall(r"`([a-z][a-z0-9_]+)`", line.split("|")[1]))
        unknown = set(config) - keys
        if unknown:
            raise ValueError("unsupported upstream keys: " + ", ".join(sorted(unknown)))
    wallpaper = (folder / "wallpaper.jpg").read_bytes()
    if jpeg_size(wallpaper) != (1920, 1080) or len(wallpaper) > 300 * 1024:
        raise ValueError("wallpaper must be 1920x1080 JPEG under 300 KB")
    credits = folder / "CREDITS.md"
    if not credits.is_file():
        raise ValueError("missing wallpaper credits")
    screenshot = folder / "screenshot.jpg"
    if screenshot.is_file():
        data = screenshot.read_bytes()
        if jpeg_size(data) != (1280, 720) or len(data) > 300 * 1024:
            raise ValueError("screenshot must be 1280x720 JPEG under 300 KB")
        # File format cannot establish provenance. A reviewer still verifies a real-TV capture.
        screenshot_status = "format valid; real-TV provenance requires review"
    elif require_screenshot:
        raise ValueError("real-TV screenshot.jpg is pending; upstream submission is incomplete")
    else:
        screenshot_status = "pending real-TV capture"
    return {"theme": folder.name, "config_keys": len(config), "wallpaper_bytes": len(wallpaper),
            "wallpaper_size": [1920, 1080], "screenshot": screenshot_status}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("folder", type=Path)
    parser.add_argument("--upstream-doc", type=Path)
    parser.add_argument("--require-tv-screenshot", action="store_true")
    args = parser.parse_args()
    try:
        print(json.dumps(check(args.folder, args.upstream_doc, args.require_tv_screenshot), indent=2))
    except (ValueError, OSError) as error:
        parser.exit(1, f"Theme check failed: {error}\n")
