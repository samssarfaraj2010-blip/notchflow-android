package com.dynamicisland.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dynamicisland.Perms
import com.dynamicisland.core.CallPhase
import com.dynamicisland.core.ChargeStatus
import com.dynamicisland.core.ClockPhase
import com.dynamicisland.core.IslandRuntime
import com.dynamicisland.core.IslandSettings
import com.dynamicisland.ui.components.ActionButton
import com.dynamicisland.ui.components.AppCard
import com.dynamicisland.ui.components.BtnStyle
import com.dynamicisland.ui.components.Chip
import com.dynamicisland.ui.components.Glyph
import com.dynamicisland.ui.components.Hint
import com.dynamicisland.ui.components.IslandSwitch
import com.dynamicisland.ui.components.Stepper
import com.dynamicisland.ui.components.T
import com.dynamicisland.ui.island.Artwork
import com.dynamicisland.ui.island.ceilSecond
import com.dynamicisland.ui.island.formatClock
import com.dynamicisland.ui.island.rememberNow
import com.dynamicisland.ui.theme.Palette

@Composable
fun FeaturesScreen(settings: IslandSettings, perms: Perms, onMedia: () -> Unit, onPhone: () -> Unit) {
    val islandOn = settings.islandEnabled && perms.overlay
    ScreenColumn {
        T("Features", 28, weight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp, top = 8.dp))
        T("Each one shares the same island. Turn off what you don't need.", 14, Palette.TextDim, modifier = Modifier.padding(start = 6.dp, bottom = 4.dp))
        if (!islandOn) Hint("The island is off. Turn it on from Home to see live activities.")
        MusicCard(settings, perms, onMedia)
        CallsCard(settings, perms, onPhone)
        ChargingCard(settings)
        TimerCard(settings, islandOn)
        StopwatchCard(settings, islandOn)
    }
}

@Composable
private fun FeatureCard(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onEnabled: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Palette.SurfaceHi), contentAlignment = Alignment.Center) {
                Glyph(icon, tint, 24.dp)
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                T(title, 18, weight = FontWeight.SemiBold)
                T(subtitle, 13, Palette.TextDim, modifier = Modifier.padding(top = 2.dp))
            }
            IslandSwitch(enabled, onEnabled)
        }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun MusicCard(settings: IslandSettings, perms: Perms, onMedia: () -> Unit) {
    val music by IslandRuntime.media.state.collectAsStateWithLifecycle()
    val connected by IslandRuntime.listenerConnected.collectAsStateWithLifecycle()
    FeatureCard(Icons.Rounded.MusicNote, Palette.Pink, "Music", "Artwork, track and controls from any media app",
        settings.music, { v -> IslandRuntime.settings.update { copy(music = v) } }) {
        val m = music
        when {
            !perms.mediaAccess -> {
                Hint("Notification access is used for media sessions and to preview notification title/text inside the island.")
                Spacer(Modifier.height(12.dp))
                ActionButton("Grant media access", style = BtnStyle.Secondary, onClick = onMedia)
            }
            m != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Artwork(m, 48.dp, 12.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    T(m.title, 15, weight = FontWeight.Medium, maxLines = 1)
                    T("${if (m.playing) "Playing" else "Paused"} · ${m.appName}", 13, Palette.TextDim, maxLines = 1)
                }
            }
            !connected -> T("Connecting to the system…", 13, Palette.TextDim)
            else -> T("Nothing playing. Start music in Spotify, YouTube Music or any media app.", 13, Palette.TextDim)
        }
    }
}

@Composable
private fun ChargingCard(settings: IslandSettings) {
    val battery by IslandRuntime.battery.state.collectAsStateWithLifecycle()
    FeatureCard(Icons.Rounded.BatteryChargingFull, Palette.Green, "Charging", "Shows when a charger is connected",
        settings.charging, { v -> IslandRuntime.settings.update { copy(charging = v) } }) {
        val b = battery
        T(
            if (b == null) "Not charging, or the island is off." else when (b.status) {
                ChargeStatus.CHARGING -> "Charging · ${b.percent}%"
                ChargeStatus.FULL -> "Fully charged · ${b.percent}%"
                ChargeStatus.PAUSED -> "Plugged in, not charging · ${b.percent}%"
            },
            13, Palette.TextDim,
        )
    }
}

