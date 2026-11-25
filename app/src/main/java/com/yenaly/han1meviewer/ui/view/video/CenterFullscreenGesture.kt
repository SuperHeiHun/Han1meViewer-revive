package com.yenaly.han1meviewer.ui.view.video

import kotlin.math.abs

/** Reserves upward swipes in the middle third without stealing horizontal seeking. */
internal class CenterFullscreenGesture(
    private val touchSlop: Float,
    private val minimumDistance: Float,
) {
    private enum class Direction { NONE, PENDING, UP, OTHER }

    private var direction = Direction.NONE
    private var downX = 0f
    private var downY = 0f

    val isTracking: Boolean get() = direction == Direction.UP

    fun onDown(x: Float, y: Float, width: Int, enabled: Boolean) {
        downX = x
        downY = y
        direction = if (enabled && width > 0 && x in width / 3f..width * 2f / 3f) {
            Direction.PENDING
        } else {
            Direction.NONE
        }
    }

    /** Returns true while the default brightness/volume/seek gestures should wait. */
    fun onMove(x: Float, y: Float): Boolean {
        if (direction == Direction.PENDING) {
            val dx = abs(x - downX)
            val dy = y - downY
            if (maxOf(dx, abs(dy)) > touchSlop) {
                direction = if (-dy > dx * 1.5f) Direction.UP else Direction.OTHER
            }
        }
        return direction == Direction.PENDING || direction == Direction.UP
    }

    fun onUp(x: Float, y: Float): Boolean {
        onMove(x, y)
        val enterFullscreen = isTracking && downY - y >= minimumDistance &&
                downY - y > abs(x - downX) * 1.5f
        cancel()
        return enterFullscreen
    }

    fun cancel() {
        direction = Direction.NONE
    }
}
