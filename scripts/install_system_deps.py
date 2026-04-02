#!/usr/bin/env python3
import subprocess
import sys
import argparse
import logging
from pathlib import Path

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("JIT_Installer")

REQUIREMENTS_DIR = Path(__file__).parent.parent / "requirements"

# Mapping to package names for direct pip install if file missing
OCR_PACKAGES = {
    "google_vision": "google-cloud-vision",
    "manga_ocr": "manga-ocr",
    "paddle_ocr": "paddlepaddle paddleocr",
    "tesseract": None,  # system package
}

TTS_PACKAGES = {
    "elevenlabs": "",   # no extra package needed (uses requests)
    "xtts_v2": "TTS",
    "melo_tts": "git+https://github.com/myshell-ai/MeloTTS.git",  # <-- fixed
    "edge_tts": "edge-tts",
}

def install_req(file_path: Path, fallback_pkg: str = None):
    if not file_path.exists():
        if fallback_pkg:
            logger.warning(f"Requirement file {file_path} not found. Installing {fallback_pkg} directly.")
            # Special handling for git URLs
            if fallback_pkg.startswith("git+"):
                subprocess.check_call([sys.executable, "-m", "pip", "install", fallback_pkg])
            else:
                subprocess.check_call([sys.executable, "-m", "pip", "install"] + fallback_pkg.split())
        else:
            logger.error(f"Missing requirement file {file_path} and no fallback provided. Skipping.")
        return
    logger.info(f"Installing dependencies from {file_path}...")
    subprocess.check_call([sys.executable, "-m", "pip", "install", "-r", str(file_path)])

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--ocr", required=True)
    parser.add_argument("--tts", required=True)
    args = parser.parse_args()

    ocr_file = REQUIREMENTS_DIR / f"ocr_{args.ocr}.txt"
    if OCR_PACKAGES.get(args.ocr):
        install_req(ocr_file, OCR_PACKAGES[args.ocr])

    tts_file = REQUIREMENTS_DIR / f"tts_{args.tts}.txt"
    if TTS_PACKAGES.get(args.tts):
        install_req(tts_file, TTS_PACKAGES[args.tts])

if __name__ == "__main__":
    main()
