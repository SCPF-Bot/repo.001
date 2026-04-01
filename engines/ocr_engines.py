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
        self.initialized_models = {}
        print(f"Initialized OCR Engine: {self.engine_type}")

    def get_text(self, image_path):
        """Main entry point for text extraction with fail-over protection."""
        try:
            # Verify image exists and is valid
            if not os.path.exists(image_path):
                raise FileNotFoundError(f"Image not found: {image_path}")
            
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
        import io
        
        # Initialize client with credentials if available
        client = vision.ImageAnnotatorClient()
        
        with io.open(image_path, 'rb') as image_file:
            content = image_file.read()
        
        image = vision.Image(content=content)
        response = client.text_detection(image=image)
        
        if response.error.message:
            raise Exception(f"Google Vision Error: {response.error.message}")
        
        if response.full_text_annotation:
            return response.full_text_annotation.text
        return ""

    def _ocr_manga_ocr(self, image_path):
        """Manga-OCR specialized for Japanese manga text."""
        # Lazy load: Manga-OCR is heavy (400MB+ RAM)
        if "manga_ocr" not in self.initialized_models:
            try:
                from manga_ocr import MangaOCR
                self.initialized_models["manga_ocr"] = MangaOCR()
                print("Manga-OCR model loaded successfully")
            except ImportError as e:
                print(f"Manga-OCR import failed: {e}")
                raise
        
        model = self.initialized_models["manga_ocr"]
        try:
            # MangaOCR expects image path or PIL Image
            result = model(image_path)
            return result.strip()
        except Exception as e:
            print(f"Manga-OCR processing error: {e}")
            raise

    def _ocr_paddle(self, image_path):
        """PaddleOCR with memory optimization for CI/CD."""
        if "paddle_ocr" not in self.initialized_models:
            try:
                from paddleocr import PaddleOCR
                # Memory optimization for GitHub Actions
                # use_gpu=False is mandatory for standard runners
                self.initialized_models["paddle_ocr"] = PaddleOCR(
                    use_angle_cls=True, 
                    lang='en', 
                    use_gpu=False, 
                    show_log=False,
                    enable_mkldnn=True,  # CPU optimization
                    use_tensorrt=False,
                    det_db_thresh=0.3,   # Slightly more sensitive detection
                    det_db_box_thresh=0.5
                )
                print("PaddleOCR model loaded successfully")
            except ImportError as e:
                print(f"PaddleOCR import failed: {e}")
                raise
            except Exception as e:
                print(f"PaddleOCR initialization error: {e}")
                raise
        
        model = self.initialized_models["paddle_ocr"]
        try:
            # PaddleOCR expects image path or numpy array
            result = model.ocr(image_path, cls=True)
            
            # Handle case where no text detected
            if not result:
                return ""
            
            # Flattening PaddleOCR output structure with better error handling
            full_text = []
            for line_results in result:
                if line_results:
                    for line in line_results:
                        if line and len(line) >= 2 and line[1]:
                            full_text.append(line[1][0])
            
            extracted_text = " ".join(full_text).strip()
            if not extracted_text:
                print("PaddleOCR detected no text")
                return ""
            
            return extracted_text
            
        except Exception as e:
            print(f"PaddleOCR processing error: {e}")
            raise

    def _ocr_comic_detector(self, image_path):
        """
        Implementation for Comic-Text-Detector logic.
        Uses specialized contour detection to find speech bubbles before OCR.
        """
        try:
            img = cv2.imread(image_path)
            if img is None:
                raise ValueError(f"Could not load image: {image_path}")
            
            # Convert to grayscale
            gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
            
            # Apply adaptive thresholding for better bubble detection
            thresh = cv2.adaptiveThreshold(
                gray, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, 
                cv2.THRESH_BINARY, 11, 2
            )
            
            # Find contours
            contours, _ = cv2.findContours(thresh, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
            
            bubble_texts = []
            img_height, img_width = img.shape[:2]
            
            for cnt in contours:
                x, y, w, h = cv2.boundingRect(cnt)
                # Filter out noise based on size and aspect ratio
                area = w * h
                img_area = img_width * img_height
                
                # More refined filtering for speech bubbles
                if (w > 30 and h > 20 and 
                    area > 200 and area < img_area * 0.3 and  # Not too large
                    w/h < 5 and h/w < 5):  # Reasonable aspect ratio
                    
                    roi = img[y:y+h, x:x+w]
                    # Preprocess ROI for better OCR
                    roi_gray = cv2.cvtColor(roi, cv2.COLOR_BGR2GRAY)
                    roi_gray = cv2.threshold(roi_gray, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)[1]
                    
                    # OCR on ROI
                    text = pytesseract.image_to_string(roi_gray, config='--psm 7').strip()
                    if text and len(text) > 1:
                        bubble_texts.append(text)
            
            if bubble_texts:
                return " ".join(bubble_texts)
            else:
                # Fallback to full image OCR if no bubbles detected
                print("No speech bubbles detected, falling back to full image OCR")
                return self._ocr_tesseract(image_path)
                
        except Exception as e:
            print(f"Comic detector error: {e}, falling back to Tesseract")
            return self._ocr_tesseract(image_path)

    def _ocr_tesseract(self, image_path):
        """The ultimate fallback: Standard Tesseract OCR."""
        try:
            # Open image and convert to grayscale
            img = Image.open(image_path).convert('L')
            
            # Optional: Apply image preprocessing for better results
            import numpy as np
            img_array = np.array(img)
            
            # Simple contrast enhancement
            img_array = cv2.equalizeHist(img_array)
            img = Image.fromarray(img_array)
            
            # Tesseract with optimized config for manga text
            config = '--psm 6 --oem 3'  # Assume uniform text block
            text = pytesseract.image_to_string(img, config=config)
            
            return text.strip()
        except Exception as e:
            print(f"Tesseract OCR error: {e}")
            return ""  # Return empty string on complete failure

    def cleanup(self):
        """Clean up loaded models to free memory (useful for long-running processes)."""
        self.initialized_models.clear()
        import gc
        gc.collect()
        if torch.cuda.is_available():
            torch.cuda.empty_cache()
