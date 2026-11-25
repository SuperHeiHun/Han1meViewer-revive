package com.yenaly.han1meviewer

import com.yenaly.han1meviewer.ui.view.video.PlayerDoubleTapAction
import com.yenaly.han1meviewer.ui.view.video.PlayerDoubleTapMode
import com.yenaly.han1meviewer.ui.view.video.PlayerGestureConfig
import com.yenaly.han1meviewer.ui.view.video.PlayerGestureMath
import com.yenaly.han1meviewer.ui.view.video.PlayerLongPressMode
import org.junit.Assert.*
import org.junit.Test

class PlayerGestureMathTest {
    @Test fun missingOrUnknownModesKeepLegacyDefaults() {
        for (value in listOf(null, "", "future")) {
            assertEquals(PlayerDoubleTapMode.PLAY_PAUSE, PlayerDoubleTapMode.fromValue(value))
            assertEquals(PlayerLongPressMode.LEGACY, PlayerLongPressMode.fromValue(value))
        }
    }

    @Test fun defaultDoubleTapPlaysOrPausesInEveryRegion() {
        for (x in listOf(0f, 200f, 500f, 800f, 1_000f)) {
            assertEquals(PlayerDoubleTapAction.PLAY_PAUSE,
                PlayerGestureMath.doubleTap(PlayerDoubleTapMode.PLAY_PAUSE, x, 1_000))
        }
    }

    @Test fun enhancedModeUsesThirtyFiveThirtyThirtyFiveRegions() {
        val mode = PlayerDoubleTapMode.BOTH
        assertEquals(PlayerDoubleTapAction.BACKWARD, PlayerGestureMath.doubleTap(mode, 349f, 1_000))
        assertEquals(PlayerDoubleTapAction.PLAY_PAUSE, PlayerGestureMath.doubleTap(mode, 350f, 1_000))
        assertEquals(PlayerDoubleTapAction.PLAY_PAUSE, PlayerGestureMath.doubleTap(mode, 650f, 1_000))
        assertEquals(PlayerDoubleTapAction.FORWARD, PlayerGestureMath.doubleTap(mode, 651f, 1_000))
    }

    @Test fun seekOnlyModeUsesTwoHalves() {
        assertEquals(PlayerDoubleTapAction.BACKWARD,
            PlayerGestureMath.doubleTap(PlayerDoubleTapMode.SEEK, 149f, 300))
        assertEquals(PlayerDoubleTapAction.FORWARD,
            PlayerGestureMath.doubleTap(PlayerDoubleTapMode.SEEK, 150f, 300))
    }

    @Test fun disabledAndInvalidDoubleTapsDoNothing() {
        assertEquals(PlayerDoubleTapAction.NONE, PlayerGestureMath.doubleTap(PlayerDoubleTapMode.NONE, 50f, 300))
        assertEquals(PlayerDoubleTapAction.NONE, PlayerGestureMath.doubleTap(PlayerDoubleTapMode.BOTH, 50f, 0))
        assertEquals(PlayerDoubleTapAction.NONE, PlayerGestureMath.doubleTap(PlayerDoubleTapMode.BOTH, Float.NaN, 300))
    }

    @Test fun existingSeekSensitivityMappingIsPreserved() {
        assertEquals(listOf(1, 2, 3, 4, 5, 7, 10, 20, 40), (1..9).map(PlayerGestureMath::sensitivity))
        assertEquals(1, PlayerGestureMath.sensitivity(-1))
        assertEquals(40, PlayerGestureMath.sensitivity(10))
    }

    @Test fun progressUsesPlayerWidthAndClampsToDuration() {
        assertEquals(52_000L, PlayerGestureMath.seekPosition(50_000, 100f, 1_000, 100_000, 5))
        assertEquals(0L, PlayerGestureMath.seekPosition(100, -1_000f, 1_000, 100_000, 5))
        assertEquals(100_000L, PlayerGestureMath.seekPosition(99_900, 1_000f, 1_000, 100_000, 5))
    }

