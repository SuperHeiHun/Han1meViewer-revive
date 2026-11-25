package com.yenaly.han1meviewer

import com.yenaly.han1meviewer.ui.view.video.CenterFullscreenGesture
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CenterFullscreenGestureTest {
    private val gesture = CenterFullscreenGesture(touchSlop = 8f, minimumDistance = 48f)

    private fun down(x: Float = 150f, enabled: Boolean = true) {
        gesture.onDown(x, 200f, width = 300, enabled = enabled)
    }

    @Test
    fun centerUpwardSwipeEntersFullscreenOnRelease() {
        down()
        assertTrue(gesture.onMove(152f, 180f))
        assertTrue(gesture.isTracking)
        assertTrue(gesture.onUp(155f, 120f))
        assertFalse(gesture.isTracking)
    }

    @Test
    fun smallMovementsWaitWithoutBecomingFullscreenGestures() {
        down()
        assertTrue(gesture.onMove(152f, 197f))
        assertFalse(gesture.isTracking)
        assertFalse(gesture.onUp(152f, 197f))
    }

    @Test
    fun shortUpwardSwipeDoesNotEnterFullscreen() {
        down()
        assertTrue(gesture.onMove(150f, 180f))
        assertFalse(gesture.onUp(150f, 165f))
    }

    @Test
    fun leftSideIsReservedForExistingGestures() {
        down(x = 50f)
        assertFalse(gesture.onMove(50f, 100f))
        assertFalse(gesture.onUp(150f, 80f))
    }

    @Test
    fun rightSideIsReservedForExistingGestures() {
        down(x = 250f)
        assertFalse(gesture.onMove(250f, 100f))
        assertFalse(gesture.onUp(150f, 80f))
    }

    @Test
    fun horizontalSwipeIsPassedThroughAndCannotTurnIntoFullscreen() {
        down()
        assertFalse(gesture.onMove(180f, 198f))
        assertFalse(gesture.onMove(180f, 100f))
        assertFalse(gesture.onUp(180f, 80f))
    }

    @Test
    fun downwardSwipeIsPassedThroughAndCannotTurnIntoFullscreen() {
        down()
        assertFalse(gesture.onMove(150f, 220f))
        assertFalse(gesture.onUp(150f, 100f))
    }

    @Test
    fun diagonalSwipeDoesNotEnterFullscreen() {
        down()
        assertFalse(gesture.onMove(180f, 170f))
        assertFalse(gesture.onUp(230f, 120f))
    }

    @Test
    fun reversingTheSwipeBeforeReleaseDoesNotEnterFullscreen() {
        down()
        assertTrue(gesture.onMove(150f, 100f))
        assertFalse(gesture.onUp(150f, 190f))
    }

    @Test
    fun cancellationOrAnotherPointerPreventsFullscreen() {
        down()
        gesture.onMove(150f, 100f)
        gesture.cancel()
        assertFalse(gesture.onMove(150f, 80f))
        assertFalse(gesture.onUp(150f, 60f))
    }

    @Test
    fun disabledGesturesDoNotInterceptExistingControls() {
        down(enabled = false)
        assertFalse(gesture.onMove(150f, 100f))
        assertFalse(gesture.onUp(150f, 60f))
    }

    @Test
    fun newTouchResetsThePreviousDirection() {
        down()
        gesture.onMove(200f, 200f)
        down()
        gesture.onMove(150f, 100f)
        assertTrue(gesture.onUp(150f, 80f))
    }

    @Test
    fun releaseDoesNotTriggerFullscreenTwice() {
        down()
        assertTrue(gesture.onUp(150f, 100f))
        assertFalse(gesture.onUp(150f, 80f))
    }

    @Test
    fun unmeasuredSurfaceDoesNotStartGesture() {
        gesture.onDown(0f, 200f, width = 0, enabled = true)
        assertFalse(gesture.onMove(0f, 100f))
        assertFalse(gesture.onUp(0f, 80f))
    }
}
