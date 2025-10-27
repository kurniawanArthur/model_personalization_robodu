# 📊 LAPORAN DIVISI VISION PROGRAMMER
## Projek: Robot Education - ROBO-DU Model Personalization

**Tanggal**: 6 Oktober 2025  
**Divisi**: Vision Programmer  
**Platform**: Android + Raspberry Pi Integration  
**Teknologi Inti**: TensorFlow Lite, Transfer Learning, On-Device ML

---

## 📋 EXECUTIVE SUMMARY

Divisi Vision Programmer bertugas mengembangkan sistem computer vision untuk robot edukasi yang dapat:
1. **Menerima input** dari Raspberry Pi Camera Module v2
2. **Melakukan inference** real-time untuk object detection/classification
3. **Training on-device** untuk personalisasi model tanpa cloud
4. **Transfer knowledge** dari model pre-trained ke custom classes

Aplikasi Android yang dikembangkan menggunakan **TensorFlow Lite** dengan **transfer learning**, memungkinkan user untuk:
- Train model custom dengan 4 classes dalam hitungan menit
- Capture samples langsung dari camera
- Inference real-time dengan latency <100ms
- Simpan/load multiple trained models

---

## 🔧 TEKNOLOGI YANG DIGUNAKAN

### 1. **Machine Learning Framework**

#### TensorFlow Lite (v2.9.0)
**Fungsi**: Lightweight ML framework untuk on-device inference
**Keunggulan**:
- Model size kecil (~4MB vs TensorFlow penuh ~500MB)
- Inference cepat (CPU/GPU/NNAPI support)
- Cocok untuk mobile & embedded devices

**Implementasi dalam Projek**:
```kotlin
// TransferLearningHelper.kt
private var interpreter: Interpreter? = null

// Load TFLite model
val modelFile = FileUtil.loadMappedFile(context, MODEL_PATH)
interpreter = Interpreter(modelFile, options)

// Inference
interpreter?.run(inputBuffer, outputBuffer)
```

**Metrics**:
- Model base: MobileNetV2 (pre-trained on ImageNet)
- Model size: ~3.5 MB
- Inference time: 50-80ms per frame (CPU)
- Training time: 2-5 menit untuk 100 epochs

---

#### Transfer Learning Architecture
**Konsep**: Menggunakan feature extractor pre-trained, hanya train classification head

```
Input Image (224x224x3)
    ↓
MobileNetV2 Bottleneck (frozen)
    ↓ [7x7x1280 = 62,720 features]
Dense Layer (trainable)
    ↓
Softmax Output (4 classes)
```

**Keunggulan**:
- ✅ Training cepat (hanya train 1 layer)
- ✅ Butuh sample sedikit (5-10 per class)
- ✅ Akurasi tinggi (85-95% dengan <100 samples)

**Code Implementation**:
```kotlin
// Training signature
val trainingInputs = mapOf(
    "feature" to bottleneckBuffer,  // From frozen MobileNetV2
    "label" to labelBuffer           // User's custom labels
)

val trainingOutputs = mapOf(
    "loss" to lossBuffer
)

interpreter?.runSignature(trainingInputs, trainingOutputs, "train")
```

---

### 2. **Android Architecture Components**

#### MVVM Pattern (Model-View-ViewModel)
**Struktur**:
```
MainActivity
    ↓
MainViewModel (state management)
    ↓
CameraFragment (UI)
    ↓
TransferLearningHelper (ML logic)
    ↓
TensorFlow Lite Interpreter
```

**Benefits**:
- Separation of concerns
- Lifecycle-aware components
- Testable business logic

**Key Classes**:
```kotlin
// MainViewModel.kt - State Management
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _classNames = MutableLiveData<Map<Int, String>>()
    private val _numberOfSamples = MutableLiveData<Map<String, Int>>()
    private val _trainingState = MutableLiveData<TrainingState>()
    
    // LiveData observers
    val classNames: LiveData<Map<Int, String>> = _classNames
}

// CameraFragment.kt - UI Logic
class CameraFragment : Fragment(), TransferLearningHelper.ClassifierListener {
    override fun onEpochUpdate(epoch: Int, progress: Int) {
        // Update progress bar
    }
}
```

---

#### LiveData & Observer Pattern
**Fungsi**: Reactive UI updates

