package com.danilkinkin.buckwheat.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danilkinkin.buckwheat.data.ExtendCurrency
import com.danilkinkin.buckwheat.data.PendingExpenseRepository
import com.danilkinkin.buckwheat.data.RecurringTransactionRepository
import com.danilkinkin.buckwheat.data.dao.TransactionDao
import com.danilkinkin.buckwheat.data.entities.ParsedExpense
import com.danilkinkin.buckwheat.data.entities.RecurringTransaction
import com.danilkinkin.buckwheat.data.entities.Transaction
import com.danilkinkin.buckwheat.data.entities.TransactionCaptureType
import com.danilkinkin.buckwheat.data.entities.TransactionType
import com.danilkinkin.buckwheat.di.GetCurrentDateUseCase
import com.danilkinkin.buckwheat.di.SpendsRepository
import com.danilkinkin.buckwheat.util.countDays
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val spendsRepository: SpendsRepository,
    private val transactionDao: TransactionDao,
    private val recurringTransactionRepository: RecurringTransactionRepository,
    private val pendingExpenseRepository: PendingExpenseRepository,
    private val getCurrentDateUseCase: GetCurrentDateUseCase,
) : ViewModel() {

    private data class BudgetSnapshot(
        val dailyBudget: BigDecimal,
        val spentFromDailyBudget: BigDecimal,
        val budget: BigDecimal,
        val spent: BigDecimal,
        val finishPeriodDate: Date?,
        val reservedRecurring: BigDecimal,
        val currency: ExtendCurrency,
    )

    private val budgetSnapshotFlow: Flow<BudgetDataOrNull> = combine(
        spendsRepository.getDailyBudget().distinctUntilChanged(),
        spendsRepository.getSpentFromDailyBudget().distinctUntilChanged(),
        spendsRepository.getBudget().distinctUntilChanged(),
        spendsRepository.getSpent().distinctUntilChanged(),
        spendsRepository.getFinishPeriodDate().distinctUntilChanged(),
        spendsRepository.getReservedRecurringFlow().distinctUntilChanged(),
        spendsRepository.getCurrency().distinctUntilChanged(),
    ) { args: Array<Any?> ->
        BudgetDataOrNull(
            dailyBudget = args[0] as BigDecimal,
            spentFromDailyBudget = args[1] as BigDecimal,
            budget = args[2] as BigDecimal,
            spent = args[3] as BigDecimal,
            finishPeriodDate = args[4] as Date?,
            reservedRecurring = args[5] as BigDecimal,
            currency = args[6] as ExtendCurrency,
        )
    }.distinctUntilChanged()

    private data class BudgetDataOrNull(
        val dailyBudget: BigDecimal,
        val spentFromDailyBudget: BigDecimal,
        val budget: BigDecimal,
        val spent: BigDecimal,
        val finishPeriodDate: Date?,
        val reservedRecurring: BigDecimal,
        val currency: ExtendCurrency,
    )

    val pendingExpenses: StateFlow<List<ParsedExpense>> = pendingExpenseRepository.pendingExpenses

    val uiState: StateFlow<DashboardUiState> = combine(
        budgetSnapshotFlow,
        recurringTransactionRepository.getDueSoon(7).distinctUntilChanged(),
        pendingExpenseRepository.pendingExpenses.map { it.size }.distinctUntilChanged(),
        transactionDao.getRecentActivity(5).distinctUntilChanged(),
    ) { budgetData, upcoming, pendingCount, recentActivity ->
        val today = getCurrentDateUseCase()
        val isBudgetActive = budgetData.finishPeriodDate != null
        val daysLeft = if (budgetData.finishPeriodDate != null) {
            countDays(budgetData.finishPeriodDate, today).coerceAtLeast(0)
        } else {
            0
        }

        val todayAllowance = budgetData.dailyBudget - budgetData.spentFromDailyBudget
        val totalSpent = budgetData.spent + budgetData.spentFromDailyBudget
        val remainingPool = (budgetData.budget - totalSpent - budgetData.reservedRecurring)
            .coerceAtLeast(BigDecimal.ZERO)

        DashboardUiState(
            todayAllowance = todayAllowance,
            todaySpent = budgetData.spentFromDailyBudget,
            dailyTarget = budgetData.dailyBudget,
            periodRemainingPool = remainingPool,
            periodTotalBudget = budgetData.budget,
            periodDaysLeft = daysLeft,
            upcomingRecurring = upcoming,
            pendingCapturedCount = pendingCount,
            recentTransactions = recentActivity,
            currency = budgetData.currency,
            isBudgetSet = isBudgetActive,
            isLoading = false,
        )
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DashboardUiState(isLoading = true)
        )

    fun dismissPendingExpense(id: String) {
        pendingExpenseRepository.removePendingExpense(id)
    }

    fun confirmPendingExpense(expense: ParsedExpense) {
        viewModelScope.launch(Dispatchers.IO) {
            val transaction = Transaction(
                type = if (expense.type == TransactionCaptureType.INCOME) TransactionType.INCOME else TransactionType.SPENT,
                value = expense.amount,
                date = expense.date,
                comment = expense.merchant,
            )
            if (expense.type == TransactionCaptureType.INCOME) {
                spendsRepository.addIncome(transaction)
            } else {
                spendsRepository.addSpent(transaction)
            }
            pendingExpenseRepository.removePendingExpense(expense.id)
        }
    }
}
