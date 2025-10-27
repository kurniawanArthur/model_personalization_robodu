package org.tensorflow.lite.examples.modelpersonalization

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.util.TreeMap

class MainViewModel : ViewModel() {
    private val _numThread = MutableLiveData<Int>()
    val numThreads get() = _numThread

    private val _trainingState =
        MutableLiveData(TrainingState.PREPARE)
    val trainingState get() = _trainingState

    private val _captureMode = MutableLiveData(true)
    val captureMode get() = _captureMode

    private val _numberOfSamples = MutableLiveData(TreeMap<String, Int>())
    val numberOfSamples get() = _numberOfSamples

    // New class management
    private val _trainingClasses = MutableLiveData<List<TrainingClass>>(emptyList())
    val trainingClasses get() = _trainingClasses

    private val _selectedClass = MutableLiveData<TrainingClass?>(null)
    val selectedClass get() = _selectedClass

    private var nextClassId = 0

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

    fun increaseNumberOfSample(classLabel: String) {
        // Update samples map
        val map: TreeMap<String, Int> = TreeMap(_numberOfSamples.value ?: TreeMap())
        val currentNumber: Int = map[classLabel] ?: 0
        map[classLabel] = currentNumber + 1
        _numberOfSamples.postValue(map)
        
        // Update TrainingClass sample count
        val currentClasses = _trainingClasses.value ?: return
        val updatedClasses = currentClasses.map { trainingClass ->
            if (trainingClass.getClassLabel() == classLabel) {
                trainingClass.copy(sampleCount = trainingClass.sampleCount + 1)
            } else {
                trainingClass
            }
        }
        _trainingClasses.postValue(updatedClasses)
    }

    fun getNumberOfSample() = numberOfSamples.value

    // Class management methods
    fun addClass(name: String): Boolean {
        val currentClasses = _trainingClasses.value ?: emptyList()
        
        // Check max 5 classes
        if (currentClasses.size >= 5) return false
        
        // Check duplicate name
        if (currentClasses.any { it.name.equals(name, ignoreCase = true) }) return false
        
        val newClass = TrainingClass(
            id = nextClassId++,
            name = name,
            sampleCount = 0,
            isSelected = currentClasses.isEmpty() // Auto-select if first class
        )
        
        val updatedClasses = currentClasses.toMutableList()
        updatedClasses.add(newClass)
        _trainingClasses.value = updatedClasses
        
        if (newClass.isSelected) {
            _selectedClass.value = newClass
        }
        
        return true
    }

    fun deleteClass(classToDelete: TrainingClass): Boolean {
        val currentClasses = _trainingClasses.value ?: return false
        val updatedClasses = currentClasses.toMutableList()
        
        if (!updatedClasses.remove(classToDelete)) return false
        
        // Remove from samples map using classLabel
        val map: TreeMap<String, Int> = _numberOfSamples.value!!
        map.remove(classToDelete.getClassLabel())
        _numberOfSamples.postValue(map)
        
        // If deleted class was selected, select another
        if (_selectedClass.value?.id == classToDelete.id) {
            val newSelected = updatedClasses.firstOrNull()
            if (newSelected != null) {
                newSelected.isSelected = true
                _selectedClass.value = newSelected
            } else {
                _selectedClass.value = null
            }
        }
        
        _trainingClasses.value = updatedClasses
        return true
    }

    fun renameClass(classToRename: TrainingClass, newName: String): Boolean {
        val currentClasses = _trainingClasses.value ?: return false
        
        // Check duplicate name (excluding current class)
        if (currentClasses.any { 
            it.id != classToRename.id && it.name.equals(newName, ignoreCase = true) 
        }) return false
        
        // Just update the name, no need to update map (uses classLabel, not name)
        classToRename.name = newName
        
        _trainingClasses.value = currentClasses.toList()
        
        // Update selected if needed
        if (_selectedClass.value?.id == classToRename.id) {
            _selectedClass.value = classToRename
        }
        
        return true
    }

    fun selectClass(classToSelect: TrainingClass) {
        val currentClasses = _trainingClasses.value ?: return
        val updatedClasses = currentClasses.map { 
            it.copy(isSelected = it.id == classToSelect.id)
        }
        _trainingClasses.value = updatedClasses
        _selectedClass.value = updatedClasses.find { it.isSelected }
    }

    fun getSelectedClassName(): String? {
        return _selectedClass.value?.name
    }

    enum class TrainingState {
        PREPARE, TRAINING, PAUSE
    }
}

