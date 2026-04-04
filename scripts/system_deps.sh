#!/bin/bash
set -e

echo "Installing Core OS Prerequisites..."

# Using -qq to keep the GitHub Action logs cleaner
sudo apt-get update -qq

sudo apt-get install -y --no-install-recommends \
    ffmpeg \
    tesseract-ocr \
    tesseract-ocr-eng \
    tesseract-ocr-jpn \
    libgl1 \
    libglib2.0-0 \
    libgomp1 \
    libsndfile1 \
    wget \
    espeak-ng

# Clear the cache to save a tiny bit of disk space during the run
sudo rm -rf /var/lib/apt/lists/*