    @Test fun unknownDurationAndInvalidCoordinatesDisableOnlySeeking() {
        assertNull(PlayerGestureMath.seekPosition(10, 100f, 300, 0, 5))
        assertNull(PlayerGestureMath.seekPosition(10, 100f, 0, 300, 5))
        assertNull(PlayerGestureMath.seekPosition(10, Float.POSITIVE_INFINITY, 300, 300, 5))
        assertNull(PlayerGestureMath.seekBy(10, 100, -1))
        assertEquals(PlayerDoubleTapAction.PLAY_PAUSE,
            PlayerGestureMath.doubleTap(PlayerDoubleTapMode.PLAY_PAUSE, 150f, 300))
    }

    @Test fun seekStepsClampWithoutLongOverflow() {
        assertEquals(Long.MAX_VALUE, PlayerGestureMath.seekBy(Long.MAX_VALUE - 5, 100, Long.MAX_VALUE))
        assertEquals(0L, PlayerGestureMath.seekBy(5, Long.MIN_VALUE, 100))
        assertEquals(0L, PlayerGestureMath.seekBy(-50, -100, 100))
        assertEquals(100L, PlayerGestureMath.seekBy(500, 100, 100))
    }

    @Test fun levelsRespectTheirOwnHeightAndSensitivity() {
        assertEquals(0.8f, PlayerGestureMath.level(0.5f, -100f, 1_000, 5), 0.0001f)
        assertEquals(0.2f, PlayerGestureMath.level(0.5f, 100f, 1_000, 5), 0.0001f)
        assertTrue(PlayerGestureMath.level(0.5f, -100f, 1_000, 1) >
                PlayerGestureMath.level(0.5f, -100f, 1_000, 9))
    }

    @Test fun levelChangesStayWithinZeroAndOne() {
        assertEquals(0f, PlayerGestureMath.level(0.5f, 10_000f, 300, 5), 0f)
        assertEquals(1f, PlayerGestureMath.level(0.5f, -10_000f, 300, 5), 0f)
        assertEquals(0.5f, PlayerGestureMath.level(0.5f, 10f, 0, 5), 0f)
    }

    @Test fun legacyLongPressMultipliesInsteadOfReinterpretingTheOldSetting() {
        val config = PlayerGestureConfig(legacySpeedMultiplier = 2.5f)
        assertEquals(3.125f, PlayerGestureMath.temporarySpeed(1.25f, config), 0f)
    }

    @Test fun newLongPressModesUseAnAbsoluteTemporarySpeed() {
        for (mode in listOf(PlayerLongPressMode.FIXED, PlayerLongPressMode.VARIABLE)) {
            assertEquals(2f, PlayerGestureMath.temporarySpeed(3f,
                PlayerGestureConfig(longPressMode = mode, temporarySpeed = 2f)), 0f)
        }
    }

    @Test fun variableSpeedUsesDensityAndOneTenthSteps() {
        assertEquals(2.1f, PlayerGestureMath.variableSpeed(2f, 48f, 1f), 0.0001f)
        assertEquals(2.1f, PlayerGestureMath.variableSpeed(2f, 96f, 2f), 0.0001f)
        assertEquals(1.8f, PlayerGestureMath.variableSpeed(2f, -96f, 1f), 0.0001f)
    }

    @Test fun newSpeedsAreBoundedAndInvalidMotionKeepsTheBase() {
        assertEquals(4f, PlayerGestureMath.variableSpeed(2f, Float.MAX_VALUE, 1f), 0f)
        assertEquals(0.5f, PlayerGestureMath.variableSpeed(2f, -Float.MAX_VALUE, 1f), 0f)
        assertEquals(2f, PlayerGestureMath.variableSpeed(2f, Float.NaN, 1f), 0f)
        assertEquals(2f, PlayerGestureMath.variableSpeed(2f, 100f, 0f), 0f)
    }
}
