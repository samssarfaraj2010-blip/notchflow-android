package com.dynamicisland.core

import android.graphics.Bitmap

/** Order of declaration = priority order (first is most important). */
enum class Kind { CALL, TIMER, STOPWATCH, MUSIC, CHARGING }

enum class CallPhase { RINGING, ACTIVE }

data class CallData(val phase: CallPhase, val startElapsed: Long)

enum class ClockPhase { IDLE, RUNNING, PAUSED, FINISHED }
enum class ChargeStatus { CHARGING, FULL, PAUSED }
enum class ChargeSource { WIRED, WIRELESS }

data class TimerData(
    val phase: ClockPhase = ClockPhase.IDLE,
    val totalMs: Long = 0,
    val endElapsed: Long = 0,
    val pausedRemainingMs: Long = 0,
) {
    fun remaining(now: Long): Long = when (phase) {
        ClockPhase.RUNNING -> (endElapsed - now).coerceAtLeast(0)
        ClockPhase.PAUSED -> pausedRemainingMs
        else -> 0
    }
}

data class StopwatchData(
    val phase: ClockPhase = ClockPhase.IDLE,
    val runStartElapsed: Long = 0,
    val accumulatedMs: Long = 0,
) {
    fun elapsed(now: Long): Long =
        accumulatedMs + if (phase == ClockPhase.RUNNING) (now - runStartElapsed).coerceAtLeast(0) else 0
}

data class BatteryData(
    val percent: Int,
    val status: ChargeStatus,
    val source: ChargeSource,
    val tempC: Float?,
)

data class MusicData(
    val packageName: String,
    val appName: String,
    val appIcon: Bitmap?,
    val title: String,
    val artist: String,
    val artwork: Bitmap?,
    val playing: Boolean,
    val basePositionMs: Long,
    val baseElapsed: Long,
    val speed: Float,
    val durationMs: Long,
    val canPrev: Boolean,
    val canNext: Boolean,
    val canToggle: Boolean,
) {
    fun positionAt(now: Long): Long {
        val raw = if (playing) basePositionMs + ((now - baseElapsed) * speed).toLong() else basePositionMs
        return if (durationMs > 0) raw.coerceIn(0, durationMs) else raw.coerceAtLeast(0)
    }
}

sealed interface IslandActivity { val kind: Kind }
data class CallActivity(val data: CallData) : IslandActivity { override val kind: Kind get() = Kind.CALL }
data class TimerActivity(val data: TimerData) : IslandActivity { override val kind: Kind get() = Kind.TIMER }
data class StopwatchActivity(val data: StopwatchData) : IslandActivity { override val kind: Kind get() = Kind.STOPWATCH }
data class MusicActivity(val data: MusicData) : IslandActivity { override val kind: Kind get() = Kind.MUSIC }
data class ChargingActivity(val data: BatteryData) : IslandActivity { override val kind: Kind get() = Kind.CHARGING }

data class IslandUiState(
    val activities: List<IslandActivity> = emptyList(),
    val currentIndex: Int = 0,
    val expanded: Boolean = false,
    /** Pill should currently be on screen. */
    val visible: Boolean = false,
    /** Visible AND screen on: tickers and animations may run. */
    val active: Boolean = false,
    /** There is at least one real activity (false only while the exit animation finishes). */
    val hasContent: Boolean = false,
) {
    val current: IslandActivity? get() = activities.getOrNull(currentIndex)
}

/** Window geometry, in dp, derived from the real display. */
data class IslandGeometry(
    val cutoutWidthDp: Float = 18f,
    val compactWidthDp: Float = 134f,
    val compactHeightDp: Float = 34f,
    val expandedWidthDp: Float = 360f,
    /** Top of the compact pill in screen coordinates. */
    val yPx: Int = 0,
)

interface IslandActions {
    fun expand()
    fun collapse()
    fun next()
    fun previous()
    fun dismissCurrent()
    fun musicPlayPause()
    fun musicNext()
    fun musicPrevious()
    fun timerPauseResume()
    fun timerCancel()
    fun stopwatchPauseResume()
    fun stopwatchReset()
    fun onExitFinished()
}
