package com.yenaly.han1meviewer

import com.yenaly.han1meviewer.ui.view.video.PlayerPanelBounds
import com.yenaly.han1meviewer.ui.view.video.playerSettingsPanelBounds
import com.yenaly.han1meviewer.ui.view.video.playbackPlaylistDockWidth
import org.junit.Assert.*
import org.junit.Test

class PlayerSettingsPanelLayoutTest {
    @Test fun inlinePortraitSettingsMatchTheDetailsViewportExactly() {
        val details = PlayerPanelBounds(0, 340, 400, 440)
        assertEquals(details, playerSettingsPanelBounds(400, 800, 360, details))
    }

    @Test fun inlineBoundsRespectWindowOffsetsAndNavigationSpace() {
        val details = PlayerPanelBounds(12, 330, 376, 440)
        assertEquals(details, playerSettingsPanelBounds(400, 800, 360, details))
    }

    @Test fun outgrowingDetailsAreClampedToTheCurrentWindow() {
        assertEquals(PlayerPanelBounds(12, 330, 388, 470),
            playerSettingsPanelBounds(400, 800, 360, PlayerPanelBounds(12, 330, 500, 900)))
    }

    @Test fun landscapeAlwaysUsesTheRightSideRatherThanTheHiddenDetails() {
        assertEquals(PlayerPanelBounds(840, 0, 360, 700),
            playerSettingsPanelBounds(1200, 700, 360, PlayerPanelBounds(0, 340, 400, 440)))
    }

    @Test fun portraitFullscreenWithoutVisibleDetailsUsesASolidRightPanel() {
        assertEquals(PlayerPanelBounds(40, 0, 360, 800), playerSettingsPanelBounds(400, 800, 360))
    }

    @Test fun missingOrStaleDetailsDoNotProduceOffscreenPopups() {
        assertEquals(PlayerPanelBounds(40, 0, 360, 800),
            playerSettingsPanelBounds(400, 800, 360, PlayerPanelBounds(0, 900, 400, 100)))
        assertNull(playerSettingsPanelBounds(0, 800, 360))
        assertNull(playerSettingsPanelBounds(400, 0, 360))
    }

    @Test fun expandedPlaylistLeavesTwoThirdsOfTheLandscapeViewport() {
        assertEquals(300, playbackPlaylistDockWidth(900, 600, 320))
        assertEquals(320, playbackPlaylistDockWidth(1800, 900, 320))
    }

    @Test fun portraitSquareAndUnmeasuredWindowsCannotExpandThePlaylist() {
        assertNull(playbackPlaylistDockWidth(400, 800, 320))
        assertNull(playbackPlaylistDockWidth(800, 800, 320))
        assertNull(playbackPlaylistDockWidth(800, 0, 320))
    }

    @Test fun resizingUsesCurrentWindowDimensionsAndNeverAPhysicalDisplaySize() {
        assertEquals(200, playbackPlaylistDockWidth(600, 400, 320))
        assertEquals(320, playbackPlaylistDockWidth(1200, 700, 320))
        assertEquals(PlayerPanelBounds(300, 0, 300, 400), playerSettingsPanelBounds(600, 400, 360))
    }
}
