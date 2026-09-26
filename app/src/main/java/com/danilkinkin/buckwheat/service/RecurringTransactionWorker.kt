package com.danilkinkin.buckwheat.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.danilkinkin.buckwheat.data.RecurringTransactionRepository
import com.danilkinkin.buckwheat.data.dao.RecurringTransactionDao
import com.danilkinkin.buckwheat.di.GetCurrentDateUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@HiltWorker
class RecurringTransactionWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val recurringTransactionDao: RecurringTransactionDao,
    private val recurringTransactionRepository: RecurringTransactionRepository,
    private val getCurrentDateUseCase: GetCurrentDateUseCase,
    private val notificationHelper: RecurringNotificationHelper,
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "com.danilkinkin.buckwheat.RecurringTransactionWorker"
    }

    override suspend fun doWork(): Result {
        val today = Instant.ofEpochMilli(getCurrentDateUseCase().time)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()

        val dueTransactions = recurringTransactionDao.getDueTransactions(today)

        for (recurring in dueTransactions) {
            if (recurring.autoDeduct) {
                recurringTransactionRepository.markAsPaid(recurring)
            } else {
                notificationHelper.postRecurringBillNotification(recurring)
            }
        }

        return Result.success()
    }
}
