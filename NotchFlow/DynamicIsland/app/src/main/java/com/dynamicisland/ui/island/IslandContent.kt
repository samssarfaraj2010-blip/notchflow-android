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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.MusicNote
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

fun expandedHeightDp(a: IslandActivity): Float = when (a) {
    is MusicActivity -> 204f
    is ChargingActivity -> 84f
    is CallActivity -> 146f
    is TimerActivity -> 166f
    is StopwatchActivity -> 166f
}

/** Smaller panels (charging, calls) are narrower than the music panel; [max] is the widest the screen allows. */
fun expandedWidthDp(a: IslandActivity, max: Float): Float = when (a) {
    is ChargingActivity -> min(max, 340f)
    is CallActivity -> min(max, 316f)
    else -> max
}

private fun timerFraction(d: TimerData, now: Long): Float =
    if (d.totalMs <= 0) 0f else (d.remaining(now).toFloat() / d.totalMs).coerceIn(0f, 1f)

// ============================================================================================
// Compact state
// ============================================================================================

@Composable
fun CompactContent(activity: IslandActivity, cutoutWidth: Dp, active: Boolean, settings: IslandSettings) {
    Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { CompactLeading(activity, active) }
        Spacer(Modifier.width(cutoutWidth))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { CompactTrailing(activity, active, settings) }
    }
}

@Composable
private fun CompactLeading(a: IslandActivity, active: Boolean) {
    when (a) {
        is MusicActivity -> Artwork(a.data, 24.dp, 7.dp)
        is ChargingActivity -> Glyph(Icons.Rounded.Bolt, Palette.Green, 18.dp)
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
        is CallActivity -> {
            if (a.data.phase == CallPhase.ACTIVE) {
                val now = rememberNow(active, 1000)
                T(formatClock((now.value - a.data.startElapsed).coerceAtLeast(0)), 13, Palette.Green, FontWeight.SemiBold, maxLines = 1, tnum = true)
            }
        }
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
fun ExpandedContent(a: IslandActivity, active: Boolean, settings: IslandSettings, actions: IslandActions, interactive: Boolean) {
    when (a) {
        is MusicActivity -> MusicExpanded(a.data, active, settings, actions, interactive)
        is ChargingActivity -> ChargingExpanded(a.data, active)
        is CallActivity -> CallExpanded(a.data, active)
        is TimerActivity -> TimerExpanded(a.data, active, actions, interactive)
        is StopwatchActivity -> StopwatchExpanded(a.data, active, actions, interactive)
    }
}

@Composable
private fun MusicExpanded(m: MusicData, active: Boolean, settings: IslandSettings, actions: IslandActions, interactive: Boolean) {
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(m, 64.dp, 14.dp)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                T(m.title, 17, Color.White, FontWeight.SemiBold, maxLines = 1)
                T(m.artist.ifBlank { m.appName }, 14, Color(0x99FFFFFF), maxLines = 1, modifier = Modifier.padding(top = 2.dp))
            }
            Visualizer(m.playing, active && settings.visualizer && settings.animationStyle != 3, Palette.Pink, Modifier.size(22.dp, 20.dp))
        }
        Spacer(Modifier.height(12.dp))
        MusicProgress(m, active)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(30.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            ControlButton(Icons.Rounded.SkipPrevious, 28.dp, m.canPrev && interactive) { actions.musicPrevious() }
            Box(
                Modifier.size(54.dp).pressable(m.canToggle && interactive) { actions.musicPlayPause() }.clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center,
            ) { Glyph(if (m.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, Color.Black, 30.dp, description = if (m.playing) "Pause" else "Play") }
            ControlButton(Icons.Rounded.SkipNext, 28.dp, m.canNext && interactive) { actions.musicNext() }
        }
    }
}

@Composable
private fun ControlButton(icon: androidx.compose.ui.graphics.vector.ImageVector, size: Dp, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).alpha(if (enabled) 1f else 0.3f).pressable(enabled, onClick), contentAlignment = Alignment.Center) {
        Glyph(icon, Color.White, size)
    }
}

