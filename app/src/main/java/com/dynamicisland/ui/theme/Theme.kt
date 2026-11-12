package com.dynamicisland.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** One cohesive, AMOLED-first palette: true black, quiet greys, a single Nothing-style red accent. */
object Palette {
    val Black = Color(0xFF000000)
    val Surface = Color(0xFF0D0D0F)
    val SurfaceHi = Color(0xFF17171A)
    val SurfaceTop = Color(0xFF232327)
    val Stroke = Color(0xFF242427)
    val Text = Color(0xFFF4F4F5)
    val TextDim = Color(0xFF8C8C93)
    val Red = Color(0xFFD71921)
    val Green = Color(0xFF32D74B)
    val Orange = Color(0xFFFF9F0A)
    val Yellow = Color(0xFFFFD60A)
    val Pink = Color(0xFFFF375F)
}

@Composable
fun IslandTheme(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Palette.Black)) { content() }
}
