import os
import time
import cv2
import torch
import numpy as np
from PIL import Image
import pytesseract
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class OCREngine:
    """OCR Engine with network resilience and fallback mechanisms."""
    
    def __init__(self, engine_type="tesseract"):
        self.engine_type = engine_type
        self.models = {}
        logger.info(f"Initialized OCR Engine: {self.engine_type}")

    def get_text(self, image_path):
        if not os.path.exists(image_path): return ""
        
        try:
            Image.open(image_path).verify()
        except Exception:
            return ""
        
        engines_to_try = [self.engine_type, "tesseract"] 
        
        for engine in engines_to_try:
            try:
                if engine == "google_vision":
                    result = self._ocr_google_vision(image_path)
                elif engine == "manga_ocr":
                    result = self._ocr_manga_ocr(image_path)
                elif engine == "paddle_ocr":
                    result = self._ocr_paddle(image_path)
                elif engine == "comic_text_detector":
                    result = self._ocr_comic_detector(image_path)
                else:
                    result = self._ocr_tesseract(image_path)
                
                if result and result.strip(): return result.strip()
            except Exception as e:
                logger.error(f"OCR engine {engine} failed: {e}")
                continue
        
        return ""

    def _ocr_google_vision(self, image_path):
        from google.cloud import vision
        import io
        
        if not os.environ.get("GOOGLE_APPLICATION_CREDENTIALS"):
            raise Exception("Google credentials not configured")
            
        client = vision.ImageAnnotatorClient()
        with io.open(image_path, 'rb') as image_file: content = image_file.read()
        image = vision.Image(content=content)
        
        # Exponential backoff for API limits
        for attempt in range(4):
            try:
                response = client.text_detection(image=image)
                if response.error.message: raise Exception(response.error.message)
                return response.full_text_annotation.text if response.full_text_annotation else ""
            except Exception as e:
                if "429" in str(e) or "quota" in str(e).lower():
                    if attempt == 3: raise
                    time.sleep(2 ** attempt)
                    continue
                raise

    def _ocr_manga_ocr(self, image_path):
        if "manga_ocr" not in self.models:
            from manga_ocr import MangaOCR
            self.models["manga_ocr"] = MangaOCR()
        return self.models["manga_ocr"](image_path).strip()

    def _ocr_paddle(self, image_path):
        if "paddle_ocr" not in self.models:
            from paddleocr import PaddleOCR
            self.models["paddle_ocr"] = PaddleOCR(use_angle_cls=True, lang='en', use_gpu=False, show_log=False)
        
        result = self.models["paddle_ocr"].ocr(image_path, cls=True)
        if not result: return ""
        
        full_text = [line[1][0] for line_results in result if line_results for line in line_results if line and len(line) >= 2]
        return " ".join(full_text).strip()

    def _ocr_comic_detector(self, image_path):
        img = cv2.imread(image_path)
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
        thresh = cv2.adaptiveThreshold(gray, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY, 11, 2)
        contours, _ = cv2.findContours(thresh, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        
        bubble_texts = []
        img_area = img.shape[0] * img.shape[1]
        
        for cnt in contours:
            x, y, w, h = cv2.boundingRect(cnt)
            area = w * h
            if w > 30 and h > 20 and 200 < area < img_area * 0.3 and w/h < 5 and h/w < 5:
                roi_gray = cv2.threshold(gray[y:y+h, x:x+w], 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)[1]
                text = pytesseract.image_to_string(roi_gray, config='--psm 7').strip()
                if len(text) > 1: bubble_texts.append(text)
        
        return " ".join(bubble_texts) if bubble_texts else self._ocr_tesseract(image_path)

    def _ocr_tesseract(self, image_path):
        img_array = cv2.equalizeHist(np.array(Image.open(image_path).convert('L')))
        return pytesseract.image_to_string(Image.fromarray(img_array), config='--psm 6 --oem 3').strip()

    def cleanup(self):
        self.models.clear()
        import gc
        gc.collect()
        if torch.cuda.is_available(): torch.cuda.empty
            _cache()
