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
        addView(compose, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
    }

    fun setContent(content: @Composable () -> Unit) = compose.setContent(content)

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            onOutsideTouch()
            return true
        }
        // While expanded the window spans the full screen width. A touch on its transparent margins is a touch
        // "outside the island" as far as the user is concerned.
        if (ev.actionMasked == MotionEvent.ACTION_DOWN &&
            (ev.x < compose.left || ev.x > compose.right || ev.y < compose.top || ev.y > compose.bottom)
        ) {
            onOutsideTouch()
            return true
        }
        return super.dispatchTouchEvent(ev)
    }
}
