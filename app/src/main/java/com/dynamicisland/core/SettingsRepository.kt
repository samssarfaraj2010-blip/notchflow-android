package com.dynamicisland.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class IslandSettings(
    val islandEnabled: Boolean = false,
    val onboardingDone: Boolean = false,
    val music: Boolean = true,
    val charging: Boolean = true,
    val timer: Boolean = true,
    val stopwatch: Boolean = true,
    val calls: Boolean = true,
    val haptics: Boolean = true,
    /** 0 Smooth, 1 Snappy, 2 Bouncy, 3 Minimal */
    val animationStyle: Int = 0,
    val visualizer: Boolean = true,
    val swipeToSwitch: Boolean = true,
    val tapOutsideCollapse: Boolean = true,
    val swipeUpDismiss: Boolean = true,
    val autoExpand: Boolean = true,
    val showInLandscape: Boolean = false,
    val verticalOffsetDp: Int = 0,
)

/** Local-only settings (SharedPreferences). Nothing leaves the device. */
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("island_settings", Context.MODE_PRIVATE)
    private val _flow = MutableStateFlow(read())
    val flow: StateFlow<IslandSettings> = _flow.asStateFlow()
    val value: IslandSettings get() = _flow.value

    @Synchronized
    fun update(block: IslandSettings.() -> IslandSettings) {
        val next = _flow.value.block().let {
            it.copy(
                animationStyle = it.animationStyle.coerceIn(0, 3),
                verticalOffsetDp = it.verticalOffsetDp.coerceIn(-8, 16),
            )
        }
        if (next == _flow.value) return
        write(next)
        _flow.value = next
    }

    /** Resets every preference except "is enabled" and "onboarding finished". */
    fun resetPreferences() = update { IslandSettings(islandEnabled = islandEnabled, onboardingDone = onboardingDone) }

    private fun read(): IslandSettings {
        val d = IslandSettings()
        return IslandSettings(
            islandEnabled = prefs.getBoolean("island_enabled", d.islandEnabled),
            onboardingDone = prefs.getBoolean("onboarding_done", d.onboardingDone),
            music = prefs.getBoolean("music", d.music),
            charging = prefs.getBoolean("charging", d.charging),
            timer = prefs.getBoolean("timer", d.timer),
            stopwatch = prefs.getBoolean("stopwatch", d.stopwatch),
            calls = prefs.getBoolean("calls", d.calls),
            haptics = prefs.getBoolean("haptics", d.haptics),
            animationStyle = prefs.getInt("animation_style", d.animationStyle).coerceIn(0, 3),
            visualizer = prefs.getBoolean("visualizer", d.visualizer),
            swipeToSwitch = prefs.getBoolean("swipe_switch", d.swipeToSwitch),
            tapOutsideCollapse = prefs.getBoolean("tap_outside", d.tapOutsideCollapse),
            swipeUpDismiss = prefs.getBoolean("swipe_up", d.swipeUpDismiss),
            autoExpand = prefs.getBoolean("auto_expand", d.autoExpand),
            showInLandscape = prefs.getBoolean("landscape", d.showInLandscape),
            verticalOffsetDp = prefs.getInt("offset_dp", d.verticalOffsetDp).coerceIn(-8, 16),
        )
    }

    private fun write(s: IslandSettings) {
        prefs.edit()
            .putBoolean("island_enabled", s.islandEnabled)
            .putBoolean("onboarding_done", s.onboardingDone)
            .putBoolean("music", s.music)
            .putBoolean("charging", s.charging)
            .putBoolean("timer", s.timer)
            .putBoolean("stopwatch", s.stopwatch)
            .putBoolean("calls", s.calls)
            .putBoolean("haptics", s.haptics)
            .putInt("animation_style", s.animationStyle)
            .putBoolean("visualizer", s.visualizer)
            .putBoolean("swipe_switch", s.swipeToSwitch)
            .putBoolean("tap_outside", s.tapOutsideCollapse)
            .putBoolean("swipe_up", s.swipeUpDismiss)
            .putBoolean("auto_expand", s.autoExpand)
            .putBoolean("landscape", s.showInLandscape)
            .putInt("offset_dp", s.verticalOffsetDp)
            .apply()
    }
}
