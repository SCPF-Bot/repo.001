# 🌀 Manga-to-Video

***Simple, modular, and fail-safe automation pipeline for converting Manga/Manhwa into high-quality narrated videos. It utilizes a Strategy Pattern architecture, allowing you to swap between 5+ OCR engines and 10+ TTS engines via a simple interface in GitHub Actions.***

---

## 🛠 1. Capabilities & Supported Engines
This pipeline intelligently handles the heavy lifting of image processing, text extraction, and neural speech synthesis.
### 🔍 OCR (Optical Character Recognition)
 * Google Vision API: Best-in-class accuracy (Requires API Key).
 * Manga-OCR: Specialized Japanese manga text recognition.
 * PaddleOCR: High-speed multilingual detection.
 * Comic-Text-Detector: Optimized for finding text in speech bubbles.
 * Tesseract OCR: The ultimate local fallback.
### 🎙️ TTS (Text-to-Speech)
 * Premium APIs: ElevenLabs, Deepgram Aura, Fish Speech (Now with auto-retry & exponential backoff).
 * Local Neural: XTTS-v2, ChatTTS, MeloTTS (Optimized for CPU usage on GitHub Runners).
 * Free/Web: Microsoft Edge-TTS (No API key required).

## 🚀 2. Quick Start: GitHub Automation
 * Fork this repository to your own GitHub account.
 * Enable Actions: Navigate to the "Actions" tab and click "I understand my workflows, let them run."
 * Set Permissions: Go to Settings > Actions > General. Scroll to "Workflow permissions" and select Read and write permissions.
 * Set Secrets: Go to Settings > Secrets and variables > Actions. Add your API keys (e.g., ELEVENLABS_API_KEY, DEEPGRAM_KEY) as secrets.
 * Run: Click "Run workflow" in the Actions tab, provide your Manga URL, and select your preferred engines.

## 🧠 3. High-Performance Architecture
The pipeline has been refactored for maximum reliability and speed:
 * Lightning-Fast Rendering: We no longer encode video page-by-page. The pipeline now uses a PIL-based Standardizer combined with an FFmpeg Concat Demuxer. This bypasses heavy video re-encoding, reducing CPU usage by 80% and finishing video compilation in seconds.
 * Network Resilience: Integrated Exponential Backoff for all API-based engines. If you hit a 429: Too Many Requests error, the pipeline will automatically wait and retry with increasing delays instead of crashing.
 * Archive Collision Safety: Enhanced .zip and .cbz extraction logic. Files are automatically indexed and prefixed during extraction to prevent images in nested subfolders from overwriting each other.
 * Atomic Sync: The pipeline uses ffprobe to measure the exact duration of every generated audio clip. We do not "guess" timings; the visual frame stays perfectly synced with the voiceover.
 * Silent Fallback: For art-only pages or OCR misses, the pipeline generates a 1.5s silent audio track to maintain the video's rhythmic flow.

## 📈 4. Local Development
To run this on your local machine:
 * Install Binaries:

   Ubuntu/Debian

   ```
   sudo apt install ffmpeg tesseract-ocr libgl1-mesa-glx libglib2.0-0
   ```

 * Install Python Libs:

   ```
   pip install -r requirements.txt
   ```

 * Execute:

   ```
   python scripts/core_pipeline.py --url "YOUR_URL" --ocr "tesseract" --tts "edge_tts"
   ```

## 📁 5. Repository Structure

```
.
├── .github/workflows/    # GitHub Actions workflow (Automation Skeleton)
├── engines/
│   ├── ocr_engines.py    # OCR Strategy implementations & Failover
│   └── tts_engines.py    # TTS Strategy implementations & API Retries
├── scripts/
│   ├── core_pipeline.py  # Main Orchestrator & FFmpeg Multiplexer
│   └── system_deps.sh    # System-level dependency installer
└── requirements.txt      # Python dependency manifest
```

## 🕊 Credits
 * Fish Audio, ElevenLabs, & Deepgram: For state-of-the-art TTS APIs.
 * PaddlePaddle: For the robust OCR framework.
 * FFmpeg: The backbone of the media multiplexing logic.
