package com.dynamicisland.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private fun bootCount(ctx: Context): Int =
    Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT, 0)

/**
 * Countdown timer. Uses SystemClock.elapsedRealtime() end timestamps (immune to wall-clock changes),
 * persists its state, and schedules an exact alarm so it completes with the screen off.
 */
class TimerEngine(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("island_runtime", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(restore())
    val state: StateFlow<TimerData> = _state.asStateFlow()

    fun start(durationMs: Long) {
        if (durationMs <= 0) return
        val end = SystemClock.elapsedRealtime() + durationMs
        commit(TimerData(ClockPhase.RUNNING, durationMs, end, 0))
        scheduleAlarm(end)
    }

    fun pause() {
        val s = _state.value
        if (s.phase != ClockPhase.RUNNING) return
        val remaining = s.remaining(SystemClock.elapsedRealtime())
        cancelAlarm()
        commit(s.copy(phase = ClockPhase.PAUSED, pausedRemainingMs = remaining))
    }

    fun resume() {
        val s = _state.value
        if (s.phase != ClockPhase.PAUSED) return
        val end = SystemClock.elapsedRealtime() + s.pausedRemainingMs
        commit(s.copy(phase = ClockPhase.RUNNING, endElapsed = end, pausedRemainingMs = 0))
        scheduleAlarm(end)
    }

    fun cancel() {
        cancelAlarm()
        commit(TimerData())
    }

    /** Idempotent. Called from the alarm receiver and from the overlay service. */
    fun checkFinished() {
        val s = _state.value
        if (s.phase == ClockPhase.RUNNING && SystemClock.elapsedRealtime() >= s.endElapsed) {
            commit(s.copy(phase = ClockPhase.FINISHED))
            vibrateDone()
        }
    }

    private fun commit(d: TimerData) {
        _state.value = d
        prefs.edit()
            .putInt("t_phase", d.phase.ordinal)
            .putLong("t_total", d.totalMs)
            .putLong("t_end", d.endElapsed)
            .putLong("t_paused", d.pausedRemainingMs)
            .putInt("t_boot", bootCount(ctx))
            .apply()
    }

    private fun restore(): TimerData {
        val phase = ClockPhase.entries.getOrElse(prefs.getInt("t_phase", 0)) { ClockPhase.IDLE }
        if (phase == ClockPhase.IDLE) return TimerData()
        // elapsedRealtime restarts at boot, so a saved timestamp from before a reboot is meaningless.
        if (prefs.getInt("t_boot", -1) != bootCount(ctx)) return TimerData()
        val d = TimerData(phase, prefs.getLong("t_total", 0), prefs.getLong("t_end", 0), prefs.getLong("t_paused", 0))
        return if (d.phase == ClockPhase.RUNNING && d.endElapsed <= SystemClock.elapsedRealtime())
            d.copy(phase = ClockPhase.FINISHED) else d
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        ctx, 7,
        Intent(ctx, TimerAlarmReceiver::class.java).setAction(ACTION_TIMER_DONE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun scheduleAlarm(endElapsed: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endElapsed, alarmIntent())
        else am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endElapsed, alarmIntent())
    }

    private fun cancelAlarm() {
        ctx.getSystemService(AlarmManager::class.java).cancel(alarmIntent())
    }

    private fun vibrateDone() {
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31)
            ctx.getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") (ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 280, 140, 280, 140, 280), -1))
        Log.d("TimerEngine", "Timer finished")
    }

    companion object {
        const val ACTION_TIMER_DONE = "com.dynamicisland.action.TIMER_DONE"
    }
}

class StopwatchEngine(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("island_runtime", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(restore())
    val state: StateFlow<StopwatchData> = _state.asStateFlow()

    fun start() {
        if (_state.value.phase != ClockPhase.IDLE) return
        commit(StopwatchData(ClockPhase.RUNNING, SystemClock.elapsedRealtime(), 0))
    }

    fun pause() {
        val s = _state.value
        if (s.phase != ClockPhase.RUNNING) return
        commit(StopwatchData(ClockPhase.PAUSED, 0, s.elapsed(SystemClock.elapsedRealtime())))
    }

    fun resume() {
        val s = _state.value
        if (s.phase != ClockPhase.PAUSED) return
        commit(StopwatchData(ClockPhase.RUNNING, SystemClock.elapsedRealtime(), s.accumulatedMs))
    }

    fun reset() = commit(StopwatchData())

    private fun commit(d: StopwatchData) {
        _state.value = d
        prefs.edit()
            .putInt("s_phase", d.phase.ordinal)
            .putLong("s_start", d.runStartElapsed)
            .putLong("s_acc", d.accumulatedMs)
            .putInt("s_boot", bootCount(ctx))
            .apply()
    }

    private fun restore(): StopwatchData {
        val phase = ClockPhase.entries.getOrElse(prefs.getInt("s_phase", 0)) { ClockPhase.IDLE }
        if (phase != ClockPhase.RUNNING && phase != ClockPhase.PAUSED) return StopwatchData()
        if (prefs.getInt("s_boot", -1) != bootCount(ctx)) return StopwatchData()
        return StopwatchData(phase, prefs.getLong("s_start", 0), prefs.getLong("s_acc", 0))
    }
}
