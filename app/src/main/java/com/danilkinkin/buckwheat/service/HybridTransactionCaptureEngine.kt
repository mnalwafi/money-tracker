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
            "com.bankbca.bca",
            "com.mandiri.livin",
            "id.co.bankmandiri.livin",
            "id.co.bri.brimo",
            "com.bri.brimo",
            "id.co.bni.newmobile",
            "id.bni.wondr",
            "com.cimbniaga.octomobile",
            "com.btpn.jenius",
            "id.seabank.mobile",
            "id.dana",
            "com.gojek.app",
            "ovo.id",
            "com.shopee.id"
        )

        // Non-financial messaging, social media, and communication packages that should never trigger financial tracking
        val IGNORED_PACKAGES = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger",
            "org.telegram.plus",
            "org.thunderdog.chimeravpn",
            "com.facebook.orca",
            "com.facebook.katana",
            "com.facebook.lite",
            "com.instagram.android",
            "com.twitter.android",
            "com.twitter.android.lite",
            "com.discord",
            "com.Slack",
            "jp.naver.line.android",
            "com.tencent.mm",
            "org.thoughtcrime.securesms",
            "com.viber.voip",
            "com.skype.raider",
            "com.snapchat.android",
            "com.reddit.frontpage",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.google.android.youtube",
            "com.spotify.music",
            "com.netflix.mediaclient",
            "com.google.android.talk",
            "com.google.android.apps.tachyon",
            "com.microsoft.teams",
            "us.zoom.videomeetings",
            // Stock market, crypto, and investment news apps
            "com.stockbit.android",
            "com.ajaib.invest",
            "com.ajaib.crypto",
            "com.bibit.id",
            "com.bareksa.app",
            "com.indodax.mobile",
            "com.pintu.android",
            "com.pluang.android",
            "com.tradingview.android",
            "com.bloomberg.android",
            "com.cnbc.android",
            "com.investing.android"
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
        // Immediately ignore personal chat, messaging, and social apps
        if (IGNORED_PACKAGES.contains(packageName)) {
            Log.d(TAG, "Ignoring notification from messaging/social app: $packageName")
            return null
        }

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
