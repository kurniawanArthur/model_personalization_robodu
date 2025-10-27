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

package org.tensorflow.lite.examples.modelpersonalization.fragments

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.camera.core.Preview
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.AspectRatio
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.tensorflow.lite.examples.modelpersonalization.MainViewModel
import org.tensorflow.lite.examples.modelpersonalization.SavedModelsAdapter
import org.tensorflow.lite.examples.modelpersonalization.R
import org.tensorflow.lite.examples.modelpersonalization.TransferLearningHelper
import org.tensorflow.lite.examples.modelpersonalization.ModelManager
import org.tensorflow.lite.examples.modelpersonalization.TransferLearningHelper.Companion.CLASS_FIVE
import org.tensorflow.lite.examples.modelpersonalization.TransferLearningHelper.Companion.CLASS_FOUR
import org.tensorflow.lite.examples.modelpersonalization.TransferLearningHelper.Companion.CLASS_ONE
import org.tensorflow.lite.examples.modelpersonalization.TransferLearningHelper.Companion.CLASS_THREE
import org.tensorflow.lite.examples.modelpersonalization.TransferLearningHelper.Companion.CLASS_TWO
import org.tensorflow.lite.examples.modelpersonalization.databinding.DialogModelManagerBinding
import org.tensorflow.lite.examples.modelpersonalization.databinding.DialogEditClassNameBinding
import org.tensorflow.lite.examples.modelpersonalization.databinding.FragmentCameraBinding
import org.tensorflow.lite.support.label.Category
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.io.File

import org.tensorflow.lite.examples.modelpersonalization.MainViewModel.TrainingState


