package com.dynamicisland.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.horizontalScroll as hScroll
import com.dynamicisland.core.BatteryData
import com.dynamicisland.core.ChargeSource
import com.dynamicisland.core.ChargeStatus
import com.dynamicisland.core.ChargingActivity
import com.dynamicisland.core.ClockPhase
import com.dynamicisland.core.IslandActions
import com.dynamicisland.core.IslandActivity
import com.dynamicisland.core.IslandGeometry
import com.dynamicisland.core.IslandRuntime
import com.dynamicisland.core.IslandUiState
import com.dynamicisland.core.MusicActivity
import com.dynamicisland.core.MusicData
import com.dynamicisland.core.StopwatchActivity
import com.dynamicisland.core.StopwatchData
import com.dynamicisland.core.TimerActivity
import com.dynamicisland.core.TimerData
import com.dynamicisland.ui.components.Chip
import com.dynamicisland.ui.components.Segmented
import com.dynamicisland.ui.components.T
import com.dynamicisland.ui.island.IslandPill
import com.dynamicisland.ui.theme.Palette

/**
 * Interactive preview. Uses the exact same IslandPill as the real overlay, fed with DEMO data
 * (clearly labelled), so what you see here is what the overlay renders.
 */
@Composable
fun IslandPreview(modifier: Modifier = Modifier, showControls: Boolean = true) {
    val settings by IslandRuntime.settings.flow.collectAsStateWithLifecycle()
    var selection by remember { mutableIntStateOf(4) } // 0 music, 1 charging, 2 timer, 3 stopwatch, 4 all
    var expanded by remember { mutableStateOf(false) }
    var index by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    val t0 = remember { SystemClock.elapsedRealtime() }

    val all: List<IslandActivity> = remember(playing) {
        listOf(
            TimerActivity(TimerData(ClockPhase.PAUSED, 300_000, 0, 192_000)),
            StopwatchActivity(StopwatchData(ClockPhase.PAUSED, 0, 83_400)),
            MusicActivity(
                MusicData(
                    "demo", "Demo Player", null, "Midnight Signal", "Demo Artist", null, playing,
                    61_000, t0, 1f, 214_000, canPrev = true, canNext = true, canToggle = true,
                ),
            ),
            ChargingActivity(BatteryData(76, ChargeStatus.CHARGING, ChargeSource.WIRELESS, 31.5f)),
        )
    }
    val list = when (selection) { 0 -> listOf(all[2]); 1 -> listOf(all[3]); 2 -> listOf(all[0]); 3 -> listOf(all[1]); else -> all }
    val safeIndex = index.coerceIn(0, list.lastIndex)
    val state = IslandUiState(list, safeIndex, expanded, visible = true, active = true, hasContent = true)

    val actions = remember(list) {
        object : IslandActions {
            override fun expand() { expanded = true }
            override fun collapse() { expanded = false }
            override fun next() { index = (index + 1) % list.size }
            override fun previous() { index = (index - 1 + list.size) % list.size }
            override fun dismissCurrent() { expanded = false }
            override fun musicPlayPause() { playing = !playing }
            override fun musicNext() = Unit
            override fun musicPrevious() = Unit
            override fun timerPauseResume() = Unit
            override fun timerCancel() = Unit
            override fun stopwatchPauseResume() = Unit
            override fun stopwatchReset() = Unit
            override fun onExitFinished() = Unit
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(224.dp).clip(RoundedCornerShape(26.dp)).background(Color(0xFF050506))
                .drawWithCache {
                    val step = 14.dp.toPx()
                    val r = 1.1.dp.toPx()
                    onDrawBehind {
                        var y = step / 2
                        while (y < size.height) {
                            var x = step / 2
                            while (x < size.width) { drawCircle(Color(0xFF17171A), r, Offset(x, y)); x += step }
                            y += step
                        }
                    }
                }
                .padding(top = 14.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            val geometry = IslandGeometry(16f, 132f, 34f, minOf(maxWidth.value - 16f, 340f))
            IslandPill(state, geometry, settings, actions)
        }
        T("Demo data · not from your device", 12, Palette.TextDim, modifier = Modifier.padding(start = 6.dp))
        if (showControls) {
            Row(Modifier.fillMaxWidth().hScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Music", "Charging", "Timer", "Stopwatch", "All").forEachIndexed { i, label ->
                    Chip(label, selection == i) { selection = i; index = 0; expanded = false }
                }
            }
            Segmented(listOf("Compact", "Expanded"), if (expanded) 1 else 0, { expanded = it == 1 })
            if (selection == 4) T("Swipe the island down to switch activities. Swipe up to collapse.", 13, Palette.TextDim, modifier = Modifier.padding(start = 6.dp))
        }
    }
}
