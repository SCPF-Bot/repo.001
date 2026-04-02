Brutal Step‑by‑Step Guide to the Manga‑to‑Video Pipeline

This is not a fluffy tutorial. You will learn exactly how this repository works, how to make it run without crying, and how to fix it when it breaks.

1. Repository Philosophy – JIT, Lazy, Fail‑First

· Just‑In‑Time (JIT) installation – Nothing is installed globally except the bare minimum (base.txt). OCR and TTS engine dependencies are installed only when you select that engine.
· Lazy loading – Models (Manga‑OCR, PaddleOCR, XTTS, MeloTTS) are loaded the first time they are used, not when the pipeline starts.
· Fail‑first with fallbacks – If your primary OCR/TTS engine fails, the pipeline automatically tries Tesseract (OCR) or edge‑tts (TTS). If everything fails, it generates silence. It will never crash – it will produce a video with blank audio if absolutely necessary.

2. Brutal Prerequisites

For GitHub Actions (easiest path)

· A GitHub repository with Actions enabled.
· Secrets (if using paid/cloud engines):
  · GOOGLE_CREDENTIALS (JSON content) for Google Vision OCR.
  · ELEVENLABS_API_KEY
  · DEEPGRAM_KEY
  · FISH_KEY
· No secrets needed for free engines: tesseract (OCR) and edge_tts (TTS).

For local execution (if you hate yourself)

· Ubuntu 20.04+ (or WSL2 on Windows).
· Python 3.10.
· System dependencies (run scripts/system_deps.sh).
· FFmpeg ≥ 4.4 (the script checks this).
· Enough disk space for temp files (at least 2× the size of your CBZ).

3. Cloning & Local Setup (Optional)

If you want to test locally before trusting the cloud:

```bash
git clone https://github.com/yourname/manga-to-video.git
cd manga-to-video
python -m venv venv
source venv/bin/activate
pip install -r requirements/base.txt
bash scripts/system_deps.sh
```

Do not install OCR/TTS engines manually – the JIT installer will handle that when you run the pipeline.

4. GitHub Actions Workflow Configuration

The file .github/workflows/manga_pipeline.yml is already written. You only need to:

1. Push the repository to GitHub.
2. Go to Actions → Manga To Video (Optimized) → Run workflow.
3. Fill in:
   · url – direct download link to a .cbz or .zip file. Must be publicly accessible.
   · ocr_engine – choose one.
   · tts_engine – choose one.

The workflow will:

· Check out the code.
· Install system deps (FFmpeg, Tesseract, etc.).
· Install base Python deps.
· JIT‑install only the dependencies for your chosen engines.
· Run the pipeline.
· Create a GitHub Release with the output video as an asset.

5. Running the Pipeline – Step by Brutal Step

5.1 Input URL requirements

· Must be a direct download (no login, no redirects). Example: https://example.com/manga.cbz.
· The archive must contain images (PNG, JPG, JPEG, WEBP, BMP) – any folder structure is fine.

5.2 What happens inside core_pipeline.py

```
1. Download the archive asynchronously (aiohttp + aiofiles).
2. Extract to a temporary directory (thread‑pool to avoid blocking).
3. For each image (natsorted):
   a. Resize + pad to 1920x1080 (letterbox).
   b. OCR → text (with fallback to Tesseract).
   c. TTS → MP3 (with fallback to edge‑tts → silence).
   d. Measure audio duration.
4. For each segment: use FFmpeg to create a video where the static image
   plays for exactly the audio duration (‑shortest).
5. Concatenate all segment videos losslessly (concat demuxer).
6. Cleanup temp directories.
```

5.3 Output location

· In GitHub Actions: the video is uploaded to a Release with tag build-<run_id>.
· Locally: output/manga_video_<PID>.mp4.

6. Understanding the Output – What You Get

· A single MP4 file, H.264 video + AAC audio.
· Resolution: 1920x1080 (letterboxed original pages).
· Each page appears as a static image for the duration of its TTS audio.
· If a page has no text, you get 1.5 seconds of silence.
· If TTS fails completely, you get silence on that page.

Brutal truth: The pipeline does not align text bubbles or detect reading order. It treats each page as one block of text. For manga with complex layouts, you may get nonsense.

7. Customising OCR / TTS Engines

