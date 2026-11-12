package com.dynamicisland.ui.island

import java.util.Locale

fun formatClock(ms: Long, tenths: Boolean = false): String {
    val total = ms.coerceAtLeast(0)
    val h = total / 3_600_000
    val m = (total / 60_000) % 60
    val s = (total / 1_000) % 60
    val t = (total / 100) % 10
    return when {
        h > 0 -> String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
        tenths -> String.format(Locale.ROOT, "%02d:%02d.%d", m, s, t)
        else -> String.format(Locale.ROOT, "%02d:%02d", m, s)
    }
}

/** Countdown text rounds UP to the next second so "00:00" only appears when the timer is truly done. */
fun ceilSecond(ms: Long): Long = (ms + 999) / 1000 * 1000

/** Short form that fits beside the camera cutout. */
fun formatCompactTimer(ms: Long): String {
    val total = ceilSecond(ms) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.ROOT, "%dh%02dm", h, m) else String.format(Locale.ROOT, "%02d:%02d", m, s)
}
