# 🌀 Manga-to-Video

***Simple, modular, and fail-safe automation pipeline for converting Manga/Manhwa into high-quality narrated videos. It utilizes a Strategy Pattern architecture, allowing you to swap between 5+ OCR engines and 10+ TTS engines (including Fish Speech, XTTS, and Deepgram) via a simple checkbox interface in GitHub Actions.***

---

## 🛠 1. Capabilities & Supported Engines
This pipeline intelligently handles the heavy lifting of image processing, text extraction, and neural speech synthesis.
### 🔍 OCR (Optical Character Recognition)
 * Google Vision API: Best-in-class accuracy for all languages.
 * Manga-OCR: Specialized Japanese manga text recognition.
 * PaddleOCR: High-speed multilingual detection.
 * Comic-Text-Detector: Optimized for finding text in speech bubbles.
 * Tesseract OCR: The ultimate local fallback.
### 🎙️ TTS (Text-to-Speech)
 * Premium APIs: ElevenLabs, Deepgram Aura 2, Fish Speech V1.5.
 * Local Neural: XTTS-v2 (Coqui), ChatTTS, MeloTTS (MyShell).
 * Free/Web: Microsoft Edge-TTS (Zero-key requirement).

## 🚀 2. Quick Start: The "Zero-Fail" Setup
### Step A: Infrastructure Preparation
 * Fork this repository to your own GitHub account.
 * Enable Actions: Navigate to the Actions tab and click "I understand my workflows, let them run."
 * Set Permissions: Go to Settings > Actions > General. Scroll to Workflow permissions and select Read and write permissions. This is mandatory for the pipeline to create Video Releases.
### Step B: The Secret Vault
Navigate to Settings > Secrets and variables > Actions and define the following secrets to enable your chosen engines:
| Secret Name | Purpose |
|---|---|
| EL_KEY | ElevenLabs API Key |
| GOOGLE_JSON | The full text of your Google Cloud Service Account JSON |
| DEEPGRAM_KEY | Deepgram API Token (Aura 2) |
| FISH_KEY | Fish.audio API Key |

## 🏗 3. Repository Architecture

```
├── .github/workflows/
│   └── manga_pipeline.yml     # The Orchestrator (UI Checkboxes)
├── engines/
│   ├── ocr_engines.py         # Modular OCR Logic (Manga-OCR, Paddle, etc.)
│   └── tts_engines.py         # Modular TTS Logic (XTTS, ChatTTS, Fish, etc.)
├── scripts/
│   ├── core_pipeline.py       # The "Brain": Handles sync, timing, and FFmpeg
│   └── system_deps.sh         # Global Binary Installer
├── requirements.txt           # Unified Python Dependencies
└── manga.url.txt              # Optional batch processing source
```

## 🎞 4. Execution via GitHub Actions
 * Navigate to the Actions tab in your repository.
 * Select Manga-To-Video-AI-Ultimate from the sidebar.
 * Click the Run workflow dropdown.
 * Fill the Inputs:
   * URL: Provide a direct link to a .zip or .cbz file containing your manga pages.
   * OCR Engine: Select your preferred text extraction method.
   * TTS Engine: Select your preferred voice synthesis method.
 * Click Run workflow.
Once finished, the video will appear in the Releases section of your repository.

## 🧠 5. The "Zero-Fail" Engineering Logic
 * Atomic Sync: We do not guess page timings. The pipeline uses ffprobe to measure the exact duration of the generated audio for every page, ensuring the image and voice never go out of sync.
 * Lazy Loading: Heavy models (like Manga-OCR or XTTS) are only loaded into RAM if you select them. This prevents "Out of Memory" crashes on GitHub's 7GB runners.
 * Silent Fallback: If a page contains no text (art-only pages), the pipeline automatically generates 1.5 seconds of silence to maintain the video's rhythmic integrity.
 * Path Sanitization: The code handles filenames with spaces, Japanese characters, and single quotes, which usually break standard FFmpeg scripts.

## 📈 6. Local Development
To run this on your local machine:
 * Install Binaries:
   # Ubuntu/Debian
sudo apt install ffmpeg tesseract-ocr

 * Install Python Libs:
   pip install -r requirements.txt

 * Execute:
   python scripts/core_pipeline.py --url "YOUR_URL" --ocr "manga_ocr" --tts "edge_tts"

## 🦋 7. Repository Diagram

![image.diagram](https://github.com/user-attachments/assets/b9bc69d3-d151-4bc6-aa86-7fc856c7bfa0)

# 🕊 Credits
 * Fish Audio & Coqui: For the state-of-the-art TTS models.
 * PaddlePaddle: For the robust OCR framework.
 * FFmpeg: The engine powering the video synthesis.

# Disclaimer
***This tool is for educational and personal use. Ensure you have the rights to the manga content you are processing...***
