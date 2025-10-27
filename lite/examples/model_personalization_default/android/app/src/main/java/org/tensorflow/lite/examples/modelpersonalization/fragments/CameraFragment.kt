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
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.tensorflow.lite.examples.modelpersonalization.ClassAdapter
import org.tensorflow.lite.examples.modelpersonalization.MainViewModel
import org.tensorflow.lite.examples.modelpersonalization.PredictionAdapter
import org.tensorflow.lite.examples.modelpersonalization.PredictionItem
import org.tensorflow.lite.examples.modelpersonalization.R
import org.tensorflow.lite.examples.modelpersonalization.TrainingClass
import org.tensorflow.lite.examples.modelpersonalization.TransferLearningHelper
import org.tensorflow.lite.examples.modelpersonalization.databinding.BottomSheetClassSelectorBinding
import org.tensorflow.lite.examples.modelpersonalization.databinding.FragmentCameraBinding
import org.tensorflow.lite.support.label.Category
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraFragment : Fragment(), TransferLearningHelper.ClassifierListener {

    companion object {
        private const val TAG = "Model Personalization"
        private const val NOTIFICATION_AUTO_HIDE_DELAY = 3000L
    }

    private var _fragmentCameraBinding: FragmentCameraBinding? = null
    private val fragmentCameraBinding get() = _fragmentCameraBinding!!

    private lateinit var transferLearningHelper: TransferLearningHelper
    private lateinit var bitmapBuffer: Bitmap

    private val viewModel: MainViewModel by activityViewModels()
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private lateinit var cameraExecutor: ExecutorService
    private val addSampleRequests = ConcurrentLinkedQueue<String>()
    
    private val notificationHandler = Handler(Looper.getMainLooper())
    private var bottomSheetDialog: BottomSheetDialog? = null
    private lateinit var classAdapter: ClassAdapter
    private lateinit var predictionAdapter: PredictionAdapter

    override fun onResume() {
        super.onResume()
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(requireActivity(), R.id.fragment_container)
                .navigate(CameraFragmentDirections.actionCameraToPermissions())
        }
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()
        cameraExecutor.shutdown()
        bottomSheetDialog?.dismiss()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false)
        return fragmentCameraBinding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeComponents()
        setupObservers()
        setupClickListeners()
        setupCamera()
        
        // Show welcome notification
        showNotification(getString(R.string.notif_welcome))
    }

    private fun initializeComponents() {
        transferLearningHelper = TransferLearningHelper(
            context = requireContext(),
            classifierListener = this
        )
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        classAdapter = ClassAdapter(
            onClassSelected = { trainingClass ->
                viewModel.selectClass(trainingClass)
                updateUIForSelectedClass(trainingClass)
                bottomSheetDialog?.dismiss()
            },
            onEditClass = { trainingClass ->
                showEditClassDialog(trainingClass)
            },
            onDeleteClass = { trainingClass ->
                showDeleteClassDialog(trainingClass)
            }
        )
        
        predictionAdapter = PredictionAdapter()
        
        // Setup predictions RecyclerView
        fragmentCameraBinding.rvPredictions.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = predictionAdapter
        }
    }

    private fun setupObservers() {
        viewModel.numThreads.observe(viewLifecycleOwner) {
            transferLearningHelper.numThreads = it
            transferLearningHelper.close()
            if (viewModel.getTrainingState() != MainViewModel.TrainingState.PREPARE) {
                viewModel.setTrainingState(MainViewModel.TrainingState.TRAINING)
                transferLearningHelper.startTraining()
            }
        }

        viewModel.trainingState.observe(viewLifecycleOwner) {
            updateTrainingButtonState()
        }

        viewModel.captureMode.observe(viewLifecycleOwner) { isCaptureMode ->
            updateTrainingButtonState()
        }

        viewModel.numberOfSamples.observe(viewLifecycleOwner) {
            updateTrainingButtonState()
            updateSampleCount()
        }

        viewModel.trainingClasses.observe(viewLifecycleOwner) { classes ->
            classAdapter.submitList(classes)
            updateUIForClassList(classes)
        }

        viewModel.selectedClass.observe(viewLifecycleOwner) { selectedClass ->
            selectedClass?.let {
                updateUIForSelectedClass(it)
            }
        }
    }

    private fun setupClickListeners() {
        with(fragmentCameraBinding) {
            // Class selector button - shows bottom sheet
            btnClassSelector.setOnClickListener {
                showClassSelectorBottomSheet()
            }

            // Capture sample button
            btnCaptureSample.setOnClickListener {
                try {
                    val selectedClass = viewModel.selectedClass.value
                    if (selectedClass != null) {
                        val classLabel = selectedClass.getClassLabel()
                        Log.d(TAG, "Capturing sample for class: ${selectedClass.name}, label: $classLabel")
                        addSampleRequests.add(classLabel)
                        showNotification("📸 Sample captured!")
                    } else {
                        Log.w(TAG, "No class selected")
                        showNotification(getString(R.string.toast_select_class_first))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error capturing sample", e)
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            // Training buttons
            btnStartTrain.setOnClickListener {
                viewModel.setTrainingState(MainViewModel.TrainingState.TRAINING)
                transferLearningHelper.startTraining()
                showNotification("🚀 Training started...")
            }

            btnPauseTrain.setOnClickListener {
                viewModel.setTrainingState(MainViewModel.TrainingState.PAUSE)
                transferLearningHelper.pauseTraining()
                showNotification("⏸️ Training paused")
            }

            btnResumeTrain.setOnClickListener {
                viewModel.setTrainingState(MainViewModel.TrainingState.TRAINING)
                transferLearningHelper.startTraining()
                showNotification("▶️ Training resumed...")
            }

            // Mode switch
            radioButton.setOnCheckedChangeListener { _, checkedId ->
                when (checkedId) {
                    R.id.btnTrainingMode -> {
                        viewModel.setCaptureMode(true)
                        showNotification("📚 Training mode")
                    }
                    R.id.btnInferenceMode -> {
                        if (viewModel.getTrainingState() == MainViewModel.TrainingState.PREPARE) {
                            btnTrainingMode.isChecked = true
                            btnInferenceMode.isChecked = false
                            Toast.makeText(
                                requireContext(),
                                "Inference can only start after training is done.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            transferLearningHelper.pauseTraining()
                            viewModel.setTrainingState(MainViewModel.TrainingState.PAUSE)
                            viewModel.setCaptureMode(false)
                            showNotification("🔍 Inference mode")
                        }
                    }
                }
            }

            // Set initial mode
            if (viewModel.getCaptureMode() == true) {
                btnTrainingMode.isChecked = true
            } else {
                btnInferenceMode.isChecked = true
            }
        }
    }

    private fun showClassSelectorBottomSheet() {
        val bottomSheetBinding = BottomSheetClassSelectorBinding.inflate(layoutInflater)
        bottomSheetDialog = BottomSheetDialog(requireContext())
        bottomSheetDialog?.setContentView(bottomSheetBinding.root)

        with(bottomSheetBinding) {
            // Setup RecyclerView
            rvClasses.apply {
                layoutManager = LinearLayoutManager(requireContext())
                adapter = classAdapter
            }

            // Update class count label
            val classes = viewModel.trainingClasses.value ?: emptyList()
            tvActiveClasses.text = getString(R.string.bottom_sheet_active_classes, classes.size)

            // Hide Add button if already 5 classes
            if (classes.size >= 5) {
                btnAddNewClass.visibility = View.GONE
            } else {
                btnAddNewClass.visibility = View.VISIBLE
            }

            // Add new class button
            btnAddNewClass.setOnClickListener {
                showAddClassDialog()
            }
        }

        bottomSheetDialog?.show()
    }

    private fun showAddClassDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.dialog_add_class_hint)
            setPadding(50, 20, 50, 20)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_add_class_title))
            .setView(input)
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.toast_name_required),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    if (viewModel.addClass(name)) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.toast_class_added, name),
                            Toast.LENGTH_SHORT
                        ).show()
                        showNotification("✅ Class \"$name\" added")
                    } else {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.toast_duplicate_name),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showEditClassDialog(trainingClass: TrainingClass) {
        val input = EditText(requireContext()).apply {
            setText(trainingClass.name)
            hint = getString(R.string.dialog_add_class_hint)
            setPadding(50, 20, 50, 20)
            selectAll()
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_rename_class_title))
            .setView(input)
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.toast_name_required),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    if (viewModel.renameClass(trainingClass, newName)) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.toast_class_renamed, newName),
                            Toast.LENGTH_SHORT
                        ).show()
                        showNotification("✏️ Renamed to \"$newName\"")
                    } else {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.toast_duplicate_name),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDeleteClassDialog(trainingClass: TrainingClass) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_delete_class_title))
            .setMessage(
                getString(
                    R.string.dialog_delete_class_message,
                    trainingClass.name,
                    trainingClass.sampleCount
                )
            )
            .setPositiveButton(getString(R.string.btn_delete)) { _, _ ->
                if (viewModel.deleteClass(trainingClass)) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.toast_class_deleted, trainingClass.name),
                        Toast.LENGTH_SHORT
                    ).show()
                    showNotification("🗑️ Class deleted")
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateUIForSelectedClass(trainingClass: TrainingClass) {
        with(fragmentCameraBinding) {
            btnClassSelector.text = trainingClass.name
            btnCaptureSample.visibility = View.VISIBLE
            updateSampleCount()
        }
    }

    private fun updateUIForClassList(classes: List<TrainingClass>) {
        with(fragmentCameraBinding) {
            if (classes.isEmpty()) {
                btnClassSelector.text = getString(R.string.btn_add_class)
                btnCaptureSample.visibility = View.GONE
                tvSampleInfo.visibility = View.GONE
            }
        }
    }

    private fun updateSampleCount() {
        val selectedClass = viewModel.selectedClass.value
        val numberOfSamples = viewModel.getNumberOfSample()
        
        if (selectedClass != null && numberOfSamples != null) {
            // Use classLabel to get count, not custom name
            val count = numberOfSamples[selectedClass.getClassLabel()] ?: 0
            fragmentCameraBinding.tvSampleInfo.apply {
                text = getString(R.string.samples_count, count)
                visibility = if (count > 0) View.VISIBLE else View.GONE
            }
            
            // Update notification based on sample count
            if (count == 1) {
                showNotification(getString(R.string.notif_collect_samples))
            } else if (count >= 10 && viewModel.getTrainingState() == MainViewModel.TrainingState.PREPARE) {
                showNotification(getString(R.string.notif_ready_to_train))
            }
        }
    }

    private fun showNotification(message: String) {
        fragmentCameraBinding.tvNotification.apply {
            text = message
            visibility = View.VISIBLE
        }
        
        // Auto-hide after 3 seconds
        notificationHandler.removeCallbacksAndMessages(null)
        notificationHandler.postDelayed({
            fragmentCameraBinding.tvNotification.visibility = View.GONE
        }, NOTIFICATION_AUTO_HIDE_DELAY)
    }

    private fun updateTrainingButtonState() {
        with(fragmentCameraBinding) {
            val isCaptureMode = viewModel.getCaptureMode() == true
            val trainingState = viewModel.getTrainingState()
            val sampleCount = viewModel.getNumberOfSample()?.values?.sum() ?: 0

            // Show inference container in inference mode, hide in training mode
            inferenceContainer.visibility = if (!isCaptureMode) View.VISIBLE else View.GONE
            
            // Hide old inference time display
            tvInferenceTime.visibility = View.GONE

            // Show/hide training UI
            optionsLayout.visibility = if (isCaptureMode) View.VISIBLE else View.GONE

            btnCollectSample.visibility = if (
                trainingState == MainViewModel.TrainingState.PREPARE &&
                sampleCount == 0 && isCaptureMode
            ) View.VISIBLE else View.GONE

            btnStartTrain.visibility = if (
                trainingState == MainViewModel.TrainingState.PREPARE &&
                sampleCount > 0 && isCaptureMode
            ) View.VISIBLE else View.GONE

            btnPauseTrain.visibility = if (
                trainingState == MainViewModel.TrainingState.TRAINING && isCaptureMode
            ) View.VISIBLE else View.GONE

            btnResumeTrain.visibility = if (
                trainingState == MainViewModel.TrainingState.PAUSE && isCaptureMode
            ) View.VISIBLE else View.GONE

            // Disable capture when training
            val canCapture = isCaptureMode && trainingState != MainViewModel.TrainingState.TRAINING
            btnCaptureSample.isEnabled = canCapture
            btnClassSelector.isEnabled = canCapture
        }
    }

    private fun setupCamera() {
        fragmentCameraBinding.viewFinder.post {
            setUpCamera()
        }
    }

    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            },
            ContextCompat.getMainExecutor(requireContext())
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation = fragmentCameraBinding.viewFinder.display.rotation
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        val rotation = fragmentCameraBinding.viewFinder.display.rotation

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(rotation)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor) { image ->
                    if (!::bitmapBuffer.isInitialized) {
                        bitmapBuffer = Bitmap.createBitmap(
                            image.width,
                            image.height,
                            Bitmap.Config.ARGB_8888
                        )
                    }

                    val sampleRequest = addSampleRequests.poll()
                    if (sampleRequest != null) {
                        addSample(image, sampleRequest)
                    } else if (viewModel.getCaptureMode() == false) {
                        classifyImage(image)
                    }

                    image.close()
                }
            }

        cameraProvider.unbindAll()

        try {
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalyzer
            )
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun classifyImage(image: ImageProxy) {
        image.use { bitmapBuffer.copyPixelsFromBuffer(image.planes[0].buffer) }
        val imageRotation = image.imageInfo.rotationDegrees
        transferLearningHelper.classify(bitmapBuffer, imageRotation)
    }

    private fun addSample(image: ImageProxy, classLabel: String) {
        try {
            Log.d(TAG, "addSample called with classLabel: $classLabel")
            image.use { bitmapBuffer.copyPixelsFromBuffer(image.planes[0].buffer) }
            val imageRotation = image.imageInfo.rotationDegrees
            transferLearningHelper.addSample(bitmapBuffer, classLabel, imageRotation)
            
            // Update sample count in ViewModel
            viewModel.increaseNumberOfSample(classLabel)
            Log.d(TAG, "Sample added successfully for classLabel: $classLabel")
        } catch (e: Exception) {
            Log.e(TAG, "Error in addSample", e)
            activity?.runOnUiThread {
                Toast.makeText(requireContext(), "Error adding sample: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onError(error: String) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onResults(results: List<Category>?, inferenceTime: Long) {
        activity?.runOnUiThread {
            if (viewModel.getCaptureMode() == false) {
                results?.let { categories ->
                    // Update inference header with time
                    fragmentCameraBinding.tvInferenceHeader.text = String.format(
                        Locale.US,
                        "📊 Real-time Predictions (⚡ %d ms)",
                        inferenceTime
                    )
                    
                    // Convert categories to prediction items with custom class names
                    val predictions = convertToPredictionItems(categories)
                    predictionAdapter.submitList(predictions)
                }
            }
        }
    }
    
    private fun convertToPredictionItems(categories: List<Category>): List<PredictionItem> {
        val trainingClasses = viewModel.trainingClasses.value ?: emptyList()
        
        // Find top prediction
        val maxConfidence = categories.maxOfOrNull { it.score } ?: 0f
        
        return categories.map { category ->
            // Map category label (1,2,3,4,5) to custom class name
            val trainingClass = trainingClasses.find { it.getClassLabel() == category.label }
            val className = trainingClass?.name ?: category.label
            
            PredictionItem(
                className = className,
                confidence = category.score,
                isTopPrediction = category.score == maxConfidence
            )
        }.sortedByDescending { it.confidence }
    }

    override fun onLossResults(lossNumber: Float) {
        val lossText = String.format(Locale.US, "Loss: %.3f", lossNumber)
        fragmentCameraBinding.tvLossConsumerPause.text = lossText
        fragmentCameraBinding.tvLossConsumerResume.text = lossText
    }
}
