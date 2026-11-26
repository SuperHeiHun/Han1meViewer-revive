package com.yenaly.han1meviewer

import com.yenaly.han1meviewer.ui.view.video.PlayerPanelLayout
import com.yenaly.han1meviewer.ui.view.video.playerPanelLayout
import com.yenaly.han1meviewer.ui.view.video.PlayerPanelBounds
import com.yenaly.han1meviewer.ui.view.video.playerSettingsPanelBounds
import org.junit.Assert.*
import org.junit.Test

class PlayerPanelLayoutTest {
    @Test fun portraitPlaylistUsesTheFullWidthAndBottomHalf() {
        assertEquals(PlayerPanelLayout(1080, 960, true), playerPanelLayout(1080, 1920, 360))
    }

    @Test fun landscapePlaylistKeepsTheExistingSidePanel() {
        assertEquals(PlayerPanelLayout(360, 1080, false), playerPanelLayout(1920, 1080, 360))
    }

    @Test fun sidePanelDoesNotExceedHalfTheWindow() {
        assertEquals(PlayerPanelLayout(250, 300, false), playerPanelLayout(500, 300, 360))
    }

    @Test fun fullscreenSettingsUseARightSidePanelInBothDirections() {
        assertEquals(PlayerPanelBounds(720, 0, 360, 1920), playerSettingsPanelBounds(1080, 1920, 360))
        assertEquals(PlayerPanelBounds(1560, 0, 360, 1080), playerSettingsPanelBounds(1920, 1080, 360))
    }

    @Test fun windowHeightRatherThanInlineVideoHeightDeterminesTheSettingsPanel() {
        assertEquals(1920, playerSettingsPanelBounds(1080, 1920, 360)?.height)
        assertNotEquals(1920, playerSettingsPanelBounds(1080, 608, 360)?.height)
    }

    @Test fun squareWindowsUseTheBottomLayout() {
        assertEquals(PlayerPanelLayout(900, 450, true), playerPanelLayout(900, 900, 360))
    }

    @Test fun oddHeightsRoundDownWithoutExceedingHalfTheWindow() {
        assertEquals(PlayerPanelLayout(411, 445, true), playerPanelLayout(411, 891, 360))
    }

    @Test fun resizingRecalculatesInsteadOfKeepingTheOldOrientationOrHeight() {
        repeat(3) {
            assertEquals(PlayerPanelLayout(400, 450, true), playerPanelLayout(400, 900, 360))
            assertEquals(PlayerPanelLayout(360, 400, false), playerPanelLayout(900, 400, 360))
        }
    }

    @Test fun unmeasuredWindowsDoNotProducePopups() {
        assertNull(playerPanelLayout(0, 900, 360))
        assertNull(playerPanelLayout(400, 0, 360))
        assertNull(playerPanelLayout(-1, 900, 360))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidSideWidthIsRejected() {
        playerPanelLayout(400, 900, 0)
    }
}
