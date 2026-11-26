package com.yenaly.han1meviewer

import com.yenaly.han1meviewer.ui.view.video.PlayerGestureConfig
import com.yenaly.han1meviewer.ui.view.video.PlayerGestureSession
import com.yenaly.han1meviewer.ui.view.video.PlayerGestureSession.State
import org.junit.Assert.*
import org.junit.Test

class PlayerGestureSessionTest {
    private val session = PlayerGestureSession(touchSlop = 8f, minimumFullscreenDistance = 48f)

    private fun down(
        x: Float = 150f, config: PlayerGestureConfig = PlayerGestureConfig(),
        canSeek: Boolean = true, canAdjustLevels: Boolean = true, fullscreen: Boolean = true,
    ) = session.down(x, 200f, 300, config, canSeek, canAdjustLevels, fullscreen)

    @Test fun touchSlopPreservesTapsAndLongPressCandidates() {
        down()
        assertEquals(State.PENDING, session.move(154f, 197f))
        assertFalse(session.hasMoved)
        assertTrue(session.longPress())
    }

    @Test fun ambiguousDiagonalWaitsForAClearDirection() {
        down()
        assertEquals(State.PENDING, session.move(170f, 180f))
        assertTrue(session.hasMoved)
        assertFalse(session.longPress())
        assertEquals(State.FULLSCREEN, session.move(172f, 100f))
    }

    @Test fun centerUpwardSwipeTakesPriorityOverVolumeAndBrightness() {
        down()
        assertEquals(State.FULLSCREEN, session.move(151f, 170f))
        assertTrue(session.entersFullscreen(151f, 100f))
    }

    @Test fun shortCenterSwipeDoesNotFallThroughToAnotherGesture() {
        down()
        assertEquals(State.FULLSCREEN, session.move(151f, 175f))
        assertFalse(session.entersFullscreen(151f, 170f))
        assertEquals(State.FULLSCREEN, session.state)
    }

    @Test fun horizontalCenterSwipeSeeksAndKeepsItsDirectionLock() {
        down()
        assertEquals(State.SEEK, session.move(180f, 199f))
        assertEquals(State.SEEK, session.move(180f, 50f))
        assertFalse(session.entersFullscreen(180f, 50f))
    }

    @Test fun verticalSidesUseLocalPlayerHalves() {
        down(x = 40f)
        assertEquals(State.BRIGHTNESS, session.move(41f, 150f))
        down(x = 260f)
        assertEquals(State.VOLUME, session.move(261f, 150f))
    }

    @Test fun centerVerticalSwipeInFullscreenAdjustsLevelsInsteadOfEnteringAgain() {
        down(fullscreen = false)
        assertEquals(State.VOLUME, session.move(151f, 100f))
        assertFalse(session.entersFullscreen(151f, 100f))
    }

    @Test fun disabledHorizontalSeekDoesNotTurnIntoVolumeOrFullscreen() {
        down(config = PlayerGestureConfig(seekEnabled = false))
        assertEquals(State.IGNORED, session.move(200f, 199f))
        assertEquals(State.IGNORED, session.move(200f, 80f))
    }

    @Test fun disabledLevelGesturesStayIndependent() {
        val config = PlayerGestureConfig(brightnessEnabled = false, volumeEnabled = true)
        down(x = 40f, config = config)
        assertEquals(State.IGNORED, session.move(40f, 100f))
        down(x = 260f, config = config)
        assertEquals(State.VOLUME, session.move(260f, 100f))
    }

    @Test fun unavailableMediaDisablesSeekButNotCenterFullscreen() {
        down(canSeek = false, canAdjustLevels = false)
        assertEquals(State.FULLSCREEN, session.move(150f, 100f))
        down(canSeek = false)
        assertEquals(State.IGNORED, session.move(220f, 200f))
    }

    @Test fun longPressCannotBecomeSeekOrFullscreen() {
        down()
        assertTrue(session.longPress())
        assertEquals(State.LONG_PRESS, session.move(250f, 50f))
        assertFalse(session.entersFullscreen(250f, 50f))
    }

    @Test fun resetDiscardsAnUncommittedGesture() {
        down()
        session.move(150f, 100f)
        session.reset()
        assertEquals(State.IDLE, session.state)
        assertFalse(session.entersFullscreen(150f, 50f))
        assertFalse(session.longPress())
    }

    @Test fun portraitDownwardSwipeTakesPriorityOverLevelControls() {
        session.down(150f, 100f, 300, PlayerGestureConfig(), true, true, true, true)
        assertEquals(State.FULLSCREEN, session.move(151f, 150f))
        assertTrue(session.isPortraitFullscreen)
        assertTrue(session.entersFullscreen(151f, 180f))
    }

    @Test fun downwardSwipeWithoutPortraitPermissionKeepsTheOldLevelGesture() {
        down()
        assertEquals(State.VOLUME, session.move(151f, 280f))
        assertFalse(session.isPortraitFullscreen)
        assertFalse(session.entersFullscreen(151f, 300f))
    }

    @Test fun shortPortraitSwipeDoesNotFallThroughToLevelControls() {
        session.down(150f, 100f, 300, PlayerGestureConfig(), true, true, true, true)
        assertEquals(State.FULLSCREEN, session.move(150f, 120f))
        assertFalse(session.entersFullscreen(150f, 130f))
    }

    @Test fun resetDiscardsPortraitDirection() {
        session.down(150f, 100f, 300, PlayerGestureConfig(), true, true, true, true)
        session.move(150f, 180f)
        session.reset()
        assertFalse(session.isPortraitFullscreen)
        assertFalse(session.entersFullscreen(150f, 220f))
    }
}