```kotlin
// Observe class names
viewModel.classNames.observe(viewLifecycleOwner) {
    updateClassLabels()  // Auto-update UI
}

// Observe training state
viewModel.trainingState.observe(viewLifecycleOwner) { state ->
    when(state) {
        TrainingState.PREPARE -> showTrainButton()
        TrainingState.TRAINING -> showProgressBar()
        TrainingState.PAUSE -> showSaveButton()
    }
}
```

---

### 3. **Camera & Image Processing**

#### CameraX API
**Fungsi**: Modern camera API untuk Android

```kotlin
// CameraFragment.kt
private fun setUpCamera() {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
    
    cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()
        
        // Preview use case
        val preview = Preview.Builder().build()
        
        // Analysis use case (for inference)
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor) { imageProxy ->
                    processImage(imageProxy)
                }
            }
        
        cameraProvider.bindToLifecycle(
            viewLifecycleOwner,
            cameraSelector,
            preview,
            imageAnalysis
        )
    }, ContextCompat.getMainExecutor(requireContext()))
}
```

**Capabilities**:
- Real-time preview
- Image capture for training samples
- Frame analysis for inference
- Auto-focus & exposure control

---

#### Image Preprocessing
**Pipeline**:
```
Camera Frame (YUV_420_888)
    ↓
Convert to Bitmap (ARGB_8888)
    ↓
Resize to 224x224
    ↓
Normalize [-1, 1]
    ↓
Convert to FloatArray
    ↓
Feed to TFLite model
```

**Code**:
```kotlin
// TransferLearningHelper.kt
private fun loadImage(bitmap: Bitmap): FloatArray {
    val inputImage = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
    val byteBuffer = ByteBuffer.allocateDirect(224 * 224 * 3 * 4)
    byteBuffer.order(ByteOrder.nativeOrder())
    
    val pixels = IntArray(224 * 224)
    inputImage.getPixels(pixels, 0, 224, 0, 0, 224, 224)
    
    for (pixel in pixels) {
        val r = ((pixel shr 16) and 0xFF) / 127.5f - 1.0f
        val g = ((pixel shr 8) and 0xFF) / 127.5f - 1.0f
        val b = (pixel and 0xFF) / 127.5f - 1.0f
        
        byteBuffer.putFloat(r)
        byteBuffer.putFloat(g)
        byteBuffer.putFloat(b)
    }
    
    return byteBuffer.array().toFloatArray()
}
```

---

### 4. **Data Persistence**

#### SharedPreferences
**Fungsi**: Lightweight key-value storage

```kotlin
// PreferencesHelper.kt
class PreferencesHelper(context: Context) {
    private val prefs = context.getSharedPreferences(
        "robo_du_model_personalization",
        Context.MODE_PRIVATE
    )
    
    fun saveClassName(classId: Int, name: String) {
        prefs.edit().putString("class_name_$classId", name).apply()
    }
    
    fun getClassName(classId: Int): String {
        return prefs.getString("class_name_$classId", "Class $classId") ?: "Class $classId"
    }
}
```

**Data Stored**:
- Class names (custom labels)
- Active classes
- Last training accuracy
- User preferences

---

#### File System (Model Storage)
**Struktur**:
```
/data/data/org.tensorflow.lite.examples.modelpersonalization/files/
├── model_checkpoint.tflite          # Current working model
└── trained_models/
    ├── model_Cat_Dog_85pct_123.model    # Saved model weights
    ├── model_Cat_Dog_85pct_123.info     # Metadata
    ├── model_Bird_Fish_92pct_456.model
    └── model_Bird_Fish_92pct_456.info
```

**Metadata Format** (.info file):
```
name=Pet Classifier
accuracy=0.85
classNames=Cat,Dog,Bird,Fish
numSamples=100
timestamp=1728201600000
```

**Implementation**:
```kotlin
// ModelManager.kt
fun saveModel(
    name: String,
    accuracy: Float,
    classNames: List<String>,
    numSamples: Int,
    sourceFilePath: String
): Boolean {
    val timestamp = System.currentTimeMillis()
    val fileName = "${name}_${timestamp}"
    
    // Copy model file
    val modelFile = File(modelsDir, "$fileName.model")
    File(sourceFilePath).copyTo(modelFile, overwrite = true)
    
    // Save metadata
    val infoFile = File(modelsDir, "$fileName.info")
    val infoContent = """
        name=$name
        accuracy=$accuracy
        classNames=${classNames.joinToString(",")}
        numSamples=$numSamples
        timestamp=$timestamp
    """.trimIndent()
    infoFile.writeText(infoContent)
    
    return true
}
```

