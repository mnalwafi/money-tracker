package com.danilkinkin.buckwheat.data

import com.danilkinkin.buckwheat.data.entities.ParsedExpense
import com.danilkinkin.buckwheat.data.entities.TransactionCaptureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.util.Date

class PendingExpenseRepositoryTest {

    private lateinit var repository: PendingExpenseRepository

    @Before
    fun setUp() {
        repository = PendingExpenseRepository()
    }

    @Test
    fun addPendingExpense_bankThenGateway_mergesIntoSingleTransactionWithPreferredMerchant() {
        val baseTime = 1_700_000_000_000L

        // 1. Bank notification (e.g. BRImo) arrives first citing Google Pay
        val bankExpense = ParsedExpense(
            id = "bank-123",
            amount = BigDecimal("87580.00"),
            currencySymbol = "Rp",
            merchant = "Google Pay",
            rawText = "Transfer berhasil ke Google Pay",
            date = Date(baseTime),
            packageName = "id.co.bri.brimo",
            type = TransactionCaptureType.EXPENSE
        )

        val bankResult = repository.addPendingExpense(bankExpense)
        assertTrue(bankResult is PendingExpenseAddResult.Added)
        assertEquals(1, repository.pendingExpenses.value.size)
        assertEquals("Google Pay", repository.pendingExpenses.value.first().merchant)

        // 2. Gateway notification (Google Play / Store) arrives 3 seconds later citing specific item (Google One)
        val gatewayExpense = ParsedExpense(
            id = "gateway-456",
            amount = BigDecimal("87580.00"),
            currencySymbol = null,
            merchant = "Google One",
            rawText = "Payment for subscription to Google One is successful, amount 87.580",
            date = Date(baseTime + 3_000L), // 3 seconds later
            packageName = "com.android.vending",
            type = TransactionCaptureType.EXPENSE
        )

        val gatewayResult = repository.addPendingExpense(gatewayExpense)

        // Must be merged, NOT added as a 2nd notification or pending expense!
        assertTrue("Expected Merged result, got: $gatewayResult", gatewayResult is PendingExpenseAddResult.Merged)
        val merged = (gatewayResult as PendingExpenseAddResult.Merged).mergedExpense

        // Merged expense must prefer the specific end-merchant "Google One"
        assertEquals("Google One", merged.merchant)
        assertEquals("bank-123", merged.id) // Retains original notification ID to update in-place

        // Exactly 1 pending transaction in the repository
        assertEquals(1, repository.pendingExpenses.value.size)
        assertEquals("Google One", repository.pendingExpenses.value.first().merchant)
    }

    @Test
    fun addPendingExpense_gatewayThenBank_mergesIntoSingleTransactionRetainingStoreMerchant() {
        val baseTime = 1_700_000_000_000L

        // 1. Gateway notification arrives first
        val gatewayExpense = ParsedExpense(
            id = "gateway-456",
            amount = BigDecimal("87580.00"),
            currencySymbol = null,
            merchant = "Google One",
            rawText = "Payment for subscription to Google One is successful, amount 87.580",
            date = Date(baseTime),
            packageName = "com.android.vending",
            type = TransactionCaptureType.EXPENSE
        )

        val gatewayResult = repository.addPendingExpense(gatewayExpense)
        assertTrue(gatewayResult is PendingExpenseAddResult.Added)
        assertEquals(1, repository.pendingExpenses.value.size)

        // 2. Bank notification arrives 2 seconds later
        val bankExpense = ParsedExpense(
            id = "bank-123",
            amount = BigDecimal("87580.00"),
            currencySymbol = "Rp",
            merchant = "Google Pay",
            rawText = "Transfer berhasil ke Google Pay",
            date = Date(baseTime + 2_000L),
            packageName = "id.co.bri.brimo",
            type = TransactionCaptureType.EXPENSE
        )

        val bankResult = repository.addPendingExpense(bankExpense)
        assertTrue("Expected Merged result, got: $bankResult", bankResult is PendingExpenseAddResult.Merged)
        val merged = (bankResult as PendingExpenseAddResult.Merged).mergedExpense

        // Retains specific store item merchant
        assertEquals("Google One", merged.merchant)
        assertEquals(1, repository.pendingExpenses.value.size)
    }

