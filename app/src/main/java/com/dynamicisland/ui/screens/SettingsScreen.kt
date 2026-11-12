package com.dynamicisland.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dynamicisland.Perms
import com.dynamicisland.core.IslandRuntime
import com.dynamicisland.core.IslandSettings
import com.dynamicisland.ui.components.ActionButton
import com.dynamicisland.ui.components.AppCard
import com.dynamicisland.ui.components.BtnStyle
import com.dynamicisland.ui.components.Divider
import com.dynamicisland.ui.components.SectionLabel
import com.dynamicisland.ui.components.Segmented
import com.dynamicisland.ui.components.StatusRow
import com.dynamicisland.ui.components.Stepper
import com.dynamicisland.ui.components.T
import com.dynamicisland.ui.components.ToggleRow
import com.dynamicisland.ui.theme.Palette
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(
    s: IslandSettings,
    perms: Perms,
    onIslandToggle: (Boolean) -> Unit,
    onOverlay: () -> Unit,
    onMedia: () -> Unit,
    onNotifications: () -> Unit,
    onPhone: () -> Unit,
    onTouch: () -> Unit,
    onAppInfo: () -> Unit,
) {
    fun edit(block: IslandSettings.() -> IslandSettings) = IslandRuntime.settings.update(block)
    var confirmReset by remember { mutableStateOf(false) }
    LaunchedEffect(confirmReset) { if (confirmReset) { delay(3000); confirmReset = false } }

    ScreenColumn {
        T("Settings", 28, weight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp, top = 8.dp, bottom = 4.dp))

        SectionLabel("General")
        AppCard {
            ToggleRow("NotchFlow", "Master switch", s.islandEnabled, onChange = onIslandToggle)
            Divider()
            ToggleRow("Expand on events", "Briefly open when a charger connects", s.autoExpand) { v -> edit { copy(autoExpand = v) } }
            Divider()
            ToggleRow("Show in landscape", "Off by default so videos and games stay clear", s.showInLandscape) { v -> edit { copy(showInLandscape = v) } }
        }

        SectionLabel("Animation")
        AppCard {
            T("Motion style", 16, weight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            Segmented(listOf("Smooth", "Snappy", "Bouncy", "Minimal"), s.animationStyle, { v -> edit { copy(animationStyle = v) } })
            Spacer(Modifier.height(8.dp))
            T(
                when (s.animationStyle) {
                    0 -> "Balanced spring, the default."
                    1 -> "Quick and tight with almost no overshoot."
                    2 -> "Soft and playful with visible overshoot."
                    else -> "Short fades, no spring and no visualizer animation."
                },
                13, Palette.TextDim,
            )
            Divider()
            ToggleRow("Music visualizer", "Animated bars while playing", s.visualizer) { v -> edit { copy(visualizer = v) } }
        }

        SectionLabel("Gestures and feedback")
        AppCard {
            ToggleRow("Swipe gestures", "Down: next activity. Left: next track. Right: previous track. Up: collapse", s.swipeToSwitch) { v -> edit { copy(swipeToSwitch = v) } }
            Divider()
            ToggleRow("Tap outside to collapse", null, s.tapOutsideCollapse) { v -> edit { copy(tapOutsideCollapse = v) } }
            Divider()
            ToggleRow("Swipe up to dismiss", "When compact, hides music or charging until it changes", s.swipeUpDismiss) { v -> edit { copy(swipeUpDismiss = v) } }
            Divider()
            ToggleRow("Haptic feedback", null, s.haptics) { v -> edit { copy(haptics = v) } }
        }

        SectionLabel("Appearance")
        AppCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column2 {
                    T("Vertical position", 16, weight = FontWeight.Medium)
                    T("Nudge the island to line up with your camera", 13, Palette.TextDim, modifier = Modifier.padding(top = 2.dp))
                }
                Stepper(s.verticalOffsetDp, -8..16, { if (it > 0) "+$it dp" else "$it dp" }) { v -> edit { copy(verticalOffsetDp = v) } }
            }
        }

        SectionLabel("Permissions")
        AppCard {
            StatusRow("Display over other apps", if (perms.overlay) "Allowed" else "Not allowed", perms.overlay, "Open", onOverlay)
            Divider()
            StatusRow("Media access", if (perms.mediaAccess) "Allowed" else "Not allowed", perms.mediaAccess, "Open", onMedia)
            Divider()
            StatusRow("Notifications", if (perms.notifications) "Allowed" else "Not allowed", perms.notifications, "Open", onNotifications)
            Divider()
            StatusRow("Tap on the status bar", if (perms.touch) "Enabled" else "Not enabled", perms.touch, "Open", onTouch)
            Divider()
            StatusRow("Call status", if (perms.phone) "Allowed" else "Not allowed", perms.phone, "Open", onPhone)
            Divider()
            StatusRow("App info", "Uninstall, force stop, restricted settings", true, "Open", onAppInfo)
        }

        SectionLabel("Privacy")
        AppCard {
            T("Everything stays on your phone. No internet permission, no analytics, no ads. Notification title/text may be previewed in the island when access is enabled.", 14, Palette.TextDim)
        }

        ActionButton(
            if (confirmReset) "Tap again to confirm" else "Reset settings",
            style = BtnStyle.Danger,
        ) {
            if (confirmReset) { IslandRuntime.settings.resetPreferences(); confirmReset = false } else confirmReset = true
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Column2(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    androidx.compose.foundation.layout.Column(Modifier.weight(1f).padding(end = 10.dp), content = content)
}
