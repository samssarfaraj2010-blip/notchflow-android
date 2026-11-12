package com.dynamicisland.overlay

import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/** Gives a ComposeView hosted in a Service window the lifecycle owners Compose requires. */
class OverlayOwner : SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

    init { controller.performRestore(null) }

    fun resume() { registry.currentState = Lifecycle.State.RESUMED }
    fun destroy() { registry.currentState = Lifecycle.State.DESTROYED }
}

/** Root view of the overlay window. Reports touches outside the island (FLAG_WATCH_OUTSIDE_TOUCH). */
class IslandHostView(
    context: Context,
    owner: SavedStateRegistryOwner,
    private val onOutsideTouch: () -> Unit,
) : FrameLayout(context) {
    private val compose = ComposeView(context)

    init {
        setViewTreeLifecycleOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        // MATCH_PARENT on purpose: the pill is centred INSIDE Compose (same layout pass as its size), so it can never
        // lag a frame behind a WRAP_CONTENT ComposeView that Android re-centres one traversal later.
        addView(compose, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.TOP))
    }

    // Pill bounds in window coordinates, reported by Compose every layout. All zero until the first report.
    private var pillL = 0
    private var pillT = 0
    private var pillR = 0
    private var pillB = 0

    fun setPillBounds(l: Int, t: Int, r: Int, b: Int) { pillL = l; pillT = t; pillR = r; pillB = b }

    fun setContent(content: @Composable () -> Unit) = compose.setContent(content)

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            onOutsideTouch()
            return true
        }
        // While expanded the window spans the full screen width. A touch on its transparent margins is a touch
        // "outside the island" as far as the user is concerned.
        if (ev.actionMasked == MotionEvent.ACTION_DOWN &&
            pillR > pillL &&
            (ev.x < pillL || ev.x > pillR || ev.y < pillT || ev.y > pillB)
        ) {
            onOutsideTouch()
            return true
        }
        return super.dispatchTouchEvent(ev)
    }
}