    @Test
    fun addPendingExpense_twoDistinctPurchasesSameAmount_doesNotFilterOutDistinctPurchases() {
        val baseTime = 1_700_000_000_000L

        // Purchase 1: Coffee at Starbucks
        val coffee1 = ParsedExpense(
            id = "coffee-1",
            amount = BigDecimal("25000.00"),
            currencySymbol = "Rp",
            merchant = "Starbucks",
            rawText = "Pembayaran Rp 25.000 di Starbucks berhasil",
            date = Date(baseTime),
            packageName = "id.co.bri.brimo",
            type = TransactionCaptureType.EXPENSE
        )

        // Purchase 2: Coffee at Kopi Kenangan 5 seconds later with exact same amount
        val coffee2 = ParsedExpense(
            id = "coffee-2",
            amount = BigDecimal("25000.00"),
            currencySymbol = "Rp",
            merchant = "Kopi Kenangan",
            rawText = "Pembayaran Rp 25.000 di Kopi Kenangan berhasil",
            date = Date(baseTime + 5_000L),
            packageName = "id.co.bri.brimo",
            type = TransactionCaptureType.EXPENSE
        )

        val res1 = repository.addPendingExpense(coffee1)
        val res2 = repository.addPendingExpense(coffee2)

        // Both must be ADDED, neither should be dropped
        assertTrue(res1 is PendingExpenseAddResult.Added)
        assertTrue(res2 is PendingExpenseAddResult.Added)
        assertEquals(2, repository.pendingExpenses.value.size)
    }

    @Test
    fun addPendingExpense_exactDuplicateNotification_ignored() {
        val baseTime = 1_700_000_000_000L

        val expense1 = ParsedExpense(
            id = "exp-1",
            amount = BigDecimal("50000.00"),
            currencySymbol = "Rp",
            merchant = "Indomaret",
            rawText = "Transaksi Rp 50.000 di Indomaret berhasil",
            date = Date(baseTime),
            packageName = "id.co.bri.brimo",
            type = TransactionCaptureType.EXPENSE
        )

        val expenseDuplicate = ParsedExpense(
            id = "exp-2",
            amount = BigDecimal("50000.00"),
            currencySymbol = "Rp",
            merchant = "Indomaret",
            rawText = "Transaksi Rp 50.000 di Indomaret berhasil",
            date = Date(baseTime + 1_000L),
            packageName = "id.co.bri.brimo",
            type = TransactionCaptureType.EXPENSE
        )

        val res1 = repository.addPendingExpense(expense1)
        val res2 = repository.addPendingExpense(expenseDuplicate)

        assertTrue(res1 is PendingExpenseAddResult.Added)
        assertTrue(res2 is PendingExpenseAddResult.IgnoredDuplicate)
        assertEquals(1, repository.pendingExpenses.value.size)
    }

    @Test
    fun addPendingExpense_sameAmountAfterDeduplicationWindow_addedSeparately() {
        val baseTime = 1_700_000_000_000L

        val expense1 = ParsedExpense(
            id = "exp-1",
            amount = BigDecimal("50000.00"),
            currencySymbol = "Rp",
            merchant = "Indomaret",
            rawText = "Transaksi Rp 50.000 di Indomaret berhasil",
            date = Date(baseTime),
            packageName = "id.co.bri.brimo",
            type = TransactionCaptureType.EXPENSE
        )

        // 35 seconds later (> 30s window)
        val expense2 = ParsedExpense(
            id = "exp-2",
            amount = BigDecimal("50000.00"),
            currencySymbol = "Rp",
            merchant = "Indomaret",
            rawText = "Transaksi Rp 50.000 di Indomaret berhasil",
            date = Date(baseTime + 35_000L),
            packageName = "id.co.bri.brimo",
            type = TransactionCaptureType.EXPENSE
        )

        val res1 = repository.addPendingExpense(expense1)
        val res2 = repository.addPendingExpense(expense2)

        assertTrue(res1 is PendingExpenseAddResult.Added)
        assertTrue(res2 is PendingExpenseAddResult.Added)
        assertEquals(2, repository.pendingExpenses.value.size)
    }
}
