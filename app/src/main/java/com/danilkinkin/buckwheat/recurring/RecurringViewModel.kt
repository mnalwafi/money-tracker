package com.danilkinkin.buckwheat.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danilkinkin.buckwheat.data.ExtendCurrency
import com.danilkinkin.buckwheat.data.RecurringTransactionRepository
import com.danilkinkin.buckwheat.data.entities.RecurringTransaction
import com.danilkinkin.buckwheat.di.GetCurrentDateUseCase
import com.danilkinkin.buckwheat.di.SpendsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class RecurringViewModel @Inject constructor(
    private val recurringRepository: RecurringTransactionRepository,
    private val spendsRepository: SpendsRepository,
    private val getCurrentDateUseCase: GetCurrentDateUseCase,
) : ViewModel() {

    val allTransactions: StateFlow<List<RecurringTransaction>> = recurringRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dueSoon: StateFlow<List<RecurringTransaction>> = recurringRepository.getDueSoon(daysAhead = 7)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val upcoming: StateFlow<List<RecurringTransaction>> = recurringRepository.getUpcoming(daysAhead = 7)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monthlyCommitment: StateFlow<BigDecimal> = recurringRepository.getTotalMonthlyCommitment()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BigDecimal.ZERO)

    val reservedThisPeriod: StateFlow<BigDecimal> = recurringRepository.getReservedAmountFlow(spendsRepository.getFinishPeriodDate())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BigDecimal.ZERO)

    val currency: StateFlow<ExtendCurrency> = spendsRepository.getCurrency()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExtendCurrency(null, ExtendCurrency.Type.NONE))

    fun saveRecurring(transaction: RecurringTransaction) {
        viewModelScope.launch {
            if (transaction.id == 0L) {
                recurringRepository.insert(transaction)
            } else {
                recurringRepository.update(transaction)
            }
        }
    }

    fun deleteRecurring(transaction: RecurringTransaction) {
        viewModelScope.launch {
            recurringRepository.delete(transaction)
        }
    }

    fun toggleActive(transaction: RecurringTransaction, isActive: Boolean) {
        viewModelScope.launch {
            recurringRepository.toggleActive(transaction, isActive)
        }
    }

    fun markAsPaid(transaction: RecurringTransaction) {
        viewModelScope.launch {
            recurringRepository.markAsPaid(transaction)
        }
    }

    fun skipOccurrence(transaction: RecurringTransaction) {
        viewModelScope.launch {
            recurringRepository.skipOccurrence(transaction)
        }
    }

    fun currentDate(): LocalDate {
        return getCurrentDateUseCase().toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
    }
}
