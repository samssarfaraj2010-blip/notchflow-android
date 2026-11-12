package com.dynamicisland.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/** Reads the system's sticky battery broadcast. No polling, no wake locks. State is null while unplugged. */
class BatteryTracker(private val ctx: Context) {
    private val _state = MutableStateFlow<BatteryData?>(null)
    val state: StateFlow<BatteryData?> = _state.asStateFlow()
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) handle(intent)
        }
    }

    fun start() {
        if (registered) return
        val sticky = ContextCompat.registerReceiver(
            ctx, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        registered = true
        sticky?.let(::handle)
    }

    fun stop() {
        if (!registered) return
        ctx.unregisterReceiver(receiver)
        registered = false
        _state.value = null
    }

    private fun handle(i: Intent) {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        if (plugged == 0) {
            _state.value = null
            return
        }
        val status = when (i.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> ChargeStatus.CHARGING
            BatteryManager.BATTERY_STATUS_FULL -> ChargeStatus.FULL
            else -> ChargeStatus.PAUSED
        }
        val source = if (plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0) ChargeSource.WIRELESS else ChargeSource.WIRED
        // EXTRA_TEMPERATURE is tenths of a degree C; only show values in a plausible range.
        val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            .takeIf { it in 1..800 }?.let { it / 10f }
        _state.value = BatteryData((level * 100f / scale).roundToInt(), status, source, temp)
    }
}