@Composable
private fun MusicProgress(m: MusicData, active: Boolean) {
    val now = rememberNow(active && m.playing, 500)
    val pos = m.positionAt(now.value)
    val fraction = if (m.durationMs > 0) (pos / m.durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    Column {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color(0x33FFFFFF))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(Color.White))
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            T(formatClock(pos), 12, Color(0x99FFFFFF), tnum = true)
            T(if (m.durationMs > 0) "-" + formatClock(m.durationMs - pos) else "--:--", 12, Color(0x99FFFFFF), tnum = true)
        }
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
    val detail = buildList {
        add(if (b.source == ChargeSource.WIRELESS) "Wireless" else "Wired")
        b.tempC?.let { add(String.format(Locale.ROOT, "%.1f °C", it)) }
    }.joinToString("  ·  ")
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T(label, 12, Color(0x99FFFFFF), maxLines = 1)
                Row(verticalAlignment = Alignment.Bottom) {
                    T("${b.percent}%", 27, if (b.status == ChargeStatus.PAUSED) Color.White else Palette.Green, FontWeight.SemiBold, tnum = true, maxLines = 1)
                    T(detail, 11, Color(0x80FFFFFF), maxLines = 1, modifier = Modifier.padding(start = 10.dp, bottom = 4.dp))
                }
            }
            Glyph(Icons.Rounded.Bolt, Palette.Green, 24.dp, Modifier.graphicsLayer { alpha = pulse?.value ?: 1f })
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Color(0x33FFFFFF))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth((b.percent / 100f).coerceIn(0f, 1f)).clip(CircleShape)
                .background(if (b.status == ChargeStatus.PAUSED) Color.White else Palette.Green))
        }
    }
}

@Composable
private fun CallExpanded(d: CallData, active: Boolean) {
    val context = LocalContext.current
    val now = rememberNow(active, 1000)
    Row(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            T("Ongoing call", 13, Color(0x99FFFFFF), maxLines = 1)
            T(formatClock((now.value - d.startElapsed).coerceAtLeast(0)), 30, Color.White, FontWeight.SemiBold, tnum = true, maxLines = 1)
            T("Manage or end the call in Phone", 12, Color(0x80FFFFFF), maxLines = 1)
            Spacer(Modifier.height(7.dp))
            IslandButton("Open Phone", Color(0xFF263238), Color.White, true, Modifier.width(112.dp)) {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
        Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF12301A)), contentAlignment = Alignment.Center) {
            Glyph(Icons.Rounded.Call, Palette.Green, 23.dp)
        }
    }
}

@Composable
private fun IslandButton(text: String, bg: Color, fg: Color, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(46.dp).alpha(if (enabled) 1f else 0.4f).pressable(enabled, onClick).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) { T(text, 15, fg, FontWeight.SemiBold, maxLines = 1) }
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
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T(label, 14, Palette.Orange, FontWeight.Medium, maxLines = 1)
                T(formatClock(ceilSecond(d.remaining(now.value))), 40, Color.White, FontWeight.SemiBold, tnum = true, maxLines = 1)
            }
            ProgressRing({ timerFraction(d, now.value) }, 62.dp, 6.dp, Palette.Orange) {
                Glyph(Icons.Rounded.HourglassTop, Palette.Orange, 24.dp)
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (d.phase != ClockPhase.FINISHED) {
                IslandButton("Cancel", Color(0xFF2A2A2E), Color.White, interactive, Modifier.weight(1f)) { actions.timerCancel() }
            }
            val main = when (d.phase) { ClockPhase.RUNNING -> "Pause"; ClockPhase.PAUSED -> "Resume"; else -> "Done" }
            IslandButton(main, Palette.Orange, Color.Black, interactive, Modifier.weight(1f)) { actions.timerPauseResume() }
        }
    }
}

@Composable
private fun StopwatchExpanded(d: StopwatchData, active: Boolean, actions: IslandActions, interactive: Boolean) {
    val now = rememberNow(active && d.phase == ClockPhase.RUNNING, 100)
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp)) {
        T(if (d.phase == ClockPhase.RUNNING) "Stopwatch · running" else "Stopwatch · paused", 14, Palette.Yellow, FontWeight.Medium, maxLines = 1)
        T(formatClock(d.elapsed(now.value), tenths = true), 40, Color.White, FontWeight.SemiBold, tnum = true, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IslandButton("Reset", Color(0xFF2A2A2E), Color.White, interactive, Modifier.weight(1f)) { actions.stopwatchReset() }
            IslandButton(if (d.phase == ClockPhase.RUNNING) "Pause" else "Resume", Palette.Yellow, Color.Black, interactive, Modifier.weight(1f)) {
                actions.stopwatchPauseResume()
            }
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
