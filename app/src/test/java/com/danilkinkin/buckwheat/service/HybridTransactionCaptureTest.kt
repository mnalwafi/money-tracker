package com.danilkinkin.buckwheat.service

import com.danilkinkin.buckwheat.data.entities.TransactionCaptureType
import com.danilkinkin.buckwheat.service.classifier.RuleBasedClassifierFallback
import com.danilkinkin.buckwheat.service.classifier.TransactionClassificationType
import com.danilkinkin.buckwheat.service.extractor.DeterministicAmountExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

class HybridTransactionCaptureTest {

    private lateinit var classifier: RuleBasedClassifierFallback
    private lateinit var extractor: DeterministicAmountExtractor

    @Before
    fun setUp() {
        classifier = RuleBasedClassifierFallback()
        extractor = DeterministicAmountExtractor()
    }

    // ==========================================
    // 1. Classifier Tests
    // ==========================================

    @Test
    fun classifier_expenseNotification_classifiedAsExpenseWithHighConfidence() {
        val texts = listOf(
            "Paid $45.50 at Target on Sep 26",
            "Debit alert: Your card ending in 4321 was debited USD 120.00 for payment at Amazon",
            "Pembayaran Rp 75.000 ke Toko Jaya berhasil",
            "You spent €18.20 at Bakery Central"
        )

        for (text in texts) {
            val result = classifier.classify(text)
            assertEquals("Expected EXPENSE for: $text", TransactionClassificationType.EXPENSE, result.type)
            assertTrue("Expected confidence >= 0.65 for: $text, was ${result.confidence}", result.confidence >= 0.65f)
        }
    }

    @Test
    fun classifier_incomeNotification_classifiedAsIncomeWithHighConfidence() {
        val texts = listOf(
            "Salary credited: USD 3,500.00 from ACME Corp has been deposited into your account",
            "Received $50.00 from Alice via PayPal",
            "Dana masuk sebesar Rp 500.000 dari BUDI SANTOSO via BCA Mobile",
            "Refund of $25.00 has been credited to your account from Store"
        )

        for (text in texts) {
            val result = classifier.classify(text)
            assertEquals("Expected INCOME for: $text", TransactionClassificationType.INCOME, result.type)
            assertTrue("Expected confidence >= 0.65 for: $text, was ${result.confidence}", result.confidence >= 0.65f)
        }
    }

    @Test
    fun classifier_noiseNotification_classifiedAsNoise() {
        val texts = listOf(
            "Your OTP verification code is 492810. Do not share this code with anyone.",
            "Special discount! Get 50% cashback voucher on your next purchase using code PROMO50.",
            "Security alert: Login detected from a new Windows PC device.",
            "Selamat Anda memenangkan voucher diskon belanja!",
            "100RB Koin Siap Diklaim!🎁 Koin 100RB dan hadiah lainnya bisa diklaim dengan transaksi di App Store & Google Play! Cek 👉",
            "🏦BBRI: Laba Bersih Bank Only 8M26 Tumbuh +7% YoY; Time Deposits Naik Signif... IHSG turun -1,51%, BYAN: Nilai Transaksi Saham ke Entitas Haji Isam Belum Diketahui."
        )

        for (text in texts) {
            val result = classifier.classify(text)
            assertEquals("Expected NOISE for: $text", TransactionClassificationType.NOISE, result.type)
        }
    }

    @Test
    fun classifier_failedTransactionNotification_classifiedAsNoise() {
        val texts = listOf(
            "Pembayaran sebesar 200k ke Google Pay gagal",
            "Transaction of $150.00 at Apple declined",
            "Payment to Netflix failed due to insufficient funds",
            "Transaksi Rp 100.000 di Indomaret dibatalkan",
            "Transfer ke Rekening 123456 tidak berhasil"
        )

        for (text in texts) {
            val result = classifier.classify(text)
            assertEquals("Expected NOISE for failed transaction: $text", TransactionClassificationType.NOISE, result.type)
        }
    }