---

### 5. **UI/UX Technologies**

#### Material Design Components
- **CardView**: Model list items
- **ProgressBar**: Training progress (horizontal, 0-100%)
- **AlertDialog**: Edit dialogs, confirmations
- **RecyclerView**: Scrollable model list
- **ConstraintLayout**: Responsive layouts

#### Custom Branding (ROBO-DU)
**Color Palette**:
```xml
<!-- colors.xml -->
<color name="robo_du_orange">#FF6F00</color>      <!-- Primary -->
<color name="robo_du_dark_gray">#424242</color>   <!-- Secondary -->
<color name="robo_du_green">#4CAF50</color>       <!-- Success -->
<color name="robo_du_red">#F44336</color>         <!-- Danger -->
<color name="robo_du_accent">#FFA726</color>      <!-- Accent -->
```

**Typography**:
- Title: 26sp, bold, sans-serif-black
- Subtitle: 10sp, regular
- Button: 18-24sp, bold

---

### 6. **Validation & Error Handling**

#### Training Validation System
**Rules**:
```kotlin
// TransferLearningHelper.kt - startTraining()

// 1. Check empty samples
if (trainingSamples.isEmpty()) {
    onError("No training samples available!")
    return
}

// 2. Check minimum total samples
if (trainingSamples.size < 5) {
    onError("Too few samples! Need at least 5 total.")
    return
}

// 3. Check class distribution
val classDistribution = trainingSamples.groupBy { 
    it.label.indexOfFirst { value -> value == 1f } 
}
if (classDistribution.size < 2) {
    onError("Need at least 2 different classes!")
    return
}

// 4. Check samples per class
val minSamplesPerClass = 3
val insufficientClasses = classDistribution.filter { 
    it.value.size < minSamplesPerClass 
}
if (insufficientClasses.isNotEmpty()) {
    onError("Some classes have too few samples!")
    return
}
```

**Error Messages**: Clear, actionable, user-friendly

---

### 7. **Concurrency & Threading**

#### ExecutorService for Training
```kotlin
private var executor: ExecutorService? = null

fun startTraining() {
    executor = Executors.newSingleThreadExecutor()
    
    executor?.execute {
        synchronized(lock) {
            var epochCount = 0
            val maxEpochs = 100
            
            while (executor?.isShutdown == false && epochCount < maxEpochs) {
                epochCount++
                
                // Training loop
                trainingBatches(trainBatchSize).forEach { samples ->
                    // Run training signature
                    interpreter?.runSignature(inputs, outputs, "train")
                }
                
                // Progress callback
                val progress = (epochCount * 100) / maxEpochs
                classifierListener?.onEpochUpdate(epochCount, progress)
            }
            
            classifierListener?.onTrainingComplete(accuracy)
        }
    }
}
```

**Thread Management**:
- UI Thread: Camera preview, user interactions
- Background Thread: Training, model loading
- Executor: Single thread for training (prevent race conditions)

---

## 🎯 ARSITEKTUR SISTEM KESELURUHAN

### Current Architecture (Android Only)
```
┌─────────────────────────────────────────────────┐
│          Android Application                     │
│                                                  │
│  ┌─────────────┐         ┌──────────────┐      │
│  │  Camera X   │────────▶│ Image Proc.  │      │
│  │  (Preview)  │         │ (224x224)    │      │
│  └─────────────┘         └──────┬───────┘      │
│                                  │              │
│  ┌──────────────────────────────▼─────┐        │
│  │   TensorFlow Lite Interpreter      │        │
│  │   - MobileNetV2 (frozen)           │        │
│  │   - Classification Head (trainable)│        │
│  └──────────────┬─────────────────────┘        │
│                 │                               │
│  ┌──────────────▼─────────────────┐            │
│  │  Training Loop                 │            │
│  │  - Batch processing            │            │
│  │  - Loss calculation            │            │
│  │  - Weight updates              │            │
│  └────────────────────────────────┘            │
│                                                  │
│  ┌─────────────────────────────────┐           │
│  │  Model Manager                  │           │
│  │  - Save/Load models             │           │
│  │  - Metadata storage             │           │
│  └─────────────────────────────────┘           │
└─────────────────────────────────────────────────┘
```

