package com.dynamicisland

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dynamicisland.overlay.IslandAccessibilityService
import com.dynamicisland.core.MediaNotificationListener

data class Perms(
    val overlay: Boolean,
    val mediaAccess: Boolean,
    val notifications: Boolean,
    val phone: Boolean,
    val touch: Boolean,
)

object Permissions {
    fun read(ctx: Context) = Perms(
        overlay = Settings.canDrawOverlays(ctx),
        mediaAccess = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName),
        notifications = NotificationManagerCompat.from(ctx).areNotificationsEnabled(),
        phone = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED,
        touch = IslandAccessibilityService.connected.value,
    )

    fun overlayIntent(ctx: Context) =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))

    fun mediaAccessIntent(ctx: Context) = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
        .putExtra(
            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
            ComponentName(ctx, MediaNotificationListener::class.java).flattenToString(),
        )

    fun accessibilityIntent() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun appInfoIntent(ctx: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))

    /** Opens a system settings page; falls back to the app-info page if the device lacks the target screen. */
    fun open(ctx: Context, intent: Intent) {
        try {
            ctx.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w("Permissions", "Settings screen unavailable, using app info", e)
            ctx.startActivity(appInfoIntent(ctx))
        }
    }
}
