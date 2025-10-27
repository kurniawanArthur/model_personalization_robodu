package org.tensorflow.lite.examples.modelpersonalization

import org.tensorflow.lite.examples.modelpersonalization.TransferLearningHelper.Companion.CLASS_FIVE
import org.tensorflow.lite.examples.modelpersonalization.TransferLearningHelper.Companion.CLASS_FOUR
import org.tensorflow.lite.examples.modelpersonalization.TransferLearningHelper.Companion.CLASS_ONE
import org.tensorflow.lite.examples.modelpersonalization.TransferLearningHelper.Companion.CLASS_THREE
import org.tensorflow.lite.examples.modelpersonalization.TransferLearningHelper.Companion.CLASS_TWO

data class TrainingClass(
    val id: Int,
    var name: String,
    var sampleCount: Int = 0,
    var isSelected: Boolean = false
) {
    // Map ID to class label for TransferLearningHelper
    fun getClassLabel(): String {
        return when (id) {
            0 -> CLASS_ONE
            1 -> CLASS_TWO
            2 -> CLASS_THREE
            3 -> CLASS_FOUR
            4 -> CLASS_FIVE
            else -> CLASS_ONE
        }
    }
}
