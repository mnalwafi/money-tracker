package com.danilkinkin.buckwheat.dashboard

import com.danilkinkin.buckwheat.data.ExtendCurrency
import com.danilkinkin.buckwheat.data.entities.RecurrenceInterval
import com.danilkinkin.buckwheat.data.entities.RecurringTransaction
import com.danilkinkin.buckwheat.data.entities.Transaction
import com.danilkinkin.buckwheat.data.entities.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Date

class DashboardUiStateTest {

    @Test
    fun testDashboardUiState_defaults() {
        val state = DashboardUiState()

        assertEquals(BigDecimal.ZERO, state.todayAllowance)
        assertEquals(BigDecimal.ZERO, state.todaySpent)
        assertEquals(BigDecimal.ZERO, state.dailyTarget)
        assertEquals(BigDecimal.ZERO, state.periodRemainingPool)
        assertEquals(BigDecimal.ZERO, state.periodTotalBudget)
        assertEquals(0, state.periodDaysLeft)
        assertTrue(state.upcomingRecurring.isEmpty())
        assertEquals(0, state.pendingCapturedCount)
        assertTrue(state.recentTransactions.isEmpty())
        assertEquals(ExtendCurrency.none(), state.currency)
        assertFalse(state.isBudgetSet)
        assertFalse(state.isLoading)
    }

    @Test
    fun testDashboardUiState_safeAllowanceCalculation() {
        val dailyTarget = BigDecimal("150.00")
        val todaySpent = BigDecimal("45.50")
        val safeAllowance = dailyTarget - todaySpent

        val state = DashboardUiState(
            todayAllowance = safeAllowance,
            todaySpent = todaySpent,
            dailyTarget = dailyTarget,
            isBudgetSet = true,
        )

        assertEquals(BigDecimal("104.50"), state.todayAllowance)
        assertTrue(state.todayAllowance > BigDecimal.ZERO)
    }

    @Test
    fun testDashboardUiState_overdraftState() {
        val dailyTarget = BigDecimal("50.00")
        val todaySpent = BigDecimal("85.00")
        val safeAllowance = dailyTarget - todaySpent

        val state = DashboardUiState(
            todayAllowance = safeAllowance,
            todaySpent = todaySpent,
            dailyTarget = dailyTarget,
            isBudgetSet = true,
        )

        assertEquals(BigDecimal("-35.00"), state.todayAllowance)
        assertTrue(state.todayAllowance < BigDecimal.ZERO)
    }

    @Test
    fun testDashboardUiState_periodRemainingPoolWithReservedRecurring() {
        val totalBudget = BigDecimal("1200.00")
        val cycleSpent = BigDecimal("400.00")
        val todaySpent = BigDecimal("50.00")
        val reservedRecurring = BigDecimal("250.00")

        val totalSpent = cycleSpent + todaySpent
        val remainingPool = (totalBudget - totalSpent - reservedRecurring).coerceAtLeast(BigDecimal.ZERO)

        val state = DashboardUiState(
            periodTotalBudget = totalBudget,
            periodRemainingPool = remainingPool,
            periodDaysLeft = 14,
            isBudgetSet = true,
        )

        assertEquals(BigDecimal("500.00"), state.periodRemainingPool)
        assertEquals(14, state.periodDaysLeft)
    }

    @Test
    fun testDashboardUiState_upcomingRecurringList() {
        val recurring1 = RecurringTransaction(
            id = 1L,
            name = "Netflix",
            amount = BigDecimal("15.99"),
            interval = RecurrenceInterval.MONTHLY,
            startDate = LocalDate.now(),
            nextOccurrence = LocalDate.now().plusDays(2),
            isActive = true,
            autoDeduct = false,
        )
        val recurring2 = RecurringTransaction(
            id = 2L,
            name = "Internet",
            amount = BigDecimal("60.00"),
            interval = RecurrenceInterval.MONTHLY,
            startDate = LocalDate.now(),
            nextOccurrence = LocalDate.now().plusDays(5),
            isActive = true,
            autoDeduct = true,
        )

        val state = DashboardUiState(
            upcomingRecurring = listOf(recurring1, recurring2),
        )

        assertEquals(2, state.upcomingRecurring.size)
        assertEquals("Netflix", state.upcomingRecurring[0].name)
        assertEquals("Internet", state.upcomingRecurring[1].name)
    }

    @Test
    fun testDashboardUiState_recentTransactionsList() {
        val tx1 = Transaction(
            type = TransactionType.SPENT,
            value = BigDecimal("12.50"),
            date = Date(),
            comment = "Coffee",
        )
        val tx2 = Transaction(
            type = TransactionType.INCOME,
            value = BigDecimal("500.00"),
            date = Date(),
            comment = "Freelance",
        )

        val state = DashboardUiState(
            recentTransactions = listOf(tx1, tx2),
            pendingCapturedCount = 3,
        )

        assertEquals(2, state.recentTransactions.size)
        assertEquals("Coffee", state.recentTransactions[0].comment)
        assertEquals(TransactionType.INCOME, state.recentTransactions[1].type)
        assertEquals(3, state.pendingCapturedCount)
    }
}
