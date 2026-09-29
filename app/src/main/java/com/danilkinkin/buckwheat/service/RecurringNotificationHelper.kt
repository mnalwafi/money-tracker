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
import com.danilkinkin.buckwheat.data.entities.RecurringTransaction
import com.danilkinkin.buckwheat.di.SpendsRepository
import com.danilkinkin.buckwheat.util.numberFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecurringNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val spendsRepository: SpendsRepository,
) {
    companion object {
        const val CHANNEL_ID = "channel_recurring_bills"
        const val EXTRA_RECURRING_ID = "extra_recurring_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        private const val BASE_NOTIFICATION_ID = 20000
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.recurring_transactions_title)
            val descriptionText = context.getString(R.string.recurring_transactions_desc)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun postRecurringBillNotification(recurring: RecurringTransaction) {
        val notificationId = (BASE_NOTIFICATION_ID + recurring.id).toInt()

        val currency = runBlocking { spendsRepository.getCurrency().first() }
        val formattedAmount = numberFormat(context, recurring.amount, currency = currency)

        val title = context.getString(R.string.recurring_bill_due_title)
        val body = context.getString(R.string.recurring_bill_due_body, recurring.name, formattedAmount)

        // Content intent: open app
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Action: Log Now / Pay Now
        val confirmIntent = Intent(context, RecurringActionReceiver::class.java).apply {
            action = RecurringActionReceiver.ACTION_CONFIRM_RECURRING
            putExtra(EXTRA_RECURRING_ID, recurring.id)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val confirmPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 1,
            confirmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Action: Skip
        val skipIntent = Intent(context, RecurringActionReceiver::class.java).apply {
            action = RecurringActionReceiver.ACTION_SKIP_RECURRING
            putExtra(EXTRA_RECURRING_ID, recurring.id)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val skipPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 2,
            skipIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(R.drawable.ic_apply, context.getString(R.string.recurring_mark_paid), confirmPendingIntent)
            .addAction(R.drawable.ic_close, context.getString(R.string.recurring_skip), skipPendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Permission POST_NOTIFICATIONS may not be granted
        }
    }

    fun dismissNotification(notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }
}
