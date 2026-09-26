package com.danilkinkin.buckwheat.service

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object NotificationListenerUtils {

    /**
     * Checks if notification listener access (reading other apps' incoming notifications) is granted.
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
     * Checks if permission to post notifications to the Android status bar is granted (Android 13+).
     */
    fun isPostNotificationPermissionGranted(context: Context): Boolean {
        val areNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (!areNotificationsEnabled) return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
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

    /**
     * Opens the App Notification Settings screen where user can allow notifications if blocked by system.
     */
    fun openAppNotificationSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
        context.startActivity(intent)
    }

    /**
     * Ensures that the NotificationListenerService is bound and active, even if the app was closed or killed.
     */
    fun ensureListenerConnected(context: Context) {
        if (!isNotificationListenerEnabled(context)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val componentName = ComponentName(context, ExpenseNotificationListenerService::class.java)
            try {
                android.service.notification.NotificationListenerService.requestRebind(componentName)
            } catch (_: Exception) {
                // Fallback for aggressive OEM task managers (MIUI, ColorOS, OneUI, EMUI):
                // Toggling component state forces Android's NotificationManagerService to immediately re-bind the listener.
                try {
                    val pm = context.packageManager
                    pm.setComponentEnabledSetting(
                        componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    pm.setComponentEnabledSetting(
                        componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                } catch (_: Exception) {}
            }
        }
    }
}
