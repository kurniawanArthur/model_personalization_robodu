/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.lite.examples.modelpersonalization

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.support.label.Category
import org.tensorflow.lite.support.label.TensorLabel
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min


class TransferLearningHelper(
    var numThreads: Int = 2,
    val context: Context,
    val classifierListener: ClassifierListener?
) {

    private var interpreter: Interpreter? = null
    private val trainingSamples: MutableList<TrainingSample> = mutableListOf()
    private var executor: ExecutorService? = null

    //This lock guarantees that only one thread is performing training and
    //inference at any point in time.
    private val lock = Any()
    private var targetWidth: Int = 0
    private var targetHeight: Int = 0
    private val handler = Handler(Looper.getMainLooper())
    private var requiredBatchSize = EXPECTED_BATCH_SIZE
    private var requiredLabelSize = NUM_CLASSES
    private var bottleneckSize = BOTTLENECK_SIZE
    private var weightsInitialized = false
    private var initializationPersisted = false

    init {
        if (setupModelPersonalization()) {
            interpreter?.getInputTensor(0)?.shape()?.let { shape ->
                if (shape.size >= 3) {
                    targetWidth = shape[2]
                    targetHeight = shape[1]
                }
            }
            updateModelConstraints()
            ensureWeightsReady(initialAttempt = true)
        } else {
            classifierListener?.onError("TFLite failed to init.")
        }
    }

    fun close() {
        executor?.shutdownNow()
        executor = null
        interpreter = null
        weightsInitialized = false
        initializationPersisted = false
    }

    fun pauseTraining() {
        executor?.shutdownNow()
        // Auto-save model when pausing
        saveModelWeights()
    }

    // Save trained model weights to file
    fun saveModelWeights(): Boolean {
        return try {
            synchronized(lock) {
                val checkpointPath = getCheckpointPath()
                val inputs: MutableMap<String, Any> = HashMap()
                addCheckpointInputIfRequired(SAVE_KEY, inputs, checkpointPath)

                val outputs = prepareOutputBuffers(SAVE_KEY)
                interpreter?.runSignature(inputs, outputs, SAVE_KEY)
                
                Log.d(TAG, "Model weights saved to: $checkpointPath")
                classifierListener?.onModelSaved()
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save model weights: ${e.message}")
            classifierListener?.onError("Failed to save model: ${e.message}")
            false
        }
    }

    // Load trained model weights from file
    fun loadModelWeights(): Boolean {
        return try {
            synchronized(lock) {
                val checkpointPath = getCheckpointPath()
                val checkpointFile = File(checkpointPath)

                if (!checkpointFile.exists() || checkpointFile.length() == 0L) {
                    Log.d(TAG, "No saved model weights found")
                    weightsInitialized = false
                    return false
                }

                val inputs: MutableMap<String, Any> = HashMap()
                addCheckpointInputIfRequired(RESTORE_KEY, inputs, checkpointPath)

                val outputs = prepareOutputBuffers(RESTORE_KEY)
                interpreter?.runSignature(inputs, outputs, RESTORE_KEY)

                weightsInitialized = true
                Log.d(TAG, "Model weights loaded from: $checkpointPath")
                classifierListener?.onModelLoaded()
                true
            }
        } catch (e: Exception) {
            weightsInitialized = false
            Log.e(TAG, "Failed to load model weights", e)
            classifierListener?.onError("Failed to load model: ${e.message}")
            false
        }
    }

    // Initialize model weights (reset to random)
    fun initializeModelWeights(): Boolean {
        synchronized(lock) {
            val interp = interpreter
            if (interp == null) {
                Log.e(TAG, "Interpreter not ready; cannot initialize model weights")
                weightsInitialized = false
                return false
            }

            val hasDeclaredInputs = signatureInputNames(INITIALIZE_KEY).isNotEmpty()
            return if (hasDeclaredInputs) {
                initializeUsingSignatureInputs(interp)
            } else {
                val emptyInputFailure = initializeUsingEmptyInputSignature(interp)
                if (emptyInputFailure == null) {
                    true
                } else {
                    initializeUsingZeroInputSignature(
                        interp,
                        "empty-input signature run failed: $emptyInputFailure"
                    )
                }
            }
        }
    }

    // Get checkpoint file path
    private fun getCheckpointPath(): String {
        val modelDir = File(context.filesDir, "trained_models")
        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }
        return File(modelDir, "model_checkpoint").absolutePath
    }

    fun getCheckpointFilePath(): String = getCheckpointPath()

    fun clearTrainingSamples() {
        synchronized(lock) {
            trainingSamples.clear()
        }
    }

    // Check if saved model exists
    fun hasSavedModel(): Boolean {
        val checkpointFile = File(getCheckpointPath())
        return checkpointFile.exists() && checkpointFile.length() > 0L
    }

    private fun setupModelPersonalization(): Boolean {
        val options = Interpreter.Options()
        options.numThreads = numThreads
        return try {
            val modelFile = loadModelFile()
            interpreter = Interpreter(modelFile, options)
            weightsInitialized = false
            initializationPersisted = false
            logSignatureMetadata()
            updateModelConstraints()
            true
        } catch (e: IOException) {
            classifierListener?.onError(
                "Model personalization failed to " +
                        "initialize. See error logs for details"
            )
            Log.e(TAG, "TFLite failed to load model with error: " + e.message)
            false
        }
    }

    private fun ensureWeightsReady(initialAttempt: Boolean = false): Boolean {
        if (interpreter == null && !setupModelPersonalization()) {
            return false
        }

        if (weightsInitialized) {
            return true
        }

        val restored = hasSavedModel() && loadModelWeights()
        if (restored || weightsInitialized) {
            return true
        }

        if (initializeModelWeights()) {
            persistInitializationIfNeeded()
            return true
        }

        if (!initialAttempt) {
            classifierListener?.onError("Unable to initialize model weights. Restart the app and try again.")
        } else {
            Log.e(TAG, "Unable to initialize model weights during startup.")
        }
        return false
    }

    private fun loadModelFile() = try {
        FileUtil.loadMappedFile(context, PRIMARY_MODEL_ASSET)
    } catch (primary: IOException) {
        Log.w(TAG, "Primary model not found, falling back to legacy asset", primary)
        FileUtil.loadMappedFile(context, LEGACY_MODEL_ASSET)
    }

    private fun addCheckpointInputIfRequired(
        signatureKey: String,
        inputs: MutableMap<String, Any>,
        checkpointPath: String
    ) {
        val signatureInputs = interpreter?.getSignatureInputs(signatureKey)
        val inputNames = extractSignatureNames(signatureInputs)

        val checkpointKey = inputNames.firstOrNull { key ->
            key.equals("checkpoint_path", ignoreCase = true) ||
                key.contains("checkpoint", ignoreCase = true)
        }

        val fallbackKey = if (checkpointKey != null) {
            checkpointKey
        } else {
            inputNames.firstOrNull { key ->
                interpreter
                    ?.getInputTensorFromSignature(key, signatureKey)
                    ?.dataType() == DataType.STRING
            }
        }

        val resolvedKey = fallbackKey ?: if (signatureKey == INITIALIZE_KEY) {
            DEFAULT_CHECKPOINT_INPUT_KEY
        } else {
            null
        }

        if (resolvedKey != null && !inputs.containsKey(resolvedKey)) {
            Log.d(
                TAG,
                "Supplying checkpoint path for signature '$signatureKey' input '$resolvedKey'"
            )
            inputs[resolvedKey] = arrayOf(checkpointPath.toByteArray(Charsets.UTF_8))
        } else if (inputNames.isNotEmpty()) {
            Log.w(
                TAG,
                "No checkpoint input matched for signature '$signatureKey'. Available inputs: $inputNames"
            )
        } else {
            Log.d(TAG, "Signature '$signatureKey' has no inputs")
        }
    }

    private fun signatureInputNames(signatureKey: String): List<String> {
        return extractSignatureNames(interpreter?.getSignatureInputs(signatureKey))
    }

    private fun initializeUsingSignatureInputs(interp: Interpreter): Boolean {
        val inputs: MutableMap<String, Any> = HashMap()
        addCheckpointInputIfRequired(INITIALIZE_KEY, inputs, getCheckpointPath())
        if (inputs.isEmpty()) {
            Log.w(
                TAG,
                "Initialize signature reports inputs but none were resolved; swapping to zero-input fallback"
            )
            return initializeUsingZeroInputSignature(interp, "no resolvable checkpoint input")
        }

        val outputs = prepareOutputBuffers(INITIALIZE_KEY)
        return try {
            interp.runSignature(inputs, outputs, INITIALIZE_KEY)
            weightsInitialized = true
            Log.d(TAG, "Model weights initialized via signature runner")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Initialize signature invocation failed", e)
            val fallback = initializeUsingZeroInputSignature(
                interp,
                "signature failure: ${e.message ?: "unknown error"}"
            )
            if (!fallback) {
                weightsInitialized = false
                classifierListener?.onError("Failed to initialize model weights: ${e.message}")
            }
            fallback
        }
    }

    private fun initializeUsingEmptyInputSignature(interp: Interpreter): String? {
        val outputs = prepareOutputBuffers(INITIALIZE_KEY)
        return try {
            interp.runSignature(emptyMap<String, Any>(), outputs, INITIALIZE_KEY)
            weightsInitialized = true
            Log.d(TAG, "Model weights initialized via empty-input signature run")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Empty-input signature run failed", e)
            e.message ?: e.javaClass.simpleName ?: "unknown error"
        }
    }

    private fun persistInitializationIfNeeded() {
        if (initializationPersisted) {
            return
        }

        val saved = if (hasSavedModel()) {
            true
        } else {
            val result = saveModelWeights()
            if (!result) {
                Log.w(TAG, "Unable to persist freshly initialized weights")
            }
            result
        }

        var loadSucceeded = false
        if (saved) {
            loadSucceeded = loadModelWeights()
            if (!loadSucceeded) {
                Log.w(TAG, "Unable to reload freshly initialized weights")
            }
        }

        if (saved && loadSucceeded) {
            initializationPersisted = true
        }
    }

    private fun initializeUsingZeroInputSignature(interp: Interpreter, reason: String): Boolean {
        val reflectionSucceeded = invokeZeroInputSignature(interp, INITIALIZE_KEY)
        if (reflectionSucceeded) {
            weightsInitialized = true
            Log.d(
                TAG,
                "Model weights initialized via reflection fallback${if (reason.isNotBlank()) " ($reason)" else ""}"
            )
            return true
        }

        val resetSucceeded = resetVariableTensorsSafely(interp, reason)
        if (resetSucceeded) {
            weightsInitialized = true
            Log.d(TAG, "Model weights reset via resetVariableTensors() fallback")
            return true
        }

        Log.e(TAG, "Unable to initialize model weights; zero-input fallbacks exhausted (${reason})")
        weightsInitialized = false
        classifierListener?.onError("Failed to initialize model weights: ${reason}")
        return false
    }

    private fun resetVariableTensorsSafely(interp: Interpreter, reason: String): Boolean {
        return try {
            interp.resetVariableTensors()
            true
        } catch (e: Exception) {
            Log.e(TAG, "resetVariableTensors() fallback failed (${reason})", e)
            false
        }
    }

    private fun invokeZeroInputSignature(interp: Interpreter, signatureKey: String): Boolean {
        return try {
            val nativeWrapper = resolveNativeWrapper(interp) ?: return false
            val runner = resolveSignatureRunner(nativeWrapper, signatureKey) ?: return false

            val inputNames = invokeStringArray(runner, "inputNames") ?: emptyArray()
            if (inputNames.isNotEmpty()) {
                Log.d(
                    TAG,
                    "Reflection fallback aborted for signature '$signatureKey'; inputs reported: ${inputNames.joinToString()}"
                )
                return false
            }

            val outputs = prepareOutputBuffers(signatureKey)
            invokeNoArg(runner, "allocateTensorsIfNeeded")
            invokeNoArg(runner, "invoke")

            if (outputs.isNotEmpty()) {
                val outputNames = invokeStringArray(runner, "outputNames") ?: emptyArray()
                outputNames.forEach { outputName ->
                    val tensor = invokeMethod(
                        runner,
                        "getOutputTensor",
                        arrayOf(String::class.java),
                        arrayOf(outputName as Any)
                    ) ?: return@forEach
                    val buffer = outputs[outputName]
                    if (buffer != null) {
                        try {
                            val copyTo = tensor.javaClass.getMethod("copyTo", Any::class.java)
                            copyTo.isAccessible = true
                            copyTo.invoke(tensor, buffer)
                        } catch (e: Exception) {
                            Log.w(TAG, "Unable to copy initialize output '$outputName'", e)
                        }
                    }
                }
            }
            Log.d(TAG, "Signature '$signatureKey' executed via reflection fallback")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Reflection fallback failed for signature '$signatureKey'", e)
            false
        }
    }

    private fun resolveNativeWrapper(interp: Interpreter): Any? {
        return try {
            val wrapperField = locateField(interp.javaClass, "wrapper")
            if (wrapperField == null) {
                Log.d(TAG, "InterpreterImpl.wrapper field not found for reflection fallback")
                null
            } else {
                wrapperField.isAccessible = true
                wrapperField.get(interp)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to access NativeInterpreterWrapper via reflection", e)
            null
        }
    }

    private fun resolveSignatureRunner(nativeWrapper: Any, signatureKey: String): Any? {
        return try {
            val method = locateMethod(
                nativeWrapper.javaClass,
                "getSignatureRunnerWrapper",
                arrayOf(String::class.java)
            )
            if (method == null) {
                Log.e(TAG, "Unable to access signature runner for '$signatureKey' via reflection: method not found")
                null
            } else {
                method.isAccessible = true
                method.invoke(nativeWrapper, signatureKey)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to access signature runner for '$signatureKey' via reflection", e)
            null
        }
    }

    private fun invokeNoArg(target: Any, methodName: String) {
        invokeMethod(target, methodName, emptyArray<Class<*>>(), emptyArray<Any>())
    }

    private fun invokeStringArray(target: Any, methodName: String): Array<String>? {
        @Suppress("UNCHECKED_CAST")
        return invokeMethod(
            target,
            methodName,
            emptyArray<Class<*>>(),
            emptyArray<Any>()
        ) as? Array<String>
    }

    private fun invokeMethod(
        target: Any,
        methodName: String,
        parameterTypes: Array<Class<*>>,
        args: Array<Any>
    ): Any? {
        val method = locateMethod(target.javaClass, methodName, parameterTypes)
            ?: throw NoSuchMethodException("Method $methodName not found on ${target.javaClass}")
        method.isAccessible = true
        return method.invoke(target, *args)
    }

    private fun locateMethod(clazz: Class<*>, name: String, parameterTypes: Array<Class<*>>): java.lang.reflect.Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredMethod(name, *parameterTypes)
            } catch (_: NoSuchMethodException) {
                current = current.superclass
            }
        }
        return null
    }

    private fun locateField(clazz: Class<*>, name: String): java.lang.reflect.Field? {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredField(name)
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }

    private fun prepareOutputBuffers(signatureKey: String): MutableMap<String, Any> {
        val outputs: MutableMap<String, Any> = HashMap()
        @Suppress("UNCHECKED_CAST")
        val signatureOutputs = interpreter?.getSignatureOutputs(signatureKey) as? Map<String, *> ?: return outputs

        signatureOutputs.keys.forEach { outputKey ->
            val tensor = interpreter?.getOutputTensorFromSignature(outputKey, signatureKey) ?: return@forEach
            val numElements = tensor.shape().fold(1) { acc, dim -> acc * max(1, dim) }
            val buffer: Any = when (tensor.dataType()) {
                DataType.FLOAT32 -> FloatBuffer.allocate(numElements)
                DataType.INT32 -> IntBuffer.allocate(numElements)
                DataType.INT64 -> LongBuffer.allocate(numElements)
                DataType.UINT8 -> ByteArray(numElements)
                DataType.BOOL -> BooleanArray(numElements)
                DataType.STRING -> Array(numElements) { ByteArray(0) }
                else -> ByteBuffer.allocateDirect(numElements * tensor.dataType().byteSize()).apply {
                    order(ByteOrder.nativeOrder())
                }
            }
            outputs[outputKey] = buffer
        }

        return outputs
    }

    private fun logSignatureMetadata() {
        val interp = interpreter ?: return
        val signatureKeys = interp.signatureKeys
        Log.d(TAG, "Available signature keys: ${signatureKeys?.joinToString() ?: "<none>"}")
        signatureKeys?.forEach { signatureKey ->
            val rawInputs = interp.getSignatureInputs(signatureKey)
            val rawOutputs = interp.getSignatureOutputs(signatureKey)

            Log.d(
                TAG,
                "Signature '$signatureKey' raw inputs descriptor: ${rawInputs?.javaClass?.name ?: "<none>"} -> $rawInputs"
            )

            val inputSummary = extractSignatureNames(rawInputs).map { key ->
                val tensor = interp.getInputTensorFromSignature(key, signatureKey)
                val type = tensor?.dataType()?.name ?: "?"
                val shape = tensor?.shape()?.contentToString() ?: "?"
                "$key(type=$type, shape=$shape)"
            }

            val outputSummary = extractSignatureNames(rawOutputs).map { key ->
                val tensor = interp.getOutputTensorFromSignature(key, signatureKey)
                val type = tensor?.dataType()?.name ?: "?"
                val shape = tensor?.shape()?.contentToString() ?: "?"
                "$key(type=$type, shape=$shape)"
            }

            Log.d(
                TAG,
                "Signature '$signatureKey' inputs=${if (inputSummary.isEmpty()) "<none>" else inputSummary.joinToString()} outputs=${if (outputSummary.isEmpty()) "<none>" else outputSummary.joinToString()}"
            )
        }
    }

    private fun extractSignatureNames(descriptor: Any?): List<String> = when (descriptor) {
        is Map<*, *> -> descriptor.keys.filterIsInstance<String>()
        is Array<*> -> descriptor.filterIsInstance<String>()
        is Collection<*> -> descriptor.filterIsInstance<String>()
        else -> emptyList()
    }

    private fun updateModelConstraints() {
        interpreter?.let { interp ->
            try {
                interp.getInputTensorFromSignature(
                    LOAD_BOTTLENECK_INPUT_KEY,
                    LOAD_BOTTLENECK_KEY
                )?.shape()?.let { shape ->
                    if (shape.size >= 3) {
                        targetHeight = shape[1]
                        targetWidth = shape[2]
                    }
                }

                interp.getInputTensorFromSignature(
                    TRAINING_INPUT_BOTTLENECK_KEY,
                    TRAINING_KEY
                )?.shape()?.let { shape ->
                    if (shape.isNotEmpty() && shape[0] > 0) {
                        requiredBatchSize = shape[0]
                    }
                    if (shape.size >= 2 && shape[1] > 0) {
                        bottleneckSize = shape[1]
                    }
                }

                interp.getInputTensorFromSignature(
                    TRAINING_INPUT_LABELS_KEY,
                    TRAINING_KEY
                )?.shape()?.let { shape ->
                    if (shape.size >= 2) {
                        if (shape[0] > 0) {
                            requiredBatchSize = shape[0]
                        }
                        if (shape[1] > 0) {
                            requiredLabelSize = shape[1]
                        }
                    }
                }
                Log.d(
                    TAG,
                    "Model constraints -> batchSize: $requiredBatchSize, labelSize: $requiredLabelSize, bottleneckSize: $bottleneckSize"
                )
            } catch (e: Exception) {
                Log.w(TAG, "Unable to read model constraints: ${e.message}")
            }
        }
        requiredBatchSize = requiredBatchSize.coerceAtLeast(1)
        requiredLabelSize = requiredLabelSize.coerceAtLeast(1)
    }

    // Process input image and add the output into list samples which are
    // ready for training.
    fun addSample(image: Bitmap, className: String, rotation: Int) {
        synchronized(lock) {
            if (interpreter == null) {
                setupModelPersonalization()
            }
            if (!ensureWeightsReady()) {
                return
            }
            processInputImage(image, rotation)?.let { tensorImage ->
                val bottleneck = loadBottleneck(tensorImage)
                trainingSamples.add(
                    TrainingSample(
                        bottleneck,
                        encoding(classes.getValue(className))
                    )
                )
            }
        }
    }

    // Start training process
    fun startTraining() {
        if (interpreter == null) {
            setupModelPersonalization()
        }

        if (!ensureWeightsReady()) {
            return
        }

        // Validation 1: Check if we have any samples
        if (trainingSamples.isEmpty()) {
            classifierListener?.onError("No training samples captured! Please capture some samples first.")
            return
        }

        // Validation 2: Check minimum samples
        val minSamplesRequired = 5 // Minimum 5 samples total
        if (trainingSamples.size < minSamplesRequired) {
            classifierListener?.onError(
                "Too few samples! Need at least $minSamplesRequired samples, got ${trainingSamples.size}. " +
                "Please capture more samples."
            )
            return
        }

        // Validation 3: Check class distribution
        val classDistribution = trainingSamples.groupBy { 
            it.label.indexOfFirst { value -> value == 1f }
        }
        
        if (classDistribution.size < 2) {
            classifierListener?.onError(
                "Need at least 2 different classes to train! " +
                "Currently only ${classDistribution.size} class(es) detected."
            )
            return
        }

        // Validation 4: Check each class has enough samples
        val minSamplesPerClass = 3
        val insufficientClasses = classDistribution.filter { it.value.size < minSamplesPerClass }
        if (insufficientClasses.isNotEmpty()) {
            classifierListener?.onError(
                "Some classes have too few samples! Each class needs at least $minSamplesPerClass samples. " +
                "Classes with insufficient samples: ${insufficientClasses.keys.joinToString()}"
            )
            return
        }

        // Create new thread for training process.
        executor = Executors.newSingleThreadExecutor()
        val expectedBatchSize = requiredBatchSize.coerceAtLeast(1)
        val expectedLabelSize = requiredLabelSize.coerceAtLeast(1)
        val trainBatchSize = max(1, min(trainingSamples.size, expectedBatchSize))

        if (trainingSamples.size < expectedBatchSize) {
            Log.w(
                TAG,
                "Padding training batches to size $expectedBatchSize using ${trainingSamples.size} samples"
            )
        }

        executor?.execute {
            synchronized(lock) {
                var avgLoss: Float
                var epochCount = 0
                val maxEpochs = 100 // Maximum training epochs
                var trainingFailed = false

                // Keep training until the helper pause or close.
                outer@ while (executor?.isShutdown == false && epochCount < maxEpochs && !trainingFailed) {
                    var totalLoss = 0f
                    var numBatchesProcessed = 0
                    epochCount++

                    // Shuffle training samples to reduce overfitting and
                    // variance.
                    trainingSamples.shuffle()

                    trainingBatches(trainBatchSize)
                        .forEach { batch ->
                            if (batch.isEmpty()) {
                                Log.w(TAG, "Encountered empty training batch; skipping")
                                return@forEach
                            }

                            val trainingBatchBottlenecks =
                                MutableList(expectedBatchSize) {
                                    FloatArray(
                                        bottleneckSize
                                    )
                                }

                            val trainingBatchLabels =
                                MutableList(expectedBatchSize) {
                                    FloatArray(
                                        expectedLabelSize
                                    )
                                }

                            batch.forEachIndexed { index, trainingSample ->
                                trainingBatchBottlenecks[index] = trainingSample.bottleneck
                                trainingBatchLabels[index] = prepareLabel(
                                    trainingSample.label,
                                    expectedLabelSize
                                )
                            }

                            if (batch.size < expectedBatchSize) {
                                for (i in batch.size until expectedBatchSize) {
                                    val source = batch[i % batch.size]
                                    trainingBatchBottlenecks[i] = source.bottleneck
                                    trainingBatchLabels[i] = prepareLabel(
                                        source.label,
                                        expectedLabelSize
                                    )
                                }
                            }

                            val loss = try {
                                training(
                                    trainingBatchBottlenecks,
                                    trainingBatchLabels
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Training step failed", e)
                                trainingFailed = true
                                handler.post {
                                    classifierListener?.onError(
                                        "Training failed: ${e.localizedMessage ?: "unexpected error"}"
                                    )
                                }
                                return@forEach
                            }
                            totalLoss += loss
                            numBatchesProcessed++
                        }

                    // Calculate the average loss after training all batches.
                    if (trainingFailed || numBatchesProcessed == 0) {
                        break@outer
                    }

                    avgLoss = totalLoss / numBatchesProcessed
                    
                    // Calculate progress (0-100%)
                    val progress = ((epochCount.toFloat() / maxEpochs) * 100).toInt()
                    
                    handler.post {
                        classifierListener?.onLossResults(avgLoss)
                        classifierListener?.onEpochUpdate(epochCount, progress)
                    }
                }
                
                // Training completed
                if (!trainingFailed) {
                    handler.post {
                        classifierListener?.onTrainingComplete()
                    }
                }
            }
        }
    }

    // Runs one training step with the given bottleneck batches and labels
    // and return the loss number.
    private fun training(
        bottlenecks: MutableList<FloatArray>,
        labels: MutableList<FloatArray>
    ): Float {
        val inputs: MutableMap<String, Any> = HashMap()
        inputs[TRAINING_INPUT_BOTTLENECK_KEY] = bottlenecks.toTypedArray()
        inputs[TRAINING_INPUT_LABELS_KEY] = labels.toTypedArray()

        val outputs: MutableMap<String, Any> = HashMap()
        val loss = FloatBuffer.allocate(1)
        outputs[TRAINING_OUTPUT_KEY] = loss

        interpreter?.runSignature(inputs, outputs, TRAINING_KEY)
        return loss.get(0)
    }

    // Invokes inference on the given image batches.
    fun classify(bitmap: Bitmap, rotation: Int) {
        processInputImage(bitmap, rotation)?.let { image ->
            synchronized(lock) {
                if (interpreter == null) {
                    setupModelPersonalization()
                }

                if (!ensureWeightsReady()) {
                    return
                }

                // Inference time is the difference between the system time at the start and finish of the
                // process
                var inferenceTime = SystemClock.uptimeMillis()

                val inputs: MutableMap<String, Any> = HashMap()
                inputs[INFERENCE_INPUT_KEY] = image.buffer

                val outputs: MutableMap<String, Any> = HashMap()
                val output = TensorBuffer.createFixedSize(
                    intArrayOf(1, NUM_CLASSES),
                    DataType.FLOAT32
                )
                outputs[INFERENCE_OUTPUT_KEY] = output.buffer

                interpreter?.runSignature(inputs, outputs, INFERENCE_KEY)
                val tensorLabel = TensorLabel(classes.keys.toList(), output)
                val result = tensorLabel.categoryList

                inferenceTime = SystemClock.uptimeMillis() - inferenceTime

                classifierListener?.onResults(result, inferenceTime)
            }
        }
    }

    // Loads the bottleneck feature from the given image array.
    private fun loadBottleneck(image: TensorImage): FloatArray {
        val inputs: MutableMap<String, Any> = HashMap()
        inputs[LOAD_BOTTLENECK_INPUT_KEY] = image.buffer
        val outputs: MutableMap<String, Any> = HashMap()

        fun createOutputHolder(): Array<FloatArray> {
            val expectedSize = bottleneckSize.coerceAtLeast(1)
            return Array(1) { FloatArray(expectedSize) }
        }

        var bottleneckHolder = createOutputHolder()
        outputs[LOAD_BOTTLENECK_OUTPUT_KEY] = bottleneckHolder

        try {
            interpreter?.runSignature(inputs, outputs, LOAD_BOTTLENECK_KEY)
            return bottleneckHolder[0]
        } catch (error: Exception) {
            if (!isUninitializedReadVariableError(error)) {
                throw error
            }

            Log.w(
                TAG,
                "Load signature failed due to uninitialized variables; attempting to recover",
                error
            )

            weightsInitialized = false
            initializationPersisted = false

            if (!ensureWeightsReady()) {
                throw error
            }

            bottleneckHolder = createOutputHolder()
            outputs[LOAD_BOTTLENECK_OUTPUT_KEY] = bottleneckHolder
            interpreter?.runSignature(inputs, outputs, LOAD_BOTTLENECK_KEY)
            return bottleneckHolder[0]
        }
    }

    private fun isUninitializedReadVariableError(error: Throwable?): Boolean {
        var current: Throwable? = error
        while (current != null) {
            val message = current.message?.lowercase()
            if (message != null) {
                if (message.contains("read_variable") || message.contains("uninitialized")) {
                    return true
                }
            }
            current = current.cause
        }
        return false
    }

    // Preprocess the image and convert it into a TensorImage for classification.
    private fun processInputImage(
        image: Bitmap,
        imageRotation: Int
    ): TensorImage? {
        val height = image.height
        val width = image.width
        val cropSize = min(height, width)
        val imageProcessor = ImageProcessor.Builder()
            .add(Rot90Op(-imageRotation / 90))
            .add(ResizeWithCropOrPadOp(cropSize, cropSize))
            .add(
                ResizeOp(
                    targetHeight,
                    targetWidth,
                    ResizeOp.ResizeMethod.BILINEAR
                )
            )
            .add(NormalizeOp(0f, 255f))
            .build()
        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(image)
        return imageProcessor.process(tensorImage)
    }

    // encode the classes name to float array
    private fun encoding(id: Int): FloatArray {
    val classEncoded = FloatArray(NUM_CLASSES) { 0f }
        classEncoded[id] = 1f
        return classEncoded
    }

    // Training model expected batch size.
    // Constructs an iterator that iterates over training sample batches.
    private fun trainingBatches(trainBatchSize: Int): Iterator<List<TrainingSample>> {
        return object : Iterator<List<TrainingSample>> {
            private var nextIndex = 0

            override fun hasNext(): Boolean {
                return nextIndex < trainingSamples.size
            }

            override fun next(): List<TrainingSample> {
                val fromIndex = nextIndex
                val toIndex: Int = nextIndex + trainBatchSize
                nextIndex = toIndex
                return if (toIndex >= trainingSamples.size) {
                    // To keep batch size consistent, last batch may include some elements from the
                    // next-to-last batch.
                    trainingSamples.subList(
                        trainingSamples.size - trainBatchSize,
                        trainingSamples.size
                    )
                } else {
                    trainingSamples.subList(fromIndex, toIndex)
                }
            }
        }
    }

    private fun prepareLabel(label: FloatArray, expectedSize: Int): FloatArray {
        return if (label.size == expectedSize) {
            label
        } else {
            label.copyOf(expectedSize)
        }
    }

    interface ClassifierListener {
        fun onError(error: String)
        fun onResults(results: List<Category>?, inferenceTime: Long)
        fun onLossResults(lossNumber: Float)
        fun onEpochUpdate(epoch: Int, progress: Int) {}
        fun onTrainingComplete() {}
        fun onModelSaved() {}
        fun onModelLoaded() {}
    }

    companion object {
        const val CLASS_ONE = "1"
        const val CLASS_TWO = "2"
        const val CLASS_THREE = "3"
        const val CLASS_FOUR = "4"
        const val CLASS_FIVE = "5"
        const val NUM_CLASSES = 5
        val CLASS_IDS = listOf(CLASS_ONE, CLASS_TWO, CLASS_THREE, CLASS_FOUR, CLASS_FIVE)
        private val classes = mapOf(
            CLASS_ONE to 0,
            CLASS_TWO to 1,
            CLASS_THREE to 2,
            CLASS_FOUR to 3,
            CLASS_FIVE to 4
        )
        private const val LOAD_BOTTLENECK_INPUT_KEY = "feature"
        private const val LOAD_BOTTLENECK_OUTPUT_KEY = "bottleneck"
        private const val LOAD_BOTTLENECK_KEY = "load"

        private const val TRAINING_INPUT_BOTTLENECK_KEY = "bottleneck"
        private const val TRAINING_INPUT_LABELS_KEY = "label"
        private const val TRAINING_OUTPUT_KEY = "loss"
        private const val TRAINING_KEY = "train"

        private const val INFERENCE_INPUT_KEY = "feature"
        private const val INFERENCE_OUTPUT_KEY = "output"
        private const val INFERENCE_KEY = "infer"

        private const val SAVE_KEY = "save"
        private const val RESTORE_KEY = "restore"
        private const val INITIALIZE_KEY = "initialize"
    private const val DEFAULT_CHECKPOINT_INPUT_KEY = "checkpoint_path"

        private const val BOTTLENECK_SIZE = 1 * 7 * 7 * 1280
        private const val PRIMARY_MODEL_ASSET = "model/model.tflite"
        private const val LEGACY_MODEL_ASSET = "model.tflite"
        private const val EXPECTED_BATCH_SIZE = 1
        private const val TAG = "TransferLearningHelper"
    }

    data class TrainingSample(val bottleneck: FloatArray, val label: FloatArray)
}
