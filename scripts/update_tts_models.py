#!/usr/bin/env python3
"""Update and verify the bundled TTS voice packs for MLBB Plus.

The voice packs ship inside the APK (see docs/tts_implementation.md): the Gradle
task ``bundleTtsModels`` reads ``app/src/main/assets/tts/tts-models.json`` and
stages the archives listed there into the build. This script is the only thing
that writes that manifest.

What it does (default = update mode):
  1. Downloads every pinned voice pack archive into ``app/tts-models-cache/``
     (gitignored; re-used across runs and by the Gradle build).
  2. Verifies each archive's SHA-256 against the pins below, and cross-checks
     them against the upstream ``checksum.txt`` of the ``tts-models`` release
     when that file is reachable.
  3. Scans each tar.bz2 (no full extraction) to validate the expected layout -
     tokens.txt, espeak-ng-data/, the .onnx graph - and to measure the exact
     uncompressed size the installer will write to disk.
  4. Regenerates ``app/src/main/assets/tts/tts-models.json`` from verified
     facts. That manifest is the single source of truth for the app, the Gradle
     task and this script's own --check mode.

To adopt a new upstream model version: update the PINNED_MODELS table below
(fresh sha256/size from upstream checksum.txt, same speakers mapping rules -
see VoiceModelInstaller.kt), then run this script and commit the regenerated
manifest. Never hand-edit the manifest.

Usage:
  python3 scripts/update_tts_models.py            # download + verify + rewrite
  python3 scripts/update_tts_models.py --check    # verify only; exit 1 on drift
  python3 scripts/update_tts_models.py --offline   # fail unless cache is complete

Exit codes: 0 = everything verified (and manifest fresh in update mode),
1 = a check failed, 2 = usage/environment error.
"""
import argparse
import hashlib
import json
import sys
import tarfile
import tempfile
import urllib.error
import urllib.request
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CACHE_DIR = ROOT / "app" / "tts-models-cache"
MANIFEST_PATH = ROOT / "app" / "src" / "main" / "assets" / "tts" / "tts-models.json"

# Permanent release tag of k2-fsa/sherpa-onnx that hosts the model packs.
SOURCE_BASE = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"
CHECKSUM_TXT_URL = SOURCE_BASE + "/checksum.txt"

# Mirrors VoiceModelInstaller.SKIPPED_FILES: metadata the installer drops.
SKIPPED_FILES = {"README.md", "MODEL_CARD", "LICENSE", ".DS_Store"}
MIN_ONNX_BYTES = 2 * 1024 * 1024

# ---------------------------------------------------------------------------
# Pins. The SHA-256 values are the trust anchor: a downloaded byte stream that
# disagrees with them is discarded, not adopted. To bump a model, change the
# pin here after reviewing upstream checksum.txt.
#
# "speakers" maps the app's voice ids (VoiceGuideSettings) to sherpa-onnx
# integer speaker ids. Kokoro sids were confirmed by measuring the fundamental
# frequency of every sid on a real 24 kHz synthesis run: sids 0-4 and 7-8 are
# the female voices, 5-6 and 9-10 the male ones, matching the alphabetical
# kokoro-onnx order (af_bella, af_heart, af_nicole, af_sarah, af_sky, am_adam,
# am_michael, bf_emma, bf_isabella, bm_george, bm_lewis). Piper packs are
# single-speaker and always map to 0.
# ---------------------------------------------------------------------------
PINNED_MODELS = [
    {
        "id": "kokoro-int8-en-v0_19",
        "label": "Kokoro (most natural)",
        "family": "kokoro",
        "dirName": "kokoro-int8-en-v0_19",
        "archive": "kokoro-int8-en-v0_19.tar.bz2",
        "sha256": "c9f0dd393615805b0bab050c340834d5e684e732aec91c0e860cd30e982c08bd",
        "modelFile": "model.int8.onnx",
        "voicesFile": "voices.bin",
        "tokensFile": "tokens.txt",
        "espeakDir": "espeak-ng-data",
        "speakers": {"af_heart": 1, "am_adam": 5},
    },
    {
        "id": "vits-piper-en_US-lessac-medium-int8",
        "label": "Piper female (small, faster)",
        "family": "piper",
        "dirName": "vits-piper-en_US-lessac-medium-int8",
        "archive": "vits-piper-en_US-lessac-medium-int8.tar.bz2",
        "sha256": "f1c6d0295cf16087b05f80fdca5b44daca5cd78e2c425d419a42ba34929805f9",
        "modelFile": "en_US-lessac-medium.onnx",
        "voicesFile": "",
        "tokensFile": "tokens.txt",
        "espeakDir": "espeak-ng-data",
        "speakers": {"lessac-medium": 0},
    },
    {
        "id": "vits-piper-en_US-ryan-medium-int8",
        "label": "Piper male (small, faster)",
        "family": "piper",
        "dirName": "vits-piper-en_US-ryan-medium-int8",
        "archive": "vits-piper-en_US-ryan-medium-int8.tar.bz2",
        "sha256": "376eb489d42e98cb49f0e13a633e8d88580bd247ed8aacc298a733210404f771",
        "modelFile": "en_US-ryan-medium.onnx",
        "voicesFile": "",
        "tokensFile": "tokens.txt",
        "espeakDir": "espeak-ng-data",
        "speakers": {"ryan-medium": 0},
    },
]

