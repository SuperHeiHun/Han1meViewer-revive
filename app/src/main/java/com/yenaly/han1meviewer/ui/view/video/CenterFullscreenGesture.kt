package com.yenaly.han1meviewer.ui.view.video

import kotlin.math.abs

/** Reserves center fullscreen swipes without stealing horizontal seeking or side level controls. */
internal class CenterFullscreenGesture(
    private val touchSlop: Float,
    private val minimumDistance: Float,
) {
    private enum class Direction { NONE, PENDING, UP, DOWN, OTHER }

    private var direction = Direction.NONE
    private var downX = 0f
    private var downY = 0f
    private var allowDownward = false

    val isTracking: Boolean get() = direction == Direction.UP || direction == Direction.DOWN
    val isDownward: Boolean get() = direction == Direction.DOWN

    fun onDown(x: Float, y: Float, width: Int, enabled: Boolean, allowDownward: Boolean = false) {
        downX = x
        downY = y
        this.allowDownward = allowDownward
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
                direction = when {
                    -dy > dx * 1.5f -> Direction.UP
                    allowDownward && dy > dx * 1.5f -> Direction.DOWN
                    else -> Direction.OTHER
                }
            }
        }
        return direction == Direction.PENDING || isTracking
    }

    fun onUp(x: Float, y: Float): Boolean {
        onMove(x, y)
        val distance = if (isDownward) y - downY else downY - y
        val enterFullscreen = isTracking && distance >= minimumDistance &&
                distance > abs(x - downX) * 1.5f
        cancel()
        return enterFullscreen
    }

    fun cancel() {
        direction = Direction.NONE
    }
}
