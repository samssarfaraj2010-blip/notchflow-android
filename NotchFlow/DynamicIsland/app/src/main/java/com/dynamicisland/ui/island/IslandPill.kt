package com.dynamicisland.ui.island

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.dynamicisland.core.IslandActions
import com.dynamicisland.core.IslandActivity
import com.dynamicisland.core.IslandGeometry
import com.dynamicisland.core.IslandSettings
import com.dynamicisland.core.IslandUiState
import com.dynamicisland.core.Kind
import com.dynamicisland.core.MusicActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Spring presets for the user's "animation style" preference. */
private data class Motion(val damping: Float, val stiffness: Float, val minimal: Boolean = false) {
    fun floatSpec(): AnimationSpec<Float> = if (minimal) tween(180, easing = FastOutSlowInEasing) else spring(damping, stiffness)
    fun dpSpec(): AnimationSpec<Dp> = if (minimal) tween(180, easing = FastOutSlowInEasing) else spring(damping, stiffness)
    companion object {
        fun of(style: Int) = when (style) {
            1 -> Motion(0.90f, 800f)
            2 -> Motion(0.60f, 330f)
            3 -> Motion(1f, 400f, minimal = true)
            else -> Motion(0.78f, 380f)
        }
    }
}

private fun haptic(view: View, s: IslandSettings, type: Int) {
    if (s.haptics) view.performHapticFeedback(type)
}

/**
 * The island. One black surface whose width, height and corner radius are driven by a single spring-animated
 * "expand" progress read in the layout/draw phases (no per-frame recomposition of the content).
 * Used both in the real overlay window and in the in-app preview.
 */
