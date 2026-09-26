package com.danilkinkin.buckwheat.service.classifier

data class ClassificationResult(
    val type: TransactionClassificationType,
    val confidence: Float,
    val probabilities: Map<TransactionClassificationType, Float> = emptyMap(),
    val source: String = "rule_fallback",
)
