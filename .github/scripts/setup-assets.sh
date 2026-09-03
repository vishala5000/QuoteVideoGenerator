#!/bin/bash

# Setup assets for GitHub Actions build

echo "Setting up assets for build..."

# Create assets directory if it doesn't exist
mkdir -p app/src/main/assets

# Check and download font if missing
if [ ! -f "app/src/main/assets/font.ttf" ]; then
    echo "Downloading Roboto font..."
    curl -L -o app/src/main/assets/font.ttf \
        https://github.com/google/fonts/raw/main/ofl/roboto/Roboto-Regular.ttf
fi

# Check and create placeholder audio if missing
if [ ! -f "app/src/main/assets/bg.mp3" ]; then
    echo "Creating placeholder audio..."
    # Use ffmpeg to create a 5-second silent MP3
    if command -v ffmpeg &> /dev/null; then
        ffmpeg -f lavfi -i anullsrc=r=44100:cl=mono -t 5 -acodec libmp3lame \
            -q:a 9 app/src/main/assets/bg.mp3 -y 2>/dev/null || \
            echo "Could not create audio placeholder"
    else
        echo "ffmpeg not available, creating empty placeholder"
        touch app/src/main/assets/bg.mp3
    fi
fi

echo "Assets setup complete!"
