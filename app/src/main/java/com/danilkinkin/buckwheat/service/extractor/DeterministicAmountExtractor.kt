package com.danilkinkin.buckwheat.service.extractor

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

data class ExtractedTransactionDetails(
    val amount: BigDecimal,
    val currencySymbol: String?,
    val merchant: String
)

@Singleton
class DeterministicAmountExtractor @Inject constructor() {

    companion object {
        // Supported currencies and codes
        private const val CURRENCY_SYMBOLS = """[$€£¥₹₩₺₽฿₫]"""
        private const val CURRENCY_CODE_NAMES = """(?:Rp\.?|Rs\.?|IDR|USD|EUR|GBP|CAD|AUD|SGD|MYR|CHF|JPY|INR|AED|SAR|NZD|HKD|VND|KRW)"""

        // Matches: $1,250.00, Rp 25.000, Rp13.000,00, EUR 45.50
        private val PREFIX_AMOUNT_PATTERN = Pattern.compile(
            """(?i)($CURRENCY_SYMBOLS|\b$CURRENCY_CODE_NAMES)\s*([0-9]{1,3}(?:[.,\s][0-9]{3})+(?:[.,][0-9]{1,2})?|[0-9]+(?:[.,][0-9]+)?)"""
        )

        // Matches: 1,250.00 USD, 25.000 IDR, 45.50 €
        private val SUFFIX_AMOUNT_PATTERN = Pattern.compile(
            """(?i)\b([0-9]{1,3}(?:[.,\s][0-9]{3})+(?:[.,][0-9]{1,2})?|[0-9]+(?:[.,][0-9]+)?)\s*($CURRENCY_SYMBOLS|$CURRENCY_CODE_NAMES\b)"""
        )

        // Matches shorthand notations: 200k, 200rb, 200 ribu, 1.5jt, 1.5 juta, 1.5m
        private val SHORTHAND_AMOUNT_PATTERN = Pattern.compile(
            """(?i)(?:($CURRENCY_SYMBOLS|\b$CURRENCY_CODE_NAMES)\s*)?([0-9]+(?:[.,][0-9]+)?)\s*(k|rb|ribu|m|jt|juta)(?:\s*($CURRENCY_SYMBOLS|$CURRENCY_CODE_NAMES\b))?\b"""
        )

        // Formatted standalone numbers with decimals or thousands separators: e.g., "45.50" or "25,000" or "50.000"
        private val FORMATTED_STANDALONE_PATTERN = Pattern.compile(
            """\b([0-9]{1,3}(?:[.,][0-9]{3})+(?:[.,][0-9]{1,2})?|[0-9]+[.,][0-9]{1,2})\b"""
        )

        // Keyword-backed standalone integers: e.g. "sebesar 50000", "paid 45", "amount 100"
        private val KEYWORD_STANDALONE_PATTERN = Pattern.compile(
            """(?i)\b(?:sebesar|amount\s+(?:of\s+)?|paid|spent|debited|credited|nominal|total|valued\s+at)\s+([0-9]+)\b"""
        )

        // Patterns to filter out dates, times, phone numbers, and card numbers before standalone amount extraction
        private val DATE_PATTERN = Regex("""\b\d{1,4}[/\-.]\d{1,2}[/\-.]\d{1,4}\b""")
        private val TIME_PATTERN = Regex("""\b\d{1,2}:\d{2}(?::\d{2})?\b""")
        private val CALL_CENTER_PATTERN = Regex("""(?i)\b(?:call\s*center|hubungi|hotline|telp|cs|contact)\b.*?(\d{5,})""")
        private val CARD_MASK_PATTERN = Regex("""(?i)\b(?:ending\s+in|card\s+|rekening\s+|rek\s+|acct?\s+)[xX*0-9]+\b""")

        // Expense merchant patterns
        private val EXPENSE_MERCHANT_PATTERNS = listOf(
            Pattern.compile("""(?i)(?:payment\s+for\s+(?:subscribtion|subscription)\s+to|payment\s+for|subscription\s+to|subscribtion\s+to|paid\s+to|paid\s+at|payment\s+to|payment\s+at|purchase\s+at|transaksi\s+pembelian(?:\s+di|\s+ke)?|pembelian(?:\s+di|\s+ke)?|pembayaran\s+(?:qris\s+)?(?:ke|di)|bayar\s+(?:ke|di)|transaksi\s+(?:di|ke))\s+([A-Za-z0-9\s&'.-]{2,35}?)(?=\s+(?:Rp\.?|IDR|USD|EUR|[$€£¥₹₩₺₽฿₫]|\b(?:is|was|on|via|using|from|with|date|ref|amount|sebesar|berhasil|completed|complete|success|successful|successfully|subscription|subscribtion)\b)|[\.,]|$)"""),
            Pattern.compile("""(?i)(?:at|to|in|for|di|ke)\s+([A-Za-z0-9\s&'.-]{2,35}?)(?=\s+(?:Rp\.?|IDR|USD|EUR|[$€£¥₹₩₺₽฿₫]|\b(?:is|was|on|via|using|from|with|date|ref|amount|sebesar|berhasil|completed|complete|success|successful|successfully|subscription|subscribtion)\b)|[\.,]|$)""")
        )

        // Income source patterns
        private val INCOME_SOURCE_PATTERNS = listOf(
            Pattern.compile("""(?i)(?:received\s+from|transfer\s+dari|dana\s+masuk\s+dari|uang\s+masuk\s+dari)\s+([A-Za-z0-9\s&'.-]{2,35}?)(?=\s+\b(?:on|via|using|with|date|ref|amount|sebesar|berhasil|completed|complete|success|successful|successfully|subscription|subscribtion)\b|[\.,]|$)"""),
            Pattern.compile("""(?i)(?:from|dari)\s+([A-Za-z0-9\s&'.-]{2,35}?)(?=\s+\b(?:on|via|using|with|date|ref|amount|sebesar|berhasil|completed|complete|success|successful|successfully|subscription|subscribtion)\b|[\.,]|$)"""),
            Pattern.compile("""(?i)(?:top[-\s]?up\s+via)\s+([A-Za-z0-9\s&'.-]{2,35}?)(?=\s+\b(?:on|date|ref|amount|sebesar|berhasil|completed|complete|success|successful|successfully|subscription|subscribtion)\b|[\.,]|$)""")
        )

        // Generic titles that should not be used as merchants
        private val GENERIC_TITLES = setOf(
            "notification", "alert", "transaction alert", "debit alert", "credit alert",
            "payment", "payment successful", "transaction successful", "pemberitahuan",
            "transaksi", "bank alert", "google play", "google wallet", "paypal",
            "info bca", "livin", "dana", "ovo", "gopay", "money tracker", "buckwheat"
        )
    }

