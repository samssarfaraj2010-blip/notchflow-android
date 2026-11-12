package com.dynamicisland.overlay

import android.content.Context
import android.view.WindowInsets
import android.view.WindowManager
import com.dynamicisland.core.IslandGeometry
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Derives island size/position from the real display: cutout rect, status bar inset, density, rotation. */
object OverlayGeometry {
    fun compute(ctx: Context, wm: WindowManager, offsetDp: Int): IslandGeometry {
        val density = ctx.resources.displayMetrics.density
        val metrics = wm.currentWindowMetrics
        val insets = metrics.windowInsets
        val screenWidthPx = metrics.bounds.width()

        val rect = insets.displayCutout?.boundingRectTop
            ?.takeIf { !it.isEmpty && it.width() < screenWidthPx / 2 }
        val cutoutW = rect?.let { it.width() / density } ?: 0f
        val cutoutH = rect?.let { it.height() / density } ?: 0f

        val compactH = if (rect != null) (cutoutH + 12f).coerceIn(32f, 40f) else 34f
        val compactW = max(cutoutW + 2 * 58f, 132f)
        val expandedW = min(screenWidthPx / density - 24f, 372f)

        val statusTop = insets.getInsets(WindowInsets.Type.statusBars()).top
        val centerPx = rect?.exactCenterY() ?: max(statusTop / 2f, 12f * density)
        val yPx = (centerPx - compactH * density / 2f + offsetDp * density).roundToInt().coerceAtLeast(0)

        return IslandGeometry(cutoutW, compactW, compactH, expandedW, yPx)
    }
}
