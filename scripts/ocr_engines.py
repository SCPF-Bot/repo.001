import os
import cv2
import logging
import importlib.util
import re
from google import genai  # Updated from google.generativeai
from typing import List, Any

# Global environment flags for Paddle optimization
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

        if api_key:
            # New Client initialization for google-genai
            self.ai_client = genai.Client(api_key=api_key)
        else:
            self.ai_client = None

    def _ai_clean_text(self, messy_text: str) -> str:
        """Uses Gemini AI to remove OCR artifacts and fix grammar."""
        if not self.ai_client or len(messy_text) < 5:
            return messy_text

        prompt = (
            "You are a manga translation assistant. Clean the following OCR text from a speech bubble. "
            "Remove garbled characters, OCR artifacts, and nonsensical symbols. "
            "Fix the grammar to make it natural English. "
            "Only return the cleaned text, nothing else."
        )
        
        try:
            # Updated generation syntax for google-genai SDK
            response = self.ai_client.models.generate_content(
                model='gemini-1.5-flash',
                contents=f"{prompt}\n\nOCR Output: {messy_text}"
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
        from manga_ocr import MangaOCR
        if self._model is None:
            self._model = MangaOCR()
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
