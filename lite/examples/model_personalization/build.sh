#!/bin/bash
# ROBO-DU Model Personalization - Quick Build Script
# For Windows with Git Bash

echo "🤖 ROBO-DU Model Personalization - Build Script"
echo "================================================="
echo ""

# Navigate to android directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR/android"

echo "📁 Current directory: $(pwd)"
echo ""

# Check if gradlew exists
if [ ! -f "gradlew.bat" ]; then
    echo "❌ Error: gradlew.bat not found!"
    echo "   Please run from the project root directory"
    exit 1
fi

echo "🔨 Starting build process..."
echo ""

# Clean build (optional, uncomment if needed)
# echo "🧹 Cleaning previous build..."
# ./gradlew.bat clean

# Build APK
echo "🔨 Building Debug APK..."
./gradlew.bat assembleDebug

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Build successful!"
    echo ""
    echo "📦 APK Location:"
    echo "   app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    
    # Check if device is connected
    if command -v adb &> /dev/null; then
        DEVICE_COUNT=$(adb devices | grep -v "List" | grep "device" | wc -l)
        
        if [ $DEVICE_COUNT -gt 0 ]; then
            echo "📱 Device detected!"
            echo ""
            read -p "   Install to device? (y/n): " -n 1 -r
            echo ""
            
            if [[ $REPLY =~ ^[Yy]$ ]]; then
                echo "📲 Installing APK..."
                adb install -r app/build/outputs/apk/debug/app-debug.apk
                
                if [ $? -eq 0 ]; then
                    echo ""
                    echo "✅ Installation successful!"
                    echo ""
                    read -p "   Launch app now? (y/n): " -n 1 -r
                    echo ""
                    
                    if [[ $REPLY =~ ^[Yy]$ ]]; then
                        echo "🚀 Launching app..."
                        adb shell am start -n org.tensorflow.lite.examples.modelpersonalization/.MainActivity
                        echo ""
                        echo "✅ App launched!"
                    fi
                else
                    echo "❌ Installation failed!"
                    echo "   Try: adb uninstall org.tensorflow.lite.examples.modelpersonalization"
                    echo "   Then run this script again"
                fi
            fi
        else
            echo "⚠️  No device connected"
            echo "   Connect device and run:"
            echo "   adb install -r app/build/outputs/apk/debug/app-debug.apk"
        fi
    else
        echo "⚠️  ADB not found in PATH"
        echo "   Install APK manually from:"
        echo "   app/build/outputs/apk/debug/app-debug.apk"
    fi
    
    echo ""
    echo "🎉 Done!"
    echo ""
    echo "📚 New Features in this build:"
    echo "   ✅ ROBO-DU Branding"
    echo "   ✅ Model Persistence (Auto-save/load)"
    echo "   ✅ Custom Class Names (Long-press to edit)"
    echo "   ✅ Training Progress Bar"
    echo "   ✅ Sample Gallery Layout"
    echo ""
else
    echo ""
    echo "❌ Build failed!"
    echo "   Check error messages above"
    exit 1
fi
