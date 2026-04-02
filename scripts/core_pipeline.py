#!/usr/bin/env python3
"""
Core Pipeline for Manga to Video Processing
Handles download, OCR, TTS, and video generation
"""

import os
import sys
import argparse
import logging
import subprocess
import json
from pathlib import Path
import requests
from natsort import natsorted
from PIL import Image
import zipfile
import tempfile
import shutil

# Add engines to path
sys.path.insert(0, str(Path(__file__).parent.parent))
from engines.ocr_engines import OCREngine
from engines.tts_engines import TTSEngine

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def download_manga(url, output_dir):
    """Download manga from URL (supports CBZ, ZIP)"""
    logger.info(f"Downloading manga from {url}")
    
    # Create images directory
    images_dir = Path(output_dir) / "images"
    images_dir.mkdir(parents=True, exist_ok=True)
    
    try:
        response = requests.get(url, stream=True, timeout=30)
        response.raise_for_status()
        
        temp_zip = Path(output_dir) / "manga_temp.zip"
        with open(temp_zip, 'wb') as f:
            for chunk in response.iter_content(chunk_size=8192):
                if chunk:
                    f.write(chunk)
        
        # Extract images
        with zipfile.ZipFile(temp_zip, 'r') as zip_ref:
            # Extract only image files to avoid directory structure issues
            for file_info in zip_ref.infolist():
                if not file_info.is_dir():
                    file_ext = Path(file_info.filename).suffix.lower()
                    if file_ext in ['.png', '.jpg', '.jpeg', '.webp', '.gif']:
                        # Extract to images directory with simplified name
                        target_name = Path(file_info.filename).name
                        target_path = images_dir / target_name
                        with zip_ref.open(file_info) as source, open(target_path, 'wb') as target:
                            shutil.copyfileobj(source, target)
        
        # Clean up temp zip
        temp_zip.unlink()
        
    except Exception as e:
        logger.error(f"Download/extraction failed: {e}")
        raise
    
    # Get sorted image files
    image_files = []
    for ext in ['*.png', '*.jpg', '*.jpeg', '*.webp', '*.gif']:
        image_files.extend(images_dir.glob(ext))
        image_files.extend(images_dir.glob(ext.upper()))
    
    if not image_files:
        raise Exception("No image files found in the downloaded manga")
    
    return natsorted(image_files)

def get_audio_duration(audio_path):
    """Get duration of audio file in seconds using ffprobe"""
    try:
        cmd = [
            "ffprobe",
            "-v", "error",
            "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1",
            str(audio_path)
        ]
        result = subprocess.run(cmd, capture_output=True, text=True, check=True)
        return float(result.stdout.strip())
    except Exception as e:
        logger.error(f"Failed to get audio duration for {audio_path}: {e}")
        return 5.0  # Default fallback duration

def process_page(image_path, ocr_engine, tts_engine, output_dir):
    """Process a single manga page"""
    logger.info(f"Processing {image_path.name}")
    
    # Extract text using OCR
    text = ocr_engine.get_text(str(image_path))
    if not text:
        logger.warning(f"No text found in {image_path.name}")
        return None
    
    logger.info(f"Extracted {len(text)} characters from {image_path.name}")
    
    # Generate audio using TTS
    audio_path = output_dir / f"{image_path.stem}.mp3"
    success = tts_engine.generate(text, str(audio_path))
    
    if not success or not audio_path.exists() or audio_path.stat().st_size == 0:
        logger.warning(f"Failed to generate audio for {image_path.name}")
        return None
    
    # Get audio duration
    duration = get_audio_duration(audio_path)
    logger.info(f"Generated audio with duration: {duration:.2f}s")
    
    return {
        'image': image_path,
        'audio': audio_path,
        'duration': duration,
        'text': text[:100]  # Store preview for logging
    }

