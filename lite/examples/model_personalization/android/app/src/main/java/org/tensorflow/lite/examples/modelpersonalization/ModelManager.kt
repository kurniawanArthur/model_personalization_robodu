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
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manager class for handling multiple saved models
 */
class ModelManager(private val context: Context) {
    
    private val modelsDir: File
        get() = File(context.filesDir, "trained_models").also {
            if (!it.exists()) it.mkdirs()
        }
    
    companion object {
        private const val TAG = "ModelManager"
        private const val MODEL_EXTENSION = ".model"
        private const val INFO_EXTENSION = ".info"
    }
    
    data class ModelInfo(
        val name: String,
        val fileName: String,
        val accuracy: Float,
        val classNames: List<String>,
        val numSamples: Int,
        val timestamp: Long,
        val filePath: String
    ) {
        fun getDisplayName(): String {
            val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            val date = dateFormat.format(Date(timestamp))
            val accuracyStr = if (accuracy > 0f) {
                String.format("%.1f%%", accuracy * 100)
            } else {
                "--"
            }
            return "$name - $accuracyStr ($date)"
        }
        
        fun getSizeInKB(): Long {
            return File(filePath).length() / 1024
        }
    }
    
    /**
     * Save current model with given name and metadata
     */
    fun saveModel(
        name: String,
        accuracy: Float,
        classNames: List<String>,
        numSamples: Int,
        sourceFilePath: String
    ): Boolean {
        return try {
            val timestamp = System.currentTimeMillis()
            val sanitizedName = name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val fileName = "${sanitizedName}_${timestamp}"
            
            // Copy model file
            val modelFile = File(modelsDir, fileName + MODEL_EXTENSION)
            File(sourceFilePath).copyTo(modelFile, overwrite = true)
            
            // Save metadata
            val infoFile = File(modelsDir, fileName + INFO_EXTENSION)
            val infoContent = """
                name=$name
                accuracy=$accuracy
                classNames=${classNames.joinToString(",")}
                numSamples=$numSamples
                timestamp=$timestamp
            """.trimIndent()
            infoFile.writeText(infoContent)
            
            Log.d(TAG, "Model saved: $fileName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save model: ${e.message}")
            false
        }
    }
    
    /**
     * Load a specific model by file name
     */
    fun loadModel(fileName: String, targetPath: String): Boolean {
        return try {
            val modelFile = File(modelsDir, fileName + MODEL_EXTENSION)
            if (!modelFile.exists()) {
                Log.e(TAG, "Model file not found: $fileName")
                return false
            }
            
            modelFile.copyTo(File(targetPath), overwrite = true)
            Log.d(TAG, "Model loaded: $fileName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model: ${e.message}")
            false
        }
    }
    
    /**
     * Get list of all saved models
     */
    fun getAllModels(): List<ModelInfo> {
        val models = mutableListOf<ModelInfo>()
        
        modelsDir.listFiles { file -> 
            file.extension == MODEL_EXTENSION.removePrefix(".")
        }?.forEach { modelFile ->
            val fileName = modelFile.nameWithoutExtension
            val infoFile = File(modelsDir, fileName + INFO_EXTENSION)
            
            if (infoFile.exists()) {
                try {
                    val info = parseInfoFile(infoFile)
                    models.add(ModelInfo(
                        name = info["name"] ?: fileName,
                        fileName = fileName,
                        accuracy = info["accuracy"]?.toFloatOrNull() ?: 0f,
                        classNames = info["classNames"]?.split(",")?.map { it.trim() } ?: emptyList(),
                        numSamples = info["numSamples"]?.toIntOrNull() ?: 0,
                        timestamp = info["timestamp"]?.toLongOrNull() ?: 0L,
                        filePath = modelFile.absolutePath
                    ))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse model info: ${e.message}")
                }
            }
        }
        
        return models.sortedByDescending { it.timestamp }
    }
    
    /**
     * Delete a specific model
     */
    fun deleteModel(fileName: String): Boolean {
        return try {
            val modelFile = File(modelsDir, fileName + MODEL_EXTENSION)
            val infoFile = File(modelsDir, fileName + INFO_EXTENSION)
            
            var success = true
            if (modelFile.exists()) success = modelFile.delete() && success
            if (infoFile.exists()) success = infoFile.delete() && success
            
            Log.d(TAG, "Model deleted: $fileName")
            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete model: ${e.message}")
            false
        }
    }
    
    /**
     * Get total storage used by saved models
     */
    fun getTotalStorageUsed(): Long {
        var total = 0L
        modelsDir.listFiles()?.forEach { file ->
            total += file.length()
        }
        return total
    }
    
    /**
     * Clear all saved models
     */
    fun clearAllModels(): Boolean {
        return try {
            modelsDir.deleteRecursively()
            modelsDir.mkdirs()
            Log.d(TAG, "All models cleared")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear models: ${e.message}")
            false
        }
    }
    
    /**
     * Generate a unique model name based on class names and accuracy
     */
    fun generateModelName(classNames: List<String>, accuracy: Float): String {
        val classNamesStr = classNames.joinToString("_") { 
            it.replace(" ", "").take(8) 
        }.take(30)
        val accuracyInt = (accuracy * 100).toInt()
        return "model_${classNamesStr}_${accuracyInt}pct"
    }
    
    /**
     * Check if a model with the given name already exists
     */
    fun modelExists(fileName: String): Boolean {
        val modelFile = File(modelsDir, fileName + MODEL_EXTENSION)
        return modelFile.exists()
    }
    
    private fun parseInfoFile(file: File): Map<String, String> {
        val map = mutableMapOf<String, String>()
        file.readLines().forEach { line ->
            val parts = line.split("=", limit = 2)
            if (parts.size == 2) {
                map[parts[0]] = parts[1]
            }
        }
        return map
    }
}