failures = []


def fail(msg):
    failures.append(msg)
    print(f"FAIL: {msg}")


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def fetch(url: str, dest: Path, timeout: int = 300) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    partial = dest.with_suffix(dest.suffix + ".part")
    print(f"  downloading {url}")
    request = urllib.request.Request(url, headers={"User-Agent": "mlbb-plus-tts-updater"})
    with urllib.request.urlopen(request, timeout=timeout) as response, partial.open("wb") as out:
        while True:
            chunk = response.read(1 << 16)
            if not chunk:
                break
            out.write(chunk)
    partial.replace(dest)


def fetch_upstream_checksums():
    """Best effort: upstream checksum.txt maps 'sha256  filename' lines."""
    try:
        request = urllib.request.Request(
            CHECKSUM_TXT_URL, headers={"User-Agent": "mlbb-plus-tts-updater"}
        )
        with urllib.request.urlopen(request, timeout=60) as response:
            text = response.read().decode("utf-8", "replace")
    except (urllib.error.URLError, OSError) as exc:
        print(f"NOTE: could not fetch upstream checksum.txt ({exc}); pin-only verification.")
        return {}
    table = {}
    for line in text.splitlines():
        parts = line.split()
        if len(parts) >= 2 and len(parts[0]) == 64:
            table[parts[-1].lstrip("*")] = parts[0].lower()
    return table


def scan_archive(model, archive_path: Path):
    """Validate layout and measure uncompressed size without extracting.

    Mirrors VoiceModelInstaller.safeChild(): entries prefixed with the model
    directory name are stripped, metadata files are skipped.
    """
    prefix = model["dirName"] + "/"
    seen_tokens = False
    seen_espeak_dir = False
    onnx_bytes = 0
    onnx_name = ""
    total = 0
    with tarfile.open(archive_path, "r:bz2") as tar:
        for entry in tar:
            name = entry.name.replace("\\", "/").lstrip("/")
            if name.startswith(prefix):
                name = name[len(prefix):]
            if not name or name == model["dirName"]:
                continue
            if entry.isdir():
                if name.rstrip("/") == model["espeakDir"]:
                    seen_espeak_dir = True
                continue
            leaf = name.rsplit("/", 1)[-1]
            if leaf in SKIPPED_FILES:
                continue
            total += entry.size
            if name == model["tokensFile"]:
                seen_tokens = True
            if name.startswith(model["espeakDir"] + "/"):
                seen_espeak_dir = True
            if leaf.endswith(".onnx") and entry.size > onnx_bytes:
                onnx_bytes, onnx_name = entry.size, leaf

    expected_onnx = model["modelFile"]
    if onnx_name and onnx_name != expected_onnx:
        print(f"  NOTE: {model['id']}: found {onnx_name}, expected {expected_onnx}")
    if not seen_tokens:
        fail(f"{model['id']}: missing {model['tokensFile']}")
    if not seen_espeak_dir:
        fail(f"{model['id']}: missing {model['espeakDir']}/")
    if onnx_bytes <= MIN_ONNX_BYTES:
        fail(f"{model['id']}: no usable .onnx graph (largest: {onnx_bytes} bytes)")
    return total


