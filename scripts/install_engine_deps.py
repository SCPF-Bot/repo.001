import subprocess
import sys
import argparse
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("JIT_Installer")

def install_req(file_path):
    try:
        logger.info(f"Installing dependencies from {file_path}...")
        subprocess.check_call([sys.executable, "-m", "pip", "install", "-r", file_path])
    except Exception as e:
        logger.error(f"Failed to install {file_path}: {e}")

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--ocr", required=True)
    parser.add_argument("--tts", required=True)
    args = parser.parse_args()

    # Mapping engine names to their requirement files
    ocr_map = {
        "google_vision": "requirements/ocr_google_vision.txt",
        "manga_ocr": "requirements/ocr_manga_ocr.txt",
        "paddle_ocr": "requirements/ocr_paddle_ocr.txt",
        "tesseract": None # Built into system_deps.sh
    }
    
    tts_map = {
        "elevenlabs": "requirements/tts_elevenlabs.txt",
        "xtts_v2": "requirements/tts_xtts_v2.txt",
        "melo_tts": "requirements/tts_melo_tts.txt",
        "edge_tts": "requirements/tts_edge_tts.txt"
    }

    if ocr_map.get(args.ocr):
        install_req(ocr_map[args.ocr])
    
    if tts_map.get(args.tts):
        install_req(tts_map[args.tts])

if __name__ == "__main__":
    main()