@Composable
fun IslandPill(
    state: IslandUiState,
    geometry: IslandGeometry,
    settings: IslandSettings,
    actions: IslandActions,
    modifier: Modifier = Modifier,
    /** Called when the expand/collapse animation has fully finished (true = expanded). Lets the overlay resize its window. */
    onExpandSettled: (Boolean) -> Unit = {},
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val latestState by rememberUpdatedState(state)
    val latestSettings by rememberUpdatedState(settings)
    val latestActions by rememberUpdatedState(actions)
    val motion = Motion.of(settings.animationStyle)

    val vis = remember { Animatable(0f) }
    val expand = remember { Animatable(0f) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val switchDir = remember { intArrayOf(1) }
    val settleJob = remember { arrayOfNulls<Job>(1) }

    LaunchedEffect(state.visible) {
        vis.animateTo(if (state.visible) 1f else 0f, spring(1f, 500f))
        if (!state.visible) actions.onExitFinished()
    }
    LaunchedEffect(state.expanded) {
        expand.animateTo(if (state.expanded) 1f else 0f, motion.floatSpec())
        onExpandSettled(state.expanded)
    }

    val current = state.current
    val targetHeight = (current?.let { expandedHeightDp(it) } ?: 160f).dp
    val expandedHeight = animateDpAsState(targetHeight, motion.dpSpec(), label = "expandedHeight")

    val targetWidth = (current?.let { expandedWidthDp(it, geometry.expandedWidthDp) } ?: geometry.expandedWidthDp).dp
    val expandedWidth = animateDpAsState(targetWidth, motion.dpSpec(), label = "expandedWidth")

    val compactW = geometry.compactWidthDp
    val compactH = geometry.compactHeightDp
    val showCompact by remember { derivedStateOf { expand.value < 0.98f } }
    val showExpanded by remember { derivedStateOf { expand.value > 0.002f } }

    fun settle() {
        settleJob[0]?.cancel()
        val fromX = dragX
        val fromY = dragY
        settleJob[0] = scope.launch {
            animate(1f, 0f, animationSpec = spring(0.8f, 500f)) { f, _ -> dragX = fromX * f; dragY = fromY * f }
        }
    }

    Box(
        modifier
            .graphicsLayer {
                val v = vis.value.coerceIn(0f, 1f)
                val s = 0.6f + 0.4f * v
                scaleX = s; scaleY = s; alpha = v
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
            .layout { measurable, constraints ->
                if (vis.value <= 0.001f) return@layout layout(1, 1) {}
                val p = expand.value.coerceIn(0f, 1.1f)
                val w = lerp(compactW.dp.toPx(), expandedWidth.value.toPx(), p).roundToInt().coerceIn(1, constraints.maxWidth)
                val h = lerp(compactH.dp.toPx(), expandedHeight.value.toPx(), p).roundToInt().coerceAtLeast(1)
                val placeable = measurable.measure(Constraints.fixed(w, h))
                layout(w, h) { placeable.place(0, 0) }
            }
            .graphicsLayer {
                val p = expand.value.coerceIn(0f, 1f)
                val r = lerp(compactH.dp.toPx() / 2f, 38.dp.toPx(), p)
                shape = RoundedCornerShape(minOf(r, size.height / 2f).coerceAtLeast(0f))
                clip = true
            }
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    if (!latestState.expanded) {
                        haptic(view, latestSettings, HapticFeedbackConstants.CONTEXT_CLICK)
                        latestActions.expand()
                    }
                })
            }
            .pointerInput(Unit) {
                val threshold = 36.dp.toPx()
                val maxDrag = 60.dp.toPx()
                var dx = 0f
                var dy = 0f
                detectDragGestures(
                    onDragStart = { settleJob[0]?.cancel(); dx = 0f; dy = 0f },
                    onDragEnd = {
                        val s = latestSettings
                        val st = latestState
                        val cur = st.current
                        if (s.swipeToSwitch && abs(dx) > abs(dy) && abs(dx) > threshold) {
                            // Requested mapping: swipe LEFT = next, swipe RIGHT = previous.
                            if (cur is MusicActivity) {
                                if (dx < 0 && cur.data.canNext) {
                                    haptic(view, s, HapticFeedbackConstants.CLOCK_TICK)
                                    latestActions.musicNext()
                                } else if (dx > 0 && cur.data.canPrev) {
                                    haptic(view, s, HapticFeedbackConstants.CLOCK_TICK)
                                    latestActions.musicPrevious()
                                }
                            }
                        } else if (abs(dy) > abs(dx) && abs(dy) > threshold) {
                            if (dy > 0) {
                                // Down: next activity.
                                if (s.swipeToSwitch && st.activities.size > 1) {
                                    switchDir[0] = 1
                                    haptic(view, s, HapticFeedbackConstants.CLOCK_TICK)
                                    latestActions.next()
                                }
                            } else if (st.expanded) latestActions.collapse()
                            else if (s.swipeUpDismiss) latestActions.dismissCurrent()
                        }
                        settle()
                    },
                    onDragCancel = { settle() },
                    onDrag = { change, amount ->
                        change.consume()
                        dx += amount.x
                        dy += amount.y
                        if (latestSettings.swipeToSwitch) {
                            if (abs(dx) > abs(dy)) { dragX = dx.coerceIn(-maxDrag, maxDrag); dragY = 0f }
                            else { dragY = dy.coerceIn(-maxDrag, maxDrag); dragX = 0f }
                        }
                    },
                )
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        if (showCompact) {
            Box(
                Modifier.requiredSize(compactW.dp, compactH.dp).graphicsLayer {
                    alpha = (1f - expand.value / 0.35f).coerceIn(0f, 1f)
                    translationX = dragX * 0.35f
                    translationY = dragY * 0.35f
                },
            ) {
                ActivitySwitcher(state, { switchDir[0] }) { a -> CompactContent(a, geometry.cutoutWidthDp.dp, state.active, settings) }
            }
        }
        if (showExpanded) {
            Box(
                Modifier.requiredSize(expandedWidth.value, expandedHeight.value).graphicsLayer {
                    alpha = ((expand.value - 0.45f) / 0.4f).coerceIn(0f, 1f)
                    translationX = dragX * 0.35f
                    translationY = dragY * 0.35f
                },
            ) {
                ActivitySwitcher(state, { switchDir[0] }) { a -> ExpandedContent(a, state.active, settings, actions, state.expanded) }
                if (state.activities.size > 1) PageDots(state)
            }
        }
    }
}

/** Slides/crossfades between activities. Keyed by kind so data updates never trigger a transition. */
@Composable
private fun ActivitySwitcher(state: IslandUiState, dir: () -> Int, content: @Composable (IslandActivity) -> Unit) {
    val lastKnown = remember { mutableMapOf<Kind, IslandActivity>() }
    state.activities.forEach { lastKnown[it.kind] = it }
    val kind = state.current?.kind ?: return
    AnimatedContent(
        targetState = kind,
        transitionSpec = {
            val d = dir()
            (fadeIn(tween(220, delayMillis = 60)) + slideInVertically(tween(300)) { -it / 2 * d }) togetherWith
                (fadeOut(tween(110)) + slideOutVertically(tween(300)) { it / 2 * d })
        },
        label = "activitySwitch",
    ) { k ->
        val activity = state.activities.firstOrNull { it.kind == k } ?: lastKnown[k]
        if (activity != null) content(activity)
    }
}

@Composable
private fun BoxScope.PageDots(state: IslandUiState) {
    Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        state.activities.forEachIndexed { i, _ ->
            Box(Modifier.size(5.dp).background(if (i == state.currentIndex) Color.White else Color(0x4DFFFFFF), CircleShape))
        }
    }
}
