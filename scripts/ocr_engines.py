import os
import sys
import cv2
import logging
import importlib
import re
from google import genai 
from typing import List, Any

# Global environment flags
os.environ["PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK"] = "True"
os.environ["FLAGS_allocator_strategy"] = "naive_best_fit"

logger = logging.getLogger(__name__)

class OCREngine:
    def __init__(self, engine_type: str = "tesseract", api_key: str = None):
        self.primary_engine = engine_type.lower()
        self.engines_to_try = [self.primary_engine]
        if self.primary_engine != "tesseract":
            self.engines_to_try.append("tesseract")
            
        self._model = None
        self.ai_client = genai.Client(api_key=api_key) if api_key else None

    def _ai_clean_text(self, messy_text: str) -> str:
        if not self.ai_client or len(messy_text) < 5:
            return messy_text
        
        prompt = "Clean this manga OCR text. Remove artifacts and fix grammar. Return only cleaned text."
        try:
            response = self.ai_client.models.generate_content(
                model='gemini-1.5-flash',
                contents=f"{prompt}\n\nTEXT: {messy_text}"
            )
            return response.text.strip()
        except Exception as e:
            logger.warning(f"AI Cleaning failed: {e}")
            return messy_text

    def get_text(self, image_path: str) -> str:
        for engine in self.engines_to_try:
            try:
                method = getattr(self, f"_ocr_{engine}")
                text = method(image_path)
                if text and len(text.strip()) > 1:
                    return self._ai_clean_text(text.strip())
            except Exception as e:
                logger.error(f"Engine {engine} failed: {e}")
                continue
        return ""

    def _ocr_manga_ocr(self, image_path: str) -> str:
        if self._model is None:
            # FORCE bypass of local directory to avoid namespace collisions
            try:
                # We specifically look for the site-packages version
                spec = importlib.util.find_spec("manga_ocr")
                if spec and "site-packages" in spec.origin:
                    manga_module = importlib.import_module("manga_ocr")
                    self._model = manga_module.MangaOCR()
                else:
                    # Fallback to standard but log path for debugging
                    from manga_ocr import MangaOCR
                    self._model = MangaOCR()
            except Exception as e:
                logger.error(f"Namespace collision detected. Path: {sys.path}")
                raise ImportError(f"Could not find valid MangaOCR library: {e}")
        
        return self._model(image_path)

    def _ocr_paddle_ocr(self, image_path: str) -> str:
        from paddleocr import PaddleOCR
        if self._model is None:
            self._model = PaddleOCR(use_angle_cls=True, lang='en')
        result = self._model.ocr(image_path, cls=True)
        if not result or result[0] is None: return ""
        return " ".join([line[1][0] for line in result[0]])

    def _ocr_tesseract(self, image_path: str) -> str:
        import pytesseract
        img = cv2.imread(image_path)
        if img is None: return ""
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
        processed = cv2.adaptiveThreshold(gray, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY, 11, 2)
        return pytesseract.image_to_string(processed, config='--psm 3')