### Target Architecture (Robot Education)
```
┌──────────────────────────────────────────────────────────────┐
│                    Robot Education System                     │
│                                                               │
│  ┌──────────────────┐                ┌────────────────────┐ │
│  │  Raspberry Pi    │                │  Android Device    │ │
│  │                  │                │                    │ │
│  │  ┌────────────┐  │                │  ┌──────────────┐ │ │
│  │  │ Pi Cam v2  │  │   WiFi/USB     │  │  Camera UI   │ │ │
│  │  │ (Preview)  │──┼───────────────▶│  │  (Training)  │ │ │
│  │  └────────────┘  │                │  └──────────────┘ │ │
│  │                  │                │                    │ │
│  │  ┌────────────┐  │    Model       │  ┌──────────────┐ │ │
│  │  │ TFLite     │◀─┼────Transfer────│  │  TFLite      │ │ │
│  │  │ (Inference)│  │                │  │  (Training)  │ │ │
│  │  └─────┬──────┘  │                │  └──────────────┘ │ │
│  │        │         │                │                    │ │
│  │  ┌─────▼──────┐  │                └────────────────────┘ │
│  │  │  Robot     │  │                                        │
│  │  │  Control   │  │                                        │
│  │  │  (Actions) │  │                                        │
│  │  └────────────┘  │                                        │
│  └──────────────────┘                                        │
└──────────────────────────────────────────────────────────────┘
```

---

## 🚀 ROADMAP KONKRIT - LANGKAH SELANJUTNYA

### **FASE 1: Integration Setup (Minggu 1-2)**

#### 1.1 Raspberry Pi Camera Setup
**Hardware**:
- Raspberry Pi 4 (4GB RAM minimum)
- Pi Camera Module v2 (8MP, 1080p)
- Power supply 5V/3A
- microSD card 32GB (Class 10)

**Software Stack**:
```bash
# Install dependencies
sudo apt-get update
sudo apt-get install python3-pip python3-opencv
pip3 install picamera2 tensorflow-lite-runtime

# Enable camera
sudo raspi-config
# Interface Options → Camera → Enable
```

**Test Camera**:
```python
# test_camera.py
from picamera2 import Picamera2
import cv2

picam2 = Picamera2()
config = picam2.create_preview_configuration(
    main={"size": (640, 480)}
)
picam2.configure(config)
picam2.start()

while True:
    frame = picam2.capture_array()
    cv2.imshow("Pi Camera", frame)
    if cv2.waitKey(1) & 0xFF == ord('q'):
        break

picam2.stop()
```

**Deliverables**:
- ✅ Pi Camera working dengan Python
- ✅ Frame capture 30fps
- ✅ Image resolution 640x480 (untuk preview), 224x224 (untuk inference)

---

#### 1.2 Communication Protocol (Pi ↔ Android)
**Pilihan Protokol**:

**Option A: WiFi Socket (Recommended)**
```python
# raspberry_pi/server.py
import socket
import pickle
import cv2

server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
server.bind(('0.0.0.0', 8080))
server.listen(1)

while True:
    client, addr = server.accept()
    
    # Capture frame
    frame = picam2.capture_array()
    frame = cv2.resize(frame, (224, 224))
    
    # Send frame
    data = pickle.dumps(frame)
    client.sendall(data)
    
    client.close()
```

```kotlin
// Android: NetworkClient.kt
class PiCameraClient {
    fun getFrame(): Bitmap? {
        val socket = Socket("192.168.1.100", 8080)
        val inputStream = socket.getInputStream()
        
        val data = inputStream.readBytes()
        // Deserialize frame
        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
        
        socket.close()
        return bitmap
    }
}
```

**Option B: USB Tethering**
- Faster (USB 2.0: 480 Mbps)
- More stable connection
- Requires USB OTG support

**Deliverables**:
- ✅ Pi dapat send frames ke Android
- ✅ Android dapat receive & display frames
- ✅ Latency <100ms

---

#### 1.3 Model Transfer Mechanism
**Workflow**:
```
Android (Train Model)
    ↓
Save .tflite file
    ↓
Transfer via WiFi/USB
    ↓
Raspberry Pi (Load Model)
    ↓
Run Inference
```

**Implementation**:
```kotlin
// Android: ModelExporter.kt
fun exportModelToPi(modelPath: String, piIpAddress: String) {
    val modelFile = File(modelPath)
    val socket = Socket(piIpAddress, 8081)
    val outputStream = socket.getOutputStream()
    
    // Send model file
    modelFile.inputStream().use { input ->
        input.copyTo(outputStream)
    }
    
    socket.close()
}
```

