package com.dynamicisland.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Single source of truth for what the island shows. Merges all activity sources into one ordered list,
 * decides which one is current, and owns expand/collapse/dismiss logic. Runs on the main thread only.
 */
class IslandController(
    private val scope: CoroutineScope,
    private val settings: SettingsRepository,
    private val timer: TimerEngine,
    private val stopwatch: StopwatchEngine,
    private val battery: BatteryTracker,
    private val media: MediaTracker,
    private val calls: CallTracker,
) : IslandActions {

    var onFullyHidden: (() -> Unit)? = null

    private val _state = MutableStateFlow(IslandUiState())
    val state: StateFlow<IslandUiState> = _state.asStateFlow()

    private var screenOn = true
    var landscapeSuppressed = false
        private set
    private var immersiveSuppressed = false

    private var selected: Kind? = null
    private var expanded = false
    private val dismissed = mutableSetOf<Kind>()
    private var lastActivities: List<IslandActivity> = emptyList()
    private var autoCollapse: Job? = null

    private var timerSeen = false
    private var prevTimerPhase = ClockPhase.IDLE
    private var batterySeen = false
    private var prevBattery: BatteryData? = null
    private var prevMusic: MusicData? = null
    private var prevCall: CallData? = null

    fun start() {
        scope.launch { settings.flow.collect { recompute() } }
        scope.launch { timer.state.collect { onTimer(it) } }
        scope.launch { stopwatch.state.collect { recompute() } }
        scope.launch { battery.state.collect { onBattery(it) } }
        scope.launch { media.state.collect { onMusic(it) } }
        scope.launch { calls.state.collect { onCall(it) } }
    }

    fun setScreenOn(on: Boolean) {
        screenOn = on
        if (!on) { expanded = false; autoCollapse?.cancel() }
        recompute()
    }

    fun setLandscapeSuppressed(v: Boolean) { landscapeSuppressed = v; recompute() }
    fun setImmersiveSuppressed(v: Boolean) { if (immersiveSuppressed != v) { immersiveSuppressed = v; recompute() } }

    // ---- source events ---------------------------------------------------------------------

    private fun onTimer(t: TimerData) {
        val was = prevTimerPhase
        prevTimerPhase = t.phase
        if (timerSeen && t.phase == ClockPhase.FINISHED && was != ClockPhase.FINISHED) {
            selected = Kind.TIMER
            expanded = true // a finished timer always demands attention
            autoCollapse?.cancel()
        }
        timerSeen = true
        recompute()
    }

    private fun onBattery(b: BatteryData?) {
        val was = prevBattery
        prevBattery = b
        if (b == null) dismissed.remove(Kind.CHARGING)
        val connected = b != null && was == null
        val becameFull = b != null && was != null && was.status != ChargeStatus.FULL && b.status == ChargeStatus.FULL
        if (batterySeen && (connected || becameFull)) {
            dismissed.remove(Kind.CHARGING)
            if (!timerFinishedShowing()) announce(Kind.CHARGING)
        }
        batterySeen = true
        recompute()
    }

    private fun onMusic(m: MusicData?) {
        val was = prevMusic
        prevMusic = m
        if (m == null) dismissed.remove(Kind.MUSIC)
        else if (was == null || was.title != m.title || was.artist != m.artist || (!was.playing && m.playing)) {
            dismissed.remove(Kind.MUSIC)
        }
        recompute()
    }

    private fun onCall(c: CallData?) {
        val was = prevCall
        prevCall = c
        if (c != null && was == null) {
            selected = Kind.CALL
            // Native dialer UI already owns ringing; do not place a competing island over it.
            // Active calls appear compactly after the native call has been answered.
            expanded = false
            autoCollapse?.cancel()
        } else if (c == null && was != null) {
            if (selected == Kind.CALL) selected = null
            expanded = false
            autoCollapse?.cancel()
        } else if (c != null && was != null && was.phase == CallPhase.RINGING && c.phase == CallPhase.ACTIVE) {
            expanded = false // answered: settle into the compact timer
        }
        recompute()
    }

    private fun timerFinishedShowing(): Boolean {
        val cur = _state.value.current
        return cur is TimerActivity && cur.data.phase == ClockPhase.FINISHED
    }

    private fun announce(kind: Kind) {
        selected = kind
        if (settings.value.autoExpand) {
            expanded = true
            autoCollapse?.cancel()
            autoCollapse = scope.launch {
                delay(AUTO_COLLAPSE_MS)
                expanded = false
                recompute()
            }
        }
    }

    // ---- state building --------------------------------------------------------------------

    private fun recompute() {
        val s = settings.value
        val t = timer.state.value
        val sw = stopwatch.state.value
        val m = media.state.value
        val b = battery.state.value
        val call = calls.state.value

        val list = buildList<IslandActivity> {
            if (s.calls && call?.phase == CallPhase.ACTIVE) add(CallActivity(call))
            if (s.timer && t.phase != ClockPhase.IDLE) add(TimerActivity(t))
            if (s.stopwatch && sw.phase != ClockPhase.IDLE) add(StopwatchActivity(sw))
            if (s.music && m != null && Kind.MUSIC !in dismissed) add(MusicActivity(m))
            if (s.charging && b != null && Kind.CHARGING !in dismissed) add(ChargingActivity(b))
        }
        val hasContent = list.isNotEmpty()
        if (hasContent) lastActivities = list
        else { expanded = false; autoCollapse?.cancel() }

        if (hasContent && list.none { it.kind == selected }) selected = null
        val suppressed = landscapeSuppressed || immersiveSuppressed
        if (suppressed) { expanded = false; autoCollapse?.cancel() }

        val shown = if (hasContent) list else lastActivities
        val index = shown.indexOfFirst { it.kind == selected }.takeIf { it >= 0 } ?: 0
        val visible = hasContent && !suppressed
        _state.value = IslandUiState(
            activities = shown,
            currentIndex = index,
            expanded = expanded && visible,
            visible = visible,
            active = visible && screenOn,
            hasContent = hasContent,
        )
    }

    // ---- user actions ----------------------------------------------------------------------

    private fun userAction() { autoCollapse?.cancel() }

    override fun expand() { userAction(); if (!expanded && _state.value.visible) { expanded = true; recompute() } }
    override fun collapse() { userAction(); if (expanded) { expanded = false; recompute() } }
    override fun next() = cycle(+1)
    override fun previous() = cycle(-1)

    private fun cycle(delta: Int) {
        userAction()
        val list = _state.value.activities
        if (list.size < 2) return
        selected = list[(_state.value.currentIndex + delta + list.size) % list.size].kind
        recompute()
    }

    override fun dismissCurrent() {
        val k = _state.value.current?.kind ?: return
        if (k != Kind.MUSIC && k != Kind.CHARGING) return // timers/stopwatches are user-controlled
        dismissed += k
        if (selected == k) selected = null
        expanded = false
        recompute()
    }

    override fun musicPlayPause() = media.playPause()
    override fun musicNext() = media.next()
    override fun musicPrevious() = media.previous()

    override fun timerPauseResume() {
        when (timer.state.value.phase) {
            ClockPhase.RUNNING -> timer.pause()
            ClockPhase.PAUSED -> timer.resume()
            ClockPhase.FINISHED -> timer.cancel()
            ClockPhase.IDLE -> Unit
        }
    }

    override fun timerCancel() = timer.cancel()

    override fun stopwatchPauseResume() {
        when (stopwatch.state.value.phase) {
            ClockPhase.RUNNING -> stopwatch.pause()
            ClockPhase.PAUSED -> stopwatch.resume()
            else -> Unit
        }
    }

    override fun stopwatchReset() = stopwatch.reset()

    /** UI finished its exit animation. Drop stale content once nothing is left to show. */
    override fun onExitFinished() {
        if (_state.value.hasContent) return
        lastActivities = emptyList()
        recompute()
        onFullyHidden?.invoke()
    }

    private companion object {
        const val AUTO_COLLAPSE_MS = 3000L
    }
}
