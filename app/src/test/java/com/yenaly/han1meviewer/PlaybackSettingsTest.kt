package com.yenaly.han1meviewer

import com.yenaly.han1meviewer.logic.backup.BackupSettingRules
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.player.*
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class PlaybackSettingsTest {
    private val queue = PlaybackPlaylist("Series", listOf("c", "a", "b").map {
        HanimeInfo(title = it, coverUrl = "", videoCode = it, itemType = HanimeInfo.NORMAL)
    })

    @Test fun defaultsDoNotStartOrRotatePlaybackUnexpectedly() {
        assertEquals(PlaybackMode.PAUSE_AT_END, PlaybackSettings().mode)
        assertEquals(PlaybackAspectRatio.FIT, PlaybackSettings().aspectRatio)
        assertFalse(PlaybackSettings().autoRotate)
    }

    @Test fun listLoopUsesWebsiteOrder() {
        assertEquals("a", completionTarget("c", queue, PlaybackMode.LIST_LOOP))
        assertEquals("b", completionTarget("a", queue, PlaybackMode.LIST_LOOP))
    }

    @Test fun listLoopWrapsToTheFirstItem() {
        assertEquals("c", completionTarget("b", queue, PlaybackMode.LIST_LOOP))
    }

    @Test fun reverseListLoopUsesDisplayOrderAndWrapsAtTheOppositeEnd() {
        assertEquals("a", completionTarget("b", queue, PlaybackMode.LIST_LOOP, reverse = true))
        assertEquals("b", completionTarget("c", queue, PlaybackMode.LIST_LOOP, reverse = true))
        assertEquals(listOf("c", "a", "b"), queue.videos.map { it.videoCode })
    }

    @Test fun repeatOneNeverMovesToTheNextItem() {
        for (reverse in listOf(false, true)) {
            assertEquals("a", completionTarget("a", queue, PlaybackMode.REPEAT_ONE, reverse))
        }
    }

    @Test fun pauseAtEndAppliesToEveryPositionAndStandaloneVideo() {
        for (code in listOf("c", "a", "b")) assertNull(completionTarget(code, queue, PlaybackMode.PAUSE_AT_END))
        assertNull(completionTarget("x", PlaybackPlaylist(), PlaybackMode.PAUSE_AT_END))
    }

    @Test fun standaloneAndSingleItemListsLoopAsOneItem() {
        assertEquals("x", completionTarget("x", PlaybackPlaylist(), PlaybackMode.LIST_LOOP))
        assertEquals("c", completionTarget("c", queue.copy(videos = queue.videos.take(1)), PlaybackMode.LIST_LOOP))
    }

    @Test fun downloadsUseTheSameModesWithoutOpeningAnOnlineQueue() {
        assertEquals("a", completionTarget("a", queue, PlaybackMode.LIST_LOOP, fromDownload = true))
        assertEquals("a", completionTarget("a", queue, PlaybackMode.REPEAT_ONE, fromDownload = true))
        assertNull(completionTarget("a", queue, PlaybackMode.PAUSE_AT_END, fromDownload = true))
    }

    @Test fun missingCurrentItemDoesNotGuessAnUnrelatedVideo() {
        assertNull(completionTarget("x", queue, PlaybackMode.LIST_LOOP))
        assertNull(completionTarget("", queue, PlaybackMode.REPEAT_ONE))
    }

    @Test fun modeChoiceIsNotStoredOnOrMutatedIntoTheQueue() {
        assertNull(completionTarget("a", queue, PlaybackMode.PAUSE_AT_END))
        assertEquals("a", completionTarget("a", queue, PlaybackMode.REPEAT_ONE))
        assertEquals("b", completionTarget("a", queue, PlaybackMode.LIST_LOOP))
        assertEquals(listOf("c", "a", "b"), queue.videos.map { it.videoCode })
    }

    @Test fun invalidEnumValuesFallBackSafely() {
        assertEquals(PlaybackMode.PAUSE_AT_END, PlaybackMode.fromValue("invalid"))
        assertEquals(PlaybackMode.PAUSE_AT_END, PlaybackMode.fromValue(null))
        assertEquals(PlaybackAspectRatio.FIT, PlaybackAspectRatio.fromValue("invalid"))
    }

    @Test fun legacyEnabledAndDisabledMapToGlobalModes() {
        assertEquals(PlaybackMode.LIST_LOOP, PlaybackMode.fromLegacy(true))
        assertEquals(PlaybackMode.PAUSE_AT_END, PlaybackMode.fromLegacy(false))
    }

    @Test fun everyNewEnumValueHasStrictBackupValidation() {
        PlaybackMode.entries.forEach { assertTrue(BackupSettingRules.isValid("playback_mode", JsonPrimitive(it.value))) }
        PlaybackAspectRatio.entries.forEach { assertTrue(BackupSettingRules.isValid("playback_aspect_ratio", JsonPrimitive(it.value))) }
        assertFalse(BackupSettingRules.isValid("playback_mode", JsonPrimitive("random")))
        assertFalse(BackupSettingRules.isValid("playback_mode", JsonPrimitive(true)))
        assertFalse(BackupSettingRules.isValid("playback_aspect_ratio", JsonPrimitive(16)))
        assertFalse(BackupSettingRules.isValid("playback_aspect_ratio", JsonPrimitive("random")))
    }

    @Test fun rotationBackupAcceptsOnlyBooleans() {
        assertEquals(JsonPrimitive(false), BackupSettingRules.defaults["playback_auto_rotate"])
        assertTrue(BackupSettingRules.isValid("playback_auto_rotate", JsonPrimitive(true)))
        assertFalse(BackupSettingRules.isValid("playback_auto_rotate", JsonPrimitive("true")))
    }

    @Test fun oldBackupsConvertTheRetiredSwitchWithoutReexportingIt() {
        val migrated = BackupSettingRules.migratePlaybackSettings(mapOf("auto_play_next" to JsonPrimitive(true)))
        assertEquals(mapOf("playback_mode" to JsonPrimitive("list_loop")), migrated)
        assertFalse("auto_play_next" in BackupSettingRules.defaults)
    }

    @Test fun explicitNewModeWinsOverLegacyBackupValue() {
        val values = mapOf("auto_play_next" to JsonPrimitive(true), "playback_mode" to JsonPrimitive("repeat_one"))
        assertEquals(mapOf("playback_mode" to JsonPrimitive("repeat_one")), BackupSettingRules.migratePlaybackSettings(values))
        assertTrue(BackupSettingRules.migratePlaybackSettings(emptyMap()).isEmpty())
    }

    @Test fun videoOrientationUsesContentDimensions() {
        assertEquals(true, videoIsLandscape(1920, 1080))
        assertEquals(false, videoIsLandscape(1080, 1920))
    }

    @Test fun missingOrSquareDimensionsDoNotForceRotation() {
        assertNull(videoIsLandscape(0, 1080))
        assertNull(videoIsLandscape(1920, -1))
        assertNull(videoIsLandscape(1000, 1000))
    }

    @Test fun originalRatioFitsLandscapeAndPortraitInsideTheAvailableArea() {
        assertEquals(1000 to 562, playbackTextureSize(1000, 600, 1920, 1080, PlaybackAspectRatio.FIT))
        assertEquals(337 to 600, playbackTextureSize(1000, 600, 1080, 1920, PlaybackAspectRatio.FIT))
    }

    @Test fun explicitRatiosDoNotDependOnSourceRatio() {
        assertEquals(1000 to 562, playbackTextureSize(1000, 600, 1080, 1920, PlaybackAspectRatio.RATIO_16_9))
        assertEquals(800 to 600, playbackTextureSize(1000, 600, 1920, 1080, PlaybackAspectRatio.RATIO_4_3))
    }

    @Test fun stretchExactlyFillsTheContainer() {
        assertEquals(1000 to 600, playbackTextureSize(1000, 600, 1080, 1920, PlaybackAspectRatio.FILL))
    }

    @Test fun cropCoversTheContainerWithoutChangingSourceRatio() {
        assertEquals(1066 to 600, playbackTextureSize(1000, 600, 1920, 1080, PlaybackAspectRatio.CROP))
        assertEquals(1000 to 1777, playbackTextureSize(1000, 600, 1080, 1920, PlaybackAspectRatio.CROP))
    }

    @Test fun unknownVideoSizeAndEmptyContainersAreSafe() {
        assertEquals(1000 to 600, playbackTextureSize(1000, 600, 0, 0, PlaybackAspectRatio.FIT))
        assertEquals(0 to 0, playbackTextureSize(0, 0, 1920, 1080, PlaybackAspectRatio.CROP))
    }
}