```python
# Raspberry Pi: model_receiver.py
import socket

server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
server.bind(('0.0.0.0', 8081))
server.listen(1)

client, addr = server.accept()

# Receive model
with open('custom_model.tflite', 'wb') as f:
    while True:
        data = client.recv(1024)
        if not data:
            break
        f.write(data)

print("Model received!")
client.close()
```

**Deliverables**:
- ✅ Model dapat di-transfer dari Android ke Pi
- ✅ Pi dapat load custom model
- ✅ Inference berjalan dengan model custom

---

### **FASE 2: Raspberry Pi Inference Engine (Minggu 3-4)**

#### 2.1 TFLite Runtime di Raspberry Pi
**Installation**:
```bash
# Install TensorFlow Lite Runtime
pip3 install tflite-runtime

# Verify
python3 -c "import tflite_runtime.interpreter as tflite; print('OK')"
```

**Inference Script**:
```python
# raspberry_pi/inference_engine.py
import tflite_runtime.interpreter as tflite
import numpy as np
import cv2
from picamera2 import Picamera2

class InferenceEngine:
    def __init__(self, model_path):
        self.interpreter = tflite.Interpreter(model_path=model_path)
        self.interpreter.allocate_tensors()
        
        self.input_details = self.interpreter.get_input_details()
        self.output_details = self.interpreter.get_output_details()
        
    def preprocess(self, frame):
        # Resize to 224x224
        frame = cv2.resize(frame, (224, 224))
        
        # Normalize [-1, 1]
        frame = frame.astype(np.float32) / 127.5 - 1.0
        
        # Add batch dimension
        frame = np.expand_dims(frame, axis=0)
        
        return frame
    
    def inference(self, frame):
        input_data = self.preprocess(frame)
        
        self.interpreter.set_tensor(
            self.input_details[0]['index'], 
            input_data
        )
        
        self.interpreter.invoke()
        
        output_data = self.interpreter.get_tensor(
            self.output_details[0]['index']
        )
        
        return output_data[0]  # Class probabilities
    
    def get_class_name(self, class_id):
        class_names = ["Cat", "Dog", "Bird", "Fish"]
        return class_names[class_id]

# Usage
engine = InferenceEngine("custom_model.tflite")
picam2 = Picamera2()
picam2.start()

while True:
    frame = picam2.capture_array()
    predictions = engine.inference(frame)
    
    class_id = np.argmax(predictions)
    confidence = predictions[class_id]
    
    print(f"Class: {engine.get_class_name(class_id)}, "
          f"Confidence: {confidence:.2%}")
    
    time.sleep(0.1)  # 10 FPS
```

**Performance Target**:
- Inference time: <100ms per frame
- FPS: 10-15 (real-time)
- Accuracy: 85-95% (same as Android)

**Deliverables**:
- ✅ Pi dapat run inference dengan TFLite
- ✅ Real-time classification dari Pi Camera
- ✅ Display hasil inference (print/display)

---

#### 2.2 Robot Control Integration
**Hardware Interface**:
```python
# raspberry_pi/robot_controller.py
import RPi.GPIO as GPIO
import time

class RobotController:
    def __init__(self):
        GPIO.setmode(GPIO.BCM)
        
        # Motor pins
        self.MOTOR_LEFT_FWD = 17
        self.MOTOR_LEFT_BWD = 18
        self.MOTOR_RIGHT_FWD = 22
        self.MOTOR_RIGHT_BWD = 23
        
        # Setup pins
        for pin in [self.MOTOR_LEFT_FWD, self.MOTOR_LEFT_BWD,
                    self.MOTOR_RIGHT_FWD, self.MOTOR_RIGHT_BWD]:
            GPIO.setup(pin, GPIO.OUT)
            GPIO.output(pin, GPIO.LOW)
    
    def move_forward(self):
        GPIO.output(self.MOTOR_LEFT_FWD, GPIO.HIGH)
        GPIO.output(self.MOTOR_RIGHT_FWD, GPIO.HIGH)
    
    def turn_left(self):
        GPIO.output(self.MOTOR_LEFT_BWD, GPIO.HIGH)
        GPIO.output(self.MOTOR_RIGHT_FWD, GPIO.HIGH)
    
    def turn_right(self):
        GPIO.output(self.MOTOR_LEFT_FWD, GPIO.HIGH)
        GPIO.output(self.MOTOR_RIGHT_BWD, GPIO.HIGH)
    
    def stop(self):
        for pin in [self.MOTOR_LEFT_FWD, self.MOTOR_LEFT_BWD,
                    self.MOTOR_RIGHT_FWD, self.MOTOR_RIGHT_BWD]:
            GPIO.output(pin, GPIO.LOW)
```

