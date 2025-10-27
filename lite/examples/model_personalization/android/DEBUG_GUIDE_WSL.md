# 🔧 Panduan Debug Android via USB di WSL Ubuntu

**Environment:** Windows + WSL2 Ubuntu  
**Target:** Debug TensorFlow Lite Model Personalization App  
**Date:** October 23, 2025

---

## 📋 Persiapan

### Requirements:
- ✅ Windows 10/11 dengan WSL2
- ✅ Android device dengan USB debugging enabled
- ✅ Kabel USB
- ✅ ADB installed di WSL

---

## 🔌 LANGKAH 1: Setup USB Forwarding dari Windows ke WSL

### A. Install usbipd di Windows

1. **Download dan Install usbipd-win** (di Windows PowerShell sebagai Administrator):
```powershell
# Buka PowerShell sebagai Administrator
winget install --interactive --exact dorssel.usbipd-win
```

Atau download manual dari: https://github.com/dorssel/usbipd-win/releases

2. **Restart komputer** setelah instalasi

---

### B. Install USB/IP tools di WSL Ubuntu

Di terminal WSL Ubuntu, jalankan:

```bash
sudo apt update
sudo apt install linux-tools-generic hwdata
sudo update-alternatives --install /usr/local/bin/usbip usbip /usr/lib/linux-tools/*-generic/usbip 20
```

---

## 📱 LANGKAH 2: Enable USB Debugging di Android

1. **Aktifkan Developer Options:**
   - Buka **Settings** → **About Phone**
   - Tap **Build Number** 7 kali
   - Akan muncul "You are now a developer!"

2. **Enable USB Debugging:**
   - Buka **Settings** → **System** → **Developer Options**
   - Aktifkan **USB Debugging**
   - (Optional) Aktifkan **Stay Awake** agar layar tidak mati

3. **Sambungkan device ke komputer via USB**

---

## 🔗 LANGKAH 3: Bind & Attach USB Device

### A. List USB Devices (di Windows PowerShell Administrator)

```powershell
usbipd list
```

Output contoh:
```
BUSID  VID:PID    DEVICE                STATE
1-4    18d1:4ee7  Android Device        Not shared
2-3    046d:c52b  Logitech USB Mouse    Not shared
```

Cari device Android Anda (biasanya VID 18d1 atau manufacturer phone Anda)

---

### B. Bind USB Device (di Windows PowerShell Administrator)

```powershell
# Ganti 1-4 dengan BUSID device Android Anda
usbipd bind --busid 1-4
```

---

### C. Attach ke WSL (di Windows PowerShell Administrator)

```powershell
# Ganti 1-4 dengan BUSID device Anda
usbipd attach --wsl --busid 1-4
```

⚠️ **PENTING:** Command ini harus dijalankan setiap kali:
- Device dicolok ulang
- WSL di-restart
- Komputer di-restart

---

## ✅ LANGKAH 4: Verifikasi Koneksi di WSL

Di terminal WSL Ubuntu:

```bash
# Check apakah device terdeteksi di WSL
lsusb
```

Output harus menunjukkan device Android:
```
Bus 001 Device 002: ID 18d1:4ee7 Google Inc. Nexus/Pixel Device
```

---

## 🛠️ LANGKAH 5: Install & Setup ADB di WSL

```bash
# Install ADB
sudo apt update
sudo apt install -y android-tools-adb android-tools-fastboot

# Restart ADB server
adb kill-server
adb start-server

# Check device connection
adb devices
```

**Expected Output:**
```
List of devices attached
<serial_number>    device
```

⚠️ **Jika muncul "unauthorized":**
1. Periksa layar Android - akan muncul dialog "Allow USB debugging?"
2. Centang "Always allow from this computer"
3. Tap **OK**
4. Jalankan `adb devices` lagi

---

## 🚀 LANGKAH 6: Build & Install APK

```bash
cd /root/test/examples/lite/examples/model_personalization/android

# Clean build
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Install ke device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Flag `-r`**: Replace existing app if already installed

---

## 📊 LANGKAH 7: Real-time Debugging dengan Logcat

### A. Start Logcat Monitoring

```bash
# Clear existing logs
adb logcat -c