    @Test
    fun classifier_conversationalChatAndPromoBaitNotification_classifiedAsNoise() {
        val texts = listOf(
            "bayar 200.000 ke rekening bri aku sekarang",
            "bayar 250.000 sekarang dan dapatkan promo shopee terbaru",
            "tolong transfer 50.000 ya bro",
            "can you pay $20 for lunch?",
            "Bro, rekening bri aku ya jangan lupa bayar 150k"
        )

        for (text in texts) {
            val result = classifier.classify(text)
            assertEquals("Expected NOISE for conversational/promo text: $text", TransactionClassificationType.NOISE, result.type)
        }
    }

    // ==========================================
    // 2. Deterministic Extractor Tests
    // ==========================================

    @Test
    fun extractor_usStandardCommaThousandsAndDotDecimals_exactAmount() {
        val details = extractor.extract(
            text = "Payment of $1,250.75 to Apple Store completed",
            title = "Chase Alert"
        )

        assertNotNull(details)
        assertEquals(BigDecimal("1250.75"), details?.amount)
        assertEquals("$", details?.currencySymbol)
        assertEquals("Apple Store", details?.merchant)
    }

    @Test
    fun extractor_europeanDotThousandsAndCommaDecimals_exactAmount() {
        val details = extractor.extract(
            text = "Paid 1.250,50 € at MediaMarkt",
            title = "Revolut"
        )

        assertNotNull(details)
        assertEquals(BigDecimal("1250.50"), details?.amount)
        assertEquals("€", details?.currencySymbol)
        assertEquals("MediaMarkt", details?.merchant)
    }

    @Test
    fun extractor_indonesianRupiahThousandsSeparator_exactAmount() {
        val details = extractor.extract(
            text = "Pembayaran QRIS Rp 25.000 di Indomaret berhasil",
            title = "BCA"
        )

        assertNotNull(details)
        assertEquals(BigDecimal("25000.00"), details?.amount)
        assertTrue(details?.currencySymbol?.contains("Rp") == true)
        assertEquals("Indomaret", details?.merchant)
    }

    @Test
    fun extractor_commaThousandsWithoutDecimals_exactAmount() {
        val details = extractor.extract(
            text = "Transferred 50,000 IDR to Tokopedia",
            title = "Bank"
        )

        assertNotNull(details)
        assertEquals(BigDecimal("50000.00"), details?.amount)
        assertEquals("IDR", details?.currencySymbol)
        assertEquals("Tokopedia", details?.merchant)
    }

    @Test
    fun extractor_plainIntegerAndSingleDecimal_exactAmount() {
        val details = extractor.extract(
            text = "Spent $1250 at Walmart",
            title = "Bank of America"
        )

        assertNotNull(details)
        assertEquals(BigDecimal("1250.00"), details?.amount)
        assertEquals("Walmart", details?.merchant)

        val decimalDetails = extractor.extract(
            text = "Spent $1250.5 at Target",
            title = "Alert"
        )

        assertNotNull(decimalDetails)
        assertEquals(BigDecimal("1250.50"), decimalDetails?.amount)
        assertEquals("Target", decimalDetails?.merchant)
    }

    @Test
    fun extractor_incomeSourceExtraction_extractsSenderProperly() {
        val details = extractor.extract(
            text = "You received $250.00 from Alice via PayPal",
            title = "PayPal",
            isIncome = true
        )

        assertNotNull(details)
        assertEquals(BigDecimal("250.00"), details?.amount)
        assertEquals("Alice", details?.merchant)
    }

