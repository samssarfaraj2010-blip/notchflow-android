package com.dynamicisland.overlay

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.WindowManager

/**
 * Android only runs a window at 90/120 Hz if it asks for it; many OEM ROMs cap apps (and especially overlays) at 60 Hz
 * otherwise. We pick the fastest display mode that keeps the CURRENT resolution, so requesting it never changes
 * resolution - only refresh rate.
 */
object HighRefresh {
    fun bestMode(ctx: Context): Display.Mode? {
        val dm = ctx.getSystemService(DisplayManager::class.java) ?: return null
        val display = dm.getDisplay(Display.DEFAULT_DISPLAY) ?: return null
        val cur = display.mode
        return display.supportedModes
            .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
            .maxByOrNull { it.refreshRate }
    }

    /** Writes the high-refresh request into [p]. Returns true if anything changed. */
    fun apply(ctx: Context, p: WindowManager.LayoutParams, high: Boolean): Boolean {
        val m = bestMode(ctx)
        val rate = if (high && m != null) m.refreshRate else 0f
        val id = if (high && m != null) m.modeId else 0
        if (p.preferredRefreshRate == rate && p.preferredDisplayModeId == id) return false
        p.preferredRefreshRate = rate
        p.preferredDisplayModeId = id
        return true
    }
}