Adding a new OCR engine

1. Add a method _ocr_yourengine(self, image_path) in ocr_engines.py.
2. Add the engine name to the if/elif chain in get_text().
3. Create a requirements file: requirements/ocr_yourengine.txt.
4. Add mapping in install_engine_deps.py under OCR_CONFIG.

Changing fallback order

Modify the list engines_to_try in ocr_engines.get_text() or tts_engines.generate().

Disabling fallbacks (not recommended)

Replace engines_to_try = [self.engine_type, "tesseract"] with [self.engine_type]. If that engine fails, the pipeline will raise an exception.

8. Performance Tuning – For Speed Demons

FFmpeg presets

In _render_video(), the line:

```python
"-c:v", "libx264", "-preset", "fast", "-crf", "22",
```

· preset can be ultrafast, superfast, veryfast, faster, fast, medium, slow, slower, veryslow. Faster = larger file size.
· crf (18–28) – lower = better quality, larger file. 22 is a good balance.

Concurrency

The pipeline processes pages sequentially to avoid memory blow‑up. If you have a 64‑core monster, you can parallelise OCR/TTS, but you will need to manage rate limits (especially for cloud APIs). Not implemented – feel free to add asyncio.gather with semaphores.

Reducing memory for large manga

Set environment variable PYTORCH_MPS_HIGH_WATERMARK_RATIO=0.0 before running to avoid torch caching. The cleanup() method already calls torch.cuda.empty_cache() and gc.collect().

9. Troubleshooting – The Brutal Edition

❌ “All OCR engines failed. Last error: ...”

· Tesseract not installed – run system_deps.sh.
· Google Vision – missing GOOGLE_APPLICATION_CREDENTIALS or invalid JSON.
· Manga‑OCR / PaddleOCR – JIT install failed. Check network, or install manually: pip install -r requirements/ocr_xxx.txt.

❌ “TTS engine X failed, falling back to edge_tts”

· ElevenLabs / Deepgram / Fish – missing API key or quota exceeded. The key must be set as an environment variable (secrets in GitHub Actions).
· MeloTTS / XTTS – the Git install may fail on resource‑constrained runners. These models are huge (>2GB). Use edge_tts instead.

❌ FFmpeg errors in video rendering

· ffmpeg: command not found – run system_deps.sh.
· Invalid duration – audio file is corrupted. Delete audio_dir and re‑run.
· Concat demuxer error – ensure all segment videos exist. The pipeline deletes temp files only on success. Check self.temp_dir manually.

❌ GitHub Action fails with “pip install -r ... not found”

· The requirement file paths are absolute, but if you changed the repository structure, update REQUIREMENTS_DIR in install_engine_deps.py.

❌ Output video has black frames or wrong length

· This happens if audio duration detection fails (get_audio_duration returns 0). Run ffprobe manually on the MP3 file. Ensure ffprobe is installed.

10. Brutal Truths & Limitations – Read Before Complaining

1. The pipeline does not understand manga layout – It OCRs the whole page. For speech bubbles, you need a bubble detector + ordering. That’s a different project.
2. No punctuation or natural pauses – The TTS engines receive raw OCR text. You may want to insert periods manually or use a text‑cleaning step.
3. Edge‑TTS is rate‑limited – For long manga (>50 pages), you may hit Azure’s throttle. The retry logic (4 attempts with backoff) helps, but consider using ElevenLabs with paid tier.
4. Memory usage – Loading PaddleOCR or XTTS can consume >2GB RAM. GitHub’s ubuntu-latest runners have 7GB, so it’s usually fine. If you get OOM, switch to Tesseract + edge‑tts.
5. The “zero fail” claim – The pipeline never crashes, but it can produce a video with silence on every page if all TTS engines fail. That’s still a success (no exception). Check your logs.

11. Final Checklist Before Your First Run

· Repository pushed to GitHub.
· Workflow file is in .github/workflows/.
· If using paid engines, secrets are added in repo Settings → Secrets and variables → Actions.
· The CBZ URL is a direct download (test with curl -L <url>).
· You have selected a sensible engine pair (e.g., tesseract + edge_tts) for the first run.

Now go break things. When it works, you have a fully automated manga‑to‑video factory. When it doesn’t, you have this guide.
