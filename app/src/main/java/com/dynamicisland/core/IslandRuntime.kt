package com.dynamicisland.core

import android.app.Application
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Process-wide holder for the long-lived engines. The overlay service and the app UI
 * both live in the same process and share these instances.
 */
object IslandRuntime {
    private var initialized = false

    lateinit var settings: SettingsRepository private set
    lateinit var timer: TimerEngine private set
    lateinit var stopwatch: StopwatchEngine private set
    lateinit var battery: BatteryTracker private set
    lateinit var media: MediaTracker private set
    lateinit var calls: CallTracker private set

    /** True while the system has bound our (notification-content-free) listener. */
    val listenerConnected = MutableStateFlow(false)
    val latestNotification = MutableStateFlow<NotificationData?>(null)

    fun init(app: Application) {
        if (initialized) return
        initialized = true
        val ctx = app.applicationContext
        settings = SettingsRepository(ctx)
        timer = TimerEngine(ctx)
        stopwatch = StopwatchEngine(ctx)
        battery = BatteryTracker(ctx)
        media = MediaTracker(ctx)
        calls = CallTracker(ctx)
    }
}
