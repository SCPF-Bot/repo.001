#!/usr/bin/env python3
"""
Optimized Just-in-Time installation of OCR/TTS engine dependencies.
"""
import subprocess
import sys
import argparse
import logging
import importlib.util
from pathlib import Path

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger("JIT_Installer")

# Path Setup
SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent
REQUIREMENTS_DIR = REPO_ROOT / "requirements"

# Engine Configuration
# Structure: (Requirement_File, Fallback_Packages_List, Import_Test_Name)
OCR_CONFIG = {
    "google_vision": ("ocr_google_vision.txt", ["google-cloud-vision"], "google.cloud.vision"),
    "manga_ocr": ("ocr_manga_ocr.txt", ["manga-ocr"], "manga_ocr"),
    "paddle_ocr": ("ocr_paddle_ocr.txt", ["paddlepaddle", "paddleocr"], "paddleocr"),
    "tesseract": (None, None, "pytesseract"),
}

TTS_CONFIG = {
    "elevenlabs": ("tts_elevenlabs.txt", ["elevenlabs"], "elevenlabs"),
    "edge_tts": ("tts_edge_tts.txt", ["edge-tts"], "edge_tts"),
    "xtts_v2": ("tts_xtts_v2.txt", ["TTS"], "TTS"),
    "melo_tts": ("tts_melo_tts.txt", ["mecab-python3", "git+https://github.com/myshell-ai/MeloTTS.git"], "melo"),
}

def check_package(package_name: str) -> bool:
    """Check if a package is already installed and importable."""
    if not package_name:
        return True
    return importlib.util.find_spec(package_name) is not None

def install_deps(req_filename: str, fallback_pkgs: list, import_name: str):
    """Install dependencies with optimized pip flags."""
    if check_package(import_name):
        logger.info(f"✅ {import_name} is already installed. Skipping.")
        return

    req_path = REQUIREMENTS_DIR / req_filename if req_filename else None
    
    # Common PIP flags for CI efficiency
    # --prefer-binary: Avoids long C++ compilations
    # --no-cache-dir: Prevents filling up runner disk space
    base_cmd = [sys.executable, "-m", "pip", "install", "--no-cache-dir", "--prefer-binary"]

    try:
        if req_path and req_path.exists():
            logger.info(f"Installing from {req_path}...")
            subprocess.check_call(base_cmd + ["-r", str(req_path)])
        elif fallback_pkgs:
            logger.warning(f"Installing {fallback_pkgs} via fallback...")
            subprocess.check_call(base_cmd + fallback_pkgs)
        else:
            logger.info(f"No additional python deps needed for {import_name}.")
    except subprocess.CalledProcessError as e:
        logger.error(f"❌ Failed to install dependencies for {import_name}. Error: {e}")
        sys.exit(1)

def main():
    parser = argparse.ArgumentParser(description="JIT Dependency Installer")
    parser.add_argument("--ocr", required=True, choices=OCR_CONFIG.keys())
    parser.add_argument("--tts", required=True, choices=TTS_CONFIG.keys())
    args = parser.parse_args()

    logger.info(f"🚀 Starting JIT installation for OCR: {args.ocr} | TTS: {args.tts}")

    # Process OCR
    ocr_file, ocr_fallback, ocr_import = OCR_CONFIG[args.ocr]
    install_deps(ocr_file, ocr_fallback, ocr_import)

    # Process TTS
    tts_file, tts_fallback, tts_import = TTS_CONFIG[args.tts]
    install_deps(tts_file, tts_fallback, tts_import)

    logger.info("🎉 All engine dependencies are ready.")

if __name__ == "__main__":
    main()
