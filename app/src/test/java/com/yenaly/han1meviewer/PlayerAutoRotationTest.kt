package com.yenaly.han1meviewer

import com.yenaly.han1meviewer.logic.player.PlayerAutoRotation
import org.junit.Assert.*
import org.junit.Test

class PlayerAutoRotationTest {
    private val rotation = PlayerAutoRotation()

    @Test fun normalPlaybackNeverRequestsLandscapeOrPortrait() {
        assertNull(rotation.directionFor(Any(), false, 1920, 1080))
        assertNull(rotation.directionFor(Any(), false, 1080, 1920))
    }

    @Test fun switchingFromLandscapeToPortraitKeepsFullscreenAndRequestsPortrait() {
        assertEquals(true, rotation.directionFor(Any(), true, 1920, 1080))
        assertEquals(false, rotation.directionFor(Any(), true, 1080, 1920))
    }

    @Test fun switchingFromPortraitToLandscapeRequestsLandscape() {
        assertEquals(false, rotation.directionFor(Any(), true, 1080, 1920))
        assertEquals(true, rotation.directionFor(Any(), true, 1920, 1080))
    }

    @Test fun repeatedDimensionsAndForegroundResumeDoNotOverrideManualOrientation() {
        val session = Any()
        assertEquals(true, rotation.directionFor(session, true, 1920, 1080))
        assertNull(rotation.directionFor(session, true, 1920, 1080))
        assertNull(rotation.directionFor(session, true, 1280, 720))
    }

    @Test fun enteringFullscreenAfterNormalPlaybackDoesNotOverrideTheChosenDirection() {
        val session = Any()
        assertNull(rotation.directionFor(session, false, 1920, 1080))
        assertNull(rotation.directionFor(session, true, 1920, 1080))
    }

    @Test fun nextSessionWithTheSameDimensionsStillRequestsItsDirection() {
        assertEquals(true, rotation.directionFor(Any(), true, 1920, 1080))
        assertEquals(true, rotation.directionFor(Any(), true, 1920, 1080))
    }

    @Test fun unknownAndSquareDimensionsWaitForRealDimensions() {
        val session = Any()
        assertNull(rotation.directionFor(session, true, 0, 1080))
        assertNull(rotation.directionFor(session, true, 1080, -1))
        assertNull(rotation.directionFor(session, true, 1080, 1080))
        assertEquals(false, rotation.directionFor(session, true, 1080, 1920))
    }

    @Test fun dimensionOrientationChangesWithinTheSameSessionAreObserved() {
        val session = Any()
        assertEquals(false, rotation.directionFor(session, true, 1080, 1920))
        assertEquals(true, rotation.directionFor(session, true, 1920, 1080))
    }

    @Test fun togglingTheSettingAllowsApplyingTheCurrentFullscreenDirectionAgain() {
        val session = Any()
        assertEquals(true, rotation.directionFor(session, true, 1920, 1080))
        rotation.reset()
        assertEquals(true, rotation.directionFor(session, true, 1920, 1080))
    }
}
