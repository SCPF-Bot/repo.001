#!/bin/bash
set -e

echo "--- Initializing System Dependency Injection ---"

# 1. Update and install core binaries
sudo apt-get update
sudo apt-get install -y --no-install-recommends \
    ffmpeg \
    tesseract-ocr \
    libtesseract-dev \
    libmagic1 \
    libgl1-mesa-glx \
    libglib2.0-0 \
    unzip \
    curl \
    espeak-ng

# 2. Clean up apt cache to save runner disk space
sudo apt-get clean
sudo rm -rf /var/lib/apt/lists/*

# 3. Ensure directories exist
mkdir -p output processing scripts engines
touch engines/__init__.py

# Make scripts executable
chmod +x scripts/*.sh 2>/dev/null || true

echo "--- System Dependencies Verified and Installed ---"

# 4. Install Python dependencies
echo "--- Installing Python Dependencies ---"

# Upgrade pip
pip install --upgrade pip setuptools wheel

# Install PyTorch CPU version
pip install torch==2.5.1 torchaudio==2.5.1 --index-url https://download.pytorch.org/whl/cpu

# Install requirements
pip install -r requirements.txt

echo "--- Python Dependencies Installed ---"

# 5. Verify installations
python -c "import torch; print(f'✓ PyTorch {torch.__version__}')"
python -c "import cv2; print(f'✓ OpenCV {cv2.__version__}')"

echo "--- All dependencies installed successfully ---"
