package com.dynamicisland.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
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

/**
 * Exists only so the user can grant "notification access", which Android requires before an app may read
 * media sessions. It deliberately does NOT override onNotificationPosted/Removed: notification contents
 * are never read, stored or transmitted.
 */
class MediaNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        IslandRuntime.listenerConnected.value = true
    }

    override fun onListenerDisconnected() {
        IslandRuntime.listenerConnected.value = false
    }
}
