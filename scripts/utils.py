import os
import shutil
import asyncio
import zipfile
import logging
import subprocess
from pathlib import Path
from typing import List, Tuple, Set

import aiohttp
import aiofiles
from PIL import Image
from natsort import natsorted

logger = logging.getLogger(__name__)

# Use a single session for all downloads if possible, 
# or use a helper that ensures proper closure.
async def download_file(url: str, dest: Path) -> None:
    """Download a file asynchronously with streaming."""
    logger.info(f"Downloading {url}")
    dest.parent.mkdir(parents=True, exist_ok=True)
    
    # timeout prevents the action from hanging forever
    timeout = aiohttp.ClientTimeout(total=600) 
    async with aiohttp.ClientSession(timeout=timeout) as session:
        async with session.get(url, allow_redirects=True) as resp:
            resp.raise_for_status()
            async with aiofiles.open(dest, 'wb') as f:
                async for chunk in resp.content.iter_chunked(16384): # Larger chunks for speed
                    await f.write(chunk)

def _sync_extract(archive_path: Path, extract_to: Path) -> List[Path]:
    """Helper for extraction with hidden file filtering."""
    image_extensions: Set[str] = {'.png', '.jpg', '.jpeg', '.webp', '.bmp'}
    extracted_images = []
    
    with zipfile.ZipFile(archive_path, 'r') as zip_ref:
        for member in zip_ref.infolist():
            # Skip metadata and hidden files
            if member.is_dir() or "__MACOSX" in member.filename or member.filename.startswith('.'):
                continue
            
            if Path(member.filename).suffix.lower() in image_extensions:
                zip_ref.extract(member, extract_to)
                extracted_images.append(extract_to / member.filename)
                
    return natsorted(extracted_images)

async def extract_archive(archive_path: Path, extract_to: Path) -> List[Path]:
    """Extract archive asynchronously and return clean, sorted image paths."""
    extract_to.mkdir(parents=True, exist_ok=True)
    loop = asyncio.get_running_loop()
    # Offload the heavy Zip CPU work to a thread
    return await loop.run_in_executor(None, _sync_extract, archive_path, extract_to)

def resize_and_pad(image_path: Path, output_path: Path, target_size: Tuple[int, int] = (1920, 1080)) -> None:
    """Resize image to fit target size with high-quality letterboxing."""
    with Image.open(image_path) as img:
        img = img.convert('RGB')
        img.thumbnail(target_size, Image.Resampling.LANCZOS) # thumbnail maintains aspect ratio
        
        # Create canvas
        new_img = Image.new('RGB', target_size, (0, 0, 0))
        # Center the image
        offset = ((target_size[0] - img.width) // 2, (target_size[1] - img.height) // 2)
        new_img.paste(img, offset)
        
        # Optimize for web (progressive saves a bit of space)
        new_img.save(output_path, 'JPEG', quality=85, optimize=True, progressive=True)

async def get_audio_duration(audio_path: Path) -> float:
    """Return duration in seconds using an async subprocess call."""
    cmd = [
        'ffprobe', '-v', 'error', '-show_entries', 'format=duration',
        '-of', 'default=noprint_wrappers=1:nokey=1', str(audio_path)
    ]
    # Async subprocess prevents the event loop from blocking
    proc = await asyncio.create_subprocess_exec(
        *cmd, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE
    )
    stdout, _ = await proc.communicate()
    try:
        return float(stdout.decode().strip())
    except (ValueError, TypeError):
        logger.warning(f"Could not determine duration for {audio_path}, defaulting to 1.0s")
        return 1.0

def cleanup_temp_dirs(*paths: Path):
    """Safely remove temporary directories."""
    for p in paths:
        if p.exists():
            shutil.rmtree(p, ignore_errors=True)
