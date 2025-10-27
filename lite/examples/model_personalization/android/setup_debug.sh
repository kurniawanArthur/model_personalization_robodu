#!/bin/bash

# 🔧 Quick Setup Script untuk Debug Android di WSL
# Run: chmod +x setup_debug.sh && ./setup_debug.sh

set -e

echo "=================================="
echo "🔧 Android Debug Setup untuk WSL"
echo "=================================="
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if running in WSL
if ! grep -qi microsoft /proc/version; then
    echo -e "${RED}❌ Script ini harus dijalankan di WSL!${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Running in WSL${NC}"
echo ""

# Step 1: Install ADB if not exists
echo "📦 Step 1: Checking ADB installation..."
if ! command -v adb &> /dev/null; then
    echo "Installing ADB..."
    sudo apt update
    sudo apt install -y android-tools-adb android-tools-fastboot
    echo -e "${GREEN}✅ ADB installed${NC}"
else
    echo -e "${GREEN}✅ ADB already installed${NC}"
    adb version
fi
echo ""

# Step 2: Install USB/IP tools
echo "📦 Step 2: Checking USB/IP tools..."
if ! command -v usbip &> /dev/null; then
    echo "Installing USB/IP tools..."
    sudo apt install -y linux-tools-generic hwdata
    sudo update-alternatives --install /usr/local/bin/usbip usbip $(ls /usr/lib/linux-tools/*/usbip | head -1) 20
    echo -e "${GREEN}✅ USB/IP tools installed${NC}"
else
    echo -e "${GREEN}✅ USB/IP tools already installed${NC}"
fi
echo ""

# Step 3: Setup udev rules
echo "📦 Step 3: Setting up udev rules..."
if [ ! -f /etc/udev/rules.d/51-android.rules ]; then
    echo "Creating udev rules..."
    sudo tee /etc/udev/rules.d/51-android.rules > /dev/null <<'EOF'
# Google
SUBSYSTEM=="usb", ATTR{idVendor}=="18d1", MODE="0666", GROUP="plugdev"
# Samsung
SUBSYSTEM=="usb", ATTR{idVendor}=="04e8", MODE="0666", GROUP="plugdev"
# Xiaomi
SUBSYSTEM=="usb", ATTR{idVendor}=="2717", MODE="0666", GROUP="plugdev"
# Oppo
SUBSYSTEM=="usb", ATTR{idVendor}=="22d9", MODE="0666", GROUP="plugdev"
# Vivo
SUBSYSTEM=="usb", ATTR{idVendor}=="2d95", MODE="0666", GROUP="plugdev"
# Realme
SUBSYSTEM=="usb", ATTR{idVendor}=="22d9", MODE="0666", GROUP="plugdev"
EOF
    
    sudo udevadm control --reload-rules
    sudo udevadm trigger
    
    # Add user to plugdev group
    if ! groups | grep -q plugdev; then
        sudo usermod -aG plugdev $USER
        echo -e "${YELLOW}⚠️  Added to plugdev group - you may need to logout/login${NC}"
    fi
    
    echo -e "${GREEN}✅ Udev rules created${NC}"
else
    echo -e "${GREEN}✅ Udev rules already exist${NC}"
fi
echo ""

# Step 4: Restart ADB
echo "🔄 Step 4: Restarting ADB server..."
adb kill-server
sleep 1
adb start-server
echo -e "${GREEN}✅ ADB server restarted${NC}"
echo ""

# Step 5: Check USB devices
echo "🔌 Step 5: Checking USB devices..."
echo "USB devices in WSL:"
lsusb
echo ""

# Step 6: Check ADB connection
echo "📱 Step 6: Checking Android device connection..."
adb devices
echo ""

DEVICE_COUNT=$(adb devices | grep -w "device" | wc -l)

if [ $DEVICE_COUNT -eq 0 ]; then
    echo -e "${YELLOW}⚠️  No device detected${NC}"
    echo ""
    echo "Please follow these steps:"
    echo "1. Di Windows PowerShell (Administrator), jalankan:"
    echo "   ${GREEN}usbipd list${NC}"
    echo "   ${GREEN}usbipd bind --busid X-X${NC}  (ganti X-X dengan BUSID Android)"
    echo "   ${GREEN}usbipd attach --wsl --busid X-X${NC}"
    echo ""
    echo "2. Pastikan USB debugging enabled di Android device"
    echo "3. Jalankan script ini lagi"
else
    echo -e "${GREEN}✅ Device connected!${NC}"
    echo ""
    echo "Device info:"
    adb shell getprop ro.product.model
    adb shell getprop ro.build.version.release
fi

echo ""
echo "=================================="
echo "✅ Setup Complete!"
echo "=================================="
echo ""
echo "Next steps:"
echo "1. Build APK: ${GREEN}./gradlew assembleDebug${NC}"
echo "2. Install: ${GREEN}adb install -r app/build/outputs/apk/debug/app-debug.apk${NC}"
echo "3. Monitor logs: ${GREEN}./start_logcat.sh${NC}"
echo ""
