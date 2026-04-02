#!/bin/bash
set -e
echo "Installing Core OS Prerequisites..."

sudo apt-get update
sudo apt-get install -y --no-install-recommends \
    ffmpeg \
    tesseract-ocr \
    tesseract-ocr-eng \
    libgl1 \
    libglib2.0-0 \
    libgomp1 \
    wget \
    espeak-ng

sudo apt-get clean