    /**
     * Extracts amount, currency symbol, and merchant from text and title.
     * Guaranteed zero floating-point error using BigDecimal.
     */
    fun extract(
        text: String,
        title: String? = null,
        isIncome: Boolean = false
    ): ExtractedTransactionDetails? {
        val safeText = text.trim()
        val safeTitle = title?.trim() ?: ""
        val combined = "$safeTitle $safeText".trim()

        if (combined.isBlank()) return null

        // 1. Extract Amount and Currency
        val amountPair = extractAmountAndCurrency(combined) ?: return null
        val (amount, currency) = amountPair

        if (amount <= BigDecimal.ZERO) return null

        // 2. Extract Merchant / Counterparty
        val merchant = extractMerchant(
            title = safeTitle,
            text = safeText,
            isIncome = isIncome
        )

        return ExtractedTransactionDetails(
            amount = amount,
            currencySymbol = currency,
            merchant = merchant
        )
    }

    /**
     * Finds and extracts amount string and currency symbol/code.
     */
    fun extractAmountAndCurrency(content: String): Pair<BigDecimal, String?>? {
        // Try prefix pattern first ($15.00, Rp 50.000)
        val prefixMatcher = PREFIX_AMOUNT_PATTERN.matcher(content)
        if (prefixMatcher.find()) {
            val currency = prefixMatcher.group(1)?.trim()
            val rawNum = prefixMatcher.group(2)?.trim()
            if (rawNum != null) {
                val parsed = parseAmountString(rawNum, currency)
                if (parsed != null && parsed > BigDecimal.ZERO) {
                    return Pair(parsed, currency)
                }
            }
        }

        // Try suffix pattern (15.00 USD, 50.000 IDR)
        val suffixMatcher = SUFFIX_AMOUNT_PATTERN.matcher(content)
        if (suffixMatcher.find()) {
            val rawNum = suffixMatcher.group(1)?.trim()
            val currency = suffixMatcher.group(2)?.trim()
            if (rawNum != null) {
                val parsed = parseAmountString(rawNum, currency)
                if (parsed != null && parsed > BigDecimal.ZERO) {
                    return Pair(parsed, currency)
                }
            }
        }

        // Try shorthand pattern with multipliers (e.g. 200k, 200rb, 1.5jt)
        val shorthandMatcher = SHORTHAND_AMOUNT_PATTERN.matcher(content)
        if (shorthandMatcher.find()) {
            val prefixCurrency = shorthandMatcher.group(1)?.trim()
            val rawNum = shorthandMatcher.group(2)?.trim()
            val multiplier = shorthandMatcher.group(3)?.lowercase()?.trim()
            val suffixCurrency = shorthandMatcher.group(4)?.trim()
            val currency = prefixCurrency ?: suffixCurrency

            if (rawNum != null && multiplier != null) {
                val base = parseAmountString(rawNum, currency)
                if (base != null) {
                    val factor = when (multiplier) {
                        "k", "rb", "ribu" -> BigDecimal(1000)
                        "m", "jt", "juta" -> BigDecimal(1000000)
                        else -> BigDecimal.ONE
                    }
                    val total = base.multiply(factor).setScale(2, RoundingMode.HALF_EVEN)
                    if (total > BigDecimal.ZERO) {
                        return Pair(total, currency)
                    }
                }
            }
        }

        // Sanitize content from dates, times, phone numbers, and card numbers before standalone check
        val sanitized = content
            .replace(DATE_PATTERN, " ")
            .replace(TIME_PATTERN, " ")
            .replace(CALL_CENTER_PATTERN, " ")
            .replace(CARD_MASK_PATTERN, " ")

        // 4. Try formatted standalone numbers with decimal or thousand separators (e.g. "45.50", "25,000", "50.000")
        val formattedMatcher = FORMATTED_STANDALONE_PATTERN.matcher(sanitized)
        while (formattedMatcher.find()) {
            val rawNum = formattedMatcher.group(1)?.trim()
            if (rawNum != null) {
                val parsed = parseAmountString(rawNum, null)
                if (parsed != null && parsed > BigDecimal.ZERO) {
                    return Pair(parsed, null)
                }
            }
        }

        // 5. Try keyword-backed integer standalone numbers (e.g. "sebesar 50000", "paid 45")
        val keywordMatcher = KEYWORD_STANDALONE_PATTERN.matcher(sanitized)
        if (keywordMatcher.find()) {
            val rawNum = keywordMatcher.group(1)?.trim()
            if (rawNum != null) {
                val parsed = parseAmountString(rawNum, null)
                if (parsed != null && parsed > BigDecimal.ZERO) {
                    return Pair(parsed, null)
                }
            }
        }

        return null
    }

