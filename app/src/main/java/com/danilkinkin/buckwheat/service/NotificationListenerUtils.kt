package com.danilkinkin.buckwheat.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

object NotificationListenerUtils {

    /**
     * Checks if notification listener access is granted for this app.
     */
    fun isNotificationListenerEnabled(context: Context): Boolean {
        val packageName = context.packageName
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
        if (enabledPackages.contains(packageName)) {
            return true
        }

        // Fallback check through Settings.Secure for older API / custom ROM compatibility
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        if (!flat.isNullOrEmpty()) {
            val expectedComponent = ComponentName(context, ExpenseNotificationListenerService::class.java).flattenToString()
            val names = flat.split(":")
            for (name in names) {
                val cn = ComponentName.unflattenFromString(name)
                if (cn != null && (cn.packageName == packageName || name == expectedComponent)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Opens the Android system settings screen where the user can grant notification listener access.
     */
    fun openNotificationListenerSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
