#!/usr/bin/env python3
import os, sys, argparse, asyncio, logging, tempfile, subprocess
from pathlib import Path
from typing import List, Tuple

SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

from ocr_engines import OCREngine
from tts_engines import TTSEngine
from utils import download_file, extract_archive, resize_and_pad, get_audio_duration, cleanup_temp_dirs

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger("MangaPipeline")

class MangaToVideoPipeline:
    def __init__(self, url: str, ocr_engine: str, tts_engine: str):
        self.url = url
        self.ocr = OCREngine(ocr_engine)
        self.tts = TTSEngine(tts_engine)
        self.repo_root = SCRIPT_DIR.parent
        self.output_dir = self.repo_root / "output"
        self.output_dir.mkdir(exist_ok=True)
        self.output_video = self.output_dir / "final_manga_video.mp4"
        self.temp_dir = Path(tempfile.mkdtemp(prefix="manga_job_"))
        self.dirs = {k: self.temp_dir / k for k in ["images", "processed", "audio"]}
        for d in self.dirs.values(): d.mkdir(parents=True, exist_ok=True)

    async def process_page(self, idx: int, orig_img: Path) -> Tuple[Path, Path, float]:
        proc_img = self.dirs["processed"] / f"page_{idx:04d}.jpg"
        await asyncio.to_thread(resize_and_pad, orig_img, proc_img, (1080, 1920))
        text = await asyncio.to_thread(self.ocr.get_text, str(proc_img))
        audio_file = self.dirs["audio"] / f"audio_{idx:04d}.mp3"
        await self.tts.generate(text or "...", str(audio_file))
        duration = await get_audio_duration(audio_file)
        return proc_img, audio_file, duration

    async def run(self) -> Path:
        try:
            archive_path = self.temp_dir / "manga.archive"
            await download_file(self.url, archive_path)
            image_paths = await extract_archive(archive_path, self.dirs["images"])
            sem = asyncio.Semaphore(4)
            async def task(i, p):
                async with sem: return await self.process_page(i, p)
            segments = await asyncio.gather(*(task(i, p) for i, p in enumerate(image_paths)))
            return await self._render_final_video(segments)
        finally:
            await self.tts.cleanup()
            cleanup_temp_dirs(self.temp_dir)

    async def _render_final_video(self, segments: List[Tuple[Path, Path, float]]) -> Path:
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

async def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", required=True); parser.add_argument("--ocr", default="paddle_ocr"); parser.add_argument("--tts", default="edge_tts")
    args = parser.parse_args()
    try:
        p = MangaToVideoPipeline(args.url, args.ocr, args.tts)
        print(f"SUCCESS: {await p.run()}")
    except Exception as e:
        logger.error(f"Failed: {e}"); sys.exit(1)

if __name__ == "__main__": asyncio.run(main())
