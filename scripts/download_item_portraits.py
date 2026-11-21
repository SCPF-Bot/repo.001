#!/usr/bin/env python3
"""Download MLBB item portraits into app/src/main/assets/items for release builds."""
import json, pathlib, sys, time, urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
JSON_PATH = ROOT / "app/src/main/assets/items.json"
ASSET_DIR = ROOT / "app/src/main/assets/items"
ASSET_DIR.mkdir(parents=True, exist_ok=True)
data = json.loads(JSON_PATH.read_text(encoding="utf-8"))
failed = []
downloaded = skipped = 0
for item in data.get("items", []):
    image = item.get("image", "")
    if not image.startswith("items/") or not image.lower().endswith(".png"):
        continue
    dest = ROOT / "app/src/main/assets" / image
    if dest.exists() and dest.stat().st_size > 500:
        skipped += 1
        continue
    url = item.get("portrait", "")
    if not url:
        failed.append((item.get("name"), "missing portrait URL"))
        continue
    req = urllib.request.Request(url, headers={"User-Agent": "MLBBPlus-asset-fetch/1.0"})
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            content = response.read()
        if not content.startswith(b"\x89PNG\r\n\x1a\n"):
            raise ValueError("response was not PNG")
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(content)
        downloaded += 1
        print(f"Downloaded {item['name']} -> {image}")
    except Exception as exc:
        failed.append((item.get("name"), str(exc)))
        print(f"FAILED {item.get('name')}: {exc}", file=sys.stderr)
    time.sleep(0.15)
print(f"Portraits: downloaded={downloaded}, skipped={skipped}, failed={len(failed)}")
# Don't fail the Android build on a temporarily unavailable portrait; ItemImage fallback handles it.
if failed:
    print("Some portraits will use the app fallback until the source is available.")
