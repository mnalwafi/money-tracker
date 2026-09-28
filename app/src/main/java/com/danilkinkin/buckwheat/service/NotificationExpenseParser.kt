package com.danilkinkin.buckwheat.service

import com.danilkinkin.buckwheat.data.entities.ParsedExpense
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Date
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationExpenseParser @Inject constructor() {

    companion object {
        // Exclusion patterns: OTPs, refunds, incoming money/salary, promo/discounts
        private val OTP_PATTERN = Pattern.compile(
            """\b(otp|one[-\s]?time[-\s]?password|verification\s+code|security\s+code|secret\s+code|kode\s+verifikasi|kode\s+otp|do\s+not\s+share)\b""",
            Pattern.CASE_INSENSITIVE
        )

        private val FAILED_TRANSACTION_PATTERN = Pattern.compile(
            """\b(failed|declined|unsuccessful|cancelled|canceled|rejected|expired|gagal|tidak\s+berhasil|dibatalkan|ditolak|kadaluwarsa|batal)\b""",
            Pattern.CASE_INSENSITIVE
        )

        private val REFUND_PATTERN = Pattern.compile(
            """\b(refund|refunded|reversed|reversal|pengembalian\s+dana)\b""",
            Pattern.CASE_INSENSITIVE
        )

        private val INCOMING_MONEY_PATTERN = Pattern.compile(
            """\b(received\s+from|received\s+money|money\s+received|salary\s+credited|credited\s+with|deposit\s+successful|incoming\s+transfer|transfer\s+masuk|dana\s+masuk|menerima\s+transfer|uang\s+masuk|top[-\s]?up\s+berhasil|credited\s+to\s+your\s+account)\b""",
            Pattern.CASE_INSENSITIVE
        )

        private val PROMO_PATTERN = Pattern.compile(
            """\b(cashback|discount|diskon|voucher|promo|special\s+offer|penawaran\s+spesial|selamat\s+anda\s+mendapatkan|claim\s+your|win|congratulations)\b""",
            Pattern.CASE_INSENSITIVE
        )

        // Expense / debit intent triggers
        private val EXPENSE_TRIGGER_PATTERN = Pattern.compile(
            """\b(spent|paid|purchase|purchased|debited|charge|charged|payment\s+of|transaction\s+of|bayar|transaksi|pembelian|qris|pembayaran|berhasil\s+bayar|debit|potongan|sent\s+to|transfer\s+ke)\b""",
            Pattern.CASE_INSENSITIVE
        )

        // Known finance / payment packages (whitelist for higher confidence)
        val DEFAULT_TARGET_PACKAGES = setOf(
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
            "us.zoom.videomeetings"
        )

        private val CHAT_REQUEST_PATTERN = Pattern.compile(
            """\b(rekening\s+(aku|saya|gue|gw|kamu|lo|lu)|tolong|please|pls|jangan\s+lupa|sekarang\s+ya|pay\s+me|send\s+me)\b""",
            Pattern.CASE_INSENSITIVE
        )

        // Currency symbols and codes regex (without trailing \b so attached formats like Rp8.000,00 match)
        private const val CURRENCY_REGEX =
            """(?i)(?:[$€£¥₹₩₺₽฿₫]|\b(?:Rp\.?|Rs\.?|IDR|USD|EUR|GBP|CAD|AUD|SGD|MYR|CHF|JPY|INR|AED|SAR|NZD|HKD|VND|KRW))"""

        // Amount pattern with currency before or after
        private val AMOUNT_PATTERN_PREFIX = Pattern.compile(
            """($CURRENCY_REGEX)\s*([0-9]{1,3}(?:[.,\s][0-9]{3})+(?:[.,][0-9]{1,2})?|[0-9]+(?:[.,][0-9]+)?)"""
        )

        private val AMOUNT_PATTERN_SUFFIX = Pattern.compile(
            """([0-9]{1,3}(?:[.,\s][0-9]{3})+(?:[.,][0-9]{1,2})?|[0-9]+(?:[.,][0-9]+)?)\s*($CURRENCY_REGEX)"""
        )

        // Merchant extraction patterns
        private val MERCHANT_AT_TO_PATTERN = Pattern.compile(
            """(?i)(?:at|to|in|for|di|ke)\s+([A-Za-z0-9\s&'.-]{2,35}?)(?=\s+\b(?:is|was|on|via|using|from|with|date|ref|amount|sebesar|berhasil|completed|complete|success|successful|successfully|subscription|subscribtion)\b|[\.,]|$)"""
        )

        private val MERCHANT_PAID_TO_PATTERN = Pattern.compile(
            """(?i)(?:paid\s+to|payment\s+to|purchase\s+at|transaksi\s+pembelian(?:\s+di|\s+ke)?|pembelian(?:\s+di|\s+ke)?|transaksi\s+di|pembayaran\s+(?:qris\s+)?(?:ke|di)|bayar\s+(?:ke|di))\s+([A-Za-z0-9\s&'.-]{2,35}?)(?=\s+\b(?:is|was|on|via|using|from|with|date|ref|amount|sebesar|berhasil|completed|complete|success|successful|successfully|subscription|subscribtion)\b|[\.,]|$)"""
        )

        // Generic notification titles to ignore when fallbacking to title
        private val GENERIC_TITLES = setOf(
            "notification", "alert", "transaction alert", "debit alert", "payment",
            "payment successful", "transaction successful", "pemberitahuan", "transaksi",
            "info bca", "bank alert", "google play", "google wallet", "paypal"
        )
    }

    /**
     * Parses notification content into a ParsedExpense if valid debit transaction is detected.
     * Returns null if non-expense, promotional, OTP, or missing amount.
     */
    fun parse(
        packageName: String,
        title: String?,
        text: String?,
        postTime: Long = System.currentTimeMillis(),
        allowedPackages: Set<String>? = null
    ): ParsedExpense? {
        val safeTitle = title?.trim() ?: ""
        val safeText = text?.trim() ?: ""
        val combinedContent = "$safeTitle $safeText".trim()

        if (combinedContent.isBlank()) return null

        // 0. Reject non-financial messaging, social media, and chat apps
        if (IGNORED_PACKAGES.contains(packageName)) {
            return null
        }

        // 1. Check package whitelist if provided
        if (allowedPackages != null && allowedPackages.isNotEmpty() && !allowedPackages.contains(packageName)) {
            return null
        }

        // 2. Reject non-expense notifications (OTP, promo, refund, incoming deposit)
        if (isExcluded(combinedContent)) {
            return null
        }

        // 3. Verify debit intent (either contains trigger keyword or from known payment package)
        val isTargetPackage = DEFAULT_TARGET_PACKAGES.contains(packageName)
        val hasDebitKeyword = EXPENSE_TRIGGER_PATTERN.matcher(combinedContent).find()
        if (!hasDebitKeyword && !isTargetPackage) {
            return null
        }

        // 4. Extract Amount and Currency
        val amountResult = extractAmount(combinedContent) ?: return null
        val (amount, currencySymbol) = amountResult

        if (amount <= BigDecimal.ZERO) return null

        // 5. Extract Merchant
        val merchant = extractMerchant(safeTitle, safeText)

        return ParsedExpense(
            amount = amount,
            currencySymbol = currencySymbol,
            merchant = merchant,
            date = Date(postTime),
            packageName = packageName,
            rawText = combinedContent
        )
    }

    /**
     * Checks if the notification text matches OTP, Refund, Incoming money, or Promo.
     */
    fun isExcluded(content: String): Boolean {
        if (FAILED_TRANSACTION_PATTERN.matcher(content).find()) return true
        if (CHAT_REQUEST_PATTERN.matcher(content).find()) return true
        if (OTP_PATTERN.matcher(content).find()) return true
        if (REFUND_PATTERN.matcher(content).find()) return true
        if (PROMO_PATTERN.matcher(content).find()) return true

        // If it explicitly says debited/spent/paid, don't reject just because "received" is in disclaimer
        val hasDebitTrigger = EXPENSE_TRIGGER_PATTERN.matcher(content).find()
        val hasIncomingMoney = INCOMING_MONEY_PATTERN.matcher(content).find()
        if (hasIncomingMoney && !hasDebitTrigger) {
            return true
        }

        return false
    }

    /**
     * Extracts normalized BigDecimal amount and currency symbol from text.
     */
    fun extractAmount(content: String): Pair<BigDecimal, String?>? {
        // Try prefix pattern first (e.g., "$15.00", "Rp 50.000")
        val prefixMatcher = AMOUNT_PATTERN_PREFIX.matcher(content)
        if (prefixMatcher.find()) {
            val currency = prefixMatcher.group(1)?.trim()
            val rawNum = prefixMatcher.group(2)?.trim()
            if (rawNum != null) {
                val parsed = parseNumber(rawNum)
                if (parsed != null) return Pair(parsed, currency)
            }
        }

        // Try suffix pattern (e.g., "15.00 USD", "50.000 IDR")
        val suffixMatcher = AMOUNT_PATTERN_SUFFIX.matcher(content)
        if (suffixMatcher.find()) {
            val rawNum = suffixMatcher.group(1)?.trim()
            val currency = suffixMatcher.group(2)?.trim()
            if (rawNum != null) {
                val parsed = parseNumber(rawNum)
                if (parsed != null) return Pair(parsed, currency)
            }
        }

        return null
    }

    /**
     * Normalizes currency number strings with commas, dots, or spaces.
     */
    fun parseNumber(raw: String): BigDecimal? {
        val clean = raw.replace(" ", "")

        val hasDot = clean.contains(".")
        val hasComma = clean.contains(",")

        val normalized = when {
            // Both dot and comma present: e.g. "1,250.50" or "1.250,50"
            hasDot && hasComma -> {
                val lastDot = clean.lastIndexOf('.')
                val lastComma = clean.lastIndexOf(',')
                if (lastDot > lastComma) {
                    // US/UK format: "1,250.50" -> remove comma
                    clean.replace(",", "")
                } else {
                    // EU/Indonesian format: "1.250,50" -> remove dot, replace comma with dot
                    clean.replace(".", "").replace(",", ".")
                }
            }
            // Only comma present: e.g. "12,50" (decimal) or "50,000" (thousands)
            hasComma -> {
                val parts = clean.split(",")
                if (parts.size == 2 && parts[1].length <= 2) {
                    // Decimal comma (e.g. "12,50")
                    clean.replace(",", ".")
                } else {
                    // Thousands separator (e.g. "50,000")
                    clean.replace(",", "")
                }
            }
            // Only dot present: e.g. "12.50" (decimal) or "50.000" (thousands in IDR/EUR)
            hasDot -> {
                val parts = clean.split(".")
                if (parts.size == 2 && parts[1].length == 3) {
                    // Likely thousands separator (e.g. "50.000")
                    clean.replace(".", "")
                } else {
                    // Likely decimal (e.g. "12.50") or standard integer
                    clean
                }
            }
            else -> clean
        }

        return try {
            BigDecimal(normalized).setScale(2, RoundingMode.HALF_EVEN)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extracts merchant name from notification text or title.
     */
    fun extractMerchant(title: String, text: String): String {
        // 1. Try explicit paid-to pattern
        val paidMatcher = MERCHANT_PAID_TO_PATTERN.matcher(text)
        if (paidMatcher.find()) {
            val merchant = cleanMerchant(paidMatcher.group(1))
            if (merchant.isNotBlank()) return merchant
        }

        // 2. Try at/to pattern in body
        val atMatcher = MERCHANT_AT_TO_PATTERN.matcher(text)
        if (atMatcher.find()) {
            val merchant = cleanMerchant(atMatcher.group(1))
            if (merchant.isNotBlank()) return merchant
        }

        // 3. Fallback: check title
        val cleanTitle = cleanMerchant(title)
        if (cleanTitle.isNotBlank() && !GENERIC_TITLES.contains(cleanTitle.lowercase())) {
            return cleanTitle
        }

        return "Merchant"
    }

    private fun cleanMerchant(raw: String?): String {
        if (raw == null) return ""
        return raw.trim()
            .trimEnd('.', ',', '!', '?', ';', ':', '-')
            .replace(Regex("""^(the|pt|cv)\s+""", RegexOption.IGNORE_CASE), "")
            .trim()
    }
}
