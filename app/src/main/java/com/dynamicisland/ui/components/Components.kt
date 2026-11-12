package com.dynamicisland.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as colorLerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicisland.ui.theme.Palette
import kotlin.math.roundToInt

@Composable
fun T(
    text: String,
    size: Int = 15,
    color: Color = Palette.Text,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    tnum: Boolean = false,
    align: TextAlign = TextAlign.Unspecified,
    spacing: Float = 0f,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size.sp,
            fontWeight = weight,
            fontFeatureSettings = if (tnum) "tnum" else null,
            textAlign = align,
            letterSpacing = spacing.sp,
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun Glyph(icon: ImageVector, tint: Color = Palette.Text, size: Dp = 22.dp, modifier: Modifier = Modifier, description: String? = null) {
    Image(
        imageVector = icon,
        contentDescription = description,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

/** Consistent touch feedback everywhere: a quick spring scale, no default ripple. */
fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.95f else 1f, spring(stiffness = 700f), label = "press")
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier = modifier.fillMaxWidth().clip(shape).background(Palette.Surface).border(1.dp, Palette.Stroke, shape).padding(18.dp),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    T(text.uppercase(), 12, Palette.TextDim, FontWeight.Medium, modifier.padding(start = 6.dp, top = 4.dp), spacing = 1.2f)
}

@Composable
fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Stroke))
}

@Composable
fun IslandSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val progress by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 1f, stiffness = 700f), label = "switch")
    val track = colorLerp(Color(0xFF2C2C30), Palette.Red, progress.coerceIn(0f, 1f))
    Box(
        modifier
            .size(52.dp, 30.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(track)
            .toggleable(
                value = checked,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onChange,
            )
            .padding(3.dp),
    ) {
        Box(Modifier.offset { IntOffset((22.dp * progress).roundToPx(), 0) }.size(24.dp).background(Color.White, CircleShape))
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 14.dp)) {
            T(title, 16, weight = FontWeight.Medium)
            if (subtitle != null) T(subtitle, 13, Palette.TextDim, modifier = Modifier.padding(top = 2.dp))
        }
        IslandSwitch(checked, onChange, enabled = enabled)
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.SurfaceHi).padding(3.dp)) {
        options.forEachIndexed { i, label ->
            val isSelected = i == selected
            val bg by animateColorAsState(if (isSelected) Palette.SurfaceTop else Color.Transparent, label = "segment")
            Box(
                Modifier.weight(1f).pressable { onSelect(i) }.clip(RoundedCornerShape(13.dp)).background(bg).padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                T(label, 13, if (isSelected) Palette.Text else Palette.TextDim, FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Palette.Text else Palette.SurfaceHi, label = "chip")
    Box(Modifier.pressable(onClick = onClick).clip(CircleShape).background(bg).padding(horizontal = 16.dp, vertical = 9.dp)) {
        T(label, 13, if (selected) Color.Black else Palette.Text, FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun RoundButton(icon: ImageVector, enabled: Boolean, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).alpha(if (enabled) 1f else 0.35f).pressable(enabled, onClick).clip(CircleShape).background(Palette.SurfaceTop),
        contentAlignment = Alignment.Center,
    ) { Glyph(icon, Palette.Text, 18.dp, description = description) }
}

@Composable
fun Stepper(value: Int, range: IntRange, label: (Int) -> String, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RoundButton(Icons.Rounded.Remove, value > range.first, "Decrease") { onChange(value - 1) }
        T(label(value), 15, tnum = true, modifier = Modifier.widthIn(min = 76.dp), align = TextAlign.Center, weight = FontWeight.Medium)
        RoundButton(Icons.Rounded.Add, value < range.last, "Increase") { onChange(value + 1) }
    }
}

enum class BtnStyle { Primary, Secondary, Danger, Accent }

@Composable
fun ActionButton(
    text: String,
    modifier: Modifier = Modifier,
    style: BtnStyle = BtnStyle.Primary,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val (bg, fg) = when (style) {
        BtnStyle.Primary -> Palette.Text to Color.Black
        BtnStyle.Secondary -> Palette.SurfaceTop to Palette.Text
        BtnStyle.Danger -> Color(0xFF3A1417) to Color(0xFFFF6B6B)
        BtnStyle.Accent -> Palette.Red to Color.White
    }
    Box(
        modifier.fillMaxWidth().height(50.dp).alpha(if (enabled) 1f else 0.4f).pressable(enabled, onClick).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) { T(text, 16, fg, FontWeight.SemiBold, maxLines = 1) }
}

@Composable
fun StatusRow(title: String, detail: String, ok: Boolean, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(if (ok) Palette.Green else Palette.Orange, CircleShape))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            T(title, 16, weight = FontWeight.Medium)
            T(detail, 13, Palette.TextDim, modifier = Modifier.padding(top = 2.dp))
        }
        if (action != null) {
            Box(Modifier.pressable(onClick = onAction).clip(CircleShape).background(Palette.SurfaceTop).padding(horizontal = 14.dp, vertical = 8.dp)) {
                T(action, 13, weight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.SurfaceHi).padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 6.dp).size(6.dp).background(Palette.Orange, CircleShape))
        Spacer(Modifier.width(12.dp))
        T(text, 13, Palette.TextDim)
    }
}

@Composable
fun ProgressRing(
    fraction: () -> Float,
    size: Dp,
    stroke: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val topLeft = Offset(sw / 2f, sw / 2f)
            val arcSize = Size(this.size.width - sw, this.size.height - sw)
            drawArc(Color(0x33FFFFFF), 0f, 360f, false, topLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            val sweep = 360f * fraction().coerceIn(0f, 1f)
            if (sweep > 0f) drawArc(color, -90f, sweep, false, topLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
        }
        content()
    }
}
