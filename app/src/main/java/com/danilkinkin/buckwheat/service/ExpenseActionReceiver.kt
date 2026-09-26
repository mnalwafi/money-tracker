package com.danilkinkin.buckwheat.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.danilkinkin.buckwheat.data.PendingExpenseRepository
import com.danilkinkin.buckwheat.data.entities.Transaction
import com.danilkinkin.buckwheat.data.entities.TransactionType
import com.danilkinkin.buckwheat.di.SpendsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.Date
import javax.inject.Inject

@AndroidEntryPoint
class ExpenseActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CONFIRM = "com.danilkinkin.buckwheat.ACTION_CONFIRM_EXPENSE"
        const val ACTION_DISMISS = "com.danilkinkin.buckwheat.ACTION_DISMISS_EXPENSE"
    }

    @Inject
    lateinit var spendsRepository: SpendsRepository

    @Inject
    lateinit var pendingExpenseRepository: PendingExpenseRepository

    @Inject
    lateinit var notificationHelper: ExpenseNotificationHelper

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val pendingExpenseId = intent.getStringExtra(ExpenseNotificationHelper.EXTRA_PENDING_EXPENSE_ID) ?: return
        val notificationId = intent.getIntExtra(ExpenseNotificationHelper.EXTRA_NOTIFICATION_ID, 0)

        when (intent.action) {
            ACTION_CONFIRM -> {
                val rawAmount = intent.getStringExtra(ExpenseNotificationHelper.EXTRA_AMOUNT) ?: return
                val merchant = intent.getStringExtra(ExpenseNotificationHelper.EXTRA_MERCHANT) ?: ""
                val timestamp = intent.getLongExtra(ExpenseNotificationHelper.EXTRA_TIMESTAMP, System.currentTimeMillis())
                val typeName = intent.getStringExtra(ExpenseNotificationHelper.EXTRA_TYPE)
                val isIncome = typeName == com.danilkinkin.buckwheat.data.entities.TransactionCaptureType.INCOME.name

                val amount = try {
                    BigDecimal(rawAmount)
                } catch (e: Exception) {
                    return
                }

                val transaction = Transaction(
                    type = if (isIncome) TransactionType.INCOME else TransactionType.SPENT,
                    value = amount,
                    date = Date(timestamp),
                    comment = merchant,
                )

                val pendingResult = goAsync()
                scope.launch {
                    try {
                        if (isIncome) {
                            spendsRepository.addIncome(transaction)
                        } else {
                            spendsRepository.addSpent(transaction)
                        }
                        pendingExpenseRepository.removePendingExpense(pendingExpenseId)
                        notificationHelper.dismissNotification(notificationId)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            ACTION_DISMISS -> {
                pendingExpenseRepository.removePendingExpense(pendingExpenseId)
                notificationHelper.dismissNotification(notificationId)
            }
        }
    }
}
