package com.dynamicisland.ui.island

import android.os.SystemClock
import android.content.Intent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dynamicisland.core.BatteryData
import com.dynamicisland.core.CallActivity
import com.dynamicisland.core.CallData
import com.dynamicisland.core.CallPhase
import com.dynamicisland.core.ChargeSource
import com.dynamicisland.core.ChargeStatus
import com.dynamicisland.core.ChargingActivity
import com.dynamicisland.core.ClockPhase
import com.dynamicisland.core.IslandActions
import com.dynamicisland.core.IslandActivity
import com.dynamicisland.core.IslandSettings
import com.dynamicisland.core.MusicActivity
import com.dynamicisland.core.NotificationActivity
import com.dynamicisland.core.MusicData
import com.dynamicisland.core.StopwatchActivity
import com.dynamicisland.core.StopwatchData
import com.dynamicisland.core.TimerActivity
import com.dynamicisland.core.TimerData
import com.dynamicisland.ui.components.Glyph
import com.dynamicisland.ui.components.ProgressRing
import com.dynamicisland.ui.components.T
import com.dynamicisland.ui.components.pressable
import com.dynamicisland.ui.theme.Palette
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** Wall-clock ticker that only runs while [running] is true (visible, screen on, something counting). */
@Composable
fun rememberNow(running: Boolean, intervalMs: Long): State<Long> {
    val now = remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(running, intervalMs) {
        now.longValue = SystemClock.elapsedRealtime()
        while (running) {
            delay(intervalMs)
            now.longValue = SystemClock.elapsedRealtime()
        }
    }
    return now
}

/** Gap between the expanded island and each screen edge. The expanded pill is "fillMaxWidth().padding(horizontal = 14.dp)". */
const val EXPANDED_SIDE_MARGIN_DP = 14f

/** Corner radius of the expanded pill (capped at half its height, so thin panels become full capsules). */
const val EXPANDED_CORNER_DP = 34f

/** Overlay window height while expanded: tallest panel plus room for the spring overshoot. */
const val EXPANDED_WINDOW_HEIGHT_DP = 108f

/** Slim, iOS-style panel heights. Every panel is as wide as the screen allows, so they stay thin. */
fun expandedHeightDp(a: IslandActivity): Float = when (a) {
    is MusicActivity -> 96f
    is ChargingActivity -> 62f
    is NotificationActivity -> 78f
    is CallActivity -> 64f
    is TimerActivity -> 72f
    is StopwatchActivity -> 72f
}

private val EndCallRed = Color(0xFFFF3B30)

private fun timerFraction(d: TimerData, now: Long): Float =
    if (d.totalMs <= 0) 0f else (d.remaining(now).toFloat() / d.totalMs).coerceIn(0f, 1f)

// ============================================================================================
// Compact state
// ============================================================================================

@Composable
fun CompactContent(activity: IslandActivity, cutoutWidth: Dp, active: Boolean, settings: IslandSettings) {
    if (activity is CallActivity) {
        CompactCall(activity.data, active)
        return
    }
    Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { CompactLeading(activity, active) }
        Spacer(Modifier.width(cutoutWidth))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { CompactTrailing(activity, active, settings) }
    }
}

/** Width/height of the call glyph slot. The invisible spacer on the right uses the exact same size. */
private val CompactCallSlot = 20.dp

/**
 * Perfectly symmetric compact call pill:  [ green call icon ] [ timer, dead centre ] [ spacer, same size as the icon ].
 * Left and right slots are identical, so the weighted centre cell is centred on the pill (and on the camera).
 */
@Composable
private fun CompactCall(d: CallData, active: Boolean) {
    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(CompactCallSlot), contentAlignment = Alignment.Center) { Glyph(Icons.Rounded.Call, Palette.Green, 17.dp) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (d.phase == CallPhase.ACTIVE) {
                val now = rememberNow(active, 1000)
                T(formatClock((now.value - d.startElapsed).coerceAtLeast(0)), 13, Palette.Green, FontWeight.SemiBold, maxLines = 1, tnum = true)
            }
        }
        Spacer(Modifier.size(CompactCallSlot))
    }
}

