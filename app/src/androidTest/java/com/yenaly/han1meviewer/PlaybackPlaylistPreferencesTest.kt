package com.yenaly.han1meviewer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yenaly.han1meviewer.ui.fragment.settings.PlayerSettingsFragment
import com.yenaly.han1meviewer.logic.player.PlaybackPlaylistSettings
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackPlaylistPreferencesTest {
    private fun withPreference(key: String = PlayerSettingsFragment.AUTO_PLAY_NEXT, test: () -> Unit) {
        val sp = Preferences.preferenceSp
        val present = sp.contains(key)
        val previous = if (present) sp.getBoolean(key, false) else false
        try {
            sp.edit().remove(key).commit()
            test()
        } finally {
            sp.edit().apply { if (present) putBoolean(key, previous) else remove(key) }.commit()
        }
    }

    @Test fun existingUsersDoNotStartAutoplayAfterUpgrade() = withPreference {
        assertFalse(Preferences.autoPlayNext)
    }

    @Test fun popupAndSettingsUseTheSamePersistentBoolean() = withPreference {
        Preferences.autoPlayNext = true
        assertTrue(Preferences.preferenceSp.getBoolean(PlayerSettingsFragment.AUTO_PLAY_NEXT, false))
        Preferences.preferenceSp.edit().putBoolean(PlayerSettingsFragment.AUTO_PLAY_NEXT, false).commit()
        assertFalse(Preferences.autoPlayNext)
    }

    @Test fun playlistOrderDefaultsToTheOriginalWebsiteOrder() = withPreference("playback_playlist_reversed") {
        assertFalse(Preferences.playbackPlaylistReversed)
    }

    @Test fun playlistOrderPersistsAndCanReturnToForward() = withPreference("playback_playlist_reversed") {
        Preferences.playbackPlaylistReversed = true
        assertTrue(Preferences.preferenceSp.getBoolean("playback_playlist_reversed", false))
        assertTrue(Preferences.playbackPlaylistReversed)
        Preferences.playbackPlaylistReversed = false
        assertFalse(Preferences.preferenceSp.getBoolean("playback_playlist_reversed", true))
        assertFalse(Preferences.playbackPlaylistReversed)
    }

    @Test fun observingSettingsIncludesBothControlsAndDirectPreferenceRestores() = withPreference {
        withPreference("playback_playlist_reversed") {
            runBlocking {
                val values = Channel<PlaybackPlaylistSettings>(Channel.UNLIMITED)
                val job = launch(start = CoroutineStart.UNDISPATCHED) {
                    Preferences.observePlaybackPlaylistSettings().collect { values.send(it) }
                }
                try {
                    assertEquals(PlaybackPlaylistSettings(), withTimeout(5_000) { values.receive() })
                    Preferences.autoPlayNext = true
                    assertEquals(PlaybackPlaylistSettings(autoPlayNext = true), withTimeout(5_000) { values.receive() })
                    Preferences.preferenceSp.edit().putBoolean("playback_playlist_reversed", true).commit()
                    assertEquals(PlaybackPlaylistSettings(true, true), withTimeout(5_000) { values.receive() })
                    Preferences.preferenceSp.edit()
                        .putBoolean(PlayerSettingsFragment.AUTO_PLAY_NEXT, false)
                        .putBoolean("playback_playlist_reversed", false).commit()
                    assertEquals(PlaybackPlaylistSettings(), withTimeout(5_000) { values.receive() })
                } finally {
                    job.cancelAndJoin()
                    values.close()
                }
            }
        }
    }

    @Test fun resubscribingAfterStopReadsTheLatestSharedSettings() = withPreference {
        withPreference("playback_playlist_reversed") {
            runBlocking {
                assertEquals(PlaybackPlaylistSettings(), Preferences.observePlaybackPlaylistSettings().first())
                Preferences.autoPlayNext = true
                Preferences.playbackPlaylistReversed = true
                assertEquals(PlaybackPlaylistSettings(true, true), Preferences.observePlaybackPlaylistSettings().first())
            }
        }
    }
}