    /**
     * Deterministically parses a number string with dot or comma separators
     * into a BigDecimal with zero precision drift.
     */
    fun parseAmountString(raw: String, currencyHint: String?): BigDecimal? {
        val clean = raw.replace(" ", "")
        val hasDot = clean.contains(".")
        val hasComma = clean.contains(",")

        val isZeroDecimalCurrency = currencyHint != null && (
            currencyHint.startsWith("Rp", ignoreCase = true) ||
            currencyHint.equals("IDR", ignoreCase = true) ||
            currencyHint.equals("JPY", ignoreCase = true) ||
            currencyHint.equals("VND", ignoreCase = true) ||
            currencyHint.equals("KRW", ignoreCase = true)
        )

        val normalized = when {
            // Both dot and comma: e.g. "1,250.00" or "1.250,00"
            hasDot && hasComma -> {
                val lastDot = clean.lastIndexOf('.')
                val lastComma = clean.lastIndexOf(',')
                if (lastDot > lastComma) {
                    // "1,250.00" (US/UK) -> remove commas
                    clean.replace(",", "")
                } else {
                    // "1.250,00" (EU/ID) -> remove dots, comma to dot
                    clean.replace(".", "").replace(",", ".")
                }
            }
            // Only comma: e.g. "12,50", "50,000", "1,250,000"
            hasComma -> {
                val parts = clean.split(",")
                if (parts.size > 2) {
                    // Multiple commas: thousands separators (e.g. 1,000,000)
                    clean.replace(",", "")
                } else {
                    val decimalPart = parts.getOrNull(1) ?: ""
                    if (decimalPart.length == 3) {
                        // Thousands separator (e.g. "50,000")
                        clean.replace(",", "")
                    } else {
                        // Decimal comma (e.g. "12,50" or "12,5")
                        clean.replace(",", ".")
                    }
                }
            }
            // Only dot: e.g. "12.50", "25.000", "1.250.000"
            hasDot -> {
                val parts = clean.split(".")
                if (parts.size > 2) {
                    // Multiple dots: thousands separators (e.g. 1.250.000)
                    clean.replace(".", "")
                } else {
                    val decimalPart = parts.getOrNull(1) ?: ""
                    if (decimalPart.length == 3) {
                        if (isZeroDecimalCurrency) {
                            // Non-fractional currency (e.g. "Rp 25.000")
                            clean.replace(".", "")
                        } else {
                            // High value standard integer thousands (e.g. 25.000)
                            clean.replace(".", "")
                        }
                    } else {
                        // Decimal dot (e.g. "12.50")
                        clean
                    }
                }
            }
            // No separators
            else -> clean
        }

        return try {
            BigDecimal(normalized).setScale(2, RoundingMode.HALF_EVEN)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extracts merchant / sender from notification body or title.
     */
    fun extractMerchant(
        title: String,
        text: String,
        isIncome: Boolean
    ): String {
        val patterns = if (isIncome) INCOME_SOURCE_PATTERNS else EXPENSE_MERCHANT_PATTERNS

        // 1. Search text body with patterns
        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            while (matcher.find()) {
                val extracted = cleanMerchant(matcher.group(1))
                if (extracted.isNotBlank() && !isGenericTitle(extracted)) {
                    return extracted
                }
            }
        }

        // 2. Fallback to title
        val cleanTitle = cleanMerchant(title)
        if (cleanTitle.isNotBlank() && !isGenericTitle(cleanTitle)) {
            return cleanTitle
        }

        return if (isIncome) "Source" else "Merchant"
    }

    private fun isGenericTitle(title: String): Boolean {
        return GENERIC_TITLES.contains(title.lowercase().trim())
    }

    private fun cleanMerchant(raw: String?): String {
        if (raw == null) return ""
        val cleaned = raw.trim()
            .trimEnd('.', ',', '!', '?', ';', ':', '-', ' ')
            .trimStart('.', ',', '!', '?', ';', ':', '-', ' ')
            .replace(Regex("""^(the|pt|cv|toko)\s+""", RegexOption.IGNORE_CASE), "")
            .trim()
        if (cleaned.all { it.isDigit() }) return ""
        if (cleaned.matches(Regex("""^(?:Rp\.?|IDR|USD|EUR)\b.*""", RegexOption.IGNORE_CASE))) return ""
        return cleaned
    }
}
