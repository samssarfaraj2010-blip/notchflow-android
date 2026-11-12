package com.dynamicisland.ui.island

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
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

/** The one spring every layout animation of the island uses: fluid, iOS-style, with a small friendly overshoot. */
private const val SPRING_DAMPING = 0.7f
private const val SPRING_STIFFNESS = 400f
private fun <T> islandSpring(): SpringSpec<T> = spring(dampingRatio = SPRING_DAMPING, stiffness = SPRING_STIFFNESS)

/**
 * Expand/collapse springs tuned from the reference recording: the panel reaches full size in ~6-7 frames at 30fps
 * (~200ms) with a very small overshoot on expand, and collapses a touch quicker and tighter than it opens.
 */
private fun <T> expandSpring(): SpringSpec<T> = spring(dampingRatio = 0.82f, stiffness = 700f)
private fun <T> collapseSpring(): SpringSpec<T> = spring(dampingRatio = 0.90f, stiffness = 820f)

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
    /** True in the real overlay: fill the window and centre the pill inside it (no WRAP_CONTENT re-centring lag). */
    centerInWindow: Boolean = false,
    /** Pill bounds in window pixels (left, top, right, bottom), reported on every layout. */
    onPillBounds: (Int, Int, Int, Int) -> Unit = { _, _, _, _ -> },
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val latestState by rememberUpdatedState(state)
    val latestSettings by rememberUpdatedState(settings)
    val latestActions by rememberUpdatedState(actions)

    val vis = remember { Animatable(0f) }
    val expand = remember { Animatable(0f) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val switchDir = remember { intArrayOf(1) }
    val settleJob = remember { arrayOfNulls<Job>(1) }

    LaunchedEffect(state.visible) {
        vis.animateTo(if (state.visible) 1f else 0f, islandSpring())
        if (!state.visible) actions.onExitFinished()
    }
    LaunchedEffect(state.expanded) {
        expand.animateTo(if (state.expanded) 1f else 0f, if (state.expanded) expandSpring() else collapseSpring())
        onExpandSettled(state.expanded)
    }

    val current = state.current
    // Expanded panel: full screen width (minus the side margins, see OverlayGeometry) and a slim, per-activity height.
    val targetHeight = (current?.let { expandedHeightDp(it) } ?: 64f).dp
    val expandedHeight = animateDpAsState(targetHeight, islandSpring(), label = "expandedHeight")
    val expandedWidth = geometry.expandedWidthDp.dp

    val compactW = geometry.compactWidthDp
    val compactH = geometry.compactHeightDp
    val showCompact by remember { derivedStateOf { expand.value < 0.98f } }
    val showExpanded by remember { derivedStateOf { expand.value > 0.002f } }

    fun settle() {
        settleJob[0]?.cancel()
        val fromX = dragX
        val fromY = dragY
        settleJob[0] = scope.launch {
            animate(1f, 0f, animationSpec = islandSpring()) { f, _ -> dragX = fromX * f; dragY = fromY * f }
        }
    }

    // ABSOLUTE ROOT of the island. All gestures live on this node, so a swipe that starts on the compact pill
    // behaves exactly like one that starts on the expanded panel.
    Box(
        modifier.then(if (centerInWindow) Modifier.fillMaxSize() else Modifier),
        contentAlignment = Alignment.TopCenter,
    ) {
    Box(
        Modifier
            // Appear/disappear scale, explicitly about the dead centre of the island (the camera punch-hole).
            .graphicsLayer {
                val v = vis.value.coerceIn(0f, 1f)
                val sc = 0.6f + 0.4f * v
                scaleX = sc; scaleY = sc; alpha = v
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            }
            // Gestures on the root: tap = expand, vertical swipe down = next activity, swipe up = collapse/dismiss,
            // horizontal swipe = previous/next track.
            .pointerInput(Unit) {
                val threshold = 36.dp.toPx()
                val maxDrag = 60.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    settleJob[0]?.cancel()
                    val slop = viewConfiguration.touchSlop
                    var dx = 0f
                    var dy = 0f
                    var dragging = false
                    var tapCandidate = true
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUpIgnoreConsumed()) {
                            // A plain tap on the compact pill expands it (buttons in the panel consume their own taps).
                            if (tapCandidate && !dragging && !change.isConsumed && !latestState.expanded) {
                                haptic(view, latestSettings, HapticFeedbackConstants.CONTEXT_CLICK)
                                latestActions.expand()
                            }
                            break
                        }
                        if (!dragging && change.isConsumed) { tapCandidate = false; break } // a child (button) owns this gesture
                        dx = change.position.x - down.position.x
                        dy = change.position.y - down.position.y
                        if (!dragging && (abs(dx) > slop || abs(dy) > slop)) { dragging = true; tapCandidate = false }
                        if (dragging) {
                            change.consume()
                            if (latestSettings.swipeToSwitch) {
                                if (abs(dx) > abs(dy)) { dragX = dx.coerceIn(-maxDrag, maxDrag); dragY = 0f }
                                else { dragY = dy.coerceIn(-maxDrag, maxDrag); dragX = 0f }
                            }
                        }
                    }
                    if (dragging) {
                        val s = latestSettings
                        val st = latestState
                        val cur = st.current
                        if (s.swipeToSwitch && abs(dx) > abs(dy) && abs(dx) > threshold) {
                            // Swipe LEFT = next track, swipe RIGHT = previous track.
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
                                // Down: switch activity. Works from the compact pill AND the expanded panel.
                                if (s.swipeToSwitch && st.activities.size > 1) {
                                    switchDir[0] = 1
                                    haptic(view, s, HapticFeedbackConstants.CLOCK_TICK)
                                    latestActions.next()
                                }
                            } else if (st.expanded) latestActions.collapse()
                            else if (s.swipeUpDismiss) latestActions.dismissCurrent()
                        }
                    }
                    settle()
                }
            }
            // Size: compact -> expanded, driven by the spring in the layout phase (no recomposition per frame).
            .layout { measurable, constraints ->
                if (vis.value <= 0.001f) return@layout layout(1, 1) {}
                val p = expand.value.coerceIn(0f, 1f)
                val maxW = constraints.maxWidth
                var w = lerp(compactW.dp.toPx(), expandedWidth.toPx(), p).roundToInt().coerceIn(1, maxW)
                // Keep (window width - pill width) even: the pill then sits on an exact pixel centre while it
                // springs, so the overshoot is perfectly symmetric left/right and never "bounces" off-centre.
                if ((maxW - w) % 2 != 0) w = if (w < maxW) w + 1 else w - 1
                val h = lerp(compactH.dp.toPx(), expandedHeight.value.toPx(), p).roundToInt().coerceAtLeast(1)
                val placeable = measurable.measure(Constraints.fixed(w, h))
                layout(w, h) { placeable.place(0, 0) }
            }
            .onGloballyPositioned { c ->
                val r = c.boundsInWindow()
                onPillBounds(r.left.roundToInt(), r.top.roundToInt(), r.right.roundToInt(), r.bottom.roundToInt())
            }
            .graphicsLayer {
                val p = expand.value.coerceIn(0f, 1f)
                val r = lerp(compactH.dp.toPx() / 2f, EXPANDED_CORNER_DP.dp.toPx(), p)
                shape = RoundedCornerShape(minOf(r, size.height / 2f).coerceAtLeast(0f))
                clip = true
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            }
            .background(Color.Black),
        contentAlignment = Alignment.TopCenter,
    ) {
        if (showCompact) {
            Box(
                Modifier.requiredSize(compactW.dp, compactH.dp).graphicsLayer {
                    alpha = (1f - expand.value / 0.16f).coerceIn(0f, 1f)
                    translationX = dragX * 0.35f
                    translationY = dragY * 0.35f
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                },
            ) {
                ActivitySwitcher(state, { switchDir[0] }) { a -> CompactContent(a, geometry.cutoutWidthDp.dp, state.active, settings) }
            }
        }
        if (showExpanded) {
            Box(
                Modifier.requiredSize(expandedWidth, expandedHeight.value).graphicsLayer {
                    val e = expand.value.coerceIn(0f, 1f)
                    alpha = ((e - 0.10f) / 0.38f).coerceIn(0f, 1f)
                    // Content settles in from slightly smaller, like the reference.
                    val cs = 0.92f + 0.08f * e
                    scaleX = cs; scaleY = cs
                    translationX = dragX * 0.35f
                    translationY = dragY * 0.35f
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                },
            ) {
                ActivitySwitcher(state, { switchDir[0] }) { a -> ExpandedContent(a, state.active, settings, actions, state.expanded, compactH.dp) }
                if (state.activities.size > 1) PageDots(state)
            }
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
            (fadeIn(tween(220, delayMillis = 60)) + slideInVertically(islandSpring()) { -it / 2 * d }) togetherWith
                (fadeOut(tween(110)) + slideOutVertically(islandSpring()) { it / 2 * d })
        },
        label = "activitySwitch",
    ) { k ->
        val activity = state.activities.firstOrNull { it.kind == k } ?: lastKnown[k]
        if (activity != null) content(activity)
    }
}

/** Vertical page dots on the right edge (the switch gesture is vertical), clear of the slim panel's content. */
@Composable
private fun BoxScope.PageDots(state: IslandUiState) {
    Column(Modifier.align(Alignment.CenterEnd).padding(end = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        state.activities.forEachIndexed { i, _ ->
            Box(Modifier.size(4.dp).background(if (i == state.currentIndex) Color.White else Color(0x4DFFFFFF), CircleShape))
        }
    }
}
