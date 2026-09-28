package com.danilkinkin.buckwheat.service.classifier

import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp

@Singleton
class RuleBasedClassifierFallback @Inject constructor() : TransactionClassifier {

    companion object {
        // High confidence OTP patterns
        private val OTP_PATTERNS = listOf(
            Pattern.compile("""\b(otp|one[-\s]?time[-\s]?(password|passcode|pin))\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\b(verification\s+code|security\s+code|secret\s+code|auth\s+code)\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\b(kode\s+verifikasi|kode\s+otp|kode\s+rahasia)\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\b(do\s+not\s+share\s+(this|your)?\s*(code|otp|password)?)\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\b(jangan\s+bagikan\s+kode\s+ini)\b""", Pattern.CASE_INSENSITIVE)
        )

        // Failed / Declined / Cancelled transaction patterns (High priority noise)
        private val FAILED_TRANSACTION_PATTERNS = listOf(
            Pattern.compile("""\b(failed|declined|unsuccessful|cancelled|canceled|rejected|expired)\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\b(gagal|tidak\s+berhasil|dibatalkan|ditolak|kadaluwarsa|batal)\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\b(payment\s+failed|transaction\s+failed|transaksi\s+gagal|pembayaran\s+gagal)\b""", Pattern.CASE_INSENSITIVE)
        )

        // Conversational / Peer-to-peer Chat patterns (Requests, reminders, personal chat)
        private val CONVERSATIONAL_CHAT_PATTERNS = listOf(
            Pattern.compile("""\b(rekening|rek)(\s+\w+)?\s+(aku|saya|gue|gw|kamu|lo|lu)\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\b(gue|gw|lo|lu)\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\b(ke\s+(aku|kamu|gue|saya)|buat\s+(aku|kamu|gue|saya))\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\b(tolong|please|pls|jangan\s+lupa|ingetin|bisa\s+transfer|bisa\s+bayar|udah\s+bayar|udah\s+transfer)\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\b(transfer\s+ke\s+(aku|gue|saya)|kirim\s+ke\s+(aku|gue|saya)|bayar\s+ke\s+(aku|gue|saya))\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\b(pay\s+me|send\s+me|wire\s+me|transfer\s+to\s+me|my\s+account|remind\s+me\s+to)\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\b(bayar|transfer)\s+.*?\b(sekarang)\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\b(sekarang\s+ya|nanti\s+ya|bro|sis|gan|kuy|dong|nih)\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\?""")
        )

        // Promotional / Noise patterns
        private val PROMO_PATTERNS = listOf(
            Pattern.compile("""\b(cashback|discount|coupon|diskon|promo|voucher)\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\b(claim(\s+your)?|klaim|diklaim|siap\s+diklaim|bisa\s+diklaim)\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\b(hadiah\s+lainnya|dapatkan\s+hadiah|bagi[-\s]?bagi\s+hadiah|koin\s+dan\s+hadiah|koin\s+siap|bonus\s+koin|gratis\s+koin)\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\b(penawaran\s+spesial|selamat\s+anda\s+mendapatkan|menangkan|win\s+up\s+to|special\s+offer)\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\b(dapatkan\s+(promo|diskon|cashback|voucher|gratis|hadiah|penawaran|koin)?)\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\b(dan\s+dapatkan|and\s+get|gratis\s+ongkir|free\s+shipping|flash\s+sale)\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\b(cek\s+(sekarang|👉|link|promo|detail|disini|di\s+sini)|klik\s+(di\s+sini|disini|link)|tap\s+untuk|yuk\s+(cek|klaim|transaksi|belanja|serbu))\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\b(bisa\s+diklaim\s+dengan\s+transaksi|(?:dengan|lakukan|setiap|untuk)\s+transaksi\s+.*?(?:klaim|dapatkan|menangkan))\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""[🎁🎉👉]"""),
            Pattern.compile("""\b(login\s+detected|new\s+device\s+login|security\s+alert|password\s+changed)\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\b(login\s+dari\s+perangkat\s+baru|ganti\s+kata\s+sandi)\b""", Pattern.CASE_INSENSITIVE)
        )

        // Strong Expense patterns
        private val STRONG_EXPENSE_PATTERNS = listOf(
            Pattern.compile("""\b(spent|debited(\s+(with|by|from))?|paid(\s+to)?|payment(\s+(to|of|for))?|purchase\s+(at|of|for)|purchased)\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\b(bayar(\s+(ke|di))?|pembayaran(\s+(ke|di))?|transaksi(\s+(di|sebesar))?|berhasil\s+bayar)\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\b(transfer\s+(ke|out\s+to)|sent\s+to|debit\s+alert|card\s+ending\s+in)\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\b(potongan\s+sebesar|terpotong\s+sebesar|tagihan\s+lunas)\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\b(pembelian(\s+qris)?|transaksi\s+pembelian|transaksi\s+qris|bayar\s+qris|qris\s+sebesar|qris\s+berhasil)\b""", Pattern.CASE_INSENSITIVE)
        )

        // Weak Expense keywords
        private val WEAK_EXPENSE_WORDS = listOf(
            "buy", "bought", "charge", "charged", "order", "checkout", "belanja", "pembelian", "qris"
        )

        // Strong Income patterns
        private val STRONG_INCOME_PATTERNS = listOf(
            Pattern.compile("""\b(received(\s+.{1,35})?\s+from|received\s+money|credited(\s+(with|by|to))?|salary\s+credited)\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\b(deposit\s+successful|inward\s+transfer|transfer\s+masuk|dana(\s+.{1,30})?\s+masuk)\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\b(menerima\s+transfer|uang(\s+.{1,30})?\s+masuk|top[-\s]?up\s+berhasil|topup\s+success)\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\b(transfer\s+dari|received\s+payment|credit\s+alert|pengembalian\s+dana|gaji)\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\b(refund\s+(from|processed|of)?|cashback\s+credited)\b""", Pattern.CASE_INSENSITIVE)
        )

        // Weak Income keywords
        private val WEAK_INCOME_WORDS = listOf(
            "deposit", "incoming", "masuk", "kredit", "penerimaan", "setoran", "received", "receive"
        )
    }

