import os
import asyncio
import requests
import torch
import wave
import numpy as np
from pathlib import Path

class TTSEngine:
    def __init__(self, engine_type="edge_tts"):
        self.engine_type = engine_type
        self.model = None
        print(f"Initialized TTS Engine: {self.engine_type}")

    async def generate(self, text, output_path):
        """Orchestrator for TTS generation with silent fallback logic."""
        if not text or len(text.strip()) < 2:
            return self._generate_silence(output_path)

        try:
            if self.engine_type == "elevenlabs":
                return self._tts_elevenlabs(text, output_path)
            
            elif self.engine_type == "deepgram_aura":
                return self._tts_deepgram(text, output_path)
            
            elif self.engine_type == "fish_speech":
                return self._tts_fish_api(text, output_path)

            elif self.engine_type == "melo_tts":
                return self._tts_melo(text, output_path)

            elif self.engine_type == "chat_tts":
                return self._tts_chat(text, output_path)

            elif self.engine_type == "xtts_v2":
                return self._tts_xtts(text, output_path)

            else:
                return await self._tts_edge(text, output_path)

        except Exception as e:
            print(f"TTS Failure on {self.engine_type}: {e}. Falling back to Edge-TTS.")
            return await self._tts_edge(text, output_path)

    async def _tts_edge(self, text, output_path):
        """Free, reliable Microsoft Neural TTS."""
        from edge_tts import Communicate
        communicate = Communicate(text, "en-US-GuyNeural")
        await communicate.save(output_path)
        return True

    def _tts_elevenlabs(self, text, output_path):
        key = os.getenv("ELEVENLABS_API_KEY")
        url = "https://api.elevenlabs.io/v1/text-to-speech/21m00Tcm4TlvDq8ikWAM"
        headers = {"xi-api-key": key, "Content-Type": "application/json"}
        data = {"text": text, "model_id": "eleven_multilingual_v2"}
        res = requests.post(url, json=data, headers=headers)
        if res.status_code == 200:
            with open(output_path, "wb") as f: f.write(res.content)
            return True
        raise Exception(f"ElevenLabs Error: {res.text}")

    def _tts_deepgram(self, text, output_path):
        key = os.getenv("DEEPGRAM_KEY")
        url = "https://api.deepgram.com/v1/speak?model=aura-helios-en"
        headers = {"Authorization": f"Token {key}", "Content-Type": "application/json"}
        res = requests.post(url, json={"text": text}, headers=headers)
        if res.status_code == 200:
            with open(output_path, "wb") as f: f.write(res.content)
            return True
        return False

    def _tts_fish_api(self, text, output_path):
        """Fish Speech V1.5 API Integration."""
        key = os.getenv("FISH_KEY")
        url = "https://api.fish.audio/v1/tts"
        headers = {"Authorization": f"Bearer {key}", "Content-Type": "application/json"}
        data = {"text": text, "format": "mp3"}
        res = requests.post(url, json=data, headers=headers)
        if res.status_code == 200:
            with open(output_path, "wb") as f: f.write(res.content)
            return True
        return False

    def _tts_melo(self, text, output_path):
        """MeloTTS - Optimized for CPU."""
        from melotts.api import TTS
        if not self.model:
            self.model = TTS(language='EN', device='cpu')
        speaker_ids = self.model.hps.data.spk2id
        self.model.tts_to_file(text, speaker_ids['EN-Default'], output_path, speed=1.0)
        return True

    def _tts_chat(self, text, output_path):
        """ChatTTS Implementation."""
        import ChatTTS
        if not self.model:
            self.model = ChatTTS.Chat()
            self.model.load_models()
        wavs = self.model.infer([text])
        # Save logic for ChatTTS numpy output
        import scipy.io.wavfile as wavfile
        wavfile.write(output_path, 24000, np.array(wavs[0]))
        return True

    def _tts_xtts(self, text, output_path):
        """XTTS-v2 (Coqui) Implementation."""
        from TTS.api import TTS
        if not self.model:
            self.model = TTS("tts_models/multilingual/multi-dataset/xtts_v2").to("cpu")
        self.model.tts_to_file(text=text, file_path=output_path, speaker_wav="scripts/ref.wav", language="en")
        return True

    def _generate_silence(self, output_path):
        """Zero-Fail fallback: creates a 1-second silent WAV file."""
        with wave.open(output_path, 'wb') as wf:
            wf.setnchannels(1)
            wf.setsampwidth(2)
            wf.setframerate(44100)
            wf.writeframes(b'\x00' * 88200) # 1 second of silence
        return True
