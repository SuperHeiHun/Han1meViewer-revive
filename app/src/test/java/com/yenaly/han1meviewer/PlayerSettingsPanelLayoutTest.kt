package com.yenaly.han1meviewer

import com.yenaly.han1meviewer.ui.view.video.PlayerPanelBounds
import com.yenaly.han1meviewer.ui.view.video.playerSettingsPanelBounds
import com.yenaly.han1meviewer.ui.view.video.playerDetailsPanelBounds
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

    @Test fun expandedPortraitPlaylistUsesOnlyTheDetailsViewport() {
        val details = PlayerPanelBounds(12, 330, 376, 440)
        assertEquals(details, playerDetailsPanelBounds(400, 800, details))
    }

    @Test fun landscapeAndMissingDetailsCannotExpandOverTheVideo() {
        assertNull(playerDetailsPanelBounds(800, 400, PlayerPanelBounds(0, 200, 800, 200)))
        assertNull(playerDetailsPanelBounds(400, 800, null))
        assertNull(playerDetailsPanelBounds(0, 800, PlayerPanelBounds(0, 330, 400, 440)))
    }

    @Test fun invalidDetailsCannotFallBackToCoveringTheVideo() {
        for (details in listOf(PlayerPanelBounds(-1, 330, 400, 440), PlayerPanelBounds(0, -1, 400, 440),
            PlayerPanelBounds(0, 800, 400, 440), PlayerPanelBounds(400, 330, 400, 440),
            PlayerPanelBounds(0, 330, 0, 440), PlayerPanelBounds(0, 330, 400, 0))) {
            assertNull(playerDetailsPanelBounds(400, 800, details))
        }
    }

    @Test fun expandedDetailsClipAtTheWindowWithoutMovingTheirTopEdge() {
        assertEquals(PlayerPanelBounds(12, 330, 388, 470),
            playerDetailsPanelBounds(400, 800, PlayerPanelBounds(12, 330, 500, 900)))
    }
}
