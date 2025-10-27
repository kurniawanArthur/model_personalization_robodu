#!/bin/bash

# 🚀 Quick Deploy & Run Script
# Run: chmod +x deploy_app.sh && ./deploy_app.sh

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo "========================================"
echo "🚀 Deploy & Run App"
echo "========================================"
echo ""

# Check device
DEVICE_COUNT=$(adb devices | grep -w "device" | wc -l)
if [ $DEVICE_COUNT -eq 0 ]; then
    echo -e "${RED}❌ No device connected!${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Device: $(adb shell getprop ro.product.model)${NC}"
echo ""

# Build APK
echo "📦 Building APK..."
./gradlew assembleDebug

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Build failed!${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Build successful${NC}"
echo ""

# Install APK
echo "📲 Installing APK..."
adb install -r app/build/outputs/apk/debug/app-debug.apk

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Installation failed!${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Installation successful${NC}"
echo ""

# Grant camera permission
echo "📸 Granting camera permission..."
adb shell pm grant org.tensorflow.lite.examples.modelpersonalization android.permission.CAMERA
echo -e "${GREEN}✅ Permission granted${NC}"
echo ""

# Launch app
echo "🚀 Launching app..."
adb shell am start -n org.tensorflow.lite.examples.modelpersonalization/.MainActivity

echo ""
echo "========================================"
echo "✅ App deployed and launched!"
echo "========================================"
echo ""
echo "Monitor logs dengan: ${GREEN}./start_logcat.sh${NC}"
echo ""
