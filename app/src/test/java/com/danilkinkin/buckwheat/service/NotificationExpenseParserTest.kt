package com.danilkinkin.buckwheat.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

class NotificationExpenseParserTest {

    private lateinit var parser: NotificationExpenseParser

    @Before
    fun setUp() {
        parser = NotificationExpenseParser()
    }

    @Test
    fun parse_googlePayExpense_extractsCorrectAmountAndMerchant() {
        val expense = parser.parse(
            packageName = "com.google.android.apps.walletnfcrel",
            title = "Google Pay",
            text = "Paid $14.50 at Starbucks on Sep 26",
            postTime = 1700000000000L
        )

        assertNotNull(expense)
        assertEquals(BigDecimal("14.50"), expense?.amount)
        assertEquals("$", expense?.currencySymbol)
        assertEquals("Starbucks", expense?.merchant)
    }

    @Test
    fun parse_payPalExpense_extractsEuroAndMerchant() {
        val expense = parser.parse(
            packageName = "com.paypal.android.p2pmobile",
            title = "PayPal",
            text = "You paid €29.99 to Netflix",
            postTime = 1700000000000L
        )

        assertNotNull(expense)
        assertEquals(BigDecimal("29.99"), expense?.amount)
        assertEquals("€", expense?.currencySymbol)
        assertEquals("Netflix", expense?.merchant)
    }

    @Test
    fun parse_indonesianQrisExpense_extractsRpAndMerchant() {
        val expense = parser.parse(
            packageName = "com.bca",
            title = "BCA Mobile",
            text = "Pembayaran QRIS Rp 45.000 di Kopi Kenangan berhasil",
            postTime = 1700000000000L
        )

        assertNotNull(expense)
        assertEquals(BigDecimal("45000.00"), expense?.amount)
        assertTrue(expense?.currencySymbol?.contains("Rp") == true)
        assertEquals("Kopi Kenangan", expense?.merchant)
    }

    @Test
    fun parse_usCommaThousandsSeparator_extractsCorrectValue() {
        val expense = parser.parse(
            packageName = "com.chase.sig.android",
            title = "Chase Alert",
            text = "Debit card purchase of $1,250.75 at Apple Store",
            postTime = 1700000000000L
        )

        assertNotNull(expense)
        assertEquals(BigDecimal("1250.75"), expense?.amount)
        assertEquals("Apple Store", expense?.merchant)
    }

    @Test
    fun parse_europeanCommaDecimalSeparator_extractsCorrectValue() {
        val expense = parser.parse(
            packageName = "com.revolut.revolut",
            title = "Revolut",
            text = "Spent 12,50 € at Boulangerie",
            postTime = 1700000000000L
        )

        assertNotNull(expense)
        assertEquals(BigDecimal("12.50"), expense?.amount)
        assertEquals("Boulangerie", expense?.merchant)
    }

    @Test
    fun parse_otpNotification_isRejected() {
        val result = parser.parse(
            packageName = "com.bca",
            title = "Security Alert",
            text = "Your OTP is 491021. Do not share this one time password with anyone.",
            postTime = 1700000000000L
        )

        assertNull(result)
    }

    @Test
    fun parse_promotionalCashbackNotification_isRejected() {
        val result = parser.parse(
            packageName = "id.dana",
            title = "Special Offer",
            text = "Get 50% discount and cashback up to Rp 50.000 on your next transaction with voucher",
            postTime = 1700000000000L
        )

        assertNull(result)
    }

    @Test
    fun parse_refundNotification_isRejected() {
        val result = parser.parse(
            packageName = "com.paypal.android.p2pmobile",
            title = "PayPal Refund",
            text = "Refund of $35.00 has been credited to your account from Merchant",
            postTime = 1700000000000L
        )

        assertNull(result)
    }

    @Test
    fun parse_incomingMoneyTransfer_isRejected() {
        val result = parser.parse(
            packageName = "com.bca",
            title = "Transfer Masuk",
            text = "Dana masuk Rp 500.000 dari John Doe",
            postTime = 1700000000000L
        )

        assertNull(result)
    }

    @Test
    fun parse_genericAppWithDebitKeyword_isAccepted() {
        val expense = parser.parse(
            packageName = "com.someother.fintech",
            title = "Transaction",
            text = "Charged $9.99 for Spotify subscription",
            postTime = 1700000000000L
        )

        assertNotNull(expense)
        assertEquals(BigDecimal("9.99"), expense?.amount)
        assertEquals("Spotify", expense?.merchant)
    }

    @Test
    fun parse_fallbackMerchantToTitle_whenMerchantPatternMissing() {
        val expense = parser.parse(
            packageName = "com.venmo",
            title = "Target",
            text = "You paid $55.00 using your Venmo debit card",
            postTime = 1700000000000L
        )

        assertNotNull(expense)
        assertEquals(BigDecimal("55.00"), expense?.amount)
        assertEquals("Target", expense?.merchant)
    }
}
