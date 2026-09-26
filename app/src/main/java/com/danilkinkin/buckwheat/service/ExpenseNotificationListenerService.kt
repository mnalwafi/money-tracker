package com.danilkinkin.buckwheat.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.danilkinkin.buckwheat.data.PendingExpenseAddResult
import com.danilkinkin.buckwheat.data.PendingExpenseRepository
import com.danilkinkin.buckwheat.di.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ExpenseNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "ExpenseNotificationListener"
    }

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var pendingExpenseRepository: PendingExpenseRepository

    @Inject
    lateinit var captureEngine: HybridTransactionCaptureEngine

    @Inject
    lateinit var parser: NotificationExpenseParser

    @Inject
    lateinit var notificationHelper: ExpenseNotificationHelper

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        // Ignore notifications posted by our own app to prevent loops
        if (sbn.packageName == packageName) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        // Ignore social and messaging notifications from non-financial apps
        val category = notification.category
        val isChatNotification = category == Notification.CATEGORY_MESSAGE || category == Notification.CATEGORY_SOCIAL
        val isKnownFinance = HybridTransactionCaptureEngine.KNOWN_FINANCE_PACKAGES.contains(sbn.packageName)
        if (isChatNotification && !isKnownFinance) {
            return
        }

        serviceScope.launch {
            try {
                // Verify feature is enabled in user settings
                val isEnabled = settingsRepository.isAutoExpenseCaptureEnabled().first()
                if (!isEnabled) {
                    return@launch
                }

                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()

                // Prefer bigText if available as it often contains detailed transaction info
                val contentBody = if (!bigText.isNullOrBlank()) bigText else text

                val parsedExpense = captureEngine.processNotification(
                    packageName = sbn.packageName,
                    title = title,
                    text = contentBody,
                    postTime = sbn.postTime
                ) ?: parser.parse(
                    packageName = sbn.packageName,
                    title = title,
                    text = contentBody,
                    postTime = sbn.postTime
                )

                if (parsedExpense != null) {
                    val addResult = pendingExpenseRepository.addPendingExpense(parsedExpense)
                    when (addResult) {
                        is PendingExpenseAddResult.Added -> {
                            Log.d(TAG, "Transaction detected: ${addResult.expense.type} ${addResult.expense.amount} at ${addResult.expense.merchant}")
                            notificationHelper.showExpenseNotification(addResult.expense)
                        }
                        is PendingExpenseAddResult.Merged -> {
                            Log.d(TAG, "Cross-app gateway transaction merged: ${addResult.mergedExpense.type} ${addResult.mergedExpense.amount} at ${addResult.mergedExpense.merchant}")
                            notificationHelper.showExpenseNotification(addResult.mergedExpense)
                        }
                        is PendingExpenseAddResult.IgnoredDuplicate -> {
                            Log.d(TAG, "Duplicate transaction ignored: ${parsedExpense.amount} at ${parsedExpense.merchant}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing incoming notification", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