# Monitor logs in real-time (filtered untuk app kita)
adb logcat | grep -E "TransferLearningHelper|TensorFlow|CameraFragment|MainActivity"
```

### B. Monitor dengan Tag Spesifik

```bash
# Hanya log dari TransferLearningHelper
adb logcat TransferLearningHelper:D *:S

# Multiple tags
adb logcat TransferLearningHelper:D TensorFlow:D CameraFragment:D *:S
```

### C. Save Logs to File

```bash
# Save semua log ke file
adb logcat > debug_log.txt

# Save filtered log
adb logcat | grep -E "TransferLearningHelper|TensorFlow|Camera" > app_debug.txt
```

---

## 🎥 LANGKAH 8: Test Camera Functionality

### Test Sequence:

1. **Launch App:**
```bash
# Start app dari command line
adb shell am start -n org.tensorflow.lite.examples.modelpersonalization/.MainActivity
```

2. **Monitor Logcat Secara Real-time:**
```bash
# Di terminal lain, jalankan:
adb logcat -v time | grep -E "TransferLearning|Camera|TensorFlow|ERROR|FATAL"
```

3. **Test di Device:**
   - Buka app
   - Tap camera untuk mulai capture
   - Perhatikan output logcat

4. **Check Permissions:**
```bash
# Check camera permission status
adb shell dumpsys package org.tensorflow.lite.examples.modelpersonalization | grep permission

# Grant camera permission manually (jika perlu)
adb shell pm grant org.tensorflow.lite.examples.modelpersonalization android.permission.CAMERA
```

---

## 🔍 LANGKAH 9: Debug Common Issues

### Issue 1: Camera Not Working

```bash
# Check camera availability
adb shell ls -l /dev/video*

# Check camera service
adb shell dumpsys media.camera
```

### Issue 2: TensorFlow Lite Errors

```bash
# Filter TFLite specific errors
adb logcat | grep -i "tflite\|tensorflow\|interpreter"
```

### Issue 3: Model Loading Issues

```bash
# Check if model file exists in app
adb shell run-as org.tensorflow.lite.examples.modelpersonalization ls -lh /data/data/org.tensorflow.lite.examples.modelpersonalization/files/

# Check asset files
adb shell run-as org.tensorflow.lite.examples.modelpersonalization find /data/data/org.tensorflow.lite.examples.modelpersonalization/ -name "*.tflite"
```

### Issue 4: Memory Issues

```bash
# Monitor memory usage
adb shell dumpsys meminfo org.tensorflow.lite.examples.modelpersonalization

# Watch in real-time
watch -n 1 'adb shell dumpsys meminfo org.tensorflow.lite.examples.modelpersonalization | grep -A 20 "App Summary"'
```

---

## 📸 LANGKAH 10: Capture Screenshots & Videos

```bash
# Take screenshot
adb shell screencap -p /sdcard/screenshot.png
adb pull /sdcard/screenshot.png .

# Record screen (max 180 seconds)
adb shell screenrecord /sdcard/demo.mp4

# Stop recording: Press Ctrl+C
# Pull video
adb pull /sdcard/demo.mp4 .
```

---

## 🐛 Advanced Debugging Techniques

### 1. Enable Additional Logging

Edit `app/build.gradle`:
```gradle
android {
    buildTypes {
        debug {
            debuggable true
            minifyEnabled false
            buildConfigField "boolean", "ENABLE_VERBOSE_LOGGING", "true"
        }
    }
}
```

### 2. Network Debugging (jika perlu)

```bash
# Port forwarding untuk debugging
adb forward tcp:8080 tcp:8080

# Reverse port forwarding
adb reverse tcp:8080 tcp:8080
```

### 3. Interactive Shell

```bash
# Enter device shell
adb shell

# Navigate to app data
cd /data/data/org.tensorflow.lite.examples.modelpersonalization/

# Check files
ls -lR
```

---

## 🔧 Troubleshooting

### Problem: "adb: device unauthorized"

**Solution:**
```bash
# 1. Revoke USB debugging authorizations di phone
# Settings → Developer Options → Revoke USB debugging authorizations

# 2. Restart ADB
adb kill-server
adb start-server

# 3. Reconnect device - dialog akan muncul lagi
adb devices
```

---

### Problem: "usbipd: command not found" di Windows

**Solution:**
```powershell
# Reinstall usbipd-win
winget uninstall dorssel.usbipd-win
winget install --interactive --exact dorssel.usbipd-win

