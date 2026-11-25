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

    @Test fun autoplayBackupDefaultsToFalseAndAcceptsOnlyBooleans() {
        assertEquals(JsonPrimitive(false), BackupSettingRules.defaults["auto_play_next"])
        assertTrue(BackupSettingRules.isValid("auto_play_next", JsonPrimitive(true)))
        assertTrue(BackupSettingRules.isValid("auto_play_next", JsonPrimitive(false)))
        assertFalse(BackupSettingRules.isValid("auto_play_next", JsonPrimitive("true")))
        assertFalse(BackupSettingRules.isValid("auto_play_next", JsonPrimitive(1)))
    }
}
