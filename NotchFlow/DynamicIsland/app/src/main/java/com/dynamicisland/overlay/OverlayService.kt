package com.dynamicisland.overlay

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlin.math.ceil
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.dynamicisland.MainActivity
import com.dynamicisland.R
import com.dynamicisland.core.ClockPhase
import com.dynamicisland.core.IslandController
import com.dynamicisland.core.IslandGeometry
import com.dynamicisland.core.IslandRuntime
import com.dynamicisland.ui.island.IslandPill
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Hosts the island as a TYPE_APPLICATION_OVERLAY window. A foreground service is the supported way to keep
 * an overlay-drawing process alive; it shows one minimal notification with a "Turn off" action.
 */
class OverlayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val owner = OverlayOwner()
    private val geometry = MutableStateFlow(IslandGeometry())

    private lateinit var wm: WindowManager
    private lateinit var controller: IslandController
    private var host: IslandHostView? = null
    private var params: WindowManager.LayoutParams? = null
    private var screenReceiver: BroadcastReceiver? = null
    private var initialized = false
    private var windowExpanded = false
    private var overlayWm: WindowManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        try {
            startAsForeground()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Foreground start not allowed", e)
            stopSelf()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission missing; stopping")
            OverlayDiag.put("Overlay permission", "MISSING")
            disableSelf()
            return
        }

        wm = getSystemService(WindowManager::class.java)
        val rt = IslandRuntime
        controller = IslandController(scope, rt.settings, rt.timer, rt.stopwatch, rt.battery, rt.media, rt.calls)
        controller.onFullyHidden = { detach() }
        initialized = true

        OverlayDiag.put("Service", "running")
        OverlayDiag.put("Overlay permission", "granted")
        owner.resume()
        updateGeometry(resources.configuration)
        controller.setScreenOn(getSystemService(PowerManager::class.java).isInteractive)
        registerScreenReceiver()
        rt.battery.start()
        rt.media.start()
        rt.calls.start()
        controller.start()

        scope.launch {
            controller.state.collect {
                OverlayDiag.put(
                    "State",
                    "activities=${it.activities.size} visible=${it.visible} expanded=${it.expanded} landscapeHidden=${controller.landscapeSuppressed}",
                )
            }
        }
        scope.launch { controller.state.map { it.hasContent }.distinctUntilChanged().collect { if (it) attach() } }
        // Grow the window before the expand animation; it shrinks again once collapsing has finished.
        scope.launch { controller.state.map { it.expanded }.distinctUntilChanged().collect { if (it) setWindowExpanded(true) } }
        scope.launch {
            rt.settings.flow.map { it.verticalOffsetDp to it.showInLandscape }.distinctUntilChanged().drop(1)
                .collect { updateGeometry(resources.configuration) }
        }
        scope.launch { rt.listenerConnected.collect { rt.media.onListenerChanged() } }
        // If the accessibility service is enabled/disabled while shown, move the window to the right layer.
        scope.launch {
            IslandAccessibilityService.connected.drop(1).collect {
                val had = host != null
                detach()
                if (had) attach()
            }
        }
        // Completes the timer while the process is awake; the exact alarm covers deep sleep.
        scope.launch {
            rt.timer.state.collectLatest { t ->
                if (t.phase == ClockPhase.RUNNING) {
                    delay(t.endElapsed - SystemClock.elapsedRealtime())
                    rt.timer.checkFinished()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                IslandRuntime.settings.update { copy(islandEnabled = false) }
                stopSelf()
                return START_NOT_STICKY
            }
            null, ACTION_START -> Unit
            else -> Log.w(TAG, "Ignored unknown action: ${intent.action}")
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (initialized) updateGeometry(newConfig)
    }

    override fun onDestroy() {
        running = false
        OverlayDiag.clear()
        if (initialized) {
            scope.cancel()
            detach()
            screenReceiver?.let { unregisterReceiver(it) }
            screenReceiver = null
            IslandRuntime.battery.stop()
            IslandRuntime.media.stop()
            IslandRuntime.calls.stop()
            owner.destroy()
        }
        super.onDestroy()
    }

    // ---- window ----------------------------------------------------------------------------

    private fun attach() {
        if (host != null) return
        val view = IslandHostView(this, owner) {
            if (IslandRuntime.settings.value.tapOutsideCollapse) controller.collapse()
        }
        view.setContent {
            val ui by controller.state.collectAsState()
            val geo by geometry.collectAsState()
            val settings by IslandRuntime.settings.flow.collectAsState()
            IslandPill(
                state = ui, geometry = geo, settings = settings, actions = controller,
                onExpandSettled = { expanded ->
                    OverlayDiag.put("Compose", "running")
                    if (!expanded) setWindowExpanded(false)
                },
            )
        }
        windowExpanded = controller.state.value.expanded
        // Above the status bar (and therefore tappable) when the accessibility service is on; below it otherwise.
        val a11y = IslandAccessibilityService.instance
        val manager = a11y?.getSystemService(WindowManager::class.java) ?: wm
        val p = buildParams(geometry.value, a11y != null)
        try {
            manager.addView(view, p)
            overlayWm = manager
            host = view
            params = p
            OverlayDiag.put("Window", "added via ${if (a11y != null) "accessibility" else "overlay"}, y=${p.y}px, size=${p.width}x${p.height}px")
        } catch (e: SecurityException) {
            Log.e(TAG, "Overlay window rejected", e)
            OverlayDiag.put("Window", "REJECTED: ${e.javaClass.simpleName}: ${e.message}")
            disableSelf()
        } catch (e: WindowManager.BadTokenException) {
            Log.e(TAG, "Overlay window rejected", e)
            OverlayDiag.put("Window", "REJECTED: ${e.javaClass.simpleName}: ${e.message}")
            disableSelf()
        }
    }

    private fun detach() {
        val h = host ?: return
        try {
            overlayWm?.removeViewImmediate(h)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Overlay view was already removed", e)
        }
        host = null
        params = null
        windowExpanded = false
        overlayWm = null
    }

    /** Explicit window sizes: small (just the pill) when collapsed, large enough for the expanded pill otherwise. */
    private fun windowSize(g: IslandGeometry, expanded: Boolean): Pair<Int, Int> {
        val d = resources.displayMetrics.density
        return if (expanded) ceil(g.expandedWidthDp * d).toInt() to ceil(EXPANDED_MAX_DP * d).toInt()
        else (ceil(g.compactWidthDp * d).toInt() + 2) to (ceil(g.compactHeightDp * d).toInt() + 2)
    }

    private fun setWindowExpanded(expanded: Boolean) {
        windowExpanded = expanded
        val h = host ?: return
        val p = params ?: return
        val (w, ht) = windowSize(geometry.value, expanded)
        val newY = geometry.value.yPx
        if (p.width != w || p.height != ht || p.y != newY) {
            p.width = w
            p.height = ht
            p.y = newY
            overlayWm?.updateViewLayout(h, p)
        }
    }

    private fun buildParams(g: IslandGeometry, accessibility: Boolean = false): WindowManager.LayoutParams {
        val (w, h) = windowSize(g, windowExpanded)
        return WindowManager.LayoutParams(
            w,
            h,
            if (accessibility) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = g.yPx
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            title = "DynamicIsland"
        }
    }

    private fun updateGeometry(cfg: Configuration) {
        val s = IslandRuntime.settings.value
        val g = OverlayGeometry.compute(this, wm, s.verticalOffsetDp)
        geometry.value = g
        OverlayDiag.put("Geometry", "cutout=${g.cutoutWidthDp.toInt()}dp pill=${g.compactWidthDp.toInt()}x${g.compactHeightDp.toInt()}dp y=${g.yPx}px")
        val landscape = cfg.orientation == Configuration.ORIENTATION_LANDSCAPE
        controller.setLandscapeSuppressed(landscape && !s.showInLandscape)
        val h = host
        val p = params
        if (h != null && p != null) {
            val (w, ht) = windowSize(g, windowExpanded)
            p.width = w
            p.height = ht
            p.y = g.yPx
            overlayWm?.updateViewLayout(h, p)
        }
    }

    // ---- screen state ----------------------------------------------------------------------

    private fun registerScreenReceiver() {
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> controller.setScreenOn(true)
                    Intent.ACTION_SCREEN_OFF -> controller.setScreenOn(false)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(this, r, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        screenReceiver = r
    }

    // ---- foreground notification -----------------------------------------------------------

    private fun startAsForeground() {
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("NotchFlow status").build(),
        )
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(
            this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_island)
            .setContentTitle("NotchFlow is on")
            .setContentText("Tap to open settings")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(0, "Turn off", stop)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        else ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun disableSelf() {
        IslandRuntime.settings.update { copy(islandEnabled = false) }
        stopSelf()
    }

    companion object {
        private const val TAG = "OverlayService"
        private const val CHANNEL_ID = "island_status"
        private const val NOTIFICATION_ID = 1
        private const val EXPANDED_MAX_DP = 232f
        const val ACTION_START = "com.dynamicisland.action.START"
        const val ACTION_STOP = "com.dynamicisland.action.STOP"

        @Volatile
        var running = false
            private set

        fun start(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, OverlayService::class.java).setAction(ACTION_START))
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Cannot start the overlay service right now", e)
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, OverlayService::class.java))
        }
    }
}
