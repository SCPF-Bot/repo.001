#!/usr/bin/env python3
import os
import argparse
import asyncio
import subprocess
import shutil
import sys
from pathlib import Path

# Add parent directory to path to import engines
sys.path.insert(0, str(Path(__file__).parent.parent))

# Fallback for natsort to ensure page order 1, 2, 10 instead of 1, 10, 2
try:
    from natsort import natsorted
except ImportError:
    natsorted = sorted

# Import engines with proper error handling
try:
    from engines.ocr_engines import OCREngine
    from engines.tts_engines import TTSEngine
except ImportError as e:
    print(f"Failed to import engines: {e}")
    print("Make sure engines/ directory exists with ocr_engines.py and tts_engines.py")
    sys.exit(1)

# Global Configuration
BASE_DIR = Path("processing")
OUT_DIR = Path("output")
TEMP_IMG = BASE_DIR / "images"
TEMP_AUD = BASE_DIR / "audio"

class MangaVideoOrchestrator:
    def __init__(self, ocr_type, tts_type):
        print(f"Initializing Orchestrator with OCR={ocr_type}, TTS={tts_type}")
        self.ocr = OCREngine(ocr_type)
        self.tts = TTSEngine(tts_type)
        self._prepare_env()

    def _prepare_env(self):
        """Clean and recreate the workspace for a fresh build."""
        for d in [OUT_DIR, TEMP_IMG, TEMP_AUD, BASE_DIR]:
            if d.exists():
                shutil.rmtree(d)
            d.mkdir(parents=True, exist_ok=True)

    async def download_and_extract(self, url):
        """Secure ingestion of manga assets."""
        print(f"--- Phase 1: Ingestion ---")
        archive_path = BASE_DIR / "manga_archive.zip"
        try:
            # Use curl with -L to follow redirects and timeout
            subprocess.run(
                ["curl", "-L", "--max-time", "300", url, "-o", str(archive_path)], 
                check=True, 
                timeout=300
            )
            # Unzip quietly; handle potential nested directories
            subprocess.run(
                ["unzip", "-o", "-q", str(archive_path), "-d", str(TEMP_IMG)], 
                check=True, 
                timeout=120
            )
            print(f"Successfully extracted assets to {TEMP_IMG}")
            
            # Remove zip file to save space
            archive_path.unlink()
            
        except subprocess.CalledProcessError as e:
            print(f"CRITICAL FAIL: Download/Unzip failed. {e}")
            sys.exit(1)
        except subprocess.TimeoutExpired as e:
            print(f"CRITICAL FAIL: Operation timed out. {e}")
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
            try:
                text = self.ocr.get_text(str(img_path))
                if not text or not text.strip():
                    text = "No text detected on this page."
                    print(f"  Warning: No text detected on page {i+1}")
                else:
                    print(f"  Extracted text: {text[:100]}...")
            except Exception as e:
                print(f"  OCR Error on page {i+1}: {e}")
                text = "Error processing text on this page."
            
            # 2. TTS Generation
            audio_file = TEMP_AUD / f"page_{i:04d}.mp3"
            try:
                await self.tts.generate(text, str(audio_file))
                if not audio_file.exists() or audio_file.stat().st_size == 0:
                    raise Exception(f"Audio file not created or empty: {audio_file}")
                print(f"  Generated audio: {audio_file.name} ({audio_file.stat().st_size} bytes)")
            except Exception as e:
                print(f"  TTS Error on page {i+1}: {e}")
                # Create a silent audio file as fallback
                self._generate_silence_fallback(audio_file)
                print(f"  Created silent fallback audio")

            # 3. Precision Duration Check (using ffprobe)
            try:
                duration = subprocess.check_output([
                    "ffprobe", "-v", "error", "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1", str(audio_file)
                ], timeout=10).decode().strip()
                duration = float(duration) if duration else 2.0
                # Cap duration to reasonable limits
                duration = min(max(duration, 1.0), 30.0)
            except Exception:
                duration = 2.0
                print(f"  Using fallback duration: {duration}s")

            # 4. Prepare FFmpeg Concatenation Strings
            # Escape paths for FFmpeg compatibility
            safe_img_path = str(img_path.resolve()).replace("'", "'\\''")
            safe_aud_path = str(audio_file.resolve()).replace("'", "'\\''")
            
            manifest_data.append(f"file '{safe_img_path}'\nduration {duration}")
            audio_list_data.append(f"file '{safe_aud_path}'")

        # Phase 3: Final Render Orchestration
        await self._render(manifest_data, audio_list_data)

    def _generate_silence_fallback(self, output_path, duration_seconds=2.0):
        """Generate silent audio file as fallback."""
        try:
            subprocess.run([
                "ffmpeg", "-f", "lavfi", "-i", 
                f"anullsrc=channel_layout=stereo:sample_rate=44100",
                "-t", str(duration_seconds), "-c:a", "libmp3lame",
                "-q:a", "9", str(output_path), "-y"
            ], check=True, capture_output=True, timeout=30)
        except Exception:
            # Ultra-fallback: create empty file
            output_path.touch()

    async def _render(self, manifest_data, audio_list_data):
        print("--- Phase 3: Final Encoding ---")
        
        # Write temporary manifests
        img_manifest = BASE_DIR / "img_list.txt"
        aud_manifest = BASE_DIR / "aud_list.txt"
        
        # FFmpeg concat demuxer requires the last file to be repeated without duration
        last_img = manifest_data[-1].split('\n')[0]
        img_manifest.write_text("\n".join(manifest_data) + f"\n{last_img}")
        aud_manifest.write_text("\n".join(audio_list_data))

        master_audio = BASE_DIR / "master_audio.mp3"
        final_video = OUT_DIR / "manga_ai_render.mp4"

        # 1. Combine all audio segments into one master track
        try:
            subprocess.run([
                "ffmpeg", "-f", "concat", "-safe", "0", "-i", str(aud_manifest),
                "-c", "copy", str(master_audio), "-y"
            ], check=True, capture_output=True, timeout=300)
            print(f"  Combined {len(audio_list_data)} audio tracks")
        except subprocess.CalledProcessError as e:
            print(f"Audio concatenation failed: {e.stderr.decode() if e.stderr else str(e)}")
            sys.exit(1)

        # 2. Stitch images to the master audio
        cmd = [
            "ffmpeg", "-f", "concat", "-safe", "0", "-i", str(img_manifest),
            "-i", str(master_audio),
            "-c:v", "libx264", "-pix_fmt", "yuv420p", "-preset", "medium", "-crf", "23",
            "-c:a", "aac", "-b:a", "192k", "-shortest", str(final_video), "-y"
        ]
        
        try:
            subprocess.run(cmd, check=True, capture_output=True, timeout=600)
            file_size = final_video.stat().st_size / (1024 * 1024)  # Size in MB
            print(f"BUILD SUCCESSFUL: {final_video} ({file_size:.2f} MB)")
        except subprocess.CalledProcessError as e:
            print(f"CRITICAL RENDER FAIL: {e.stderr.decode() if e.stderr else str(e)}")
            sys.exit(1)

    def cleanup(self):
        """Clean up large files to save space"""
        try:
            for d in [TEMP_IMG, TEMP_AUD]:
                if d.exists():
                    shutil.rmtree(d)
        except:
            pass

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Zero-Fail Manga Pipeline")
    parser.add_argument("--url", required=True, help="URL to CBZ/Zip")
    parser.add_argument("--ocr", default="tesseract", help="OCR Engine choice")
    parser.add_argument("--tts", default="edge_tts", help="TTS Engine choice")
    args = parser.parse_args()

    orchestrator = MangaVideoOrchestrator(args.ocr, args.tts)
    try:
        asyncio.run(orchestrator.run(args.url))
    finally:
        orchestrator.cleanup()
