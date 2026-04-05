import os, sys, argparse, asyncio, logging, tempfile, subprocess
from pathlib import Path
from typing import List, Tuple

SCRIPT_DIR = Path(__file__).resolve().parent
# FIX: Append to path instead of inserting at 0 to avoid shadowing libraries
if str(SCRIPT_DIR) not in sys.path:
    sys.path.append(str(SCRIPT_DIR))

from ocr_engines import OCREngine
from tts_engines import TTSEngine
from utils import download_file, extract_archive, resize_and_pad, get_audio_duration, cleanup_temp_dirs

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger("MangaPipeline")

class MangaToVideoPipeline:
    def __init__(self, url: str, ocr_engine: str, tts_engine: str):
        self.url = url
        api_key = os.getenv("GOOGLE_API_KEY")
        self.ocr = OCREngine(ocr_engine, api_key=api_key)
        self.tts = TTSEngine(tts_engine)
        self.repo_root = SCRIPT_DIR.parent
        self.output_dir = self.repo_root / "output"
        self.output_dir.mkdir(exist_ok=True)
        self.output_video = self.output_dir / "final_manga_video.mp4"
        self.temp_dir = Path(tempfile.mkdtemp(prefix="manga_job_"))
        self.dirs = {k: self.temp_dir / k for k in ["images", "processed", "audio"]}
        for d in self.dirs.values(): d.mkdir(parents=True)

    async def run(self):
        try:
            print(f"ACTUAL_OCR={self.ocr.primary_engine}")
            print(f"ACTUAL_TTS={self.tts.engine_type}")
            
            archive_path = self.temp_dir / "manga.zip"
            await download_file(self.url, archive_path)
            image_paths = await extract_archive(archive_path, self.dirs["images"])
            
            segments = []
            for i, img_path in enumerate(image_paths):
                proc_path = self.dirs["processed"] / f"page_{i:03d}.jpg"
                resize_and_pad(img_path, proc_path)
                
                text = self.ocr.get_text(str(img_path))
                audio_path = self.dirs["audio"] / f"page_{i:03d}.mp3"
                await self.tts.generate(text, str(audio_path))
                
                duration = await get_audio_duration(audio_path)
                segments.append((proc_path, audio_path, duration))
            
            return await self._render_video(segments)
        finally:
            cleanup_temp_dirs(self.temp_dir)

    async def _render_video(self, segments):
        meta, audio_list = self.temp_dir / "meta.txt", self.temp_dir / "audio_list.txt"
        with open(meta, "w") as f1, open(audio_list, "w") as f2:
            for i, a, d in segments:
                f1.write(f"file '{i.absolute()}'\nduration {d}\n")
                f2.write(f"file '{a.absolute()}'\n")
            f1.write(f"file '{segments[-1][0].absolute()}'\n")

        final_audio = self.temp_dir / "final_audio.mp3"
        subprocess.run(["ffmpeg", "-y", "-f", "concat", "-safe", "0", "-i", str(audio_list), "-c", "copy", str(final_audio)], check=True)
        
        cmd = [
            "ffmpeg", "-y", "-f", "concat", "-safe", "0", "-i", str(meta), "-i", str(final_audio),
            "-vf", "scale=1080:1920:force_original_aspect_ratio=decrease,pad=1080:1920:(ow-iw)/2:(oh-ih)/2",
            "-c:v", "libx264", "-pix_fmt", "yuv420p", "-preset", "veryfast", "-crf", "23", "-c:a", "aac", "-shortest", str(self.output_video.absolute())
        ]
        await asyncio.to_thread(subprocess.run, cmd, check=True)
        return self.output_video

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", required=True)
    parser.add_argument("--ocr", default="tesseract")
    parser.add_argument("--tts", default="edge_tts")
    args = parser.parse_args()
    
    pipeline = MangaToVideoPipeline(args.url, args.ocr, args.tts)
    asyncio.run(pipeline.run())
