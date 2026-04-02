#!/usr/bin/env python3
"""
Manga to Video Pipeline
Downloads archive, extracts pages, performs OCR, generates TTS, and renders video.
"""
import os
import sys
import argparse
import asyncio
import logging
import tempfile
import subprocess
from pathlib import Path
from typing import List, Tuple

# Ensure we can import sibling modules
SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

from ocr_engines import OCREngine
from tts_engines import TTSEngine
from utils import download_file, extract_archive, resize_and_pad, get_audio_duration, cleanup_temp_dirs

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger("MangaPipeline")

class MangaToVideoPipeline:
    def __init__(self, url: str, ocr_engine: str, tts_engine: str):
        self.url = url
        self.ocr_engine_type = ocr_engine
        self.tts_engine_type = tts_engine
        self.ocr = OCREngine(ocr_engine)
        self.tts = TTSEngine(tts_engine)
        self.temp_dir = Path(tempfile.mkdtemp(prefix="manga_pipeline_"))
        self.images_dir = self.temp_dir / "images"
        self.processed_dir = self.temp_dir / "processed"
        self.audio_dir = self.temp_dir / "audio"
        self.segments_dir = self.temp_dir / "segments"
        self.output_video = Path.cwd() / "output" / f"manga_video_{os.getpid()}.mp4"

    async def run(self) -> Path:
        """Execute the full pipeline."""
        try:
            # 1. Download and extract
            archive_path = self.temp_dir / "manga.cbz"
            await download_file(self.url, archive_path)
            image_paths = await extract_archive(archive_path, self.images_dir)
            logger.info(f"Extracted {len(image_paths)} images")

            # 2. Process each page: OCR -> TTS
            self.processed_dir.mkdir(exist_ok=True)
            self.audio_dir.mkdir(exist_ok=True)
            self.segments_dir.mkdir(exist_ok=True)
            segments = []  # list of (image_path, audio_path, duration)

            for idx, orig_img in enumerate(image_paths):
                logger.info(f"Processing page {idx+1}/{len(image_paths)}")
                # Preprocess image (resize+pad)
                proc_img = self.processed_dir / f"page_{idx:04d}.jpg"
                resize_and_pad(orig_img, proc_img)

                # OCR
                text = self.ocr.get_text(str(proc_img))
                if not text.strip():
                    logger.warning(f"No text found on page {idx+1}, using silence")
                    text = " "

                # TTS
                audio_file = self.audio_dir / f"audio_{idx:04d}.mp3"
                await self.tts.generate(text, str(audio_file))
                duration = get_audio_duration(audio_file)
                segments.append((proc_img, audio_file, duration))

            # 3. Render video using segment-based approach
            final_video = await self._render_video(segments)
            logger.info(f"Video created at {final_video}")
            return final_video

        except Exception as e:
            logger.exception("Pipeline failed")
            raise
        finally:
            self.tts.cleanup()
            cleanup_temp_dirs(self.temp_dir)

    async def _render_video(self, segments: List[Tuple[Path, Path, float]]) -> Path:
        """Generate final video by creating one segment per image+audio and concatenating."""
        segment_videos = []
        for idx, (img_path, audio_path, duration) in enumerate(segments):
            seg_video = self.segments_dir / f"segment_{idx:04d}.mp4"
            # Build ffmpeg command: loop image for the duration of the audio
            cmd = [
                "ffmpeg", "-y",
                "-loop", "1", "-i", str(img_path),
                "-i", str(audio_path),
                "-c:v", "libx264", "-preset", "fast", "-crf", "22",
                "-c:a", "aac", "-b:a", "128k",
                "-t", str(duration),  # ensure video ends with audio
                "-pix_fmt", "yuv420p",
                "-shortest",
                str(seg_video)
            ]
            try:
                subprocess.run(cmd, check=True, capture_output=True, text=True)
            except subprocess.CalledProcessError as e:
                logger.error(f"FFmpeg segment error: {e.stderr}")
                raise
            segment_videos.append(seg_video)

        # Concatenate all segments
        concat_file = self.temp_dir / "concat.txt"
        with open(concat_file, "w") as f:
            for seg in segment_videos:
                f.write(f"file '{seg}'\n")

        # Use concat demuxer for lossless concatenation
        concat_cmd = [
            "ffmpeg", "-y",
            "-f", "concat", "-safe", "0", "-i", str(concat_file),
            "-c", "copy",
            str(self.output_video)
        ]
        subprocess.run(concat_cmd, check=True, capture_output=True, text=True)
        self.output_video.parent.mkdir(parents=True, exist_ok=True)
        return self.output_video

async def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", required=True, help="Direct download URL of CBZ/ZIP")
    parser.add_argument("--ocr", default="tesseract", choices=["google_vision", "manga_ocr", "paddle_ocr", "tesseract"])
    parser.add_argument("--tts", default="edge_tts", choices=["elevenlabs", "edge_tts", "xtts_v2", "melo_tts"])
    args = parser.parse_args()

    pipeline = MangaToVideoPipeline(args.url, args.ocr, args.tts)
    output = await pipeline.run()
    print(f"SUCCESS: {output}")

if __name__ == "__main__":
    asyncio.run(main())
