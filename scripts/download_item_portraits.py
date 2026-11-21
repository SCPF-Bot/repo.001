#!/usr/bin/env python3
"""Download MLBB item portraits, preserving the source image format."""
import json
import pathlib
import sys
import time
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
JSON_PATH = ROOT / "app/src/main/assets/items.json"
ASSET_DIR = ROOT / "app/src/main/assets/items"
ASSET_DIR.mkdir(parents=True, exist_ok=True)

FORMAT_SIGNATURES = (
    (b"\x89PNG\r\n\x1a\n", ".png", "image/png"),
    (b"RIFF", ".webp", "image/webp"),  # also verify WEBP marker below
    (b"\xff\xd8\xff", ".jpg", "image/jpeg"),
    (b"GIF87a", ".gif", "image/gif"),
    (b"GIF89a", ".gif", "image/gif"),
)


def detect_format(content, content_type=""):
    if content.startswith(b"\x89PNG\r\n\x1a\n"):
        return ".png"
    if content.startswith(b"RIFF") and content[8:12] == b"WEBP":
        return ".webp"
    if content.startswith(b"\xff\xd8\xff"):
        return ".jpg"
    if content.startswith((b"GIF87a", b"GIF89a")):
        return ".gif"
    content_type = content_type.split(";", 1)[0].strip().lower()
    return {"image/png": ".png", "image/webp": ".webp", "image/jpeg": ".jpg", "image/jpg": ".jpg", "image/gif": ".gif"}.get(content_type)


def is_valid_image(path):
    if not path.exists() or path.stat().st_size < 100:
        return False
    try:
        return detect_format(path.read_bytes()[:16]) is not None
    except OSError:
        return False


data = json.loads(JSON_PATH.read_text(encoding="utf-8"))
failed = []
downloaded = skipped = 0
for item in data.get("items", []):
    url = item.get("portrait", "")
    if not url:
        continue

    current_rel = item.get("image", "")
    current_path = ROOT / "app/src/main/assets" / current_rel if current_rel else None
    if current_path and is_valid_image(current_path):
        skipped += 1
        continue

    req = urllib.request.Request(url, headers={"User-Agent": "MLBBPlus-asset-fetch/1.0"})
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            content = response.read()
            content_type = response.headers.get("Content-Type", "")
        ext = detect_format(content, content_type)
        if not ext:
            raise ValueError(f"unknown image format (Content-Type: {content_type!r})")
        dest = ASSET_DIR / f"{item['id']}{ext}"
        dest.write_bytes(content)
        item["image"] = f"items/{dest.name}"
        downloaded += 1
        print(f"Downloaded {item['name']} -> {item['image']} ({ext[1:]})")
    except Exception as exc:
        failed.append((item.get("name"), str(exc)))
        print(f"FAILED {item.get('name')}: {exc}", file=sys.stderr)
    time.sleep(0.15)

# Persist image paths to match the detected formats (e.g. .webp rather than .png).
JSON_PATH.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"Portraits: downloaded={downloaded}, skipped={skipped}, failed={len(failed)}")
if failed:
    print("Some portraits remain unavailable; the app's fallback will be used.")
