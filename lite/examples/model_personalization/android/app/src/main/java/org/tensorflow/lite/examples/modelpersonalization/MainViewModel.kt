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

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import java.util.TreeMap

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val prefsHelper = PreferencesHelper(application)
    private val _numThread = MutableLiveData<Int>()
    val numThreads get() = _numThread

    private val _trainingState =
        MutableLiveData(TrainingState.PREPARE)
    val trainingState get() = _trainingState

    private val _captureMode = MutableLiveData(true)
    val captureMode get() = _captureMode

    private val _numberOfSamples = MutableLiveData(TreeMap<String, Int>())
    val numberOfSamples get() = _numberOfSamples

    // Custom class names - Load from SharedPreferences
    private val _classNames = MutableLiveData<Map<String, String>>()
    val classNames get() = _classNames
    
    init {
        // Load saved class names from SharedPreferences
        _classNames.value = prefsHelper.getAllClassNames()
    }

    // Training progress
    private val _trainingProgress = MutableLiveData<Int>(0)
    val trainingProgress get() = _trainingProgress

    private val _trainingEpoch = MutableLiveData<Int>(0)
    val trainingEpoch get() = _trainingEpoch

    fun configModel(numThreads: Int) {
        _numThread.value = numThreads
    }

    fun getNumThreads() = numThreads.value

    fun setTrainingState(state: TrainingState) {
        _trainingState.value = state
    }

    fun getTrainingState() = trainingState.value

    fun setCaptureMode(isCapture: Boolean) {
        _captureMode.value = isCapture
    }

    fun getCaptureMode() = captureMode.value

    fun increaseNumberOfSample(className: String) {
        val map: TreeMap<String, Int> = _numberOfSamples.value!!
        val currentNumber: Int = if (map.containsKey(className)) {
            map[className]!!
        } else {
            0
        }
        map[className] = currentNumber + 1
        _numberOfSamples.postValue(map)
    }

    fun getNumberOfSample() = numberOfSamples.value

    // Custom class name methods
    fun setClassName(classId: String, newName: String, markNamed: Boolean = true) {
    // Save to SharedPreferences
        prefsHelper.saveClassName(classId, newName)
        if (markNamed) {
            prefsHelper.setClassNamed(classId, true)
    }

        // Update LiveData
        val currentMap = _classNames.value?.toMutableMap() ?: mutableMapOf()
        currentMap[classId] = newName
        _classNames.value = currentMap
    }

    fun getClassName(classId: String): String {
        return prefsHelper.getClassName(classId)
    }

    fun isClassNamed(classId: String): Boolean = prefsHelper.isClassNamed(classId)

    fun markClassNamed(classId: String, named: Boolean = true) {
        prefsHelper.setClassNamed(classId, named)
    }

    fun resetSamples() {
        _numberOfSamples.value = TreeMap()
    }

    fun saveCurrentModelName(name: String) {
        prefsHelper.saveCurrentModelName(name)
    }

    fun getCurrentModelName(): String? = prefsHelper.getCurrentModelName()

    fun saveModelAccuracy(accuracy: Float) {
        prefsHelper.saveModelAccuracy(accuracy)
    }

    fun getModelAccuracy(): Float = prefsHelper.getModelAccuracy()

    fun generateModelName(): String = prefsHelper.generateModelName()
    
    fun getActiveClasses(): Set<String> {
        return prefsHelper.getActiveClasses()
    }
    
    fun setClassActive(classId: String, active: Boolean) {
        prefsHelper.setClassActive(classId, active)
    }

    // Training progress methods
    fun setTrainingProgress(progress: Int) {
        _trainingProgress.value = progress
    }

    fun setTrainingEpoch(epoch: Int) {
        _trainingEpoch.value = epoch
    }

    fun getTrainingProgress() = trainingProgress.value ?: 0
    fun getTrainingEpoch() = trainingEpoch.value ?: 0

    enum class TrainingState {
        PREPARE, TRAINING, PAUSE
    }
}
