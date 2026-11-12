package com.dynamicisland.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dynamicisland.overlay.OverlayDiag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dynamicisland.Perms
import com.dynamicisland.core.IslandSettings
import com.dynamicisland.ui.components.ActionButton
import com.dynamicisland.ui.components.AppCard
import com.dynamicisland.ui.components.BtnStyle
import com.dynamicisland.ui.components.Divider
import com.dynamicisland.ui.components.Hint
import com.dynamicisland.ui.components.IslandSwitch
import com.dynamicisland.ui.components.SectionLabel
import com.dynamicisland.ui.components.StatusRow
import com.dynamicisland.ui.components.T
import com.dynamicisland.ui.theme.Palette

@Composable
fun HomeScreen(
    settings: IslandSettings,
    perms: Perms,
    onToggle: (Boolean) -> Unit,
    onOverlay: () -> Unit,
    onMedia: () -> Unit,
    onNotifications: () -> Unit,
    onPhone: () -> Unit,
    onTouch: () -> Unit,
    onFeatures: () -> Unit,
) {
    ScreenColumn {
        Row(Modifier.padding(start = 6.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(11.dp).background(Palette.Red, CircleShape))
            Spacer(Modifier.width(10.dp))
            T("NotchFlow", 28, weight = FontWeight.Bold)
        }
        T("Not just a notch. A whole new way to interact.", 14, Palette.TextDim, modifier = Modifier.padding(start = 6.dp, bottom = 4.dp))

        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    T("Island", 20, weight = FontWeight.SemiBold)
                    val status = when {
                        !settings.islandEnabled -> "Off"
                        !perms.overlay -> "Needs overlay permission"
                        else -> "On · shows when something is happening"
                    }
                    T(status, 13, if (settings.islandEnabled && perms.overlay) Palette.Green else Palette.TextDim, modifier = Modifier.padding(top = 2.dp))
                }
                IslandSwitch(settings.islandEnabled, onToggle)
            }
        }

        SectionLabel("Live preview")
        IslandPreview()

        SectionLabel("Access")
        AppCard {
            StatusRow(
                "Display over other apps",
                if (perms.overlay) "Allowed" else "Required to draw the island",
                perms.overlay,
                if (perms.overlay) null else "Allow",
                onOverlay,
            )
            Divider()
            StatusRow(
                "Media access",
                if (perms.mediaAccess) "Connected · reads media sessions only" else "Optional · needed for the Music island",
                perms.mediaAccess,
                if (perms.mediaAccess) null else "Allow",
                onMedia,
            )
            Divider()
            StatusRow(
                "Status notification",
                if (perms.notifications) "Allowed" else "Optional · lets you turn off from the shade",
                perms.notifications,
                if (perms.notifications) null else "Allow",
                onNotifications,
            )
            Divider()
            StatusRow(
                "Tap on the status bar",
                if (perms.touch) "Enabled · reads no screen content" else "Needed so the island can be tapped",
                perms.touch,
                if (perms.touch) null else "Enable",
                onTouch,
            )
            Divider()
            StatusRow(
                "Call status",
                if (perms.phone) "Allowed · state only, never numbers" else "Optional · needed for the Calls island",
                perms.phone,
                if (perms.phone) null else "Allow",
                onPhone,
            )
        }
        if (!perms.touch || !perms.mediaAccess) {
            Hint("To turn on “Tap on the status bar”, open Accessibility, pick NotchFlow and switch it on. If a switch is greyed out: open App info, tap ⋮ (top right), choose “Allow restricted settings”, then try again. Android applies this to apps installed outside the Play Store.")
        }
        if (settings.islandEnabled) {
            val diag by OverlayDiag.lines.collectAsStateWithLifecycle()
            SectionLabel("Overlay status")
            AppCard {
                if (diag.isEmpty()) T("No data yet. Start a timer from Features to test.", 13, Palette.TextDim)
                else diag.entries.forEachIndexed { i, (k, v) ->
                    if (i > 0) Spacer(Modifier.size(6.dp))
                    T("$k: $v", 12, Palette.TextDim)
                }
            }
        }
        ActionButton("Manage features", style = BtnStyle.Secondary, onClick = onFeatures)
    }
}
