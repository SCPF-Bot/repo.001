"""
Manga AI Engines Package
Provides OCR and TTS engine implementations with failover capabilities.
"""

from .ocr_engines import OCREngine
from .tts_engines import TTSEngine

__all__ = ['OCREngine', 'TTSEngine']