    override fun classify(text: String): ClassificationResult {
        if (text.isBlank()) {
            return ClassificationResult(
                type = TransactionClassificationType.NOISE,
                confidence = 1.0f,
                probabilities = mapOf(
                    TransactionClassificationType.NOISE to 1.0f,
                    TransactionClassificationType.EXPENSE to 0.0f,
                    TransactionClassificationType.INCOME to 0.0f
                ),
                source = "rule_fallback"
            )
        }

        var noiseScore = 0.0
        var expenseScore = 0.0
        var incomeScore = 0.0

        // 1. Evaluate Failed / Declined transactions (Highest priority noise - money was not moved)
        for (pattern in FAILED_TRANSACTION_PATTERNS) {
            if (pattern.matcher(text).find()) {
                noiseScore += 10.0
            }
        }

        // 2. Evaluate OTP patterns (High priority noise)
        for (pattern in OTP_PATTERNS) {
            if (pattern.matcher(text).find()) {
                noiseScore += 8.0
            }
        }

        // 3. Evaluate Promotional patterns (High priority noise)
        for (pattern in PROMO_PATTERNS) {
            if (pattern.matcher(text).find()) {
                noiseScore += 6.0
            }
        }

        // 4. Evaluate Conversational / Peer-to-peer Chat patterns (High priority noise)
        for (pattern in CONVERSATIONAL_CHAT_PATTERNS) {
            if (pattern.matcher(text).find()) {
                noiseScore += 8.0
            }
        }

        // 3. Evaluate Expense patterns
        for (pattern in STRONG_EXPENSE_PATTERNS) {
            if (pattern.matcher(text).find()) {
                expenseScore += 4.5
            }
        }
        val lowerText = text.lowercase()
        for (word in WEAK_EXPENSE_WORDS) {
            if (lowerText.contains(word)) {
                expenseScore += 1.5
            }
        }

        // 4. Evaluate Income patterns
        for (pattern in STRONG_INCOME_PATTERNS) {
            if (pattern.matcher(text).find()) {
                incomeScore += 4.5
            }
        }
        for (word in WEAK_INCOME_WORDS) {
            if (lowerText.contains(word)) {
                incomeScore += 1.5
            }
        }

        // Suppress false expense/income triggers caused by promotional marketing text
        if (noiseScore > 0) {
            expenseScore = (expenseScore - noiseScore * 0.7).coerceAtLeast(0.0)
            incomeScore = (incomeScore - noiseScore * 0.7).coerceAtLeast(0.0)
        }

        // Conflict handling: If text has both "received" and explicit "debited" / "paid", check context
        if (expenseScore > 0 && incomeScore > 0) {
            // E.g. "We received your payment of $50" -> this is an expense confirmation for user
            if (lowerText.contains("payment") || lowerText.contains("paid") || lowerText.contains("bayar")) {
                expenseScore += 2.0
            }
        }

        // Calculate Softmax probabilities
        val maxScore = maxOf(noiseScore, expenseScore, incomeScore)
        if (maxScore == 0.0) {
            // No matches found, default to NOISE with low confidence
            return ClassificationResult(
                type = TransactionClassificationType.NOISE,
                confidence = 0.5f,
                probabilities = mapOf(
                    TransactionClassificationType.NOISE to 0.5f,
                    TransactionClassificationType.EXPENSE to 0.25f,
                    TransactionClassificationType.INCOME to 0.25f
                ),
                source = "rule_fallback"
            )
        }

        val expNoise = exp(noiseScore - maxScore)
        val expExpense = exp(expenseScore - maxScore)
        val expIncome = exp(incomeScore - maxScore)
        val sumExp = expNoise + expExpense + expIncome

        val pNoise = (expNoise / sumExp).toFloat()
        val pExpense = (expExpense / sumExp).toFloat()
        val pIncome = (expIncome / sumExp).toFloat()

        val probabilities = mapOf(
            TransactionClassificationType.NOISE to pNoise,
            TransactionClassificationType.EXPENSE to pExpense,
            TransactionClassificationType.INCOME to pIncome
        )

        val predictedType = when {
            pNoise >= pExpense && pNoise >= pIncome -> TransactionClassificationType.NOISE
            pExpense >= pNoise && pExpense >= pIncome -> TransactionClassificationType.EXPENSE
            else -> TransactionClassificationType.INCOME
        }

        val confidence = probabilities[predictedType] ?: 0.5f

        return ClassificationResult(
            type = predictedType,
            confidence = confidence,
            probabilities = probabilities,
            source = "rule_fallback"
        )
    }
}
