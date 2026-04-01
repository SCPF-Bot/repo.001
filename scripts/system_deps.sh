#!/bin/bash
set -e

echo "=== Installing System Dependencies ==="

sudo apt-get update

sudo apt-get install -y --no-install-recommends \
    ffmpeg \
    tesseract-ocr \
    libtesseract-dev \
    libmagic1 \
    libgl1 \
    libglib2.0-0 \
    unzip \
    curl \
    espeak-ng \
    libsm6 \
    libxext6 \
    libxrender-dev \
    libgomp1

sudo apt-get clean
sudo rm -rf /var/lib/apt/lists/*

echo "✓ System dependencies installed"
