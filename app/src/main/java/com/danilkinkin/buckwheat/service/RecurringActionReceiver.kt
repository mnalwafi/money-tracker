package com.danilkinkin.buckwheat.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.danilkinkin.buckwheat.data.RecurringTransactionRepository
import com.danilkinkin.buckwheat.data.dao.RecurringTransactionDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RecurringActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CONFIRM_RECURRING = "com.danilkinkin.buckwheat.ACTION_CONFIRM_RECURRING"
        const val ACTION_SKIP_RECURRING = "com.danilkinkin.buckwheat.ACTION_SKIP_RECURRING"
        const val ACTION_DISMISS_RECURRING = "com.danilkinkin.buckwheat.ACTION_DISMISS_RECURRING"
    }

    @Inject
    lateinit var recurringTransactionRepository: RecurringTransactionRepository

    @Inject
    lateinit var recurringTransactionDao: RecurringTransactionDao

    @Inject
    lateinit var notificationHelper: RecurringNotificationHelper

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val recurringId = intent.getLongExtra(RecurringNotificationHelper.EXTRA_RECURRING_ID, -1L)
        val notificationId = intent.getIntExtra(RecurringNotificationHelper.EXTRA_NOTIFICATION_ID, 0)

        when (intent.action) {
            ACTION_CONFIRM_RECURRING -> {
                if (recurringId != -1L) {
                    val pendingResult = goAsync()
                    scope.launch {
                        try {
                            val recurring = recurringTransactionDao.getById(recurringId)
                            if (recurring != null) {
                                recurringTransactionRepository.markAsPaid(recurring)
                            }
                            notificationHelper.dismissNotification(notificationId)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }

            ACTION_SKIP_RECURRING -> {
                if (recurringId != -1L) {
                    val pendingResult = goAsync()
                    scope.launch {
                        try {
                            val recurring = recurringTransactionDao.getById(recurringId)
                            if (recurring != null) {
                                recurringTransactionRepository.skipOccurrence(recurring)
                            }
                            notificationHelper.dismissNotification(notificationId)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }

            ACTION_DISMISS_RECURRING -> {
                notificationHelper.dismissNotification(notificationId)
            }
        }
    }
}
