import os
import cv2
import torch
import numpy as np
from PIL import Image
import pytesseract

class OCREngine:
    def __init__(self, engine_type="tesseract"):
        self.engine_type = engine_type
        self.model = None
        print(f"Initialized OCR Engine: {self.engine_type}")

    def get_text(self, image_path):
        """Main entry point for text extraction with fail-over protection."""
        try:
            if self.engine_type == "google_vision":
                return self._ocr_google_vision(image_path)
            
            elif self.engine_type == "manga_ocr":
                return self._ocr_manga_ocr(image_path)
            
            elif self.engine_type == "paddle_ocr":
                return self._ocr_paddle(image_path)
            
            elif self.engine_type == "comic_text_detector":
                return self._ocr_comic_detector(image_path)
            
            else:
                return self._ocr_tesseract(image_path)
        except Exception as e:
            print(f"Critical Error in {self.engine_type}: {e}. Falling back to Tesseract.")
            return self._ocr_tesseract(image_path)

    def _ocr_google_vision(self, image_path):
        from google.cloud import vision
        client = vision.ImageAnnotatorClient()
        with open(image_path, "rb") as image_file:
            content = image_file.read()
        image = vision.Image(content=content)
        response = client.text_detection(image=image)
        if response.error.message:
            raise Exception(f"Google Vision Error: {response.error.message}")
        return response.full_text_annotation.text if response.full_text_annotation else ""

    def _ocr_manga_ocr(self, image_path):
        # Lazy load: Manga-OCR is heavy (400MB+ RAM)
        from manga_ocr import MangaOCR
        if self.model is None:
            self.model = MangaOCR()
        return self.model(image_path)

    def _ocr_paddle(self, image_path):
        from paddleocr import PaddleOCR
        # use_gpu=False is mandatory for GitHub Actions standard runners
        if self.model is None:
            self.model = PaddleOCR(use_angle_cls=True, lang='en', use_gpu=False, show_log=False)
        result = self.model.ocr(image_path, cls=True)
        
        # Flattening PaddleOCR output structure
        full_text = []
        for idx in range(len(result)):
            res = result[idx]
            if res:
                for line in res:
                    full_text.append(line[1][0])
        return " ".join(full_text)

    def _ocr_comic_detector(self, image_path):
        """
        Implementation for Comic-Text-Detector logic.
        Uses specialized contour detection to find speech bubbles before OCR.
        """
        img = cv2.imread(image_path)
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
        # Apply thresholding to isolate bubbles
        _, thresh = cv2.threshold(gray, 200, 255, cv2.THRESH_BINARY)
        # Find contours which are likely text bubbles
        contours, _ = cv2.find_contours(thresh, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        
        bubble_texts = []
        for cnt in contours:
            x, y, w, h = cv2.boundingRect(cnt)
            if w > 50 and h > 20: # Filter out noise
                roi = img[y:y+h, x:x+w]
                # Run Tesseract on the specific bubble ROI for higher accuracy
                text = pytesseract.image_to_string(roi).strip()
                if text:
                    bubble_texts.append(text)
        
        return " ".join(bubble_texts) if bubble_texts else self._ocr_tesseract(image_path)

    def _ocr_tesseract(self, image_path):
        """The ultimate fallback: Standard Tesseract OCR."""
        img = Image.open(image_path).convert('L') # Convert to Grayscale
        return pytesseract.image_to_string(img)
