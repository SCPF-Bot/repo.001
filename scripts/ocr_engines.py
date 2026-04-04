import os
import cv2
import logging
import importlib.util
from typing import List, Any

# Global environment flags for Paddle optimization
os.environ["PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK"] = "True"
os.environ["FLAGS_allocator_strategy"] = "naive_best_fit"

logger = logging.getLogger(__name__)

class OCREngine:
    def __init__(self, engine_type: str = "tesseract"):
        self.primary_engine = engine_type.lower()
        self.engines_to_try = [self.primary_engine]
        
        # Always fallback to Tesseract if the primary neural engine fails
        if self.primary_engine != "tesseract":
            self.engines_to_try.append("tesseract")
            
        self._model = None
        self._google_client = None

    def get_text(self, image_path: str) -> str:
        """
        Attempts OCR with the primary engine, falling back to 
        alternatives if errors occur or no text is found.
        """
        for engine in self.engines_to_try:
            try:
                method = getattr(self, f"_ocr_{engine}")
                text = method(image_path)
                if text and len(text.strip()) > 1:
                    return text.strip()
            except Exception as e:
                logger.error(f"Engine {engine} failed: {e}")
                continue
        return ""

    def _ocr_google_vision(self, image_path: str) -> str:
        if importlib.util.find_spec("google") is None:
            raise ImportError("google-cloud-vision not installed")
            
        from google.cloud import vision
        if self._google_client is None:
            self._google_client = vision.ImageAnnotatorClient()
            
        with open(image_path, "rb") as f:
            content = f.read()
            
        image = vision.Image(content=content)
        response = self._google_client.text_detection(image=image)
        
        if response.error.message:
            raise Exception(f"Google Vision API Error: {response.error.message}")
            
        return response.full_text_annotation.text if response.full_text_annotation else ""

    def _ocr_manga_ocr(self, image_path: str) -> str:
        from manga_ocr import MangaOCR
        if self._model is None:
            logger.info("Loading MangaOCR model (Heavy Download/Load)...")
            self._model = MangaOCR()
        return self._model(image_path)

    def _ocr_paddle_ocr(self, image_path: str) -> str:
        from paddleocr import PaddleOCR
        import logging as py_logging
        
        # Silence internal Paddle logging to prevent log flooding
        py_logging.getLogger("ppocr").setLevel(py_logging.ERROR)

        if self._model is None:
            logger.info("Initializing PaddleOCR 3.x (Auto Hardware Detection)...")
            # FIXED: Removed 'use_gpu' and 'show_log' for 2026 PaddleOCR 3.x compatibility
            self._model = PaddleOCR(
                use_angle_cls=True, 
                lang='en'
            )

        # Process image; returns list of results
        result = self._model.ocr(image_path, cls=True)
        
        if not result or result[0] is None:
            return ""
            
        # Extract text strings from the result structure
        return " ".join([line[1][0] for line in result[0]])

    def _ocr_tesseract(self, image_path: str) -> str:
        import pytesseract
        
        img = cv2.imread(image_path)
        if img is None:
            return ""
            
        # Convert to grayscale and apply adaptive thresholding for better contrast
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
        processed = cv2.adaptiveThreshold(
            gray, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, 
            cv2.THRESH_BINARY, 11, 2
        )
        
        # PSM 3: Automatic page segmentation
        return pytesseract.image_to_string(processed, config='--psm 3')