    @Test
    fun extractor_shorthandMultipliers_exactAmount() {
        val details200k = extractor.extract(
            text = "Pembayaran sebesar 200k ke Google Pay berhasil",
            title = "BCA"
        )
        assertNotNull(details200k)
        assertEquals(BigDecimal("200000.00"), details200k?.amount)
        assertEquals("Google Pay", details200k?.merchant)

        val details1_5jt = extractor.extract(
            text = "Transfer 1.5jt ke Tokopedia",
            title = "Bank"
        )
        assertNotNull(details1_5jt)
        assertEquals(BigDecimal("1500000.00"), details1_5jt?.amount)

        val details50rb = extractor.extract(
            text = "Spent 50rb at Lawson",
            title = "Wallet"
        )
        assertNotNull(details50rb)
        assertEquals(BigDecimal("50000.00"), details50rb?.amount)
    }

    // ==========================================
    // 3. End-to-End Pipeline Verification
    // ==========================================

    @Test
    fun pipeline_expenseFlow_producesParsedExpenseWithCorrectType() {
        val title = "Google Wallet"
        val text = "Paid $15.50 at Starbucks"
        val classification = classifier.classify("$title $text")

        assertEquals(TransactionClassificationType.EXPENSE, classification.type)
        assertTrue(classification.confidence >= 0.65f)

        val extracted = extractor.extract(text = text, title = title, isIncome = false)
        assertNotNull(extracted)

        val captureType = if (classification.type == TransactionClassificationType.INCOME) {
            TransactionCaptureType.INCOME
        } else {
            TransactionCaptureType.EXPENSE
        }

        assertEquals(TransactionCaptureType.EXPENSE, captureType)
        assertEquals(BigDecimal("15.50"), extracted?.amount)
        assertEquals("Starbucks", extracted?.merchant)
    }

    @Test
    fun pipeline_incomeFlow_producesParsedIncomeWithCorrectType() {
        val title = "Chase"
        val text = "Direct deposit: Your account was credited $2,800.00 from ACME Corp"
        val classification = classifier.classify("$title $text")

        assertEquals(TransactionClassificationType.INCOME, classification.type)
        assertTrue(classification.confidence >= 0.65f)

        val extracted = extractor.extract(text = text, title = title, isIncome = true)
        assertNotNull(extracted)

        val captureType = if (classification.type == TransactionClassificationType.INCOME) {
            TransactionCaptureType.INCOME
        } else {
            TransactionCaptureType.EXPENSE
        }

        assertEquals(TransactionCaptureType.INCOME, captureType)
        assertEquals(BigDecimal("2800.00"), extracted?.amount)
        assertEquals("ACME Corp", extracted?.merchant)
    }

    @Test
    fun pipeline_noiseFlow_ignoredBeforeExtraction() {
        val title = "Security Alert"
        val text = "Your secret verification OTP is 894120. Valid for 5 minutes."
        val classification = classifier.classify("$title $text")

        assertEquals(TransactionClassificationType.NOISE, classification.type)
    }

    @Test
    fun pipeline_chatAppNotification_rejectedByPackageFilter() {
        val isIgnored = HybridTransactionCaptureEngine.IGNORED_PACKAGES.contains("com.whatsapp")
        assertTrue("WhatsApp must be blacklisted in IGNORED_PACKAGES", isIgnored)

        val isTelegramIgnored = HybridTransactionCaptureEngine.IGNORED_PACKAGES.contains("org.telegram.messenger")
        assertTrue("Telegram must be blacklisted in IGNORED_PACKAGES", isTelegramIgnored)
    }

    @Test
    fun pipeline_googleOneSubscriptionNotification_extractedAccurately() {
        val title = "Google Play"
        val text = "Payment for subscription to Google One is successful, amount 87.580"
        val classification = classifier.classify("$title $text")

        assertEquals(TransactionClassificationType.EXPENSE, classification.type)

        val extracted = extractor.extract(text = text, title = title, isIncome = false)
        assertNotNull(extracted)
        assertEquals(BigDecimal("87580.00"), extracted?.amount)
        assertEquals("Google One", extracted?.merchant)
    }

