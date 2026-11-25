package com.yenaly.han1meviewer

import com.yenaly.han1meviewer.logic.backup.BackupSettingRules
import com.yenaly.han1meviewer.logic.player.PlaybackSettings
import com.yenaly.han1meviewer.logic.player.PlayerAutoFullscreen
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class PlayerAutoFullscreenTest {
    private val gate = PlayerAutoFullscreen()
    private val media = Any()

    @Test fun automaticFullscreenIsDisabledByDefault() {
        assertFalse(PlaybackSettings().autoFullscreen)
        assertFalse(gate.shouldEnter(media, enabled = false, eligible = true, alreadyFullscreen = false))
    }

    @Test fun activePlaybackCanEnterOnce() {
        assertTrue(gate.shouldEnter(media, true, true, false))
        assertFalse(gate.shouldEnter(media, true, true, false))
    }

    @Test fun loadingOrBackgroundCallbacksDoNotConsumeTheSession() {
        assertFalse(gate.shouldEnter(media, true, false, false))
        assertTrue(gate.shouldEnter(media, true, true, false))
    }

    @Test fun missingMediaCannotRequestFullscreen() {
        assertFalse(gate.shouldEnter(null, true, true, false))
        assertTrue(gate.shouldEnter(media, true, true, false))
    }

    @Test fun bufferingResumeAndReplayDoNotUndoAManualExit() {
        assertTrue(gate.shouldEnter(media, true, true, false))
        assertFalse(gate.shouldEnter(media, true, false, false))
        repeat(4) { assertFalse(gate.shouldEnter(media, true, true, false)) }
    }

    @Test fun alreadyFullscreenSessionsAreConsumedWithoutAnotherTransition() {
        assertFalse(gate.shouldEnter(media, true, true, true))
        assertFalse(gate.shouldEnter(media, true, true, false))
    }

    @Test fun aNewVideoOrQualityWithANewMediaSessionCanEnter() {
        assertTrue(gate.shouldEnter(media, true, true, false))
        assertTrue(gate.shouldEnter(Any(), true, true, false))
    }

    @Test fun disablingAndEnablingRearmsOnlyAfterPlaybackIsEligible() {
        assertTrue(gate.shouldEnter(media, true, true, false))
        assertFalse(gate.shouldEnter(media, false, true, false))
        assertFalse(gate.shouldEnter(media, true, false, false))
        assertTrue(gate.shouldEnter(media, true, true, false))
    }

    @Test fun loadingANewSourceClearsTheOldSession() {
        assertTrue(gate.shouldEnter(media, true, true, false))
        gate.reset()
        assertTrue(gate.shouldEnter(media, true, true, false))
    }

    @Test fun autoRotateAndAutomaticFullscreenAreIndependentSettings() {
        val settings = PlaybackSettings(autoFullscreen = true)
        assertTrue(settings.autoFullscreen)
        assertFalse(settings.autoRotate)
        assertFalse(PlaybackSettings(autoRotate = true).autoFullscreen)
    }

    @Test fun portableBackupAcceptsOnlyBooleansAndDefaultsToFalse() {
        assertEquals(JsonPrimitive(false), BackupSettingRules.defaults["auto_fullscreen"])
        assertTrue(BackupSettingRules.isValid("auto_fullscreen", JsonPrimitive(true)))
        assertTrue(BackupSettingRules.isValid("auto_fullscreen", JsonPrimitive(false)))
        assertFalse(BackupSettingRules.isValid("auto_fullscreen", JsonPrimitive("true")))
        assertFalse(BackupSettingRules.isValid("auto_fullscreen", JsonPrimitive(1)))
    }
}
