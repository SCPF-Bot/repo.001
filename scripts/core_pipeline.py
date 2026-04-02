#!/usr/bin/env python3
"""
Core Pipeline for Manga to Video Processing
Handles download, OCR, TTS, and video generation
"""

import os
import sys
import argparse
import logging
import tempfile
import subprocess
from pathlib import Path
import requests
from natsort import natsorted
from PIL import Image
import zipfile
import io

# Add engines to path
sys.path.insert(0, str(Path(__file__).parent.parent))
from engines.ocr_engines import OCREngine
from engines.tts_engines import TTSEngine

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def download_manga(url, output_dir):
    """Download manga from URL (supports CBZ, ZIP)"""
    logger.info(f"Downloading manga from {url}")
    
    response = requests.get(url, stream=True)
    response.raise_for_status()
    
    temp_zip = Path(output_dir) / "manga_temp.zip"
    with open(temp_zip, 'wb') as f:
        for chunk in response.iter_content(chunk_size=8192):
            f.write(chunk)
    
    # Extract images
    extract_dir = Path(output_dir) / "images"
    extract_dir.mkdir(exist_ok=True)
    
    with zipfile.ZipFile(temp_zip, 'r') as zip_ref:
        zip_ref.extractall(extract_dir)
    
    # Clean up temp zip
    temp_zip.unlink()
    
    # Get sorted image files
    image_files = []
    for ext in ['*.png', '*.jpg', '*.jpeg', '*.webp']:
        image_files.extend(extract_dir.glob(f"**/{ext}"))
        image_files.extend(extract_dir.glob(f"**/{ext.upper()}"))
    
    return natsorted(image_files)

def process_page(image_path, ocr_engine, tts_engine, output_dir):
    """Process a single manga page"""
    logger.info(f"Processing {image_path.name}")
    
    # Extract text using OCR
    text = ocr_engine.get_text(str(image_path))
    if not text:
        logger.warning(f"No text found in {image_path.name}")
        return None
    
    logger.info(f"Extracted {len(text)} characters")
    
    # Generate audio using TTS
    audio_path = output_dir / f"{image_path.stem}.mp3"
    tts_engine.generate(text, str(audio_path))
    
    return audio_path

def create_video(image_files, audio_files, output_path):
    """Create video from images and audio"""
    logger.info("Creating video...")
    
    # This is a simplified version - you'll need to implement
    # proper video creation with ffmpeg or similar
    
    temp_file = Path("/tmp/video_input.txt")
    with open(temp_file, 'w') as f:
        for img in image_files:
            duration = 5  # Default 5 seconds per page
            f.write(f"file '{img}'\n")
            f.write(f"duration {duration}\n")
    
    # FFmpeg command to create video
    cmd = [
        "ffmpeg",
        "-f", "concat",
        "-safe", "0",
        "-i", str(temp_file),
        "-c:v", "libx264",
        "-pix_fmt", "yuv420p",
        "-y",
        output_path
    ]
    
    subprocess.run(cmd, check=True)
    temp_file.unlink()
    
    logger.info(f"Video created: {output_path}")

def main():
    parser = argparse.ArgumentParser(description="Manga to Video Pipeline")
    parser.add_argument("--url", required=True, help="URL to download manga")
    parser.add_argument("--ocr", default="tesseract", help="OCR engine to use")
    parser.add_argument("--tts", default="edge_tts", help="TTS engine to use")
    
    args = parser.parse_args()
    
    # Create directories
    base_dir = Path("processing")
    images_dir = base_dir / "images"
    audio_dir = base_dir / "audio"
    output_dir = Path("output")
    
    base_dir.mkdir(exist_ok=True)
    images_dir.mkdir(exist_ok=True)
    audio_dir.mkdir(exist_ok=True)
    output_dir.mkdir(exist_ok=True)
    
    try:
        # Initialize engines
        ocr_engine = OCREngine(args.ocr)
        tts_engine = TTSEngine(args.tts)
        
        # Download and extract manga
        image_files = download_manga(args.url, base_dir)
        logger.info(f"Found {len(image_files)} pages")
        
        # Process each page
        audio_files = []
        for idx, img_path in enumerate(image_files):
            audio_path = process_page(img_path, ocr_engine, tts_engine, audio_dir)
            if audio_path:
                audio_files.append(audio_path)
        
        # Create video
        if image_files and audio_files:
            output_video = output_dir / "manga_video.mp4"
            create_video(image_files, audio_files, output_video)
            logger.info(f"Success! Video saved to {output_video}")
        else:
            logger.error("No pages or audio generated")
            sys.exit(1)
            
    except Exception as e:
        logger.error(f"Pipeline failed: {e}", exc_info=True)
        sys.exit(1)
    finally:
        # Cleanup
        ocr_engine.cleanup()
        tts_engine.cleanup()

if __name__ == "__main__":
    main()
