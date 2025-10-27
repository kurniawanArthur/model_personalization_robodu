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
import android.content.SharedPreferences
import org.tensorflow.lite.examples.modelpersonalization.TransferLearningHelper.Companion.CLASS_FIVE
import org.tensorflow.lite.examples.modelpersonalization.TransferLearningHelper.Companion.CLASS_FOUR
import org.tensorflow.lite.examples.modelpersonalization.TransferLearningHelper.Companion.CLASS_IDS
import org.tensorflow.lite.examples.modelpersonalization.TransferLearningHelper.Companion.CLASS_ONE
import org.tensorflow.lite.examples.modelpersonalization.TransferLearningHelper.Companion.CLASS_THREE
import org.tensorflow.lite.examples.modelpersonalization.TransferLearningHelper.Companion.CLASS_TWO

/**
 * Helper class to manage SharedPreferences for app settings and data persistence
 */
class PreferencesHelper(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    
    companion object {
        private const val PREFS_NAME = "robo_du_model_personalization"
        
        // Class names keys
    private const val KEY_CLASS_NAME_1 = "class_name_1"
    private const val KEY_CLASS_NAME_2 = "class_name_2"
    private const val KEY_CLASS_NAME_3 = "class_name_3"
    private const val KEY_CLASS_NAME_4 = "class_name_4"
    private const val KEY_CLASS_NAME_5 = "class_name_5"

    private const val KEY_CLASS_NAMED_1 = "class_named_1"
    private const val KEY_CLASS_NAMED_2 = "class_named_2"
    private const val KEY_CLASS_NAMED_3 = "class_named_3"
    private const val KEY_CLASS_NAMED_4 = "class_named_4"
    private const val KEY_CLASS_NAMED_5 = "class_named_5"
        
        // Active classes
        private const val KEY_ACTIVE_CLASSES = "active_classes"
        
        // Current model info
        private const val KEY_CURRENT_MODEL_NAME = "current_model_name"
        private const val KEY_LAST_ACCURACY = "last_accuracy"
        private const val KEY_LAST_TRAINED_TIME = "last_trained_time"
        
        // Default class names
        private const val DEFAULT_CLASS_1 = "Class 1"
        private const val DEFAULT_CLASS_2 = "Class 2"
        private const val DEFAULT_CLASS_3 = "Class 3"
        private const val DEFAULT_CLASS_4 = "Class 4"
        private const val DEFAULT_CLASS_5 = "Class 5"
    }
    
    // ===== CLASS NAMES =====
    
    fun saveClassName(classId: String, name: String) {
        val key = when (classId) {
            CLASS_ONE -> KEY_CLASS_NAME_1
            CLASS_TWO -> KEY_CLASS_NAME_2
            CLASS_THREE -> KEY_CLASS_NAME_3
            CLASS_FOUR -> KEY_CLASS_NAME_4
            CLASS_FIVE -> KEY_CLASS_NAME_5
            else -> return
        }
        prefs.edit().putString(key, name).apply()
    }
    
    fun getClassName(classId: String): String {
        val (key, default) = when (classId) {
            CLASS_ONE -> KEY_CLASS_NAME_1 to DEFAULT_CLASS_1
            CLASS_TWO -> KEY_CLASS_NAME_2 to DEFAULT_CLASS_2
            CLASS_THREE -> KEY_CLASS_NAME_3 to DEFAULT_CLASS_3
            CLASS_FOUR -> KEY_CLASS_NAME_4 to DEFAULT_CLASS_4
            CLASS_FIVE -> KEY_CLASS_NAME_5 to DEFAULT_CLASS_5
            else -> return classId
        }
        return prefs.getString(key, default) ?: default
    }
    
    fun getAllClassNames(): Map<String, String> {
        return mapOf(
            CLASS_ONE to getClassName(CLASS_ONE),
            CLASS_TWO to getClassName(CLASS_TWO),
            CLASS_THREE to getClassName(CLASS_THREE),
            CLASS_FOUR to getClassName(CLASS_FOUR),
            CLASS_FIVE to getClassName(CLASS_FIVE)
        )
    }
    
    // ===== ACTIVE CLASSES =====
    
    fun saveActiveClasses(classes: Set<String>) {
        prefs.edit().putStringSet(KEY_ACTIVE_CLASSES, classes).apply()
    }
    
    fun getActiveClasses(): Set<String> {
        return prefs.getStringSet(KEY_ACTIVE_CLASSES, 
            setOf(CLASS_ONE, CLASS_TWO, CLASS_THREE, CLASS_FOUR, CLASS_FIVE)
        ) ?: setOf(CLASS_ONE, CLASS_TWO, CLASS_THREE, CLASS_FOUR, CLASS_FIVE)
    }
    
    fun isClassActive(classId: String): Boolean {
        return getActiveClasses().contains(classId)
    }
    
    fun setClassActive(classId: String, active: Boolean) {
        val current = getActiveClasses().toMutableSet()
        if (active) {
            current.add(classId)
        } else {
            current.remove(classId)
        }
        saveActiveClasses(current)
    }
    
    // ===== MODEL INFO =====
    
    fun saveCurrentModelName(name: String) {
        prefs.edit().putString(KEY_CURRENT_MODEL_NAME, name).apply()
    }
    
    fun getCurrentModelName(): String? {
        return prefs.getString(KEY_CURRENT_MODEL_NAME, null)
    }
    
    fun saveModelAccuracy(accuracy: Float) {
        prefs.edit().putFloat(KEY_LAST_ACCURACY, accuracy).apply()
    }
    
    fun getModelAccuracy(): Float {
        return prefs.getFloat(KEY_LAST_ACCURACY, 0f)
    }
    
    fun saveLastTrainedTime(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_TRAINED_TIME, timestamp).apply()
    }
    
    fun getLastTrainedTime(): Long {
        return prefs.getLong(KEY_LAST_TRAINED_TIME, 0L)
    }
    
    // ===== MODEL GENERATION =====
    
    fun generateModelName(): String {
        val activeClasses = getActiveClasses()
        val orderedClasses = CLASS_IDS.filter { activeClasses.contains(it) }
        val classNamesStr = orderedClasses.joinToString("_") { classId ->
            getClassName(classId).replace(" ", "").take(8)
        }
        val accuracy = (getModelAccuracy() * 100).toInt()
        val timestamp = System.currentTimeMillis()
        
        return "model_${classNamesStr}_${accuracy}pct_$timestamp"
    }
    
    // ===== UTILITY =====
    
    fun clearAll() {
        prefs.edit().clear().apply()
    }
    
    fun getNumberOfActiveClasses(): Int {
        return getActiveClasses().size
    }

    fun isClassNamed(classId: String): Boolean {
        val key = when (classId) {
            CLASS_ONE -> KEY_CLASS_NAMED_1
            CLASS_TWO -> KEY_CLASS_NAMED_2
            CLASS_THREE -> KEY_CLASS_NAMED_3
            CLASS_FOUR -> KEY_CLASS_NAMED_4
            CLASS_FIVE -> KEY_CLASS_NAMED_5
            else -> return false
        }
        val stored = prefs.getBoolean(key, false)
        if (stored) return true

        // Backward compatibility: consider named if user has customized the label before this flag existed
        val defaultName = when (classId) {
            CLASS_ONE -> DEFAULT_CLASS_1
            CLASS_TWO -> DEFAULT_CLASS_2
            CLASS_THREE -> DEFAULT_CLASS_3
            CLASS_FOUR -> DEFAULT_CLASS_4
            CLASS_FIVE -> DEFAULT_CLASS_5
            else -> ""
        }
        val currentName = getClassName(classId)
        val inferred = currentName.isNotBlank() && currentName != defaultName
        if (inferred) {
            setClassNamed(classId, true)
        }
        return inferred
    }

    fun setClassNamed(classId: String, named: Boolean) {
        val key = when (classId) {
            CLASS_ONE -> KEY_CLASS_NAMED_1
            CLASS_TWO -> KEY_CLASS_NAMED_2
            CLASS_THREE -> KEY_CLASS_NAMED_3
            CLASS_FOUR -> KEY_CLASS_NAMED_4
            CLASS_FIVE -> KEY_CLASS_NAMED_5
            else -> return
        }
        prefs.edit().putBoolean(key, named).apply()
    }
}
