import os
import time
import asyncio
import requests
import subprocess
from pathlib import Path
import logging
from concurrent.futures import ThreadPoolExecutor

logger = logging.getLogger(__name__)

class TTSEngine:
    def __init__(self, engine_type: str = "edge_tts"):
        self.engine_type = engine_type
        self._models = {}
        self._executor = ThreadPoolExecutor(max_workers=1)

    async def generate(self, text: str, output_path: str) -> bool:
        if not text or len(text.strip()) < 2:
            return self._generate_silence(output_path)

        text = self._clean_text(text)[:5000]  # API limits
        engines_to_try = [self.engine_type, "edge_tts"]

        for engine in engines_to_try:
            try:
                success = await self._call_engine(engine, text, output_path)
                if success and Path(output_path).exists() and Path(output_path).stat().st_size > 100:
                    return True
            except Exception as e:
                logger.error(f"TTS engine {engine} failed: {e}")
                continue
        # Ultimate fallback: generate silence
        return self._generate_silence(output_path)

    async def _call_engine(self, engine: str, text: str, output_path: str) -> bool:
        loop = asyncio.get_event_loop()
        if engine == "elevenlabs":
            return await loop.run_in_executor(self._executor, self._tts_elevenlabs, text, output_path)
        elif engine == "deepgram_aura":
            return await loop.run_in_executor(self._executor, self._tts_deepgram, text, output_path)
        elif engine == "fish_speech":
            return await loop.run_in_executor(self._executor, self._tts_fish_api, text, output_path)
        elif engine == "melo_tts":
            return await loop.run_in_executor(self._executor, self._tts_melo, text, output_path)
        elif engine == "xtts_v2":
            return await loop.run_in_executor(self._executor, self._tts_xtts, text, output_path)
        else:
            return await self._tts_edge(text, output_path)

    # ------------------------------------------------------------------
    # Engine implementations with exponential backoff
    # ------------------------------------------------------------------
    async def _tts_edge(self, text: str, output_path: str) -> bool:
        from edge_tts import Communicate
        for attempt in range(4):
            try:
                comm = Communicate(text, "en-US-JennyNeural")
                await comm.save(output_path)
                if Path(output_path).stat().st_size > 0:
                    return True
                raise RuntimeError("Empty file")
            except Exception:
                if attempt == 3:
                    raise
                await asyncio.sleep(2 ** attempt)
        return False

    def _tts_elevenlabs(self, text: str, output_path: str) -> bool:
        key = os.getenv("ELEVENLABS_API_KEY")
        if not key:
            raise Exception("Missing ELEVENLABS_API_KEY")
        url = "https://api.elevenlabs.io/v1/text-to-speech/Xb7hH8MSUJpSbSDYk0k2"
        headers = {"xi-api-key": key, "Content-Type": "application/json"}
        data = {"text": text, "model_id": "eleven_monolingual_v1", "voice_settings": {"stability": 0.5, "similarity_boost": 0.75}}
        for attempt in range(4):
            try:
                resp = requests.post(url, json=data, headers=headers, timeout=30)
                if resp.status_code == 200:
                    with open(output_path, "wb") as f:
                        f.write(resp.content)
                    return True
                if resp.status_code == 429:
                    time.sleep(2 ** attempt)
                    continue
                resp.raise_for_status()
            except Exception:
                if attempt == 3:
                    raise
                time.sleep(2 ** attempt)
        return False

    def _tts_deepgram(self, text: str, output_path: str) -> bool:
        key = os.getenv("DEEPGRAM_KEY")
        if not key:
            raise Exception("Missing DEEPGRAM_KEY")
        url = "https://api.deepgram.com/v1/speak?model=aura-helios-en"
        headers = {"Authorization": f"Token {key}", "Content-Type": "application/json"}
        for attempt in range(4):
            try:
                resp = requests.post(url, json={"text": text}, headers=headers, timeout=30)
                if resp.status_code == 200:
                    with open(output_path, "wb") as f:
                        f.write(resp.content)
                    return True
                if resp.status_code == 429:
                    time.sleep(2 ** attempt)
                    continue
                resp.raise_for_status()
            except Exception:
                if attempt == 3:
                    raise
                time.sleep(2 ** attempt)
        return False

    def _tts_fish_api(self, text: str, output_path: str) -> bool:
        key = os.getenv("FISH_KEY")
        if not key:
            raise Exception("Missing FISH_KEY")
        url = "https://api.fish.audio/v1/tts"
        headers = {"Authorization": f"Bearer {key}", "Content-Type": "application/json"}
        data = {"text": text[:1000], "format": "mp3", "voice": "taylor"}
        for attempt in range(4):
            try:
                resp = requests.post(url, json=data, headers=headers, timeout=30)
                if resp.status_code == 200:
                    with open(output_path, "wb") as f:
                        f.write(resp.content)
                    return True
                if resp.status_code == 429:
                    time.sleep(2 ** attempt)
                    continue
                resp.raise_for_status()
            except Exception:
                if attempt == 3:
                    raise
                time.sleep(2 ** attempt)
        return False

    def _tts_melo(self, text: str, output_path: str) -> bool:
        from melotts.api import TTS
        model_key = "melo"
        if model_key not in self._models:
            self._models[model_key] = TTS(language='EN', device='cpu')
        model = self._models[model_key]
        model.tts_to_file(text[:500], model.hps.data.spk2id['EN-Default'], output_path, speed=0.9)
        return True

    def _tts_xtts(self, text: str, output_path: str) -> bool:
        from TTS.api import TTS
        model_key = "xtts"
        if model_key not in self._models:
            self._models[model_key] = TTS("tts_models/multilingual/multi-dataset/xtts_v2", gpu=False)
        self._models[model_key].tts_to_file(text=text[:500], file_path=output_path, language="en")
        return True

    # ------------------------------------------------------------------
    # Helpers
    # ------------------------------------------------------------------
    def _clean_text(self, text: str) -> str:
        text = ' '.join(text.split())
        replacements = {'…': '...', '—': '-', '–': '-', '"': "'", '\u201c': '"', '\u201d': '"', '\u2018': "'", '\u2019': "'"}
        for old, new in replacements.items():
            text = text.replace(old, new)
        return ''.join(ch for ch in text if ch.isprintable() or ch == '\n').strip()

    def _generate_silence(self, output_path: str, duration_seconds: float = 1.5) -> bool:
        try:
            subprocess.run([
                "ffmpeg", "-f", "lavfi", "-i", f"anullsrc=channel_layout=stereo:sample_rate=44100",
                "-t", str(duration_seconds), "-c:a", "libmp3lame", "-q:a", "9", output_path, "-y"
            ], check=True, capture_output=True)
            return True
        except Exception:
            Path(output_path).touch()
            return False

    def cleanup(self):
        self._models.clear()
        import gc
        gc.collect()
        try:
            import torch
            if torch.cuda.is_available():
                torch.cuda.empty_cache()
        except ImportError:
            pass
