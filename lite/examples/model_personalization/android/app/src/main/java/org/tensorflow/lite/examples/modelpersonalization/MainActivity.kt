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

import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import org.tensorflow.lite.examples.modelpersonalization.databinding.ActivityMainBinding
import org.tensorflow.lite.examples.modelpersonalization.databinding.DialogModelManagerBinding
import org.tensorflow.lite.examples.modelpersonalization.fragments.HelperDialog
import org.tensorflow.lite.examples.modelpersonalization.fragments.SettingFragment

class MainActivity : AppCompatActivity() {
    private lateinit var activityMainBinding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var modelManager: ModelManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityMainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(activityMainBinding.root)
        
        modelManager = ModelManager(this)

        activityMainBinding.imgSetting.setOnClickListener {
            if (viewModel.getCaptureMode() == true) {
                SettingFragment().show(
                    supportFragmentManager,
                    SettingFragment.TAG
                )
            } else {
                Toast.makeText(
                    this, "Change the setting only available in " +
                            "training mode", Toast.LENGTH_LONG
                ).show()
            }
        }
        activityMainBinding.tvHelper.setOnClickListener {
            HelperDialog().show(supportFragmentManager, HelperDialog.TAG)
        }
        activityMainBinding.imgModelManager.setOnClickListener {
            showModelManagerDialog()
        }
        
        // Setup back press handler
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                    // Workaround for Android Q memory leak issue in IRequestFinishCallback$Stub.
                    // (https://issuetracker.google.com/issues/139738913)
                    finishAfterTransition()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }
    
    private fun showModelManagerDialog() {
        val dialogBinding = DialogModelManagerBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        lateinit var adapter: SavedModelsAdapter
        adapter = SavedModelsAdapter(
            onLoad = { model ->
                loadModel(model)
                dialog.dismiss()
            },
            onDelete = { model ->
                confirmDeleteModel(model) {
                    refreshModelList(dialogBinding, adapter)
                }
            }
        )

        dialogBinding.rvSavedModels.layoutManager = LinearLayoutManager(this)
        dialogBinding.rvSavedModels.adapter = adapter

        dialogBinding.btnClearAll.setOnClickListener {
            confirmClearAllModels {
                refreshModelList(dialogBinding, adapter)
            }
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

        val storageKb = modelManager.getTotalStorageUsed() / 1024f
        binding.tvStorageInfo.text = getString(R.string.model_storage_usage, storageKb)

        val hasModels = models.isNotEmpty()
        binding.rvSavedModels.visibility = if (hasModels) android.view.View.VISIBLE else android.view.View.GONE
        binding.tvNoModels.visibility = if (hasModels) android.view.View.GONE else android.view.View.VISIBLE
    }
    
    private fun loadModel(model: ModelManager.ModelInfo) {
        // TODO: Implement loading model into TransferLearningHelper
        Toast.makeText(this, "Loading ${model.name}...", Toast.LENGTH_SHORT).show()
        // This would need to communicate with CameraFragment's TransferLearningHelper
    }
    
    private fun confirmDeleteModel(model: ModelManager.ModelInfo, onDeleted: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Delete Model")
            .setMessage("Are you sure you want to delete '${model.name}'?")
            .setPositiveButton("Delete") { _, _ ->
                if (modelManager.deleteModel(model.fileName)) {
                    Toast.makeText(this, "Model deleted", Toast.LENGTH_SHORT).show()
                    onDeleted()
                } else {
                    Toast.makeText(this, "Failed to delete model", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun confirmClearAllModels(onCleared: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Clear All Models")
            .setMessage("Are you sure you want to delete ALL saved models? This cannot be undone.")
            .setPositiveButton("Delete All") { _, _ ->
                if (modelManager.clearAllModels()) {
                    Toast.makeText(this, "All models cleared", Toast.LENGTH_SHORT).show()
                    onCleared()
                } else {
                    Toast.makeText(this, "Failed to clear models", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

}
