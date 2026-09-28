package com.danilkinkin.buckwheat.service.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

class DeterministicAmountExtractorTest {

    private lateinit var extractor: DeterministicAmountExtractor

    @Before
    fun setUp() {
        extractor = DeterministicAmountExtractor()
    }

    @Test
    fun extract_qrisNotificationWithTimestampAndCallCenter_extractsExactAmountNotDate() {
        val notification = "26/09/2026 19:23:41 - Transaksi Pembelian QRIS sebesar Rp13.000,00 BERHASIL. Info lebih lanjut hubungi Call Center BRI 1500017"

        val result = extractor.extract(notification, isIncome = false)

        assertNotNull("Expected extraction result, got null", result)
        assertEquals(
            "Expected amount 13000.00 instead of day of month 26",
            BigDecimal("13000.00"),
            result!!.amount
        )
        assertTrue(
            "Expected currency to start with Rp",
            result.currencySymbol?.startsWith("Rp", ignoreCase = true) == true
        )
        assertEquals("QRIS", result.merchant)
    }

    @Test
    fun extract_attachedCurrencyWithoutSpace_extractsCorrectly() {
        val notification = "Pembayaran QRIS ke Kopi Kenangan Rp18.000 berhasil"

        val result = extractor.extract(notification, isIncome = false)

        assertNotNull(result)
        assertEquals(BigDecimal("18000.00"), result!!.amount)
        assertTrue(result.currencySymbol?.startsWith("Rp", ignoreCase = true) == true)
        assertEquals("Kopi Kenangan", result.merchant)
    }

    @Test
    fun extract_indonesianBankNotificationWithDotSeparator() {
        val notification = "Pembayaran Rp 75.000 ke Toko Jaya berhasil"

        val result = extractor.extract(notification, isIncome = false)

        assertNotNull(result)
        assertEquals(BigDecimal("75000.00"), result!!.amount)
        assertEquals("Jaya", result.merchant)
    }

    @Test
    fun extract_englishNotificationWithDateAndCardEnding() {
        val notification = "Debit alert: Your card ending in 4321 was debited USD 120.00 for payment at Amazon. Call Center 180012345"

        val result = extractor.extract(notification, isIncome = false)

        assertNotNull(result)
        assertEquals(BigDecimal("120.00"), result!!.amount)
        assertEquals("USD", result.currencySymbol)
        assertEquals("Amazon", result.merchant)
    }

    @Test
    fun extract_standaloneAmountWithDatePrefix_ignoresDateAndExtractsAmount() {
        val notification = "26/09/2026 10:15:00 Paid 45.50 at Starbucks"

        val result = extractor.extract(notification, isIncome = false)

        assertNotNull(result)
        assertEquals(
            "Must ignore date '26' and extract actual payment amount '45.50'",
            BigDecimal("45.50"),
            result!!.amount
        )
        assertEquals("Starbucks", result.merchant)
    }

    @Test
    fun extract_shorthandAmount_expandsMultiplier() {
        val notification = "Pembayaran sebesar 200k ke Google Pay berhasil"

        val result = extractor.extract(notification, isIncome = false)

        assertNotNull(result)
        assertEquals(BigDecimal("200000.00"), result!!.amount)
        assertEquals("Google Pay", result.merchant)
    }

    @Test
    fun extract_keywordBackedIntegerAmountWithDate() {
        val notification = "Tagihan sebesar 15000 berhasil dibayar pada 26/09/2026"

        val result = extractor.extract(notification, isIncome = false)

        assertNotNull(result)
        assertEquals(BigDecimal("15000.00"), result!!.amount)
    }

    @Test
    fun extract_brimoExactUserQrisNotification_extracts8000AndQris() {
        val notification = "28/09/2026 08:46:16 Transaksi Pembelian QRIS sebesar Rp8.000,00 BERHASIL. Info lebih lanjut hubungi Call Center BRI 1500017"

        val result = extractor.extract(notification, isIncome = false)

        assertNotNull("Expected extraction result, got null", result)
        assertEquals(BigDecimal("8000.00"), result!!.amount)
        assertEquals("Rp", result.currencySymbol)
        assertEquals("QRIS", result.merchant)
    }

    @Test
    fun extract_sobatBriExactUserIncomeNotification_extracts12000000() {
        val notification = "Sobat BRI! Dana Rp12.000.000 masuk ke rekening 155601001393539 pada 28/09/2026 07:13:30 KET.:PT PETROLINK SERVICES INDONESIA-BANK EKO"

        val result = extractor.extract(notification, isIncome = true)

        assertNotNull("Expected extraction result, got null", result)
        assertEquals(BigDecimal("12000000.00"), result!!.amount)
        assertEquals("Rp", result.currencySymbol)
    }

    @Test
    fun extract_shopeeCoinPromoNotification_returnsNull() {
        val notification = "100RB Koin Siap Diklaim!🎁 Koin 100RB dan hadiah lainnya bisa diklaim dengan transaksi di App Store & Google Play! Cek 👉"

        val result = extractor.extract(notification, isIncome = false)

        org.junit.Assert.assertNull("Loyalty coins/points must not be extracted as financial amount", result)
    }

    @Test
    fun extract_shopeePaymentWithCoinReward_extractsOnlyRealMoney() {
        val notification = "Pembayaran Rp 50.000 di Alfamart berhasil. Kamu mendapatkan 50 Koin Shopee."

        val result = extractor.extract(notification, isIncome = false)

        assertNotNull("Must extract real money payment", result)
        assertEquals(BigDecimal("50000.00"), result!!.amount)
        assertEquals("Alfamart", result.merchant)
    }

    @Test
    fun extract_stockMarketNotification_returnsNull() {
        val notification = "🏦BBRI: Laba Bersih Bank Only 8M26 Tumbuh +7% YoY; Time Deposits Naik Signif... IHSG turun -1,51%, BYAN: Nilai Transaksi Saham ke Entitas Haji Isam Belum Diketahui."

        val result = extractor.extract(notification, isIncome = false)

        org.junit.Assert.assertNull("Stock market percentages and reporting periods must not be extracted as financial amounts", result)
    }
}
