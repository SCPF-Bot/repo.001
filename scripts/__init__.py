"""
Manga AI Engines Package
Optimized for lazy-loading to prevent memory bloat in CI/CD environments.
"""

__all__ = ['OCREngine', 'TTSEngine']

def __getattr__(name):
    """
    Python 3.7+ feature: Only imports the engine classes when they are 
    actually accessed for the first time.
    """
    if name == "OCREngine":
        from .ocr_engines import OCREngine
        return OCREngine
    if name == "TTSEngine":
        from .tts_engines import TTSEngine
        return TTSEngine
    
    raise AttributeError(f"module {__name__} has no attribute {name}")