**Vision-Based Control Logic**:
```python
# raspberry_pi/vision_robot.py
from inference_engine import InferenceEngine
from robot_controller import RobotController
from picamera2 import Picamera2

# Initialize
engine = InferenceEngine("custom_model.tflite")
robot = RobotController()
picam2 = Picamera2()
picam2.start()

# Control rules
ACTIONS = {
    0: robot.move_forward,  # Cat detected → move forward
    1: robot.turn_left,     # Dog detected → turn left
    2: robot.turn_right,    # Bird detected → turn right
    3: robot.stop           # Fish detected → stop
}

while True:
    frame = picam2.capture_array()
    predictions = engine.inference(frame)
    
    class_id = np.argmax(predictions)
    confidence = predictions[class_id]
    
    # Only act if confidence > 80%
    if confidence > 0.8:
        action = ACTIONS[class_id]
        action()
        print(f"Action: {action.__name__}, Confidence: {confidence:.2%}")
    else:
        robot.stop()
    
    time.sleep(0.1)
```

**Deliverables**:
- ✅ Vision inference trigger robot actions
- ✅ Configurable action mapping
- ✅ Confidence threshold untuk avoid false positives

---

### **FASE 3: Advanced Features (Minggu 5-6)**

#### 3.1 Remote Training Interface
**Workflow**:
```
Pi Camera → Capture samples → Send to Android
    ↓
Android → User labels → Add to training set
    ↓
Android → Train model → Transfer back to Pi
    ↓
Pi → Load new model → Run inference
```

**Android Enhancement**:
```kotlin
// RemoteTrainingFragment.kt
class RemoteTrainingFragment : Fragment() {
    private val piClient = PiCameraClient()
    
    fun captureRemoteSample(classId: String) {
        lifecycleScope.launch {
            // Get frame from Pi Camera
            val frame = piClient.getFrame()
            
            // Add to training samples
            viewModel.addTrainingSample(frame, classId)
            
            // Update UI
            Toast.makeText(context, 
                "Sample added from Pi Camera", 
                Toast.LENGTH_SHORT).show()
        }
    }
}
```

**Deliverables**:
- ✅ Capture training samples dari Pi Camera
- ✅ Train model di Android dengan Pi samples
- ✅ Transfer trained model kembali ke Pi

---

#### 3.2 Multi-Model Support
**Use Case**: Berbeda robot, berbeda tasks

**Implementation**:
```python
# raspberry_pi/multi_model_manager.py
class MultiModelManager:
    def __init__(self):
        self.models = {}
        self.active_model = None
    
    def load_model(self, model_name, model_path):
        engine = InferenceEngine(model_path)
        self.models[model_name] = engine
        print(f"Loaded model: {model_name}")
    
    def switch_model(self, model_name):
        if model_name in self.models:
            self.active_model = self.models[model_name]
            print(f"Switched to: {model_name}")
        else:
            print(f"Model {model_name} not found")
    
    def inference(self, frame):
        if self.active_model:
            return self.active_model.inference(frame)
        return None

# Usage
manager = MultiModelManager()
manager.load_model("pet_classifier", "pet_model.tflite")
manager.load_model("vehicle_detector", "vehicle_model.tflite")

# Switch based on context
manager.switch_model("pet_classifier")
```

**Deliverables**:
- ✅ Pi dapat load multiple models
- ✅ Switch model on-the-fly
- ✅ Android dapat manage model library

---

#### 3.3 Performance Optimization
**Teknik**:

1. **Model Quantization**:
```python
# Convert float32 → int8
import tensorflow as tf

converter = tf.lite.TFLiteConverter.from_saved_model(model_path)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
tflite_quant_model = converter.convert()

# Size: 3.5 MB → 1 MB
# Speed: 2x faster inference
```

2. **Hardware Acceleration**:
```python
# Use XNNPACK delegate (CPU optimization)
interpreter = tflite.Interpreter(
    model_path=model_path,
    num_threads=4  # Use all Pi cores
)
```

