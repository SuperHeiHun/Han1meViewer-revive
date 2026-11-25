package com.yenaly.han1meviewer

import com.yenaly.han1meviewer.logic.backup.BackupSettingRules
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.model.HanimeVideo
import com.yenaly.han1meviewer.logic.player.PlaybackPlaylist
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class PlaybackPlaylistTest {
    private fun video(code: String, playing: Boolean = false) = HanimeInfo(
        title = "Video $code", coverUrl = "", videoCode = code,
        isPlaying = playing, itemType = HanimeInfo.NORMAL,
    )

    private fun series(vararg codes: String) = HanimeVideo.Playlist("Series", codes.map { video(it) })

    @Test fun nextItemUsesWebsiteOrderNotNumericVideoCodeOrder() {
        val playlist = PlaybackPlaylist().updated(series("30", "4", "21"), "30")
        assertEquals("4", playlist.nextAfter("30")?.videoCode)
        assertEquals("21", playlist.nextAfter("4")?.videoCode)
    }

    @Test fun lastItemDoesNotWrapOrRepeat() {
        val playlist = PlaybackPlaylist().updated(series("a", "b"), "b")
        assertNull(playlist.nextAfter("b"))
    }

    @Test fun missingCurrentVideoDoesNotGuessTheFirstItem() {
        val playlist = PlaybackPlaylist().updated(series("a", "b"), "x")
        assertNull(playlist.nextAfter("x"))
        assertTrue(playlist.videos.none { it.isPlaying })
    }

    @Test fun emptyAndSingleItemPlaylistsHaveNoNext() {
        assertNull(PlaybackPlaylist().nextAfter("a"))
        assertNull(PlaybackPlaylist().updated(series("a"), "a").nextAfter("a"))
    }

    @Test fun duplicateAndBlankCodesCannotCauseSelfPlayback() {
        val playlist = PlaybackPlaylist().updated(series("a", "a", "", " ", "b", "b"), "a")
        assertEquals(listOf("a", "b"), playlist.videos.map { it.videoCode })
        assertEquals("b", playlist.nextAfter("a")?.videoCode)
    }

    @Test fun currentMarkerOverridesStaleServerFlags() {
        val input = HanimeVideo.Playlist("Series", listOf(video("a", true), video("b", true)))
        val playlist = PlaybackPlaylist().updated(input, "b")
        assertFalse(playlist.videos[0].isPlaying)
        assertTrue(playlist.videos[1].isPlaying)
        assertTrue(input.video[0].isPlaying)
    }

    @Test fun selectionKeepsTheSameOrderAndDoesNotMutatePreviousSnapshot() {
        val first = PlaybackPlaylist().updated(series("a", "b", "c"), "a")
        val second = first.forVideo("b")
        assertEquals(first.videos.map { it.videoCode }, second.videos.map { it.videoCode })
        assertTrue(first.videos[0].isPlaying)
        assertFalse(second.videos[0].isPlaying)
        assertTrue(second.videos[1].isPlaying)
    }

    @Test fun selectedSeriesItemWithoutReturnedSeriesKeepsItsQueue() {
        val first = PlaybackPlaylist().updated(series("a", "b", "c"), "a")
        val second = first.updated(null, "b")
        assertEquals("Series", second.title)
        assertEquals("c", second.nextAfter("b")?.videoCode)
    }

    @Test fun unrelatedVideoWithoutSeriesClearsPreviousQueue() {
        val first = PlaybackPlaylist().updated(series("a", "b"), "a")
        assertTrue(first.updated(null, "x").videos.isEmpty())
        assertNull(first.updated(null, "x").nextAfter("a"))
    }

    @Test fun newSeriesReplacesOldItemsAndTitle() {
        val first = PlaybackPlaylist().updated(series("a", "b"), "a")
        val second = first.updated(HanimeVideo.Playlist("Other", listOf(video("x"), video("y"))), "x")
        assertEquals("Other", second.title)
        assertEquals(listOf("x", "y"), second.videos.map { it.videoCode })
    }

    @Test fun retiredAutoplayIsOnlyAcceptedWhenReadingLegacyBackups() {
        assertFalse(BackupSettingRules.defaults.containsKey("auto_play_next"))
        assertTrue(BackupSettingRules.isValid("auto_play_next", JsonPrimitive(true)))
        assertTrue(BackupSettingRules.isValid("auto_play_next", JsonPrimitive(false)))
        assertFalse(BackupSettingRules.isValid("auto_play_next", JsonPrimitive("true")))
        assertFalse(BackupSettingRules.isValid("auto_play_next", JsonPrimitive(1)))
    }

    @Test fun reverseOrderFlipsWebsiteOrderWithoutSortingOrMutatingTheQueue() {
        val playlist = PlaybackPlaylist().updated(series("30", "4", "21"), "4")
        val original = playlist.videos.map { it.videoCode }
        assertEquals(listOf("21", "4", "30"), playlist.orderedVideos(reverse = true).map { it.videoCode })
        assertEquals(listOf("21", "4", "30"), playlist.orderedVideos(reverse = true).map { it.videoCode })
        assertEquals(original, playlist.orderedVideos(reverse = false).map { it.videoCode })
        assertEquals(original, playlist.videos.map { it.videoCode })
    }

    @Test fun reverseAutoplayFollowsTheSameSequenceAsTheDisplayedList() {
        val playlist = PlaybackPlaylist().updated(series("30", "4", "21"), "21")
        playlist.orderedVideos(reverse = true).zipWithNext().forEach { (current, next) ->
            assertEquals(next.videoCode, playlist.nextAfter(current.videoCode, reverse = true)?.videoCode)
        }
        assertEquals("21", playlist.nextAfter("4")?.videoCode)
        assertEquals("30", playlist.nextAfter("4", reverse = true)?.videoCode)
    }

    @Test fun reverseAutoplayStopsAtItsLastItemWithoutWrapping() {
        val playlist = PlaybackPlaylist().updated(series("a", "b", "c"), "a")
        assertNull(playlist.nextAfter("a", reverse = true))
        assertEquals("b", playlist.nextAfter("c", reverse = true)?.videoCode)
    }

    @Test fun reverseAutoplayDoesNotGuessForMissingEmptyOrSingleItemQueues() {
        assertNull(PlaybackPlaylist().nextAfter("a", reverse = true))
        assertNull(PlaybackPlaylist().updated(series("a"), "a").nextAfter("a", reverse = true))
        val playlist = PlaybackPlaylist().updated(series("a", "b"), "x")
        assertNull(playlist.nextAfter("x", reverse = true))
        assertTrue(playlist.orderedVideos(reverse = true).none { it.isPlaying })
    }

    @Test fun reversingKeepsThePlayingMarkerOnTheSameVideo() {
        val playlist = PlaybackPlaylist().updated(series("a", "b", "c"), "a")
        val reversed = playlist.orderedVideos(reverse = true)
        assertEquals("a", reversed.single { it.isPlaying }.videoCode)
        assertTrue(reversed.last().isPlaying)
        assertTrue(playlist.videos.first().isPlaying)
    }

    @Test fun retainedSeriesStillSupportsReverseOrderAfterSwitchingVideos() {
        val first = PlaybackPlaylist().updated(series("a", "b", "c"), "c")
        val second = first.updated(null, "b")
        assertEquals(listOf("c", "b", "a"), second.orderedVideos(reverse = true).map { it.videoCode })
        assertEquals("a", second.nextAfter("b", reverse = true)?.videoCode)
        assertEquals("b", second.orderedVideos(reverse = true).single { it.isPlaying }.videoCode)
        assertEquals(listOf("a", "b", "c"), second.videos.map { it.videoCode })
    }

    @Test fun playlistOrderBackupDefaultsToForwardAndAcceptsOnlyBooleans() {
        val key = "playback_playlist_reversed"
        assertEquals(JsonPrimitive(false), BackupSettingRules.defaults[key])
        assertTrue(BackupSettingRules.isValid(key, JsonPrimitive(true)))
        assertTrue(BackupSettingRules.isValid(key, JsonPrimitive(false)))
        assertFalse(BackupSettingRules.isValid(key, JsonPrimitive("true")))
        assertFalse(BackupSettingRules.isValid(key, JsonPrimitive(1)))
    }
}
