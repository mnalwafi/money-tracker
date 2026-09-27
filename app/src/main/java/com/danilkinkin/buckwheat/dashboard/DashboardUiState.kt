package com.danilkinkin.buckwheat.dashboard

import androidx.compose.runtime.Immutable
import com.danilkinkin.buckwheat.data.ExtendCurrency
import com.danilkinkin.buckwheat.data.entities.RecurringTransaction
import com.danilkinkin.buckwheat.data.entities.Transaction
import java.math.BigDecimal

@Immutable
data class DashboardUiState(
    val todayAllowance: BigDecimal = BigDecimal.ZERO,
    val todaySpent: BigDecimal = BigDecimal.ZERO,
    val dailyTarget: BigDecimal = BigDecimal.ZERO,
    val periodRemainingPool: BigDecimal = BigDecimal.ZERO,
    val periodTotalBudget: BigDecimal = BigDecimal.ZERO,
    val periodDaysLeft: Int = 0,
    val upcomingRecurring: List<RecurringTransaction> = emptyList(),
    val pendingCapturedCount: Int = 0,
    val recentTransactions: List<Transaction> = emptyList(),
    val currency: ExtendCurrency = ExtendCurrency.none(),
    val isBudgetSet: Boolean = false,
    val isLoading: Boolean = false,
)
