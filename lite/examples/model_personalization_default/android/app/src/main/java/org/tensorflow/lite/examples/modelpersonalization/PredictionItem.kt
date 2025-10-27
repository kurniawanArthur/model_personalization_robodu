package org.tensorflow.lite.examples.modelpersonalization

data class PredictionItem(
    val className: String,
    val confidence: Float,
    val isTopPrediction: Boolean = false
)
