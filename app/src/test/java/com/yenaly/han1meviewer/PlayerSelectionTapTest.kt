package com.yenaly.han1meviewer

import com.yenaly.han1meviewer.ui.view.video.PlayerSelectionTap
import org.junit.Assert.*
import org.junit.Test

class PlayerSelectionTapTest {
    private val tap = PlayerSelectionTap(8f)

    @Test fun shortTapIsAcceptedOnce() {
        tap.down(20f, 20f)
        assertTrue(tap.up(22f, 21f, 100, 500))
        assertFalse(tap.up(22f, 21f, 100, 500))
    }

    @Test fun holdingDoesNotBecomeASelection() {
        tap.down(20f, 20f)
        assertFalse(tap.up(20f, 20f, 500, 500))
        tap.down(20f, 20f)
        assertFalse(tap.up(20f, 20f, 2_000, 500))
    }

    @Test fun draggingOutAndBackStillCancels() {
        tap.down(20f, 20f)
        tap.move(20f, 40f)
        tap.move(20f, 20f)
        assertFalse(tap.up(20f, 20f, 200, 500))
    }

    @Test fun horizontalAndDiagonalDragsCancel() {
        for ((x, y) in listOf(40f to 20f, 26f to 26f)) {
            tap.down(20f, 20f)
            assertFalse(tap.up(x, y, 200, 500))
        }
    }

    @Test fun cancellationOrAnotherPointerCannotSelectOnRelease() {
        tap.down(20f, 20f)
        tap.cancel()
        assertFalse(tap.up(20f, 20f, 100, 500))
    }

    @Test fun nextTouchStartsAFreshTap() {
        tap.down(20f, 20f)
        tap.cancel()
        tap.down(30f, 30f)
        assertTrue(tap.up(30f, 30f, 100, 500))
    }

    @Test fun invalidCoordinatesAndTimeAreRejected() {
        tap.down(Float.NaN, 0f)
        assertFalse(tap.up(0f, 0f, 100, 500))
        tap.down(0f, 0f)
        assertFalse(tap.up(Float.POSITIVE_INFINITY, 0f, 100, 500))
        tap.down(0f, 0f)
        assertFalse(tap.up(0f, 0f, -1, 500))
    }
}
