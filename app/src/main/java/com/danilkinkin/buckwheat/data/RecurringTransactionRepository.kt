package com.danilkinkin.buckwheat.data

import com.danilkinkin.buckwheat.data.dao.RecurringTransactionDao
import com.danilkinkin.buckwheat.data.entities.RecurringTransaction
import com.danilkinkin.buckwheat.data.entities.Transaction
import com.danilkinkin.buckwheat.data.entities.TransactionType
import com.danilkinkin.buckwheat.di.GetCurrentDateUseCase
import com.danilkinkin.buckwheat.di.SpendsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class RecurringTransactionRepository @Inject constructor(
    private val recurringTransactionDao: RecurringTransactionDao,
    private val getCurrentDateUseCase: GetCurrentDateUseCase,
    // Use Provider to avoid potential circular dependency with SpendsRepository
    private val spendsRepositoryProvider: Provider<SpendsRepository>,
) {
    fun getAll(): Flow<List<RecurringTransaction>> = recurringTransactionDao.getAll()

    fun getActive(): Flow<List<RecurringTransaction>> = recurringTransactionDao.getActive()

    suspend fun getActiveList(): List<RecurringTransaction> = recurringTransactionDao.getActiveList()

    /**
     * Splits active transactions into "Due Soon" (due within the next [daysAhead] days or overdue)
     * and "Upcoming" (due after [daysAhead] days).
     */
    fun getDueSoon(daysAhead: Long = 7): Flow<List<RecurringTransaction>> {
        return recurringTransactionDao.getActive().map { list ->
            val today = currentLocalDate()
            val cutoff = today.plusDays(daysAhead)
            list.filter { !it.nextOccurrence.isAfter(cutoff) }
        }
    }

    fun getUpcoming(daysAhead: Long = 7): Flow<List<RecurringTransaction>> {
        return recurringTransactionDao.getActive().map { list ->
            val today = currentLocalDate()
            val cutoff = today.plusDays(daysAhead)
            list.filter { it.nextOccurrence.isAfter(cutoff) }
        }
    }

    /**
     * Calculates the aggregate monthly commitment for all active recurring transactions.
     */
    fun getTotalMonthlyCommitment(): Flow<BigDecimal> {
        return recurringTransactionDao.getActive().map { list ->
            var sum = BigDecimal.ZERO
            for (item in list) {
                sum = sum.add(item.amount.multiply(item.interval.monthlyFactor()))
            }
            sum.setScale(2, RoundingMode.HALF_UP)
        }
    }

    /**
     * Calculates the reserved amount for active recurring items that will fall
     * between today and [finishPeriodDate].
     */
    suspend fun calculateReservedAmount(finishPeriodDate: Date?): BigDecimal {
        if (finishPeriodDate == null) return BigDecimal.ZERO
        val finishLocalDate = dateToLocalDate(finishPeriodDate)
        val today = currentLocalDate()
        if (finishLocalDate.isBefore(today)) return BigDecimal.ZERO

        val activeList = recurringTransactionDao.getActiveList()
        var totalReserved = BigDecimal.ZERO

        for (item in activeList) {
            val occurrences = item.interval.countOccurrencesBetween(
                startDate = today,
                endDate = finishLocalDate,
                firstOccurrence = item.nextOccurrence,
            )
            if (occurrences > 0) {
                totalReserved = totalReserved.add(item.amount.multiply(BigDecimal(occurrences)))
            }
        }

        return totalReserved.setScale(2, RoundingMode.HALF_UP)
    }

    /**
     * Flow emitting the reserved amount whenever active recurring transactions change.
     */
    fun getReservedAmountFlow(finishDateFlow: Flow<Date?>): Flow<BigDecimal> {
        return combine(recurringTransactionDao.getActive(), finishDateFlow) { activeList, finishDate ->
            if (finishDate == null) return@combine BigDecimal.ZERO
            val finishLocalDate = dateToLocalDate(finishDate)
            val today = currentLocalDate()
            if (finishLocalDate.isBefore(today)) return@combine BigDecimal.ZERO

            var totalReserved = BigDecimal.ZERO
            for (item in activeList) {
                val occurrences = item.interval.countOccurrencesBetween(
                    startDate = today,
                    endDate = finishLocalDate,
                    firstOccurrence = item.nextOccurrence,
                )
                if (occurrences > 0) {
                    totalReserved = totalReserved.add(item.amount.multiply(BigDecimal(occurrences)))
                }
            }
            totalReserved.setScale(2, RoundingMode.HALF_UP)
        }
    }

    suspend fun insert(recurringTransaction: RecurringTransaction): Long {
        return recurringTransactionDao.insert(recurringTransaction)
    }

    suspend fun update(recurringTransaction: RecurringTransaction) {
        recurringTransactionDao.update(recurringTransaction)
    }

    suspend fun delete(recurringTransaction: RecurringTransaction) {
        recurringTransactionDao.delete(recurringTransaction)
    }

    suspend fun deleteById(id: Long) {
        recurringTransactionDao.deleteById(id)
    }

    suspend fun toggleActive(recurringTransaction: RecurringTransaction, isActive: Boolean) {
        recurringTransactionDao.update(recurringTransaction.copy(isActive = isActive))
    }

    /**
     * Marks an occurrence as paid:
     * 1. Records a SPENT Transaction in SpendsRepository
     * 2. Advances nextOccurrence by one interval
     */
    suspend fun markAsPaid(recurringTransaction: RecurringTransaction) {
        val spendsRepo = spendsRepositoryProvider.get()
        val transaction = Transaction(
            type = TransactionType.SPENT,
            value = recurringTransaction.amount,
            date = getCurrentDateUseCase(),
            comment = recurringTransaction.categoryTag?.takeIf { it.isNotBlank() } ?: recurringTransaction.name,
        )
        spendsRepo.addSpent(transaction)

        val nextDate = recurringTransaction.interval.calculateNext(recurringTransaction.nextOccurrence)
        recurringTransactionDao.update(recurringTransaction.copy(nextOccurrence = nextDate))
    }

    /**
     * Skips the current occurrence without deducting funds.
     */
    suspend fun skipOccurrence(recurringTransaction: RecurringTransaction) {
        val nextDate = recurringTransaction.interval.calculateNext(recurringTransaction.nextOccurrence)
        recurringTransactionDao.update(recurringTransaction.copy(nextOccurrence = nextDate))
    }

    private fun currentLocalDate(): LocalDate {
        return dateToLocalDate(getCurrentDateUseCase())
    }

    private fun dateToLocalDate(date: Date): LocalDate {
        return Instant.ofEpochMilli(date.time)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
    }
}