    @Test
    fun pipeline_googleOneSubscriptionWithTypoNotification_extractedAccurately() {
        val title = "Google Play"
        val text = "Payment for subscribtion to google one is successfullt, amount 87.580"
        val classification = classifier.classify("$title $text")

        assertEquals(TransactionClassificationType.EXPENSE, classification.type)

        val extracted = extractor.extract(text = text, title = title, isIncome = false)
        assertNotNull(extracted)
        assertEquals(BigDecimal("87580.00"), extracted?.amount)
        assertEquals("google one", extracted?.merchant?.lowercase())
    }

    @Test
    fun pipeline_brimoToGooglePayNotification_extractedAccurately() {
        val title = "BRImo"
        val text = "Transfer ke GOOGLE PAY sebesar Rp 87.580 berhasil"
        val classification = classifier.classify("$title $text")

        assertEquals(TransactionClassificationType.EXPENSE, classification.type)

        val extracted = extractor.extract(text = text, title = title, isIncome = false)
        assertNotNull(extracted)
        assertEquals(BigDecimal("87580.00"), extracted?.amount)
        assertEquals("GOOGLE PAY", extracted?.merchant)
    }

    @Test
    fun pipeline_brimoQrisExpenseNotification_extractedAccurately() {
        val title = "BRImo"
        val text = "28/09/2026 08:46:16 Transaksi Pembelian QRIS sebesar Rp8.000,00 BERHASIL. Info lebih lanjut hubungi Call Center BRI 1500017"
        val classification = classifier.classify("$title $text")

        assertEquals(TransactionClassificationType.EXPENSE, classification.type)
        assertTrue("Confidence must be >= 0.70, was ${classification.confidence}", classification.confidence >= 0.70f)

        val extracted = extractor.extract(text = text, title = title, isIncome = false)
        assertNotNull(extracted)
        assertEquals(BigDecimal("8000.00"), extracted?.amount)
        assertEquals("QRIS", extracted?.merchant)
        assertEquals("Rp", extracted?.currencySymbol)
    }

    @Test
    fun pipeline_sobatBriIncomeNotification_extractedAccurately() {
        val title = "SMS"
        val text = "Sobat BRI! Dana Rp12.000.000 masuk ke rekening 155601001393539 pada 28/09/2026 07:13:30 KET.:PT PETROLINK SERVICES INDONESIA-BANK EKO"
        val classification = classifier.classify("$title $text")

        assertEquals(TransactionClassificationType.INCOME, classification.type)
        assertTrue("Confidence must be >= 0.70, was ${classification.confidence}", classification.confidence >= 0.70f)

        val extracted = extractor.extract(text = text, title = title, isIncome = true)
        assertNotNull(extracted)
        assertEquals(BigDecimal("12000000.00"), extracted?.amount)
        assertEquals("Rp", extracted?.currencySymbol)
    }

    @Test
    fun pipeline_brimoPackageWhitelistedInKnownFinancePackages() {
        assertTrue(HybridTransactionCaptureEngine.KNOWN_FINANCE_PACKAGES.contains("id.co.bri.brimo"))
        assertTrue(HybridTransactionCaptureEngine.KNOWN_FINANCE_PACKAGES.contains("com.bri.brimo"))
    }

    @Test
    fun pipeline_shopeeRealPaymentWithCoinReward_extractedAccurately() {
        val title = "ShopeePay"
        val text = "Pembayaran Rp 50.000 di Alfamart berhasil. Kamu mendapatkan 50 Koin Shopee."
        val classification = classifier.classify("$title $text")

        assertEquals(TransactionClassificationType.EXPENSE, classification.type)

        val extracted = extractor.extract(text = text, title = title, isIncome = false)
        assertNotNull(extracted)
        assertEquals(BigDecimal("50000.00"), extracted?.amount)
        assertEquals("Alfamart", extracted?.merchant)
    }
}
