package com.danilkinkin.buckwheat.service

import android.util.Log
import com.danilkinkin.buckwheat.data.entities.ParsedExpense
import com.danilkinkin.buckwheat.data.entities.TransactionCaptureType
import com.danilkinkin.buckwheat.service.classifier.TfLiteTransactionClassifier
import com.danilkinkin.buckwheat.service.classifier.TransactionClassificationType
import com.danilkinkin.buckwheat.service.extractor.DeterministicAmountExtractor
import java.math.BigDecimal
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HybridTransactionCaptureEngine @Inject constructor(
    private val classifier: TfLiteTransactionClassifier,
    private val amountExtractor: DeterministicAmountExtractor,
) {

    companion object {
        private const val TAG = "HybridCaptureEngine"
        const val DEFAULT_CONFIDENCE_THRESHOLD = 0.65f

        // Known financial packages for quick detection
        val KNOWN_FINANCE_PACKAGES = setOf(
            "com.google.android.apps.walletnfcrel",
            "com.paypal.android.p2pmobile",
            "com.revolut.revolut",
            "co.uk.getmondo",
            "com.venmo",
            "com.squareup.cash",
            "com.transferwise.android",
            "com.chase.sig.android",
            "com.infonow.bofa",
            "com.wf.wellsfargomobile",
            "com.citi.citimobile",
            "com.bca",
            "com.mandiri.livin",
            "id.dana",
            "com.gojek.app",
            "ovo.id"
        )
    }

    /**
     * Executes the hybrid capture pipeline:
     * 1. Preprocesses notification text
     * 2. ML / Heuristic classification (EXPENSE, INCOME, NOISE)
     * 3. Deterministic amount and merchant extraction with zero float drift
     * 4. Returns ParsedExpense or null if noise/unsupported
     */
    fun processNotification(
        packageName: String,
        title: String?,
        text: String?,
        postTime: Long = System.currentTimeMillis(),
        confidenceThreshold: Float = DEFAULT_CONFIDENCE_THRESHOLD,
        allowedPackages: Set<String>? = null
    ): ParsedExpense? {
        val safeTitle = title?.trim() ?: ""
        val safeText = text?.trim() ?: ""
        val combined = "$safeTitle $safeText".trim()

        if (combined.isBlank()) {
            return null
        }

        // Package filtering if specific whitelist configured
        if (allowedPackages != null && allowedPackages.isNotEmpty() && !allowedPackages.contains(packageName)) {
            Log.d(TAG, "Ignoring notification from non-whitelisted package: $packageName")
            return null
        }

        // 1. Text Classification (TFLite with heuristic fallback)
        val classification = classifier.classify(combined)
        Log.d(
            TAG,
            "Classification: type=${classification.type}, confidence=${classification.confidence}, source=${classification.source}"
        )

        // Filter NOISE or low confidence detections
        if (classification.type == TransactionClassificationType.NOISE) {
            Log.d(TAG, "Skipping notification: classified as NOISE")
            return null
        }

        if (classification.confidence < confidenceThreshold) {
            Log.d(TAG, "Skipping notification: confidence ${classification.confidence} < threshold $confidenceThreshold")
            return null
        }

        val isIncome = classification.type == TransactionClassificationType.INCOME

        // 2. Deterministic Amount & Merchant Extraction
        val details = amountExtractor.extract(
            text = safeText,
            title = safeTitle,
            isIncome = isIncome
        )

        if (details == null || details.amount <= BigDecimal.ZERO) {
            Log.d(TAG, "Extraction failed: could not locate valid transaction amount in: $combined")
            return null
        }

        Log.d(
            TAG,
            "Captured transaction: type=${if (isIncome) "INCOME" else "EXPENSE"}, amount=${details.amount} ${details.currencySymbol ?: ""}, merchant=${details.merchant}"
        )

        return ParsedExpense(
            type = if (isIncome) TransactionCaptureType.INCOME else TransactionCaptureType.EXPENSE,
            amount = details.amount,
            currencySymbol = details.currencySymbol,
            merchant = details.merchant,
            date = Date(postTime),
            packageName = packageName,
            rawText = combined,
            confidence = classification.confidence,
            source = classification.source
        )
    }
}
