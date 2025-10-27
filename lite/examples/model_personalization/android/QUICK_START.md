# 🎯 Quick Start - Debug Android di WSL

## ⚡ Cara Cepat (3 Langkah)

### 1️⃣ Setup di Windows (PowerShell Administrator)

```powershell
# Install usbipd (hanya sekali)
winget install --interactive --exact dorssel.usbipd-win

# Setiap kali colok device:
usbipd list                    # Lihat BUSID device Android
usbipd bind --busid 1-4        # Ganti 1-4 dengan BUSID Anda
usbipd attach --wsl --busid 1-4
```

### 2️⃣ Setup di WSL Ubuntu (hanya sekali)

```bash
cd /root/test/examples/lite/examples/model_personalization/android
./setup_debug.sh
```

### 3️⃣ Deploy & Debug

```bash
# Deploy app
./deploy_app.sh

# Monitor logs (di terminal terpisah)
./start_logcat.sh
```

---

## 📋 Commands Penting

```bash
# Check koneksi device
adb devices

# Install app
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch app
adb shell am start -n org.tensorflow.lite.examples.modelpersonalization/.MainActivity

# Monitor logs
adb logcat | grep -E "TransferLearning|Camera|ERROR"

# Clear app data
adb shell pm clear org.tensorflow.lite.examples.modelpersonalization
```

---

## 🔧 Troubleshooting

### Device tidak terdeteksi di WSL?

**Windows (PowerShell Admin):**
```powershell
usbipd detach --busid 1-4
usbipd attach --wsl --busid 1-4
```

**WSL:**
```bash
lsusb                    # Harus tampil device
adb kill-server
adb start-server
adb devices              # Harus tampil device
```

### "unauthorized" di adb devices?

1. Cek layar Android - ada dialog "Allow USB debugging?"
2. Centang "Always allow"
3. Tap OK
4. Jalankan `adb devices` lagi

### App crash saat ambil gambar?

```bash
# Jalankan logcat untuk lihat error
./start_logcat.sh

# Atau save ke file
adb logcat > crash_log.txt
```

---

## 📞 Siap untuk Debug!

**Workflow:**
1. Colok USB Android ke komputer
2. Di Windows: `usbipd attach --wsl --busid 1-4`
3. Di WSL: `./deploy_app.sh`
4. Di terminal lain: `./start_logcat.sh`
5. Test app di phone
6. Copy error dari logcat
7. Paste ke sini untuk analisa

**Files:**
- 📖 `DEBUG_GUIDE_WSL.md` - Panduan lengkap
- 🔧 `setup_debug.sh` - Setup otomatis
- 📊 `start_logcat.sh` - Monitor logs
- 🚀 `deploy_app.sh` - Deploy app

Mari mulai! 🚀
