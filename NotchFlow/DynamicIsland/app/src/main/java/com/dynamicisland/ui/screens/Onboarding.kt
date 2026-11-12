package com.dynamicisland.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dynamicisland.Perms
import com.dynamicisland.ui.components.ActionButton
import com.dynamicisland.ui.components.BtnStyle
import com.dynamicisland.ui.components.Hint
import com.dynamicisland.ui.components.T
import com.dynamicisland.ui.theme.Palette

@Composable
fun Onboarding(perms: Perms, onOverlay: () -> Unit, onMedia: () -> Unit, onFinish: (enable: Boolean) -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(4) { i ->
                Box(Modifier.size(width = if (i == step) 22.dp else 8.dp, height = 8.dp).background(if (i <= step) Palette.Text else Palette.Stroke, CircleShape))
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(top = 28.dp)) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    (fadeIn(tween(250)) + slideInHorizontally(tween(300)) { it / 6 }) togetherWith
                        (fadeOut(tween(150)) + slideOutHorizontally(tween(300)) { -it / 6 })
                },
                label = "onboarding",
            ) { s ->
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    when (s) {
                        0 -> {
                            T("Meet NotchFlow.", 34, weight = FontWeight.Bold)
                            T("Not just a notch. A whole new way to interact. A small pill by your camera that grows into music, charging, calls and timers.", 16, Palette.TextDim)
                            Spacer(Modifier.height(8.dp))
                            IslandPreview(showControls = false)
                        }
                        1 -> {
                            T("It floats above your apps.", 30, weight = FontWeight.Bold)
                            T("Android calls this “Display over other apps”. It's the standard permission for overlays; the island only draws a small pill and never reads what's on your screen.", 16, Palette.TextDim)
                            Hint(if (perms.overlay) "Permission granted." else "You'll be taken to a system screen. Switch it on for NotchFlow, then come back.")
                        }
                        2 -> {
                            T("Music needs one more key.", 30, weight = FontWeight.Bold)
                            T("To show what's playing, Android requires “notification access”. NotchFlow uses it only to read media sessions (title, artwork, play state). Notification contents are never read, stored or sent anywhere, and the app has no internet permission.", 16, Palette.TextDim)
                            Hint("This step is optional. If the switch is greyed out, open App info, tap ⋮ and choose “Allow restricted settings”.")
                        }
                        else -> {
                            T("You're set.", 34, weight = FontWeight.Bold)
                            T("You can turn the island on or off any time from Home, or from the notification. Everything is under Settings.", 16, Palette.TextDim)
                        }
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (step) {
                0 -> ActionButton("Get started") { step = 1 }
                1 -> {
                    if (perms.overlay) ActionButton("Continue") { step = 2 } else {
                        ActionButton("Allow overlay access", style = BtnStyle.Accent, onClick = onOverlay)
                        ActionButton("Not now", style = BtnStyle.Secondary) { step = 2 }
                    }
                }
                2 -> {
                    if (perms.mediaAccess) ActionButton("Continue") { step = 3 } else {
                        ActionButton("Open media access", style = BtnStyle.Accent, onClick = onMedia)
                        ActionButton("Skip for now", style = BtnStyle.Secondary) { step = 3 }
                    }
                }
                else -> {
                    ActionButton("Turn on NotchFlow", style = BtnStyle.Accent) { onFinish(true) }
                    ActionButton("Maybe later", style = BtnStyle.Secondary) { onFinish(false) }
                }
            }
        }
    }
}
