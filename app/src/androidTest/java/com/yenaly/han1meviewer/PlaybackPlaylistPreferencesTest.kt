package com.yenaly.han1meviewer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yenaly.han1meviewer.ui.fragment.settings.PlayerSettingsFragment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackPlaylistPreferencesTest {
    private fun withAutoplayPreference(test: () -> Unit) {
        val sp = Preferences.preferenceSp
        val key = PlayerSettingsFragment.AUTO_PLAY_NEXT
        val present = sp.contains(key)
        val previous = if (present) sp.getBoolean(key, false) else false
        try {
            sp.edit().remove(key).commit()
            test()
        } finally {
            sp.edit().apply { if (present) putBoolean(key, previous) else remove(key) }.commit()
        }
    }

    @Test fun existingUsersDoNotStartAutoplayAfterUpgrade() = withAutoplayPreference {
        assertFalse(Preferences.autoPlayNext)
    }

    @Test fun popupAndSettingsUseTheSamePersistentBoolean() = withAutoplayPreference {
        Preferences.autoPlayNext = true
        assertTrue(Preferences.preferenceSp.getBoolean(PlayerSettingsFragment.AUTO_PLAY_NEXT, false))
        Preferences.preferenceSp.edit().putBoolean(PlayerSettingsFragment.AUTO_PLAY_NEXT, false).commit()
        assertFalse(Preferences.autoPlayNext)
    }
}
