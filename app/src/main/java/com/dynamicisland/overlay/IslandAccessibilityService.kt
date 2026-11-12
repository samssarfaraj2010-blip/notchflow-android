package com.dynamicisland.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Normal overlay windows sit BELOW the status bar, so touches on the island (which lives in the status-bar
 * strip) never reach it. An accessibility service may add TYPE_ACCESSIBILITY_OVERLAY windows, which sit above it.
 * This service is used for that only: it declares no event types and cannot retrieve window content.
 */
class IslandAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        connected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        connected.value = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        connected.value = false
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: IslandAccessibilityService? = null
            private set
        val connected = MutableStateFlow(false)
    }
}