# Restart computer
```

---

### Problem: Device tidak terdeteksi di WSL setelah attach

**Solution:**
```bash
# 1. Di Windows PowerShell (Admin):
usbipd detach --busid 1-4
usbipd attach --wsl --busid 1-4

# 2. Di WSL:
lsusb
adb kill-server
adb start-server
adb devices
```

---

### Problem: "no permissions" error di WSL

**Solution:**
```bash
# Create udev rules
sudo tee /etc/udev/rules.d/51-android.rules > /dev/null <<'EOF'
SUBSYSTEM=="usb", ATTR{idVendor}=="18d1", MODE="0666", GROUP="plugdev"
SUBSYSTEM=="usb", ATTR{idVendor}=="04e8", MODE="0666", GROUP="plugdev"
EOF

sudo udevadm control --reload-rules
sudo udevadm trigger

# Add user to plugdev group
sudo usermod -aG plugdev $USER

# Logout & login WSL
exit
# (login kembali)
```

---

### Problem: Camera crash saat capture

**Check Logcat untuk:**
```bash
# Search untuk crash stack traces
adb logcat | grep -A 50 "FATAL\|AndroidRuntime"

# Check camera specific errors
adb logcat | grep -i "camera\|preview\|imageanalysis"

# TensorFlow Lite errors
adb logcat | grep -i "tflite\|interpreter\|READ_VARIABLE"
```

---

## 📋 Quick Reference Commands

```bash
# === Device Management ===
adb devices                          # List connected devices
adb shell getprop ro.build.version   # Android version
adb shell getprop ro.product.model   # Device model

# === App Management ===
adb install -r app.apk               # Install/reinstall app
adb uninstall <package>              # Uninstall app
adb shell pm list packages           # List all packages
adb shell pm clear <package>         # Clear app data

# === Logging ===
adb logcat -c                        # Clear logs
adb logcat -d                        # Dump and exit
adb logcat -v time                   # With timestamps
adb logcat -f /sdcard/log.txt        # Save to file on device

# === File Transfer ===
adb push local_file /sdcard/         # Copy to device
adb pull /sdcard/file local_dir/     # Copy from device

# === Process Management ===
adb shell ps | grep <package>        # Find process
adb shell am force-stop <package>    # Stop app
adb shell am start <package>/.MainActivity  # Start app

# === USB Management (Windows PowerShell) ===
usbipd list                          # List USB devices
usbipd bind --busid X-X              # Bind device
usbipd attach --wsl --busid X-X      # Attach to WSL
usbipd detach --busid X-X            # Detach from WSL
```

---

## 🎯 Workflow untuk Debug Session

```bash
# TERMINAL 1: Monitor Logcat
adb logcat -v time | grep -E "TransferLearning|Camera|TensorFlow|ERROR"

# TERMINAL 2: Development Commands
cd /root/test/examples/lite/examples/model_personalization/android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n org.tensorflow.lite.examples.modelpersonalization/.MainActivity

# TERMINAL 3: System monitoring (optional)
watch -n 2 'adb shell dumpsys meminfo org.tensorflow.lite.examples.modelpersonalization | grep -A 5 "App Summary"'
```

---

## 📞 Siap Debug!

Setelah setup di atas, saya akan bisa melihat:
- ✅ Real-time logcat output
- ✅ Error messages dan stack traces
- ✅ TensorFlow Lite initialization logs
- ✅ Camera operations
- ✅ Model loading/inference logs
- ✅ Memory usage
- ✅ App crashes

**Silakan jalankan langkah-langkah di atas, lalu:**
1. Paste output `adb devices` untuk konfirmasi koneksi
2. Launch app dan coba ambil gambar
3. Copy & paste logcat output yang muncul

Saya akan analisa error spesifiknya dan berikan solusi! 🚀

---

**Pro Tips:**
- Gunakan `Ctrl+C` untuk stop logcat monitoring
- Gunakan `adb shell input keyevent KEYCODE_HOME` untuk home button via command
- Gunakan `adb shell input tap X Y` untuk simulate tap
- Save logcat output penting ke file untuk analisa mendalam

