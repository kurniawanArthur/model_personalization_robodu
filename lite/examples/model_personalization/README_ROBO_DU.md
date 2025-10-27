# 🤖 ROBO-DU Model Personalization

Enhanced version of TensorFlow Lite Model Personalization with additional features and custom branding.

---

## 🆕 What's New in ROBO-DU Version

### ✨ **New Features:**
1. **🎨 ROBO-DU Branding** - Custom orange branding with "ROBO-DU" title
2. **💾 Model Persistence** - Auto-save/load trained models
3. **🏷️ Custom Class Names** - Long-press buttons to rename classes
4. **📊 Training Progress** - Visual progress bar and epoch counter
5. **🗂️ Sample Gallery** - View and manage training samples (Layout ready)

### 🆚 **Comparison with Original:**

| Feature | Original TF Demo | ROBO-DU Enhanced |
|---------|-----------------|------------------|
| Branding | TensorFlow Lite | **ROBO-DU** |
| Model Persistence | ❌ No | ✅ **Auto-save/load** |
| Class Names | "1", "2", "3", "4" | ✅ **Customizable** |
| Training Progress | Loss only | ✅ **Progress bar + Epoch** |
| Sample Management | None | ✅ **Gallery ready** |

---

## 🚀 Quick Start

### **Option 1: Use Build Script (Easiest)**

```bash
# Navigate to project folder
cd "a:/One Drive/OneDrive/Documents/Vision Programmer_RoboDu/examples-master/examples-master/lite/examples/model_personalization"

# Run build script
./build.sh

# Follow prompts to install and launch
```

### **Option 2: Manual Build**

```bash
# Navigate to android folder
cd android/

# Build APK
./gradlew.bat assembleDebug

# Install to device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📱 How to Use

### **1. Basic Workflow:**
```
1. Open app → See "ROBO-DU" branding
2. Capture samples by pressing class buttons
3. Long-press button to rename class (e.g., "Cat", "Dog")
4. Click "Train" → Watch progress bar
5. Pause training → Model auto-saves ✅
6. Close app
7. Re-open → Model auto-loads ✅
8. Switch to Inference mode → Test predictions
```

### **2. Custom Class Names:**
```
1. LONG-PRESS any class button (1, 2, 3, or 4)
2. Dialog appears with current name
3. Type new name (e.g., "My Cat")
4. Click "Save"
5. ✅ Button text updated!
```

### **3. Model Persistence:**
```
Automatic! No action needed.

- Training auto-saves when paused
- Model auto-loads on app start
- Your training is never lost!
```

---

## 📁 Project Structure

```
model_personalization/
├── android/                          # Android app
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/.../
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── MainViewModel.kt        # ✨ Enhanced
│   │   │   │   ├── TransferLearningHelper.kt  # ✨ Enhanced
│   │   │   │   └── fragments/
│   │   │   │       └── CameraFragment.kt   # ✨ Enhanced
│   │   │   └── res/
│   │   │       ├── layout/
│   │   │       │   ├── activity_main.xml   # ✨ New branding
│   │   │       │   ├── fragment_camera.xml # ✨ Progress bar
│   │   │       │   ├── dialog_edit_class_name.xml  # ✨ New
│   │   │       │   └── fragment_sample_gallery.xml  # ✨ New
│   │   │       ├── values/
│   │   │       │   ├── strings.xml         # ✨ ROBO-DU strings
│   │   │       │   └── colors.xml          # ✨ Brand colors
│   │   └── build.gradle
│   └── build.gradle
├── transfer_learning/                # Python model generation
│   ├── generate_training_model.py
│   └── requirements.txt
├── build.sh                          # ✨ Quick build script
├── IMPROVEMENTS_SUMMARY.md           # ✨ Feature docs
├── BRANDING_UPDATE.md                # ✨ Branding docs
├── VISUAL_PREVIEW.md                 # ✨ Visual guide
└── README_ROBO_DU.md                 # ✨ This file
```

---

## 🔧 Requirements

- **Android Studio** 2021.2.1+ (Chipmunk or newer)
- **Android SDK** API Level 23+ (Android 6.0+)
- **JDK** 8 or higher
- **Physical Android device** with camera
- **USB Debugging** enabled on device

---

## 📚 Documentation

- **IMPROVEMENTS_SUMMARY.md** - Complete list of new features
- **BRANDING_UPDATE.md** - ROBO-DU branding details
- **VISUAL_PREVIEW.md** - Visual design preview
- **Original README.md** - TensorFlow Lite documentation

---

## 🎯 Key Features Explained

### 1️⃣ **Model Persistence**
Your trained model is automatically saved to:
```
/data/data/org.tensorflow.lite.examples.modelpersonalization/files/trained_models/
```

### 2️⃣ **Custom Class Names**
Stored in ViewModel and can be extended to SharedPreferences for persistence across app restarts.

### 3️⃣ **Training Progress**
- Visual progress bar (0-100%)
- Epoch counter shows current training epoch
- Loss value updates in real-time

### 4️⃣ **Sample Gallery**
Layout ready for displaying and managing captured training samples.

---

## 🐛 Troubleshooting

### **Build Errors:**
```bash
# Clean and rebuild
cd android/
./gradlew.bat clean assembleDebug
```

### **Installation Failed:**
```bash
# Uninstall old version
adb uninstall org.tensorflow.lite.examples.modelpersonalization

# Reinstall
adb install app/build/outputs/apk/debug/app-debug.apk
```

### **Device Not Found:**
```bash
# Check ADB connection
adb devices

# Enable USB debugging on phone:
Settings → Developer Options → USB Debugging
```

### **Model Not Loading:**
Check logs:
```bash
adb logcat | grep -i "ModelPersonalization"
```

---

## 📊 Performance

- **Model Size:** ~14MB (MobileNetV2)
- **Training Time:** ~2-5 minutes for 100 samples
- **Inference Time:** ~30-50ms per frame
- **Storage:** ~500KB per 100 samples

---

## 🤝 Credits

**Original Project:** TensorFlow Lite Examples  
**Enhanced By:** ROBO-DU Team  
**Version:** 1.1 Enhanced  

**Key Enhancements:**
- Model persistence architecture
- Custom class naming system
- Training progress visualization
- ROBO-DU branding and UI improvements

---

## 📝 License

Same as original TensorFlow Lite examples - Apache License 2.0

---

## 🚀 Next Steps

After installation:

1. **Test Model Persistence:**
   - Train model → Pause → Close app → Reopen
   - Verify model loads automatically

2. **Test Custom Names:**
   - Long-press each button
   - Rename to meaningful names
   - Verify names display correctly

3. **Test Progress Bar:**
   - Start training
   - Watch progress bar and epoch counter
   - Verify loss updates

4. **Share Feedback:**
   - Report bugs or suggestions
   - Request additional features

---

## 🎉 Enjoy Your Enhanced ROBO-DU App!

Built with ❤️ for better on-device machine learning experience.

**Questions?** Check the detailed documentation in:
- `IMPROVEMENTS_SUMMARY.md`
- `BRANDING_UPDATE.md`
- `VISUAL_PREVIEW.md`

---

**Ready to build?** Run `./build.sh` and see the magic! ✨

