package com.dynamicisland.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.app.Notification
import android.os.Build
import android.util.Log
import com.dynamicisland.overlay.OverlayService

/** Fires from AlarmManager when the timer is due. Non-exported; reached by an explicit immutable PendingIntent. */
class TimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TimerEngine.ACTION_TIMER_DONE) IslandRuntime.timer.checkFinished()
        else Log.w("TimerAlarmReceiver", "Ignored unexpected action: ${intent.action}")
    }
}

/** Restarts the island after boot or app update, only if the user had it enabled and permission still exists. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (IslandRuntime.settings.value.islandEnabled && Settings.canDrawOverlays(context)) {
                    OverlayService.start(context)
                }
            }
            else -> Log.w("BootReceiver", "Ignored unexpected action: ${intent.action}")
        }
    }
}

/** User-enabled notification access: reads only title/text for the temporary island preview. */
class MediaNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        IslandRuntime.listenerConnected.value = true
    }

    override fun onListenerDisconnected() {
        IslandRuntime.listenerConnected.value = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName || sbn.isOngoing || sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        val appName = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)
        IslandRuntime.latestNotification.value = NotificationData(sbn.key, title.ifBlank { appName }, text, appName)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (IslandRuntime.latestNotification.value?.key == sbn.key) IslandRuntime.latestNotification.value = null
    }
}
