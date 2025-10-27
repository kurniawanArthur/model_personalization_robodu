#!/bin/bash

# 📊 Logcat Monitor Script
# Run: chmod +x start_logcat.sh && ./start_logcat.sh

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Clear screen
clear

echo "========================================"
echo "📊 Android Logcat Monitor"
echo "========================================"
echo ""

# Check device connection
DEVICE_COUNT=$(adb devices | grep -w "device" | wc -l)

if [ $DEVICE_COUNT -eq 0 ]; then
    echo -e "${RED}❌ No device connected!${NC}"
    echo "Run: ${GREEN}./setup_debug.sh${NC} first"
    exit 1
fi

echo -e "${GREEN}✅ Device connected${NC}"
echo "Device: $(adb shell getprop ro.product.model)"
echo ""

# Clear existing logs
echo "Clearing old logs..."
adb logcat -c
sleep 1

echo -e "${BLUE}Starting logcat monitoring...${NC}"
echo "Press Ctrl+C to stop"
echo "========================================"
echo ""

# Monitor with colors and filtering
adb logcat -v time | grep --line-buffered -E "TransferLearning|TensorFlow|Camera|MainActivity|ERROR|FATAL|AndroidRuntime" | while IFS= read -r line; do
    if echo "$line" | grep -qi "error\|fatal\|crash"; then
        echo -e "${RED}$line${NC}"
    elif echo "$line" | grep -qi "warn"; then
        echo -e "${YELLOW}$line${NC}"
    elif echo "$line" | grep -qi "TransferLearning\|TensorFlow"; then
        echo -e "${GREEN}$line${NC}"
    else
        echo "$line"
    fi
done
