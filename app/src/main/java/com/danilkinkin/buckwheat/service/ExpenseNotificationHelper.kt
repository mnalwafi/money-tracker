package com.danilkinkin.buckwheat.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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
        const val CHANNEL_ID = "channel_expense_capture"
        const val EXTRA_PENDING_EXPENSE_ID = "extra_pending_expense_id"
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
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showExpenseNotification(expense: ParsedExpense) {
        val notificationId = expense.id.hashCode()

        // 1. Content Intent (Tapping notification opens MainActivity to review/edit)
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_PENDING_EXPENSE_ID, expense.id)
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
        val title = context.getString(R.string.expense_detected_notification_title, formattedAmount, expense.merchant)
        val body = context.getString(R.string.expense_detected_notification_body)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_money)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                R.drawable.ic_edit,
                context.getString(R.string.log_expense_action),
                logPendingIntent
            )
            .addAction(
                R.drawable.ic_delete_forever,
                context.getString(R.string.dismiss_action),
                dismissPendingIntent
            )

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            // Notification permission might be denied on Android 13+
        }
    }

    fun dismissNotification(notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }
}
