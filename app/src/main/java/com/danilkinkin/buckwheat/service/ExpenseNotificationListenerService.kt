package com.danilkinkin.buckwheat.service

import android.app.Notification
import android.content.ComponentName
import android.content.Intent
import android.os.Build
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

        @Volatile
        var isConnected: Boolean = false
            private set
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        Log.d(TAG, "NotificationListener connected and actively listening for transactions")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        Log.d(TAG, "NotificationListener disconnected, requesting rebind to maintain background capture")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                requestRebind(ComponentName(this, ExpenseNotificationListenerService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request rebind", e)
            }
        }
    }

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

        // Acquire a temporary partial wake lock so background CPU is not suspended before processing completes
        val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
        val wakeLock = powerManager?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "buckwheat:notification_capture")
        wakeLock?.acquire(5000L)

        serviceScope.launch {
            try {
                // Verify feature is enabled in user settings or notification access is granted
                val isSettingsEnabled = settingsRepository.isAutoExpenseCaptureEnabled().first()
                val isListenerPermitted = NotificationListenerUtils.isNotificationListenerEnabled(this@ExpenseNotificationListenerService)
                if (!isSettingsEnabled && !isListenerPermitted) {
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
                    val isAppForeground = com.danilkinkin.buckwheat.Application.isAppInForeground

                    when (addResult) {
                        is PendingExpenseAddResult.Added -> {
                            Log.d(TAG, "Transaction detected: ${addResult.expense.type} ${addResult.expense.amount} at ${addResult.expense.merchant}")
                            if (!isAppForeground) {
                                notificationHelper.showExpenseNotification(addResult.expense)
                            }
                        }
                        is PendingExpenseAddResult.Merged -> {
                            Log.d(TAG, "Cross-app gateway transaction merged: ${addResult.mergedExpense.type} ${addResult.mergedExpense.amount} at ${addResult.mergedExpense.merchant}")
                            if (!isAppForeground) {
                                notificationHelper.showExpenseNotification(addResult.mergedExpense)
                            }
                        }
                        is PendingExpenseAddResult.IgnoredDuplicate -> {
                            Log.d(TAG, "Duplicate transaction ignored: ${parsedExpense.amount} at ${parsedExpense.merchant}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing incoming notification", e)
            } finally {
                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                } catch (_: Exception) {}
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isConnected = false
        serviceScope.cancel()
    }
}