@Composable
private fun TimerCard(settings: IslandSettings, islandOn: Boolean) {
    val timer by IslandRuntime.timer.state.collectAsStateWithLifecycle()
    var minutes by remember { mutableIntStateOf(5) }
    FeatureCard(Icons.Rounded.HourglassTop, Palette.Orange, "Timer", "Accurate with the screen off",
        settings.timer, { v -> IslandRuntime.settings.update { copy(timer = v) } }) {
        if (timer.phase == ClockPhase.IDLE) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 5, 10, 15, 30).forEach { Chip("$it min", minutes == it) { minutes = it } }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                T("Duration", 15, Palette.TextDim, modifier = Modifier.weight(1f))
                Stepper(minutes, 1..180, { "$it min" }) { minutes = it }
            }
            Spacer(Modifier.height(14.dp))
            ActionButton("Start timer", enabled = islandOn && settings.timer) { IslandRuntime.timer.start(minutes * 60_000L) }
        } else {
            val now = rememberNow(timer.phase == ClockPhase.RUNNING, 250)
            val text = if (timer.phase == ClockPhase.FINISHED) "Time's up" else formatClock(ceilSecond(timer.remaining(now.value)))
            T(text, 40, weight = FontWeight.SemiBold, tnum = true)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton("Cancel", Modifier.weight(1f), BtnStyle.Secondary) { IslandRuntime.timer.cancel() }
                ActionButton(
                    when (timer.phase) { ClockPhase.RUNNING -> "Pause"; ClockPhase.PAUSED -> "Resume"; else -> "Done" },
                    Modifier.weight(1f),
                ) {
                    when (timer.phase) {
                        ClockPhase.RUNNING -> IslandRuntime.timer.pause()
                        ClockPhase.PAUSED -> IslandRuntime.timer.resume()
                        else -> IslandRuntime.timer.cancel()
                    }
                }
            }
        }
    }
}

@Composable
private fun StopwatchCard(settings: IslandSettings, islandOn: Boolean) {
    val sw by IslandRuntime.stopwatch.state.collectAsStateWithLifecycle()
    FeatureCard(Icons.Rounded.Timer, Palette.Yellow, "Stopwatch", "Keeps counting in the background",
        settings.stopwatch, { v -> IslandRuntime.settings.update { copy(stopwatch = v) } }) {
        if (sw.phase == ClockPhase.IDLE) {
            ActionButton("Start stopwatch", enabled = islandOn && settings.stopwatch) { IslandRuntime.stopwatch.start() }
        } else {
            val now = rememberNow(sw.phase == ClockPhase.RUNNING, 100)
            T(formatClock(sw.elapsed(now.value), tenths = true), 40, weight = FontWeight.SemiBold, tnum = true)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton("Reset", Modifier.weight(1f), BtnStyle.Secondary) { IslandRuntime.stopwatch.reset() }
                ActionButton(if (sw.phase == ClockPhase.RUNNING) "Pause" else "Resume", Modifier.weight(1f)) {
                    if (sw.phase == ClockPhase.RUNNING) IslandRuntime.stopwatch.pause() else IslandRuntime.stopwatch.resume()
                }
            }
        }
    }
}

@Composable
private fun CallsCard(settings: IslandSettings, perms: Perms, onPhone: () -> Unit) {
    val call by IslandRuntime.calls.state.collectAsStateWithLifecycle()
    FeatureCard(Icons.Rounded.Call, Palette.Green, "Calls", "Shows incoming and ongoing calls",
        settings.calls, { v -> IslandRuntime.settings.update { copy(calls = v) } }) {
        val c = call
        when {
            !perms.phone -> {
                Hint("Call status access tells NotchFlow when a call starts and ends. It never reads numbers, contacts or your call log, so caller names aren't shown.")
                Spacer(Modifier.height(12.dp))
                ActionButton("Allow call status", style = BtnStyle.Secondary, onClick = onPhone)
            }
            c != null -> T(if (c.phase == CallPhase.RINGING) "Incoming call" else "On a call", 13, Palette.TextDim)
            else -> T("No call right now.", 13, Palette.TextDim)
        }
    }
}