@Composable
private fun CompactLeading(a: IslandActivity, active: Boolean) {
    when (a) {
        is MusicActivity -> Artwork(a.data, 24.dp, 7.dp)
        is ChargingActivity -> Glyph(Icons.Rounded.Bolt, Palette.Green, 18.dp)
        is NotificationActivity -> Glyph(Icons.Rounded.Notifications, Color.White, 18.dp)
        is CallActivity -> Glyph(Icons.Rounded.Call, Palette.Green, 17.dp)
        is StopwatchActivity -> Glyph(Icons.Rounded.Timer, Palette.Yellow, 18.dp)
        is TimerActivity -> {
            val now = rememberNow(active && a.data.phase == ClockPhase.RUNNING, 250)
            ProgressRing({ timerFraction(a.data, now.value) }, 20.dp, 2.5.dp, Palette.Orange)
        }
    }
}

@Composable
private fun CompactTrailing(a: IslandActivity, active: Boolean, settings: IslandSettings) {
    when (a) {
        is MusicActivity -> Visualizer(
            playing = a.data.playing,
            animate = active && settings.visualizer && settings.animationStyle != 3,
            color = Palette.Pink,
            modifier = Modifier.size(18.dp, 16.dp),
        )
        is ChargingActivity -> T("${a.data.percent}%", 13, Palette.Green, FontWeight.SemiBold, maxLines = 1, tnum = true)
        is NotificationActivity -> T(a.data.appName, 11, Color.White, FontWeight.Medium, maxLines = 1)
        is CallActivity -> Unit // drawn by CompactCall
        is TimerActivity -> {
            val now = rememberNow(active && a.data.phase == ClockPhase.RUNNING, 250)
            val text = if (a.data.phase == ClockPhase.FINISHED) "Done" else formatCompactTimer(a.data.remaining(now.value))
            T(text, 13, Palette.Orange, FontWeight.SemiBold, maxLines = 1, tnum = true)
        }
        is StopwatchActivity -> {
            val now = rememberNow(active && a.data.phase == ClockPhase.RUNNING, 500)
            T(formatClock(a.data.elapsed(now.value)), 13, Palette.Yellow, FontWeight.SemiBold, maxLines = 1, tnum = true)
        }
    }
}

// ============================================================================================
// Expanded state
// ============================================================================================

@Composable
fun ExpandedContent(
    a: IslandActivity,
    active: Boolean,
    settings: IslandSettings,
    actions: IslandActions,
    interactive: Boolean,
    /** Height of the compact pill: the band at the top of the panel that is level with the camera. */
    cameraBand: Dp,
) {
    when (a) {
        is MusicActivity -> MusicExpanded(a.data, active, settings, actions, interactive, cameraBand)
        is ChargingActivity -> ChargingExpanded(a.data, active)
        is NotificationActivity -> NotificationExpanded(a.data)
        is CallActivity -> CallExpanded(a.data, active, actions, interactive)
        is TimerActivity -> TimerExpanded(a.data, active, actions, interactive)
        is StopwatchActivity -> StopwatchExpanded(a.data, active, actions, interactive)
    }
}

/**
 * Slim music panel. Top band (level with the camera): artwork left, visualizer right, centre kept clear.
 * Below it: title/artist on the left, transport controls on the right, and a thin progress line at the bottom.
 */
@Composable
private fun MusicExpanded(m: MusicData, active: Boolean, settings: IslandSettings, actions: IslandActions, interactive: Boolean, band: Dp) {
    Column(Modifier.fillMaxSize().padding(start = 22.dp, end = 22.dp, bottom = 9.dp)) {
        Row(Modifier.fillMaxWidth().height(band), verticalAlignment = Alignment.CenterVertically) {
            Artwork(m, 24.dp, 7.dp)
            Spacer(Modifier.weight(1f))
            Visualizer(m.playing, active && settings.visualizer && settings.animationStyle != 3, Palette.Pink, Modifier.size(20.dp, 16.dp))
        }
        Row(Modifier.fillMaxWidth().height(38.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                T(m.title, 15, Color.White, FontWeight.SemiBold, maxLines = 1)
                T(m.artist.ifBlank { m.appName }, 12, Color(0x99FFFFFF), maxLines = 1)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                ControlButton(Icons.Rounded.SkipPrevious, 24.dp, m.canPrev && interactive) { actions.musicPrevious() }
                Box(
                    Modifier.size(38.dp).pressable(m.canToggle && interactive) { actions.musicPlayPause() }.clip(CircleShape).background(Color.White),
                    contentAlignment = Alignment.Center,
                ) { Glyph(if (m.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, Color.Black, 22.dp, description = if (m.playing) "Pause" else "Play") }
                ControlButton(Icons.Rounded.SkipNext, 24.dp, m.canNext && interactive) { actions.musicNext() }
            }
        }
        Spacer(Modifier.weight(1f))
        MusicProgress(m, active)
    }
}

@Composable
private fun ControlButton(icon: ImageVector, size: Dp, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).alpha(if (enabled) 1f else 0.3f).pressable(enabled, onClick), contentAlignment = Alignment.Center) {
        Glyph(icon, Color.White, size)
    }
}