def create_video_with_audio(page_data, output_path):
    """Create video with synchronized audio for each page using concat filter"""
    logger.info("Creating video with audio...")
    
    # Create temporary directory for processed clips
    temp_dir = Path(tempfile.mkdtemp())
    
    try:
        clip_files = []
        
        for idx, page in enumerate(page_data):
            logger.info(f"Processing clip {idx + 1}/{len(page_data)}")
            
            # Create a video clip from image with duration matching audio
            clip_path = temp_dir / f"clip_{idx:04d}.mp4"
            
            # FFmpeg command to create video clip from image with exact audio duration
            cmd = [
                "ffmpeg",
                "-loop", "1",
                "-i", str(page['image']),
                "-i", str(page['audio']),
                "-c:v", "libx264",
                "-c:a", "aac",
                "-t", str(page['duration']),
                "-pix_fmt", "yuv420p",
                "-vf", "scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2",
                "-shortest",
                "-y",
                str(clip_path)
            ]
            
            try:
                subprocess.run(cmd, check=True, capture_output=True, text=True)
                clip_files.append(clip_path)
            except subprocess.CalledProcessError as e:
                logger.error(f"FFmpeg error for clip {idx}: {e.stderr}")
                raise
        
        # Create concat file list
        concat_file = temp_dir / "concat_list.txt"
        with open(concat_file, 'w') as f:
            for clip in clip_files:
                f.write(f"file '{clip}'\n")
        
        # Concatenate all clips
        final_cmd = [
            "ffmpeg",
            "-f", "concat",
            "-safe", "0",
            "-i", str(concat_file),
            "-c", "copy",
            "-y",
            str(output_path)
        ]
        
        subprocess.run(final_cmd, check=True, capture_output=True, text=True)
        
        logger.info(f"Video created successfully: {output_path}")
        
    finally:
        # Clean up temporary files
        shutil.rmtree(temp_dir, ignore_errors=True)

def estimate_total_duration(page_data):
    """Calculate total video duration"""
    total = sum(page['duration'] for page in page_data)
    minutes = int(total // 60)
    seconds = int(total % 60)
    return f"{minutes}:{seconds:02d}"

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
    
    ocr_engine = None
    tts_engine = None
    
    try:
        # Initialize engines
        logger.info(f"Initializing OCR engine: {args.ocr}")
        ocr_engine = OCREngine(args.ocr)
        
        logger.info(f"Initializing TTS engine: {args.tts}")
        tts_engine = TTSEngine(args.tts)
        
        # Download and extract manga
        image_files = download_manga(args.url, base_dir)
        logger.info(f"Found {len(image_files)} pages")
        
        if not image_files:
            logger.error("No images found in the downloaded manga")
            sys.exit(1)
        
        # Process each page
        page_data = []
        for idx, img_path in enumerate(image_files, 1):
            logger.info(f"Processing page {idx}/{len(image_files)}: {img_path.name}")
            result = process_page(img_path, ocr_engine, tts_engine, audio_dir)
            if result:
                page_data.append(result)
                logger.info(f"✓ Page {idx} processed successfully")
            else:
                logger.warning(f"✗ Page {idx} skipped due to processing failure")
        
        # Check if we have any successfully processed pages
        if not page_data:
            logger.error("No pages were successfully processed")
            sys.exit(1)
        
        logger.info(f"Successfully processed {len(page_data)}/{len(image_files)} pages")
        
        # Calculate estimated duration
        duration_str = estimate_total_duration(page_data)
        logger.info(f"Estimated video duration: {duration_str}")
        
        # Create video
        output_video = output_dir / "manga_video.mp4"
        create_video_with_audio(page_data, output_video)
        
        # Get final file size
        file_size = output_video.stat().st_size / (1024 * 1024)  # MB
        logger.info(f"Success! Video saved to {output_video}")
        logger.info(f"File size: {file_size:.2f} MB")
        logger.info(f"Duration: {duration_str}")
        
    except KeyboardInterrupt:
        logger.info("Pipeline interrupted by user")
        sys.exit(1)
    except Exception as e:
        logger.error(f"Pipeline failed: {e}", exc_info=True)
        sys.exit(1)
    finally:
        # Cleanup
        if ocr_engine:
            ocr_engine.cleanup()
        if tts_engine:
            tts_engine.cleanup()
        
        # Optional: Clean up processing directory to save space
        # Uncomment if you want to clean up after successful run
        # if 'page_data' in locals() and page_data:
        #     shutil.rmtree(base_dir, ignore_errors=True)
        #     logger.info("Cleaned up temporary files")

if __name__ == "__main__":
    main()