def build_manifest_entry(model, archive_path: Path, uncompressed: int) -> dict:
    return {
        "id": model["id"],
        "label": model["label"],
        "family": model["family"],
        "dirName": model["dirName"],
        "archive": model["archive"],
        "url": f"{SOURCE_BASE}/{model['archive']}",
        "sha256": model["sha256"].lower(),
        "sizeBytes": archive_path.stat().st_size,
        "uncompressedBytes": uncompressed,
        "modelFile": model["modelFile"],
        "voicesFile": model["voicesFile"],
        "tokensFile": model["tokensFile"],
        "espeakDir": model["espeakDir"],
        "speakers": dict(sorted(model["speakers"].items())),
    }


def serialize(entries) -> str:
    doc = {
        "schema": 1,
        "updated": date.today().isoformat(),
        "source": SOURCE_BASE,
        "note": (
            "Maintained by scripts/update_tts_models.py - do not edit by hand. "
            "The archives are bundled into the APK at build time by the "
            "bundleTtsModels Gradle task; they are not committed to git (the "
            "Kokoro archive alone is 103 MB, over GitHub's per-file limit)."
        ),
        "models": entries,
    }
    return json.dumps(doc, indent=2, ensure_ascii=False) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true",
                        help="verify only: report drift between pins, cache and manifest; write nothing")
    parser.add_argument("--offline", action="store_true",
                        help="never touch the network; fail if a cached archive is missing")
    args = parser.parse_args()

    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    upstream = {} if args.offline else fetch_upstream_checksums()

    entries = []
    for model in PINNED_MODELS:
        print(f"\n== {model['id']} ==")
        pinned = model["sha256"].lower()
        archive_path = CACHE_DIR / model["archive"]

        if upstream:
            up = upstream.get(model["archive"])
            if up is None:
                print(f"  NOTE: {model['archive']} not listed in upstream checksum.txt")
            elif up != pinned:
                fail(f"{model['id']}: pin {pinned} disagrees with upstream checksum.txt ({up}). "
                     "Review upstream and update PINNED_MODELS if the change is intentional.")
            else:
                print("  pin matches upstream checksum.txt")

        if not archive_path.is_file() or sha256_of(archive_path) != pinned:
            if args.offline:
                fail(f"{model['archive']}: not in cache and --offline was given")
                continue
            try:
                fetch(f"{SOURCE_BASE}/{model['archive']}", archive_path)
            except (urllib.error.URLError, OSError) as exc:
                fail(f"{model['id']}: download failed ({exc})")
                continue

        actual = sha256_of(archive_path)
        if actual != pinned:
            fail(f"{model['id']}: archive sha256 {actual} != pinned {pinned}")
            continue
        print(f"  sha256 ok ({archive_path.stat().st_size / 1_000_000:.1f} MB)")

        uncompressed = scan_archive(model, archive_path)
        print(f"  layout ok, {uncompressed / 1_000_000:.1f} MB unpacked")
        entries.append(build_manifest_entry(model, archive_path, uncompressed))

    if failures:
        print(f"\n{len(failures)} failure(s); manifest not written.")
        return 1

    manifest_text = serialize(entries)
    if args.check:
        current = MANIFEST_PATH.read_text(encoding="utf-8") if MANIFEST_PATH.exists() else ""
        # Compare ignoring the "updated" stamp: a stale date alone is not drift.
        def strip_date(text):
            return "\n".join(line for line in text.splitlines() if not line.strip().startswith('"updated"'))
        if strip_date(current) != strip_date(manifest_text):
            fail(f"{MANIFEST_PATH.relative_to(ROOT)} is stale or hand-edited; "
                 "run scripts/update_tts_models.py to regenerate it.")
        else:
            print(f"\nManifest is in sync ({len(entries)} models verified).")
        return 1 if failures else 0

    old = MANIFEST_PATH.read_text(encoding="utf-8") if MANIFEST_PATH.is_file() else ""
    if strip_date_in_update(old, manifest_text):
        MANIFEST_PATH.parent.mkdir(parents=True, exist_ok=True)
        MANIFEST_PATH.write_text(manifest_text, encoding="utf-8")
        print(f"\nWrote {MANIFEST_PATH.relative_to(ROOT)} ({len(entries)} models).")
    else:
        print(f"\nManifest already up to date ({len(entries)} models verified).")
    return 0


def strip_date_in_update(old: str, new: str) -> bool:
    """True when old and new differ outside the 'updated' stamp."""
    def strip(text):
        return "\n".join(line for line in text.splitlines() if not line.strip().startswith('"updated"'))
    return strip(old) != strip(new)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print("\ninterrupted")
        sys.exit(2)
