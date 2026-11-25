package com.yenaly.han1meviewer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yenaly.han1meviewer.ui.fragment.settings.PlayerSettingsFragment
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
}
