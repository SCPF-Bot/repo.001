#!/usr/bin/env python3
"""
Core Pipeline for Manga to Video Processing
Handles download, OCR, TTS, and lightning-fast video generation
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
    """Download manga from URL (supports CBZ, ZIP) and safely extracts"""
    logger.info(f"Downloading manga from {url}")
    
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
        
        # Extract images safely, preventing subfolder name collisions
        with zipfile.ZipFile(temp_zip, 'r') as zip_ref:
            for idx, file_info in enumerate(zip_ref.infolist()):
                if not file_info.is_dir():
                    file_ext = Path(file_info.filename).suffix.lower()
                    if file_ext in ['.png', '.jpg', '.jpeg', '.webp', '.gif']:
                        # Prefix with index to ensure unique filenames
                        target_name = f"{idx:04d}_{Path(file_info.filename).name}"
                        target_path = images_dir / target_name
                        with zip_ref.open(file_info) as source, open(target_path, 'wb') as target:
                            shutil.copyfileobj(source, target)
        
        temp_zip.unlink()
        
    except Exception as e:
        logger.error(f"Download/extraction failed: {e}")
        raise
    
    image_files = []
    for ext in ['*.png', '*.jpg', '*.jpeg', '*.webp', '*.gif']:
        image_files.extend(images_dir.glob(ext))
        image_files.extend(images_dir.glob(ext.upper()))
    
    if not image_files:
        raise Exception("No image files found in the downloaded manga")
    
    return natsorted(image_files)

def standardize_image(image_path, target_size=(1920, 1080)):
    """Resize and pad image using PIL for blazing-fast FFmpeg concatenation."""
    try:
        img = Image.open(image_path).convert('RGB')
        img.thumbnail(target_size, Image.Resampling.LANCZOS)
        
        # Create black background and paste resized image in the center
        new_img = Image.new("RGB", target_size, (0, 0, 0))
        paste_pos = ((target_size[0] - img.width) // 2, (target_size[1] - img.height) // 2)
        new_img.paste(img, paste_pos)
        
        std_path = image_path.parent / f"std_{image_path.name}.jpg"
        new_img.save(std_path, format='JPEG', quality=95)
        return std_path
    except Exception as e:
        logger.error(f"Image standardization failed for {image_path}: {e}")
        return None

def get_audio_duration(audio_path):
    """Get duration of audio file in seconds using ffprobe"""
    try:
        cmd = [
            "ffprobe", "-v", "error", "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1", str(audio_path)
        ]
        result = subprocess.run(cmd, capture_output=True, text=True, check=True)
        return float(result.stdout.strip())
    except Exception as e:
        logger.error(f"Failed to get audio duration for {audio_path}: {e}")
        return 1.5  

def process_page(image_path, ocr_engine, tts_engine, output_dir):
    """Process a single manga page"""
    logger.info(f"Processing {image_path.name}")
    
    text = ocr_engine.get_text(str(image_path))
    if not text:
        logger.warning(f"No text found in {image_path.name}")
        text = " "  # Pass space to force silence generation
    
    audio_path = output_dir / f"{image_path.stem}.mp3"
    
    import asyncio
    success = asyncio.run(tts_engine.generate(text, str(audio_path)))
    
    if not success or not audio_path.exists():
        logger.warning(f"Failed to generate audio for {image_path.name}")
        return None
    
    # Standardize image upfront to bypass heavy FFmpeg filters later
    std_img_path = standardize_image(image_path)
    if not std_img_path:
        return None

    duration = get_audio_duration(audio_path)
    
    return {
        'image': std_img_path,
        'audio': audio_path,
        'duration': duration,
        'text': text[:100]
    }

def create_video_with_audio(page_data, output_path):
    """Create video via FFmpeg concat demuxer (extremely fast and memory efficient)."""
    logger.info("Multiplexing streams via FFmpeg Concat Demuxer...")
    temp_dir = Path(tempfile.mkdtemp())
    
    try:
        images_list = temp_dir / "images.txt"
        audio_list = temp_dir / "audio.txt"
        
        with open(images_list, 'w', encoding='utf-8') as img_f, open(audio_list, 'w', encoding='utf-8') as aud_f:
            for page in page_data:
                img_path = str(page['image'].absolute()).replace('\\', '/')
                aud_path = str(page['audio'].absolute()).replace('\\', '/')
                
                img_f.write(f"file '{img_path}'\n")
                img_f.write(f"duration {page['duration']}\n")
                aud_f.write(f"file '{aud_path}'\n")
                
            # FFmpeg quirk: The last image must be repeated without a duration
            last_img = str(page_data[-1]['image'].absolute()).replace('\\', '/')
            img_f.write(f"file '{last_img}'\n")

        cmd = [
            "ffmpeg", "-y",
            "-f", "concat", "-safe", "0", "-i", str(images_list),
            "-f", "concat", "-safe", "0", "-i", str(audio_list),
            "-c:v", "libx264", "-pix_fmt", "yuv420p",
            "-c:a", "aac", "-b:a", "192k",
            "-shortest", 
            str(output_path)
        ]
        
        subprocess.run(cmd, check=True, capture_output=True, text=True)
        logger.info(f"Video created successfully: {output_path}")
        
    except subprocess.CalledProcessError as e:
        logger.error(f"FFmpeg concatenation error: {e.stderr}")
        raise
    finally:
        shutil.rmtree(temp_dir, ignore_errors=True)

def estimate_total_duration(page_data):
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
    
    base_dir = Path("processing")
    images_dir = base_dir / "images"
    audio_dir = base_dir / "audio"
    output_dir = Path("output")
    
    base_dir.mkdir(exist_ok=True)
    images_dir.mkdir(exist_ok=True)
    audio_dir.mkdir(exist_ok=True)
    output_dir.mkdir(exist_ok=True)
    
    ocr_engine = TTSEngine = None
    
    try:
        ocr_engine = OCREngine(args.ocr)
        tts_engine = TTSEngine(args.tts)
        
        image_files = download_manga(args.url, base_dir)
        logger.info(f"Found {len(image_files)} pages")
        
        page_data = []
        for idx, img_path in enumerate(image_files, 1):
            logger.info(f"Processing page {idx}/{len(image_files)}")
            result = process_page(img_path, ocr_engine, tts_engine, audio_dir)
            if result:
                page_data.append(result)
        
        if not page_data:
            logger.error("No pages were successfully processed")
            sys.exit(1)
            
        duration_str = estimate_total_duration(page_data)
        output_video = output_dir / "manga_video.mp4"
        create_video_with_audio(page_data, output_video)
        
        file_size = output_video.stat().st_size / (1024 * 1024)
        logger.info(f"Success! Video saved to {output_video} ({file_size:.2f} MB, {duration_str})")
        
    except Exception as e:
        logger.error(f"Pipeline failed: {e}")
        sys.exit(1)
    finally:
        if ocr_engine: ocr_engine.cleanup()
        if tts_engine: tts_engine.cleanup()

if __name__ == "__main__":
    main()
                      
