import os
import cv2
import numpy as np
from PIL import Image
import logging
from typing import Optional

logger = logging.getLogger(__name__)

class OCREngine:
    def __init__(self, engine_type: str = "tesseract"):
        self.engine_type = engine_type
        self._model = None
        self._google_client = None

    def get_text(self, image_path: str) -> str:
        """Extract text with retries and automatic fallback to Tesseract."""
        engines_to_try = [self.engine_type, "tesseract"]
        last_error = None

        for engine in engines_to_try:
            try:
                if engine == "google_vision":
                    return self._ocr_google_vision(image_path)
                elif engine == "manga_ocr":
                    return self._ocr_manga_ocr(image_path)
                elif engine == "paddle_ocr":
                    return self._ocr_paddle_ocr(image_path)
                else:
                    return self._ocr_tesseract(image_path)
            except Exception as e:
                logger.error(f"Engine {engine} failed: {e}")
                last_error = e
                continue

        raise RuntimeError(f"All OCR engines failed. Last error: {last_error}")

    # ------------------------------------------------------------------
    # Lazy‑loaded implementations
    # ------------------------------------------------------------------
    def _ocr_google_vision(self, image_path: str) -> str:
        from google.cloud import vision
        if self._google_client is None:
            # Expects GOOGLE_APPLICATION_CREDENTIALS env var
            self._google_client = vision.ImageAnnotatorClient()

        with open(image_path, "rb") as f:
            content = f.read()
        image = vision.Image(content=content)
        response = self._google_client.text_detection(image=image)
        if response.error.message:
            raise Exception(f"Google Vision API error: {response.error.message}")

        texts = [annotation.description for annotation in response.text_annotations]
        return texts[0] if texts else ""

    def _ocr_manga_ocr(self, image_path: str) -> str:
        from manga_ocr import MangaOCR
        if self._model is None:
            self._model = MangaOCR()
        return self._model(image_path)

    def _ocr_paddle_ocr(self, image_path: str) -> str:
        from paddleocr import PaddleOCR
        if self._model is None:
            # Use CPU, disable GPU to save memory
            self._model = PaddleOCR(use_angle_cls=True, lang='en', use_gpu=False, show_log=False)
        result = self._model.ocr(image_path, cls=True)
        if not result or not result[0]:
            return ""
        return "\n".join([line[1][0] for line in result[0]])

    def _ocr_tesseract(self, image_path: str) -> str:
        import pytesseract
        # Preprocess for better accuracy
        img = cv2.imread(image_path)
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
        # Apply threshold to get black text on white
        _, thresh = cv2.threshold(gray, 150, 255, cv2.THRESH_BINARY_INV)
        # Optional: denoise
        processed = cv2.medianBlur(thresh, 3)
        return pytesseract.image_to_string(processed, lang='eng')
