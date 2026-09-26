package com.danilkinkin.buckwheat.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.danilkinkin.buckwheat.MainActivity
import com.danilkinkin.buckwheat.R
import com.danilkinkin.buckwheat.data.entities.ParsedExpense
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "ExpenseNotifHelper"
        const val CHANNEL_ID = "channel_expense_capture"
        const val EXTRA_PENDING_EXPENSE_ID = "extra_pending_expense_id"
        const val EXTRA_TYPE = "extra_type"
        const val EXTRA_AMOUNT = "extra_amount"
        const val EXTRA_MERCHANT = "extra_merchant"
        const val EXTRA_TIMESTAMP = "extra_timestamp"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.notification_channel_expense_name)
            val descriptionText = context.getString(R.string.notification_channel_expense_desc)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showExpenseNotification(expense: ParsedExpense) {
        // Compute a guaranteed positive notification ID
        val notificationId = (Math.abs(expense.id.hashCode()) % 100000) + 10000
        val isIncome = expense.type == com.danilkinkin.buckwheat.data.entities.TransactionCaptureType.INCOME

        // 1. Content Intent (Tapping notification opens MainActivity to review/edit)
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_PENDING_EXPENSE_ID, expense.id)
            putExtra(EXTRA_TYPE, expense.type.name)
            putExtra(EXTRA_AMOUNT, expense.amount.toString())
            putExtra(EXTRA_MERCHANT, expense.merchant)
            putExtra(EXTRA_TIMESTAMP, expense.date.time)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. Action: Log Immediately
        val logIntent = Intent(context, ExpenseActionReceiver::class.java).apply {
            action = ExpenseActionReceiver.ACTION_CONFIRM
            putExtra(EXTRA_PENDING_EXPENSE_ID, expense.id)
            putExtra(EXTRA_TYPE, expense.type.name)
            putExtra(EXTRA_AMOUNT, expense.amount.toString())
            putExtra(EXTRA_MERCHANT, expense.merchant)
            putExtra(EXTRA_TIMESTAMP, expense.date.time)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val logPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 2 + 1,
            logIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Action: Dismiss
        val dismissIntent = Intent(context, ExpenseActionReceiver::class.java).apply {
            action = ExpenseActionReceiver.ACTION_DISMISS
            putExtra(EXTRA_PENDING_EXPENSE_ID, expense.id)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 2 + 2,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedAmount = "${expense.currencySymbol ?: ""} ${expense.amount}".trim()
        val title = if (isIncome) {
            context.getString(R.string.income_detected_notification_title, formattedAmount, expense.merchant)
        } else {
            context.getString(R.string.expense_detected_notification_title, formattedAmount, expense.merchant)
        }
        val body = if (isIncome) {
            context.getString(R.string.income_detected_notification_body)
        } else {
            context.getString(R.string.expense_detected_notification_body)
        }
        val actionLabel = if (isIncome) {
            context.getString(R.string.add_income_action)
        } else {
            context.getString(R.string.log_expense_action)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_money)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setShowWhen(true)
            .setWhen(expense.date.time)
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(contentPendingIntent)
            .addAction(
                R.drawable.ic_apply,
                actionLabel,
                logPendingIntent
            )
            .addAction(
                R.drawable.ic_close,
                context.getString(R.string.dismiss_action),
                dismissPendingIntent
            )

        val areEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (!areEnabled) {
            Log.w(TAG, "NotificationManagerCompat.areNotificationsEnabled is FALSE! Android will suppress notifications until enabled in settings.")
        }

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
            Log.d(TAG, "Successfully posted system notification id=$notificationId for ${expense.merchant}")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: POST_NOTIFICATIONS runtime permission is missing", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post notification", e)
        }
    }

    fun dismissNotification(notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }
}