/** Thin progress line with the elapsed / remaining time on either side. */
@Composable
private fun MusicProgress(m: MusicData, active: Boolean) {
    val now = rememberNow(active && m.playing, 500)
    val pos = m.positionAt(now.value)
    val fraction = if (m.durationMs > 0) (pos / m.durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    Row(Modifier.fillMaxWidth().height(12.dp), verticalAlignment = Alignment.CenterVertically) {
        T(formatClock(pos), 10, Color(0x99FFFFFF), tnum = true, maxLines = 1)
        Box(Modifier.weight(1f).padding(horizontal = 8.dp).height(3.dp).clip(CircleShape).background(Color(0x33FFFFFF))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(Color.White))
        }
        T(if (m.durationMs > 0) "-" + formatClock(m.durationMs - pos) else "--:--", 10, Color(0x99FFFFFF), tnum = true, maxLines = 1)
    }
}

@Composable
private fun ChargingExpanded(b: BatteryData, active: Boolean) {
    val pulse: State<Float>? = if (b.status == ChargeStatus.CHARGING && active)
        rememberInfiniteTransition(label = "chargePulse").animateFloat(
            0.55f, 1f, infiniteRepeatable(tween(1250, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "chargeAlpha",
        ) else null
    val label = when (b.status) {
        ChargeStatus.CHARGING -> if (b.source == ChargeSource.WIRELESS) "Charging wirelessly" else "Charging"
        ChargeStatus.FULL -> "Fully charged"
        ChargeStatus.PAUSED -> "Plugged in · not charging"
    }
    val accent = if (b.status == ChargeStatus.PAUSED) Color.White else Palette.Green
    Row(Modifier.fillMaxSize().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
        Glyph(Icons.Rounded.Bolt, Palette.Green, 22.dp, Modifier.graphicsLayer { alpha = pulse?.value ?: 1f })
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            T(label, 11, Color(0x99FFFFFF), maxLines = 1)
            Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).clip(CircleShape).background(Color(0x33FFFFFF))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth((b.percent / 100f).coerceIn(0f, 1f)).clip(CircleShape).background(accent))
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            T("${b.percent}%", 22, accent, FontWeight.SemiBold, tnum = true, maxLines = 1)
            b.tempC?.let { T(String.format(Locale.ROOT, "%.1f °C", it), 10, Color(0x80FFFFFF), maxLines = 1) }
        }
    }
}

@Composable
private fun NotificationExpanded(data: com.dynamicisland.core.NotificationData) {
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 11.dp), verticalArrangement = Arrangement.Center) {
        T(data.appName, 11, Color(0x99FFFFFF), FontWeight.Medium, maxLines = 1, modifier = Modifier.widthIn(max = 130.dp))
        T(data.title, 15, Color.White, FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
        if (data.text.isNotBlank()) T(data.text, 12, Color(0xCCFFFFFF), maxLines = 1, modifier = Modifier.padding(top = 1.dp))
    }
}

/**
 * Slim call panel:  [ call badge · "Ongoing call" / timer ]  ......  [ red End call button ].
 * Everything sits left or right of the camera; the centre of the pill is intentionally empty.
 */
@Composable
private fun CallExpanded(d: CallData, active: Boolean, actions: IslandActions, interactive: Boolean) {
    val context = LocalContext.current
    val now = rememberNow(active, 1000)
    Row(Modifier.fillMaxSize().padding(start = 20.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).pressable(interactive) {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_PHONE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF12301A)), contentAlignment = Alignment.Center) {
                Glyph(Icons.Rounded.Call, Palette.Green, 19.dp)
            }
            Column(Modifier.padding(start = 10.dp)) {
                T("Ongoing call", 11, Color(0x99FFFFFF), maxLines = 1)
                T(formatClock((now.value - d.startElapsed).coerceAtLeast(0)), 20, Color.White, FontWeight.SemiBold, tnum = true, maxLines = 1)
            }
        }
        IslandIconButton(Icons.Rounded.CallEnd, "End call", EndCallRed, Color.White, interactive, size = 44.dp) { actions.endCall() }
    }
}

