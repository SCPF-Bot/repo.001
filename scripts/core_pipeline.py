import os
import argparse
import asyncio
import subprocess
import shutil
import sys
from pathlib import Path

# Fallback for natsort to ensure page order 1, 2, 10 instead of 1, 10, 2
try:
    from natsort import natsorted
except ImportError:
    natsorted = sorted

from engines.ocr_engines import OCREngine
from engines.tts_engines import TTSEngine

# Global Configuration
BASE_DIR = Path("processing")
OUT_DIR = Path("output")
TEMP_IMG = BASE_DIR / "images"
TEMP_AUD = BASE_DIR / "audio"

class MangaVideoOrchestrator:
    def __init__(self, ocr_type, tts_type):
        self.ocr = OCREngine(ocr_type)
        self.tts = TTSEngine(tts_type)
        self._prepare_env()

    def _prepare_env(self):
        """Clean and recreate the workspace for a fresh build."""
        for d in [OUT_DIR, TEMP_IMG, TEMP_AUD]:
            if d.exists():
                shutil.rmtree(d)
            d.mkdir(parents=True, exist_ok=True)

    async def download_and_extract(self, url):
        """Secure ingestion of manga assets."""
        print(f"--- Phase 1: Ingestion ---")
        archive_path = BASE_DIR / "manga_archive.zip"
        try:
            # Use curl with -L to follow redirects (essential for Drive/Dropbox/GitHub links)
            subprocess.run(["curl", "-L", url, "-o", str(archive_path)], check=True)
            # Unzip quietly; handle potential nested directories
            subprocess.run(["unzip", "-o", "-q", str(archive_path), "-d", str(TEMP_IMG)], check=True)
            print(f"Successfully extracted assets to {TEMP_IMG}")
        except subprocess.CalledProcessError as e:
            print(f"CRITICAL FAIL: Download/Unzip failed. {e}")
            sys.exit(1)

    async def run(self, url):
        await self.download_and_extract(url)

        # Gather images and sort them naturally
        valid_exts = ('.jpg', '.jpeg', '.png', '.webp', '.bmp')
        images = natsorted([
            f for f in TEMP_IMG.rglob("*") 
            if f.suffix.lower() in valid_exts and not f.name.startswith('.')
        ])

        if not images:
            print("CRITICAL FAIL: No valid images found in the archive.")
            sys.exit(1)

        print(f"--- Phase 2: Processing {len(images)} Pages ---")
        manifest_data = []
        audio_list_data = []

        for i, img_path in enumerate(images):
            print(f"Processing Page {i+1}/{len(images)}: {img_path.name}")
            
            # 1. OCR Extraction
            text = self.ocr.get_text(str(img_path))
            
            # 2. TTS Generation
            audio_file = TEMP_AUD / f"page_{i:04d}.mp3"
            await self.tts.generate(text, str(audio_file))

            # 3. Precision Duration Check (using ffprobe)
            # This is the heart of Zero-Fail sync.
            try:
                duration = subprocess.check_output([
                    "ffprobe", "-v", "error", "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1", str(audio_file)
                ]).decode().strip()
                # If ffprobe returns nothing or error, default to 2 seconds
                duration = float(duration) if duration else 2.0
            except Exception:
                duration = 2.0

            # 4. Prepare FFmpeg Concatenation Strings
            # Escape single quotes in paths for FFmpeg compatibility
            safe_img_path = str(img_path.resolve()).replace("'", "'\\''")
            safe_aud_path = str(audio_file.resolve()).replace("'", "'\\''")
            
            manifest_data.append(f"file '{safe_img_path}'\nduration {duration}")
            audio_list_data.append(f"file '{safe_aud_path}'")

        # Phase 3: Final Render Orchestration
        await self._render(manifest_data, audio_list_data)

    async def _render(self, manifest_data, audio_list_data):
        print("--- Phase 3: Final Encoding ---")
        
        # Write temporary manifests
        img_manifest = BASE_DIR / "img_list.txt"
        aud_manifest = BASE_DIR / "aud_list.txt"
        
        # FFmpeg concat demuxer requires the last file to be repeated without duration
        # OR just listed once more to close the loop.
        last_img = manifest_data[-1].split('\n')[0]
        img_manifest.write_text("\n".join(manifest_data) + f"\n{last_img}")
        aud_manifest.write_text("\n".join(audio_list_data))

        master_audio = BASE_DIR / "master_audio.mp3"
        final_video = OUT_DIR / "manga_ai_render.mp4"

        # 1. Combine all audio segments into one master track
        subprocess.run([
            "ffmpeg", "-f", "concat", "-safe", "0", "-i", str(aud_manifest),
            "-c", "copy", str(master_audio), "-y"
        ], check=True)

        # 2. Stitch images to the master audio
        # Using libx264 with yuv420p for maximum compatibility with social media/mobile
        cmd = [
            "ffmpeg", "-f", "concat", "-safe", "0", "-i", str(img_manifest),
            "-i", str(master_audio),
            "-c:v", "libx264", "-pix_fmt", "yuv420p", "-preset", "slow", "-crf", "18",
            "-c:a", "aac", "-b:a", "192k", "-shortest", str(final_video), "-y"
        ]
        
        try:
            subprocess.run(cmd, check=True)
            print(f"BUILD SUCCESSFUL: {final_video}")
        except subprocess.CalledProcessError as e:
            print(f"CRITICAL RENDER FAIL: {e}")
            sys.exit(1)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Zero-Fail Manga Pipeline")
    parser.add_argument("--url", required=True, help="URL to CBZ/Zip")
    parser.add_argument("--ocr", default="tesseract", help="OCR Engine choice")
    parser.add_argument("--tts", default="edge_tts", help="TTS Engine choice")
    args = parser.parse_argument_group().parser.parse_args()

    orchestrator = MangaVideoOrchestrator(args.ocr, args.tts)
    asyncio.run(orchestrator.run(args.url))