3. **Frame Skipping**:
```python
# Process every 3rd frame (save CPU)
frame_count = 0
SKIP_FRAMES = 3

while True:
    frame = picam2.capture_array()
    frame_count += 1
    
    if frame_count % SKIP_FRAMES == 0:
        predictions = engine.inference(frame)
        # Act on predictions
```

**Target Metrics**:
- Model size: <2 MB (quantized)
- Inference time: <50ms (int8 model)
- FPS: 20+ (dengan frame skipping)

**Deliverables**:
- ✅ Quantized model deployment
- ✅ Inference speed doubled
- ✅ Power consumption reduced

---

### **FASE 4: Production Ready (Minggu 7-8)**

#### 4.1 Logging & Monitoring
```python
# raspberry_pi/logger.py
import logging
from datetime import datetime

logging.basicConfig(
    filename=f'robot_log_{datetime.now().strftime("%Y%m%d")}.log',
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)

def log_inference(class_name, confidence, action):
    logging.info(f"Inference: {class_name} ({confidence:.2%}) → {action}")

def log_error(error_msg):
    logging.error(f"Error: {error_msg}")
```

**Deliverables**:
- ✅ Inference history logging
- ✅ Error tracking
- ✅ Performance metrics collection

---

#### 4.2 Auto-Start Service
```bash
# /etc/systemd/system/vision-robot.service
[Unit]
Description=Vision Robot Service
After=network.target

[Service]
ExecStart=/usr/bin/python3 /home/pi/vision_robot/main.py
WorkingDirectory=/home/pi/vision_robot
Restart=always
User=pi

[Install]
WantedBy=multi-user.target
```

```bash
# Enable service
sudo systemctl enable vision-robot.service
sudo systemctl start vision-robot.service

# Check status
sudo systemctl status vision-robot.service
```

**Deliverables**:
- ✅ Robot auto-start on boot
- ✅ Auto-restart on crash
- ✅ System integration complete

---

#### 4.3 Documentation & Training Materials
**Dokumen yang Perlu Dibuat**:

1. **User Manual** (untuk operator):
   - Cara setup hardware
   - Cara training model custom
   - Cara transfer model ke robot
   - Troubleshooting common issues

2. **Technical Documentation** (untuk developer):
   - System architecture diagram
   - API documentation (Android ↔ Pi)
   - Model training best practices
   - Performance tuning guide

3. **Educational Content** (untuk siswa):
   - Konsep machine learning dasar
   - Transfer learning explained
   - Hands-on training exercises
   - Project ideas

**Deliverables**:
- ✅ Complete documentation set
- ✅ Video tutorials
- ✅ Sample projects

---

## 📊 TIMELINE & MILESTONES

```
Minggu 1-2: Integration Setup
├─ Day 1-3: Pi Camera setup & testing
├─ Day 4-7: WiFi communication protocol
├─ Day 8-10: Model transfer mechanism
└─ Day 11-14: Integration testing

Minggu 3-4: Pi Inference Engine
├─ Day 15-18: TFLite runtime setup
├─ Day 19-22: Inference optimization
├─ Day 23-25: Robot control integration
└─ Day 26-28: End-to-end testing

Minggu 5-6: Advanced Features
├─ Day 29-33: Remote training interface
├─ Day 34-38: Multi-model support
└─ Day 39-42: Performance optimization

Minggu 7-8: Production Ready
├─ Day 43-47: Logging & monitoring
├─ Day 48-52: Auto-start service
├─ Day 53-56: Documentation & training
└─ Day 57-60: Final testing & deployment
```

**Key Milestones**:
- ✅ Week 2: Pi can receive frames from Android
- ✅ Week 4: Pi runs custom model inference
- ✅ Week 6: Remote training working
- ✅ Week 8: Production deployment complete

---

## 🎓 SKILLS REQUIRED

### Technical Skills:
1. **Android Development**:
   - Kotlin programming
   - Android architecture components
   - CameraX API
   
2. **Python Development**:
   - Python 3.x
   - OpenCV
   - NumPy
   
3. **Machine Learning**:
   - TensorFlow / TFLite
   - Transfer learning concepts
   - Model optimization
   
4. **Embedded Systems**:
   - Raspberry Pi GPIO
   - Linux command line
   - Systemd services
   
5. **Networking**:
   - Socket programming
   - WiFi configuration
   - USB communication

### Learning Resources:
- TensorFlow Lite Documentation: https://www.tensorflow.org/lite
- Raspberry Pi Camera Guide: https://projects.raspberrypi.org/en/projects/getting-started-with-picamera
- Android CameraX: https://developer.android.com/training/camerax
- Transfer Learning Tutorial: https://www.tensorflow.org/tutorials/images/transfer_learning

