import os, asyncio, logging, subprocess, aiohttp
from pathlib import Path

class TTSEngine:
    def __init__(self, engine_type: str = "edge_tts"):
        self.engine_type = engine_type
        self._session = None

    async def _get_session(self):
        if self._session is None or self._session.closed:
            self._session = aiohttp.ClientSession()
        return self._session

    async def generate(self, text: str, path: str) -> bool:
        txt = text.replace('\n', ' ').strip()[:3000]
        if len(txt) < 2: return await self._silence(path)
        
        try:
            if self.engine_type == "edge_tts":
                from edge_tts import Communicate
                # Added rate='-30%' to achieve ~0.7x speed
                await Communicate(
                    txt, 
                    os.getenv("EDGE_TTS_VOICE", "en-US-AndrewNeural"),
                    rate='-30%' 
                ).save(path)
                return True
            elif self.engine_type == "elevenlabs":
                sess = await self._get_session()
                url = f"https://api.elevenlabs.io/v1/text-to-speech/{os.getenv('ELEVENLABS_VOICE_ID')}"
                # Note: ElevenLabs speed is controlled via the dashboard/voice settings
                async with sess.post(url, json={"text": txt}, headers={"xi-api-key": os.getenv("ELEVENLABS_API_KEY")}) as r:
                    if r.status == 200:
                        with open(path, "wb") as f: f.write(await r.read())
                        return True
        except Exception as e:
            logging.warning(f"TTS Engine failed, falling back to silence: {e}")
            
        return await self._silence(path)

    async def _silence(self, path):
        duration = 1.5
        cmd = ["ffmpeg", "-y", "-f", "lavfi", "-i", f"anullsrc=r=24000:cl=mono", "-t", str(duration), "-acodec", "libmp3lame", path]
        subprocess.run(cmd, check=True, capture_output=True)
        return True

    async def cleanup(self):
        if self._session and not self._session.closed:
            await self._session.close()
