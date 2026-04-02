import os
import time
import asyncio
import requests
import torch
import wave
import numpy as np
import subprocess
from pathlib import Path
import logging
from concurrent.futures import ThreadPoolExecutor

logger = logging.getLogger(__name__)

class TTSEngine:
    """TTS Engine with comprehensive failover and rate-limit protection mechanisms."""
    
    def __init__(self, engine_type="edge_tts"):
        self.engine_type = engine_type
        self.models = {}
        logger.info(f"Initialized TTS Engine: {self.engine_type}")

    async def generate(self, text, output_path):
        if not text or len(text.strip()) < 2:
            return self._generate_silence(output_path)

        text = self._clean_text(text)[:5000]
        engines_to_try = [self.engine_type, "edge_tts"]
        
        loop = asyncio.get_event_loop()
        executor = ThreadPoolExecutor(max_workers=1)
        
        for engine in engines_to_try:
            try:
                if engine == "elevenlabs":
                    success = await loop.run_in_executor(executor, self._tts_elevenlabs, text, output_path)
                elif engine == "deepgram_aura":
                    success = await loop.run_in_executor(executor, self._tts_deepgram, text, output_path)
                elif engine == "fish_speech":
                    success = await loop.run_in_executor(executor, self._tts_fish_api, text, output_path)
                elif engine == "melo_tts":
                    success = await loop.run_in_executor(executor, self._tts_melo, text, output_path)
                elif engine == "chat_tts":
                    success = await loop.run_in_executor(executor, self._tts_chat, text, output_path)
                elif engine == "xtts_v2":
                    success = await loop.run_in_executor(executor, self._tts_xtts, text, output_path)
                else:
                    success = await self._tts_edge(text, output_path)
                
                if success and Path(output_path).exists() and Path(output_path).stat().st_size > 0:
                    executor.shutdown(wait=False)
                    return True
                    
            except Exception as e:
                logger.error(f"TTS engine {engine} failed: {e}")
                continue
        
        executor.shutdown(wait=False)
        return self._generate_silence(output_path)

    def _clean_text(self, text):
        text = ' '.join(text.split())
        replacements = {'…': '...', '—': '-', '–': '-', '"': "'", '\u201c': '"', '\u201d': '"', '\u2018': "'", '\u2019': "'"}
        for old, new in replacements.items(): text = text.replace(old, new)
        return ''.join(char for char in text if char.isprintable() or char == '\n').strip()

    async def _tts_edge(self, text, output_path):
        from edge_tts import Communicate
        for attempt in range(3):
            try:
                communicate = Communicate(text, "en-US-JennyNeural")
                await communicate.save(output_path)
                if Path(output_path).exists() and Path(output_path).stat().st_size > 0: return True
                raise Exception("Produced empty file")
            except Exception as e:
                if attempt == 2: raise
                await asyncio.sleep(2 ** attempt)

    def _tts_elevenlabs(self, text, output_path):
        key = os.getenv("ELEVENLABS_API_KEY")
        if not key: raise Exception("ELEVENLABS_API_KEY not set")
        
        url = "https://api.elevenlabs.io/v1/text-to-speech/Xb7hH8MSUJpSbSDYk0k2"
        headers = {"xi-api-key": key, "Content-Type": "application/json"}
        data = {"text": text, "model_id": "eleven_monolingual_v1", "voice_settings": {"stability": 0.5, "similarity_boost": 0.75}}
        
        for attempt in range(4):
            try:
                res = requests.post(url, json=data, headers=headers, timeout=30)
                if res.status_code == 200:
                    with open(output_path, "wb") as f: f.write(res.content)
                    return True
                elif res.status_code == 429: # Rate limit hit
                    time.sleep(2 ** attempt)
                    continue
                raise Exception(f"HTTP {res.status_code}: {res.text}")
            except requests.RequestException as e:
                if attempt == 3: raise
                time.sleep(2 ** attempt)

    def _tts_deepgram(self, text, output_path):
        key = os.getenv("DEEPGRAM_KEY")
        if not key: raise Exception("DEEPGRAM_KEY not set")
        
        url = "https://api.deepgram.com/v1/speak?model=aura-helios-en"
        headers = {"Authorization": f"Token {key}", "Content-Type": "application/json"}
        
        for attempt in range(4):
            try:
                res = requests.post(url, json={"text": text}, headers=headers, timeout=30)
                if res.status_code == 200:
                    with open(output_path, "wb") as f: f.write(res.content)
                    return True
                elif res.status_code == 429:
                    time.sleep(2 ** attempt)
                    continue
                raise Exception(f"HTTP {res.status_code}: {res.text}")
            except requests.RequestException:
                if attempt == 3: raise
                time.sleep(2 ** attempt)

    def _tts_fish_api(self, text, output_path):
        key = os.getenv("FISH_KEY")
        if not key: raise Exception("FISH_KEY not set")
        
        url = "https://api.fish.audio/v1/tts"
        headers = {"Authorization": f"Bearer {key}", "Content-Type": "application/json"}
        data = {"text": text[:1000], "format": "mp3", "voice": "taylor"}
        
        for attempt in range(4):
            try:
                res = requests.post(url, json=data, headers=headers, timeout=30)
                if res.status_code == 200:
                    with open(output_path, "wb") as f: f.write(res.content)
                    return True
                elif res.status_code == 429:
                    time.sleep(2 ** attempt)
                    continue
                raise Exception(f"HTTP {res.status_code}: {res.text}")
            except requests.RequestException:
                if attempt == 3: raise
                time.sleep(2 ** attempt)

    def _tts_melo(self, text, output_path):
        from melotts.api import TTS
        model_key = "melo"
        if model_key not in self.models: self.models[model_key] = TTS(language='EN', device='cpu')
        model = self.models[model_key]
        model.tts_to_file(text[:500], model.hps.data.spk2id['EN-Default'], output_path, speed=0.9)
        return True

    def _tts_chat(self, text, output_path):
        import ChatTTS
        import scipy.io.wavfile as wavfile
        model_key = "chat"
        if model_key not in self.models:
            self.models[model_key] = ChatTTS.Chat()
            self.models[model_key].load_models(device='cpu')
        
        wavs = self.models[model_key].infer([text[:500]])
        temp_wav = output_path.replace('.mp3', '_temp.wav')
        wavfile.write(temp_wav, 24000, np.array(wavs[0]))
        
        subprocess.run(["ffmpeg", "-i", temp_wav, "-c:a", "libmp3lame", "-q:a", "4", output_path, "-y"], check=True, capture_output=True)
        Path(temp_wav).unlink()
        return True

    def _tts_xtts(self, text, output_path):
        from TTS.api import TTS
        model_key = "xtts"
        if model_key not in self.models:
            self.models[model_key] = TTS("tts_models/multilingual/multi-dataset/xtts_v2").to("cpu")
        self.models[model_key].tts_to_file(text=text[:500], file_path=output_path, language="en")
        return True

    def _generate_silence(self, output_path, duration_seconds=1.5):
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
        self.models.clear()
        import gc
        gc.collect()
        if torch.cuda.is_available(): torch.cuda.empty_cache()
        