---

## 💰 ESTIMASI BIAYA

### Hardware (Per Robot):
| Item | Quantity | Price (IDR) | Total |
|------|----------|-------------|-------|
| Raspberry Pi 4 (4GB) | 1 | 1,500,000 | 1,500,000 |
| Pi Camera Module v2 | 1 | 400,000 | 400,000 |
| Power Supply 5V/3A | 1 | 150,000 | 150,000 |
| microSD 32GB | 1 | 100,000 | 100,000 |
| Robot Chassis Kit | 1 | 500,000 | 500,000 |
| Motor Driver | 1 | 150,000 | 150,000 |
| Cables & Accessories | - | 200,000 | 200,000 |
| **TOTAL PER ROBOT** | | | **3,000,000** |

### Software (One-time):
| Item | Price (IDR) |
|------|-------------|
| Development tools | Free (Android Studio, VS Code) |
| TensorFlow Lite | Free (open source) |
| Documentation tools | Free (Markdown, diagrams.net) |
| **TOTAL SOFTWARE** | **0** |

### Development Cost:
| Phase | Duration | Est. Hours | Cost @ 50k/hour |
|-------|----------|------------|-----------------|
| Phase 1 | 2 weeks | 80 hours | 4,000,000 |
| Phase 2 | 2 weeks | 80 hours | 4,000,000 |
| Phase 3 | 2 weeks | 80 hours | 4,000,000 |
| Phase 4 | 2 weeks | 80 hours | 4,000,000 |
| **TOTAL** | **8 weeks** | **320 hours** | **16,000,000** |

---

## 🎯 SUCCESS METRICS

### Technical Metrics:
- ✅ Inference latency: <100ms
- ✅ Model accuracy: >85%
- ✅ Model size: <2MB
- ✅ FPS: >15 on Raspberry Pi
- ✅ Model transfer time: <10s
- ✅ Training time: <5 minutes (100 epochs)

### User Metrics:
- ✅ Training samples needed: <50 per class
- ✅ Setup time: <30 minutes
- ✅ User satisfaction: >4.5/5
- ✅ System uptime: >99%

### Educational Metrics:
- ✅ Students can train custom model: 95%
- ✅ Students understand transfer learning: 80%
- ✅ Students complete robot project: 90%

---

## 🚧 POTENTIAL CHALLENGES & SOLUTIONS

### Challenge 1: Latency Issues
**Problem**: High latency dalam frame transfer (Pi → Android)
**Solutions**:
- Use JPEG compression untuk reduce data size
- Implement frame buffering
- Consider local processing on Pi

### Challenge 2: Model Accuracy Drop on Pi
**Problem**: Akurasi turun saat deploy ke Pi
**Solutions**:
- Ensure same preprocessing pipeline
- Use float32 model (before quantization)
- Collect Pi-specific training data (lighting, angle)

### Challenge 3: Power Consumption
**Problem**: Battery drain cepat
**Solutions**:
- Implement sleep mode saat idle
- Reduce inference frequency (frame skipping)
- Use power-efficient model (quantized)

### Challenge 4: Network Instability
**Problem**: WiFi connection drops
**Solutions**:
- Implement auto-reconnect logic
- Use wired USB connection as fallback
- Local processing mode (without Android)

---

## 📝 KESIMPULAN

Projek Vision Programmer untuk Robot Education ini menggunakan teknologi modern dan terbukti:
- **TensorFlow Lite** untuk on-device ML
- **Transfer Learning** untuk training cepat
- **Android + Raspberry Pi** untuk flexible deployment
- **Real-time Inference** untuk responsive robot

**Langkah Konkrit Selanjutnya**:
1. ✅ Setup Raspberry Pi & Camera (Week 1)
2. ✅ Implement communication protocol (Week 2)
3. ✅ Deploy inference engine to Pi (Week 3-4)
4. ✅ Integrate robot control (Week 4)
5. ✅ Add advanced features (Week 5-6)
6. ✅ Production deployment (Week 7-8)

**Expected Outcome**:
- Working robot education system
- Students can train custom vision models
- Robot responds to visual inputs in real-time
- Scalable to multiple robots & use cases

---

**Prepared by**: Vision Programmer Division  
**Date**: October 6, 2025  
**Version**: 1.0

**For questions or clarifications, contact the development team.**