/** Round icon button used by the slim panels (the island has no Material dependency, so this is the "IconButton"). */
@Composable
private fun IslandIconButton(
    icon: ImageVector,
    description: String,
    bg: Color,
    tint: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier.size(size).alpha(if (enabled) 1f else 0.4f).pressable(enabled, onClick).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) { Glyph(icon, tint, size * 0.5f, description = description) }
}

@Composable
private fun TimerExpanded(d: TimerData, active: Boolean, actions: IslandActions, interactive: Boolean) {
    val now = rememberNow(active && d.phase == ClockPhase.RUNNING, 250)
    val label = when (d.phase) {
        ClockPhase.RUNNING -> "Timer"
        ClockPhase.PAUSED -> "Timer paused"
        ClockPhase.FINISHED -> "Time's up"
        ClockPhase.IDLE -> ""
    }
    Row(Modifier.fillMaxSize().padding(start = 20.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        ProgressRing({ timerFraction(d, now.value) }, 44.dp, 4.dp, Palette.Orange) {
            Glyph(Icons.Rounded.HourglassTop, Palette.Orange, 18.dp)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            T(label, 11, Palette.Orange, FontWeight.Medium, maxLines = 1)
            T(formatClock(ceilSecond(d.remaining(now.value))), 24, Color.White, FontWeight.SemiBold, tnum = true, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (d.phase != ClockPhase.FINISHED) {
                IslandIconButton(Icons.Rounded.Close, "Cancel timer", Color(0xFF2A2A2E), Color.White, interactive) { actions.timerCancel() }
            }
            val (icon, desc) = when (d.phase) {
                ClockPhase.RUNNING -> Icons.Rounded.Pause to "Pause timer"
                ClockPhase.PAUSED -> Icons.Rounded.PlayArrow to "Resume timer"
                else -> Icons.Rounded.Check to "Done"
            }
            IslandIconButton(icon, desc, Palette.Orange, Color.Black, interactive) { actions.timerPauseResume() }
        }
    }
}

@Composable
private fun StopwatchExpanded(d: StopwatchData, active: Boolean, actions: IslandActions, interactive: Boolean) {
    val now = rememberNow(active && d.phase == ClockPhase.RUNNING, 100)
    Row(Modifier.fillMaxSize().padding(start = 22.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            T(if (d.phase == ClockPhase.RUNNING) "Stopwatch · running" else "Stopwatch · paused", 11, Palette.Yellow, FontWeight.Medium, maxLines = 1)
            T(formatClock(d.elapsed(now.value), tenths = true), 24, Color.White, FontWeight.SemiBold, tnum = true, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IslandIconButton(Icons.Rounded.Replay, "Reset stopwatch", Color(0xFF2A2A2E), Color.White, interactive) { actions.stopwatchReset() }
            IslandIconButton(
                if (d.phase == ClockPhase.RUNNING) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (d.phase == ClockPhase.RUNNING) "Pause stopwatch" else "Resume stopwatch",
                Palette.Yellow, Color.Black, interactive,
            ) { actions.stopwatchPauseResume() }
        }
    }
}

// ============================================================================================
// Shared bits
// ============================================================================================

@Composable
fun Artwork(m: MusicData, size: Dp, radius: Dp) {
    val bitmap = m.artwork ?: m.appIcon
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    Box(Modifier.size(size).clip(RoundedCornerShape(radius)).background(Color(0xFF1E1E22)), contentAlignment = Alignment.Center) {
        if (image != null) Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Glyph(Icons.Rounded.MusicNote, Palette.TextDim, size * 0.55f)
    }
}

/** Four bars. Only animates while playing, on screen and allowed; otherwise a static, zero-cost drawing. */
@Composable
fun Visualizer(playing: Boolean, animate: Boolean, color: Color, modifier: Modifier = Modifier) {
    val phase: State<Float>? = if (playing && animate)
        rememberInfiniteTransition(label = "viz").animateFloat(
            0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "vizPhase",
        ) else null
    Canvas(modifier) {
        val bars = 4
        val gap = size.width * 0.14f
        val barWidth = (size.width - gap * (bars - 1)) / bars
        for (i in 0 until bars) {
            val level = if (phase != null) 0.3f + 0.7f * (0.5f + 0.5f * sin(2f * PI.toFloat() * (phase.value + i * 0.23f))) else 0.3f
            val h = size.height * level
            drawRoundRect(color, Offset(i * (barWidth + gap), (size.height - h) / 2f), Size(barWidth, h), CornerRadius(barWidth / 2f))
        }
    }
}
