package com.danilkinkin.buckwheat.service.classifier

interface TransactionClassifier {
    fun classify(text: String): ClassificationResult
}
