package com.dynamicisland.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dynamicisland.Perms
import com.dynamicisland.Permissions
import com.dynamicisland.core.IslandRuntime
import com.dynamicisland.overlay.OverlayService
import com.dynamicisland.ui.components.Glyph
import com.dynamicisland.ui.components.T
import com.dynamicisland.ui.components.pressable
import com.dynamicisland.ui.theme.Palette

enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Rounded.Home),
    Features("Features", Icons.Rounded.Dashboard),
    Settings("Settings", Icons.Rounded.Tune),
}

@Composable
fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
            .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val settings by IslandRuntime.settings.flow.collectAsStateWithLifecycle()
    var perms by remember { mutableStateOf(Permissions.read(ctx)) }
    var pendingEnable by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(Tab.Home) }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        perms = Permissions.read(ctx)
    }

    val phoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        perms = Permissions.read(ctx)
        IslandRuntime.calls.refresh()
    }

    fun enable(p: Perms) {
        if (Build.VERSION.SDK_INT >= 33 && !p.notifications) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        IslandRuntime.settings.update { copy(islandEnabled = true) }
        OverlayService.start(ctx)
    }

    fun setIsland(on: Boolean) {
        if (!on) {
            pendingEnable = false
            IslandRuntime.settings.update { copy(islandEnabled = false) }
            OverlayService.stop(ctx)
        } else if (!perms.overlay) {
            pendingEnable = true
            Permissions.open(ctx, Permissions.overlayIntent(ctx))
        } else enable(perms)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val fresh = Permissions.read(ctx)
        perms = fresh
        val s = IslandRuntime.settings.value
        when {
            // Overlay permission was revoked while enabled: switch off cleanly instead of crashing later.
            s.islandEnabled && !fresh.overlay -> {
                IslandRuntime.settings.update { copy(islandEnabled = false) }
                OverlayService.stop(ctx)
            }
            pendingEnable && fresh.overlay -> { pendingEnable = false; enable(fresh) }
            // Process was killed while enabled: bring the service back (we're in the foreground here).
            s.islandEnabled && !OverlayService.running -> OverlayService.start(ctx)
        }
    }

    if (!settings.onboardingDone) {
        Onboarding(
            perms = perms,
            onOverlay = { Permissions.open(ctx, Permissions.overlayIntent(ctx)) },
            onMedia = { Permissions.open(ctx, Permissions.mediaAccessIntent(ctx)) },
            onFinish = { turnOn ->
                IslandRuntime.settings.update { copy(onboardingDone = true) }
                if (turnOn) setIsland(true)
            },
        )
        return
    }

    val openOverlay = { Permissions.open(ctx, Permissions.overlayIntent(ctx)) }
    val openMedia = { Permissions.open(ctx, Permissions.mediaAccessIntent(ctx)) }
    val openNotifications = {
        if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else Permissions.open(ctx, Permissions.appInfoIntent(ctx))
    }

    val openPhone = { phoneLauncher.launch(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.ANSWER_PHONE_CALLS)) }
    val openAccessibility = { Permissions.open(ctx, Permissions.accessibilityIntent()) }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            label = "tabs",
        ) { t ->
            when (t) {
                Tab.Home -> HomeScreen(settings, perms, ::setIsland, openOverlay, openMedia, openNotifications, openPhone, openAccessibility) { tab = Tab.Features }
                Tab.Features -> FeaturesScreen(settings, perms, openMedia, openPhone)
                Tab.Settings -> SettingsScreen(
                    settings, perms, ::setIsland, openOverlay, openMedia, openNotifications, openPhone, openAccessibility,
                ) { Permissions.open(ctx, Permissions.appInfoIntent(ctx)) }
            }
        }
        BottomBar(tab, { tab = it }, Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * Floating "liquid glass" bar: inset from the bottom and sides, fully pill-shaped, translucent so the scrolling
 * content shows through, with a faint top-lit sheen and a hairline reflective edge.
 *
 * Note: Modifier.blur() blurs the composable's OWN pixels (it would smear the icons and labels), not what is behind
 * it, so it is deliberately not applied here. The glass look comes from the translucent fill + sheen + edge.
 */
@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val glassFill = Color.Black.copy(alpha = 0.6f)
    val sheen = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.02f)))
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 14.dp)
            .clip(CircleShape)
            .background(glassFill)
            .background(sheen)
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), CircleShape),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp)) {
            Tab.entries.forEach { tab ->
                val isSelected = tab == selected
                val bg by animateColorAsState(if (isSelected) Color.White.copy(alpha = 0.14f) else Color.Transparent, label = "tabBg")
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Row(
                        Modifier.pressable { onSelect(tab) }.clip(CircleShape).background(bg).padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Glyph(tab.icon, if (isSelected) Palette.Text else Palette.TextDim, 22.dp, description = tab.label)
                        if (isSelected) T(tab.label, 14, Palette.Text, FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }
    }
}