class CameraFragment : Fragment(),
    TransferLearningHelper.ClassifierListener {

    companion object {
        private const val TAG = "Model Personalization"
        private const val LONG_PRESS_DURATION = 500
        private const val SAMPLE_COLLECTION_DELAY = 300
    }

    private var _fragmentCameraBinding: FragmentCameraBinding? = null
    private val fragmentCameraBinding
        get() = _fragmentCameraBinding!!

    private lateinit var transferLearningHelper: TransferLearningHelper
    private lateinit var bitmapBuffer: Bitmap

    private val viewModel: MainViewModel by activityViewModels()
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var previousClass: String? = null
    private lateinit var modelManager: ModelManager

    /** Blocking camera operations are performed using this executor */
    private lateinit var cameraExecutor: ExecutorService

    // When the user presses the "add sample" button for some class,
    // that class will be added to this queue. It is later extracted by
    // InferenceThread and processed.
    private val addSampleRequests = ConcurrentLinkedQueue<String>()
    private var isCollectingSamples = false
    private val sampleCollectionHandler = Handler(Looper.getMainLooper())
    private var longPressTriggered = false
    private var currentPressedClassId: String? = null
    private var longPressRunnable: Runnable? = null

    override fun onResume() {
        super.onResume()

        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(
                requireActivity(),
                R.id.fragment_container
            ).navigate(CameraFragmentDirections.actionCameraToPermissions())
        }
    }

    private fun handleClassTouch(classId: String, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!ensureClassNamed(classId)) {
                    return true
                }
                longPressTriggered = false
                currentPressedClassId = classId
                isCollectingSamples = true
                longPressRunnable = object : Runnable {
                    override fun run() {
                        if (!isCollectingSamples || currentPressedClassId != classId) {
                            return
                        }
                        longPressTriggered = true
                        collectSample(classId)
                        sampleCollectionHandler.postDelayed(
                            this,
                            SAMPLE_COLLECTION_DELAY.toLong()
                        )
                    }
                }
                sampleCollectionHandler.postDelayed(
                    longPressRunnable!!,
                    LONG_PRESS_DURATION.toLong()
                )
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasLongPress = longPressTriggered
                stopContinuousCapture()
                if (!wasLongPress && currentPressedClassId == classId) {
                    collectSample(classId)
                }
                currentPressedClassId = null
                longPressTriggered = false
            }
        }
        return true
    }

    private fun stopContinuousCapture() {
        isCollectingSamples = false
        longPressRunnable?.let { sampleCollectionHandler.removeCallbacks(it) }
        sampleCollectionHandler.removeCallbacksAndMessages(null)
        longPressRunnable = null
    }

    private fun collectSample(classId: String) {
        if (viewModel.getCaptureMode() != true) return
        if (viewModel.getTrainingState() == MainViewModel.TrainingState.TRAINING) return
        addSampleRequests.add(classId)
    }

    private fun ensureClassNamed(classId: String): Boolean {
        return if (viewModel.isClassNamed(classId)) {
            true
        } else {
            showEditClassNameDialog(classId, requireName = true)
            false
        }
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()

        // Shut down our background executor
        cameraExecutor.shutdown()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding =
            FragmentCameraBinding.inflate(inflater, container, false)

        return fragmentCameraBinding.root
    }

    @SuppressLint("MissingPermission", "ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        transferLearningHelper = TransferLearningHelper(
            context = requireContext(),
            classifierListener = this
        )

        cameraExecutor = Executors.newSingleThreadExecutor()

        viewModel.numThreads.observe(viewLifecycleOwner) {
            transferLearningHelper.numThreads = it
            transferLearningHelper.close()
            if (viewModel.getTrainingState() != MainViewModel.TrainingState.PREPARE) {
                // If the model is training, continue training with old image
                // sets.
                viewModel.setTrainingState(MainViewModel.TrainingState.TRAINING)
                transferLearningHelper.startTraining()
            }
        }

        viewModel.trainingState.observe(viewLifecycleOwner) {
            updateTrainingButtonState()
        }

        viewModel.captureMode.observe(viewLifecycleOwner) { isCaptureMode ->
            if (isCaptureMode) {
                viewModel.getNumberOfSample()?.let {
                    updateNumberOfSample(it)
                }
                // Unhighlight all class buttons
                highlightResult(null)
            }

            // Update the UI after switch to training mode.
            updateTrainingButtonState()
        }

        viewModel.numberOfSamples.observe(viewLifecycleOwner) {
            // Update the number of samples
            updateNumberOfSample(it)
            updateTrainingButtonState()
        }

        // Observe class names changes
        viewModel.classNames.observe(viewLifecycleOwner) {
            updateClassLabels()
            updateClassButtonVisualState()
        }

        modelManager = ModelManager(requireContext())

        with(fragmentCameraBinding) {
            if (viewModel.getCaptureMode()!!) {
                btnTrainingMode.isChecked = true
            } else {
                btnInferenceMode.isChecked = true
            }
            listOf(
                Triple(llClassOne, btnEditClassOne, CLASS_ONE),
                Triple(llClassTwo, btnEditClassTwo, CLASS_TWO),
                Triple(llClassThree, btnEditClassThree, CLASS_THREE),
                Triple(llClassFour, btnEditClassFour, CLASS_FOUR),
                Triple(llClassFive, btnEditClassFive, CLASS_FIVE)
            ).forEach { (button, editButton, classId) ->
                button.setOnTouchListener { _, event -> handleClassTouch(classId, event) }
                editButton.setOnClickListener { showEditClassNameDialog(classId) }
            }

            btnSaveModel.setOnClickListener { showSaveModelDialog() }
            btnManageModels.setOnClickListener { showModelManagerDialog() }
            btnNewModel.setOnClickListener { promptResetModel() }

            btnPauseTrain.setOnClickListener {
                viewModel.setTrainingState(MainViewModel.TrainingState.PAUSE)
                transferLearningHelper.pauseTraining()
            }
            btnResumeTrain.setOnClickListener {
                viewModel.setTrainingState(MainViewModel.TrainingState.TRAINING)
                transferLearningHelper.startTraining()
            }
            btnStartTrain.setOnClickListener {
                // Start training process
                viewModel.setTrainingState(MainViewModel.TrainingState.TRAINING)
                transferLearningHelper.startTraining()
            }
            radioButton.setOnCheckedChangeListener { _, checkedId ->
                if (checkedId == R.id.btnTrainingMode) {
                    // Switch to training mode.
                    viewModel.setCaptureMode(true)
                } else {
                    if (viewModel.getTrainingState() == MainViewModel.TrainingState.PREPARE) {
                        fragmentCameraBinding.btnTrainingMode.isChecked = true
                        fragmentCameraBinding.btnInferenceMode.isChecked = false

                        Toast.makeText(
                            requireContext(), "Inference can only " +
                                    "start after training is done.", Toast
                                .LENGTH_LONG
                        ).show()
                    } else {
                        // Pause the training process and switch to inference mode.
                        transferLearningHelper.pauseTraining()
                        viewModel.setTrainingState(MainViewModel.TrainingState.PAUSE)
                        viewModel.setCaptureMode(false)
                    }
                }
            }

            viewFinder.post {
                // Set up the camera and its use cases
                setUpCamera()
            }
        }

        updateClassLabels()
        updateClassButtonVisualState()
        viewModel.getNumberOfSample()?.let { updateNumberOfSample(it) }
    }

    // Initialize CameraX, and prepare to bind the camera use cases
    private fun setUpCamera() {
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                // CameraProvider
                cameraProvider = cameraProviderFuture.get()

                // Build and bind the camera use cases
                bindCameraUseCases()
            },
            ContextCompat.getMainExecutor(requireContext())
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation =
            fragmentCameraBinding.viewFinder.display.rotation
    }

    // Declare and bind preview, capture and analysis use cases
    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {

        // CameraProvider
        val cameraProvider =
            cameraProvider
                ?: throw IllegalStateException("Camera initialization failed.")

        // CameraSelector - makes assumption that we're only using the back camera
        val cameraSelector =
            CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

        // Preview. Using 640x480 resolution (4:3 aspect ratio) to match our models
        preview =
            Preview.Builder()
                .setTargetResolution(android.util.Size(640, 480))
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .build()

        // ImageAnalysis. Using RGBA 8888 to match how our models work
        imageAnalyzer =
            ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(640, 480))
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                // The analyzer can then be assigned to the instance
                .also {
                    it.setAnalyzer(cameraExecutor) { image ->
                        if (!::bitmapBuffer.isInitialized) {
                            // The image rotation and RGB image buffer are initialized only once
                            // the analyzer has started running
                            bitmapBuffer = Bitmap.createBitmap(
                                image.width,
                                image.height,
                                Bitmap.Config.ARGB_8888
                            )
                        }

                        val sampleClass = addSampleRequests.poll()
                        if (sampleClass != null) {
                            addSample(image, sampleClass)
                            viewModel.increaseNumberOfSample(sampleClass)
                        } else {
                            if (viewModel.getCaptureMode() == false) {
                                classifyImage(image)
                            }
                        }
                        image.close()
                    }
                }

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalyzer
            )

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun classifyImage(image: ImageProxy) {
        // Copy out RGB bits to the shared bitmap buffer
        image.use { bitmapBuffer.copyPixelsFromBuffer(image.planes[0].buffer) }

        val imageRotation = image.imageInfo.rotationDegrees
        // Pass Bitmap and rotation to the transfer learning helper for
        // processing and classification.
        transferLearningHelper.classify(bitmapBuffer, imageRotation)
    }

    private fun addSample(image: ImageProxy, className: String) {
        // Copy out RGB bits to the shared bitmap buffer
        image.use { bitmapBuffer.copyPixelsFromBuffer(image.planes[0].buffer) }

        val imageRotation = image.imageInfo.rotationDegrees
        // Pass Bitmap and rotation to the transfer learning helper for
        // processing and prepare training data.
        transferLearningHelper.addSample(bitmapBuffer, className, imageRotation)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onError(error: String) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onResults(
        results: List<Category>?,
        inferenceTime: Long
    ) {
        activity?.runOnUiThread {
            // Update the result in inference mode.
            if (viewModel.getCaptureMode() == false) {
                // Show result
                results?.let { list ->
                    // Highlight the class which is highest score.
                    list.maxByOrNull { it.score }?.let {
                        highlightResult(it.label)
                    }
                    updateScoreClasses(list)
                }

                fragmentCameraBinding.tvInferenceTime.text =
                    String.format("%d ms", inferenceTime)
            }
        }
    }

    // Show the loss number after each training.
    override fun onLossResults(lossNumber: Float) {
        activity?.runOnUiThread {
            String.format(
                Locale.US,
                "Loss: %.3f", lossNumber
            ).let {
                fragmentCameraBinding.tvLossConsumerPause.text = it
                fragmentCameraBinding.tvLossConsumerResume.text = it
            }
        }
    }

    // Update epoch and progress bar
    override fun onEpochUpdate(epoch: Int, progress: Int) {
        activity?.runOnUiThread {
            viewModel.setTrainingEpoch(epoch)
            viewModel.setTrainingProgress(progress)
            
            fragmentCameraBinding.progressTraining.progress = progress
            fragmentCameraBinding.tvTrainingEpoch.text = "Epoch: $epoch"
        }
    }

    // Training completed callback
    override fun onTrainingComplete() {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), "Training completed! 🎉", Toast.LENGTH_LONG).show()
            viewModel.setTrainingState(MainViewModel.TrainingState.PAUSE)
        }
    }

    // Show the accurate score of each class.
    private fun updateScoreClasses(categories: List<Category>) {
        categories.forEach {
            val view = getClassButtonScore(it.label)
            (view as? TextView)?.text = String.format(
                Locale.US, "%.1f", it.score
            )
        }
    }

    // Get the class button which represent for the label
    private fun getClassButton(label: String): View? {
        return when (label) {
            CLASS_ONE -> fragmentCameraBinding.llClassOne
            CLASS_TWO -> fragmentCameraBinding.llClassTwo
            CLASS_THREE -> fragmentCameraBinding.llClassThree
            CLASS_FOUR -> fragmentCameraBinding.llClassFour
            CLASS_FIVE -> fragmentCameraBinding.llClassFive
            else -> null
        }
    }

    // Get the class button score which represent for the label
    private fun getClassButtonScore(label: String): View? {
        return when (label) {
            CLASS_ONE -> fragmentCameraBinding.tvNumberClassOne
            CLASS_TWO -> fragmentCameraBinding.tvNumberClassTwo
            CLASS_THREE -> fragmentCameraBinding.tvNumberClassThree
            CLASS_FOUR -> fragmentCameraBinding.tvNumberClassFour
            CLASS_FIVE -> fragmentCameraBinding.tvNumberClassFive
            else -> null
        }
    }

    // Get the class name from resource id
    private fun getClassNameFromResourceId(id: Int): String {
        return when (id) {
            fragmentCameraBinding.llClassOne.id -> CLASS_ONE
            fragmentCameraBinding.llClassTwo.id -> CLASS_TWO
            fragmentCameraBinding.llClassThree.id -> CLASS_THREE
            fragmentCameraBinding.llClassFour.id -> CLASS_FOUR
            fragmentCameraBinding.llClassFive.id -> CLASS_FIVE
            else -> {
                ""
            }
        }
    }

    // Highlight the current label and unhighlight the previous label
    private fun highlightResult(label: String?) {
        // skip the previous position if it is no position.
        previousClass?.let {
            setClassButtonHighlight(getClassButton(it), false)
        }
        if (label != null) {
            setClassButtonHighlight(getClassButton(label), true)
        }
        previousClass = label
    }

    private fun setClassButtonHighlight(view: View?, isHighlight: Boolean) {
        view?.run {
            background = AppCompatResources.getDrawable(
                context,
                if (isHighlight) R.drawable.btn_default_highlight else R.drawable.btn_default
            )
        }
    }

    // Update the number of samples. If there are no label in the samples,
    // set it 0.
    private fun updateNumberOfSample(numberOfSamples: Map<String, Int>) {
        setSampleCount(fragmentCameraBinding.tvNumberClassOne, numberOfSamples[CLASS_ONE])
        setSampleCount(fragmentCameraBinding.tvNumberClassTwo, numberOfSamples[CLASS_TWO])
        setSampleCount(fragmentCameraBinding.tvNumberClassThree, numberOfSamples[CLASS_THREE])
        setSampleCount(fragmentCameraBinding.tvNumberClassFour, numberOfSamples[CLASS_FOUR])
        setSampleCount(fragmentCameraBinding.tvNumberClassFive, numberOfSamples[CLASS_FIVE])
    }

    private fun setSampleCount(textView: TextView, count: Int?) {
        val safeCount = count ?: 0
        textView.text = getString(R.string.samples_count_format, safeCount)
    }

    private fun updateTrainingButtonState() {
        with(fragmentCameraBinding) {
            tvInferenceTime.visibility = if (viewModel
                    .getCaptureMode() == true
            ) View.GONE else View.VISIBLE

            btnCollectSample.visibility = if (
                viewModel.getTrainingState() == MainViewModel.TrainingState.PREPARE &&
                (viewModel.getNumberOfSample()?.size ?: 0) == 0 && viewModel
                    .getCaptureMode() == true
            ) View.VISIBLE else View.GONE

            btnStartTrain.visibility = if (
                viewModel.getTrainingState() == MainViewModel.TrainingState.PREPARE &&
                (viewModel.getNumberOfSample()?.size ?: 0) > 0 && viewModel
                    .getCaptureMode() == true
            ) View.VISIBLE else View.GONE

            btnPauseTrain.visibility =
                if (viewModel.getTrainingState() == MainViewModel
                        .TrainingState.TRAINING && viewModel
                        .getCaptureMode() == true
                ) View.VISIBLE else View.GONE

            btnResumeTrain.visibility =
                if (viewModel.getTrainingState() == MainViewModel
                        .TrainingState.PAUSE && viewModel
                        .getCaptureMode() == true
                ) View.VISIBLE else View.GONE

            // Disable adding button when it is training or in inference mode.
            val canCapture = viewModel.getCaptureMode() == true &&
                    viewModel.getTrainingState() != MainViewModel.TrainingState.TRAINING

            listOf(
                llClassOne,
                llClassTwo,
                llClassThree,
                llClassFour,
                llClassFive
            ).forEach { button ->
                button.isEnabled = canCapture
            }

            listOf(
                btnEditClassOne,
                btnEditClassTwo,
                btnEditClassThree,
                btnEditClassFour,
                btnEditClassFive
            ).forEach { editButton ->
                editButton.isEnabled = viewModel.getTrainingState() != MainViewModel.TrainingState.TRAINING
            }
        }

        updateClassButtonVisualState()
    }

    private fun updateClassButtonVisualState() {
        listOf(
            fragmentCameraBinding.llClassOne to CLASS_ONE,
            fragmentCameraBinding.llClassTwo to CLASS_TWO,
            fragmentCameraBinding.llClassThree to CLASS_THREE,
            fragmentCameraBinding.llClassFour to CLASS_FOUR,
            fragmentCameraBinding.llClassFive to CLASS_FIVE
        ).forEach { (button, classId) ->
            val named = viewModel.isClassNamed(classId)
            button.alpha = if (named) 1f else 0.6f
        }
    }

    private fun showSaveModelDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_class_name, null)
        val editText = dialogView.findViewById<EditText>(R.id.etClassName)

        val defaultName = viewModel.getCurrentModelName() ?: viewModel.generateModelName()
        editText.setText(defaultName)
        editText.setSelection(defaultName.length)
        editText.hint = getString(R.string.hint_model_name)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_save_model_title)
            .setView(dialogView)
            .setNegativeButton(R.string.btn_setting_dialog_cancel, null)
            .setPositiveButton(R.string.btn_setting_dialog_confirm, null)
            .create()

        dialog.setOnShowListener {
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positive.setOnClickListener {
                val modelName = editText.text.toString().trim()
                if (modelName.isEmpty()) {
                    editText.error = getString(R.string.error_empty_model_name)
                    return@setOnClickListener
                }

                val checkpointPath = transferLearningHelper.getCheckpointFilePath()
                if (!transferLearningHelper.saveModelWeights()) {
                    Toast.makeText(requireContext(), R.string.toast_model_save_failed, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val classNames = TransferLearningHelper.CLASS_IDS.map { id -> viewModel.getClassName(id) }
                val totalSamples = viewModel.getNumberOfSample()?.values?.sum() ?: 0
                val success = modelManager.saveModel(
                    modelName,
                    viewModel.getModelAccuracy(),
                    classNames,
                    totalSamples,
                    checkpointPath
                )

                if (success) {
                    viewModel.saveCurrentModelName(modelName)
                    Toast.makeText(requireContext(), R.string.toast_model_saved, Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    Toast.makeText(requireContext(), R.string.toast_model_save_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    private fun showModelManagerDialog() {
        val dialogBinding = DialogModelManagerBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        lateinit var adapter: SavedModelsAdapter
        adapter = SavedModelsAdapter(
            onLoad = {
                dialog.dismiss()
                loadSavedModel(it)
            },
            onDelete = { info ->
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.dialog_delete_model_title)
                    .setMessage(getString(R.string.dialog_delete_model_message, info.name))
                    .setPositiveButton(R.string.btn_delete) { _, _ ->
                        if (modelManager.deleteModel(info.fileName)) {
                            Toast.makeText(requireContext(), R.string.toast_model_deleted, Toast.LENGTH_SHORT).show()
                            refreshModelList(dialogBinding, adapter)
                        } else {
                            Toast.makeText(requireContext(), R.string.toast_model_delete_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton(R.string.btn_setting_dialog_cancel, null)
                    .show()
            }
        )

        dialogBinding.rvSavedModels.layoutManager = LinearLayoutManager(requireContext())
        dialogBinding.rvSavedModels.adapter = adapter

        dialogBinding.btnClearAll.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_clear_models_title)
                .setMessage(R.string.dialog_clear_models_message)
                .setPositiveButton(R.string.btn_clear_all) { _, _ ->
                    if (modelManager.clearAllModels()) {
                        Toast.makeText(requireContext(), R.string.toast_models_cleared, Toast.LENGTH_SHORT).show()
                        refreshModelList(dialogBinding, adapter)
                    }
                }
                .setNegativeButton(R.string.btn_setting_dialog_cancel, null)
                .show()
        }

        dialogBinding.btnClose.setOnClickListener { dialog.dismiss() }

        dialog.setOnShowListener { refreshModelList(dialogBinding, adapter) }
        dialog.show()
    }

    private fun refreshModelList(
        binding: DialogModelManagerBinding,
        adapter: SavedModelsAdapter
    ) {
        val models = modelManager.getAllModels()
        adapter.submitList(models)
        binding.tvStorageInfo.text = getString(
            R.string.model_storage_usage,
            modelManager.getTotalStorageUsed() / 1024f
        )
        binding.rvSavedModels.visibility = if (models.isEmpty()) View.GONE else View.VISIBLE
        binding.tvNoModels.visibility = if (models.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun loadSavedModel(modelInfo: ModelManager.ModelInfo) {
        val checkpointPath = transferLearningHelper.getCheckpointFilePath()
        if (!modelManager.loadModel(modelInfo.fileName, checkpointPath)) {
            Toast.makeText(requireContext(), R.string.toast_model_load_failed, Toast.LENGTH_SHORT).show()
            return
        }
        if (!transferLearningHelper.loadModelWeights()) {
            Toast.makeText(requireContext(), R.string.toast_model_load_failed, Toast.LENGTH_SHORT).show()
            return
        }

        transferLearningHelper.clearTrainingSamples()
        viewModel.resetSamples()
        addSampleRequests.clear()

        TransferLearningHelper.CLASS_IDS.forEachIndexed { index, classId ->
            val savedName = modelInfo.classNames.getOrNull(index) ?: viewModel.getClassName(classId)
            viewModel.setClassName(classId, savedName, markNamed = true)
            viewModel.markClassNamed(classId, true)
        }

        updateNumberOfSample(emptyMap())
        updateClassLabels()
        updateClassButtonVisualState()
        updateTrainingButtonState()

        viewModel.saveCurrentModelName(modelInfo.name)
        viewModel.saveModelAccuracy(modelInfo.accuracy)

        Toast.makeText(requireContext(), R.string.toast_model_loaded, Toast.LENGTH_SHORT).show()
    }

    private fun promptResetModel() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_reset_model_title)
            .setMessage(R.string.dialog_reset_model_message)
            .setPositiveButton(R.string.btn_setting_dialog_confirm) { _, _ -> resetModelState() }
            .setNegativeButton(R.string.btn_setting_dialog_cancel, null)
            .show()
    }

    private fun resetModelState() {
        transferLearningHelper.clearTrainingSamples()
        transferLearningHelper.initializeModelWeights()
        File(transferLearningHelper.getCheckpointFilePath()).apply {
            if (exists()) delete()
        }
        addSampleRequests.clear()
        viewModel.resetSamples()
        viewModel.saveModelAccuracy(0f)
        viewModel.saveCurrentModelName("")
        updateNumberOfSample(emptyMap())
        Toast.makeText(requireContext(), R.string.toast_model_reset, Toast.LENGTH_SHORT).show()
    }

    // Show dialog to edit class name
    private fun showEditClassNameDialog(classId: String, requireName: Boolean = false) {
        val themedContext = ContextThemeWrapper(requireContext(), R.style.AppTheme)
        val dialogBinding = DialogEditClassNameBinding.inflate(LayoutInflater.from(themedContext))

        val editText = dialogBinding.etClassName
        val currentName = viewModel.getClassName(classId)
        if (viewModel.isClassNamed(classId)) {
            editText.setText(currentName)
            editText.setSelection(currentName.length)
        } else {
            editText.setText("")
            editText.hint = getString(R.string.class_name_required_hint)
        }

        val dialog = MaterialAlertDialogBuilder(themedContext, R.style.AppAlertDialogTheme)
            .setTitle(R.string.dialog_edit_class_name)
            .setView(dialogBinding.root)
            .create()

        dialog.setCancelable(!requireName)
        dialog.setOnShowListener { dialog.setCanceledOnTouchOutside(!requireName) }

        if (requireName) {
            dialogBinding.btnCancel.visibility = View.GONE
        } else {
            dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        }

        dialogBinding.btnSave.setOnClickListener {
            val newName = editText.text.toString().trim()
            if (newName.isEmpty()) {
                editText.error = getString(R.string.error_empty_class_name)
                return@setOnClickListener
            }
            viewModel.setClassName(classId, newName, markNamed = true)
            viewModel.markClassNamed(classId, true)
            Toast.makeText(requireContext(), R.string.toast_class_name_saved, Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            updateClassLabels()
            updateClassButtonVisualState()
            updateTrainingButtonState()
        }

        dialog.show()
    }

    // Update class labels with custom names
    private fun updateClassLabels() {
        val names = viewModel.classNames.value ?: return
        setClassLabel(fragmentCameraBinding.tvLabelClassOne, CLASS_ONE, names[CLASS_ONE])
        setClassLabel(fragmentCameraBinding.tvLabelClassTwo, CLASS_TWO, names[CLASS_TWO])
        setClassLabel(fragmentCameraBinding.tvLabelClassThree, CLASS_THREE, names[CLASS_THREE])
        setClassLabel(fragmentCameraBinding.tvLabelClassFour, CLASS_FOUR, names[CLASS_FOUR])
        setClassLabel(fragmentCameraBinding.tvLabelClassFive, CLASS_FIVE, names[CLASS_FIVE])
    }

    private fun setClassLabel(textView: TextView, classId: String, rawName: String?) {
        val baseName = rawName ?: getString(R.string.class_label_placeholder)
        val displayName = if (viewModel.isClassNamed(classId)) {
            baseName
        } else {
            getString(R.string.class_label_unset_template, baseName)
        }
        textView.text = displayName
    }

    // Override callbacks for model save/load
    override fun onModelSaved() {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), "Model saved successfully", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onModelLoaded() {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), "Model loaded successfully", Toast.LENGTH_SHORT).show()
        }
    }
}
