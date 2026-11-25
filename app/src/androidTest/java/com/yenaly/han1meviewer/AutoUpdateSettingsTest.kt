package com.yenaly.han1meviewer

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yenaly.han1meviewer.ui.fragment.settings.HomeSettingsFragment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoUpdateSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val keys = listOf(
        HomeSettingsFragment.DISABLE_AUTO_UPDATE,
        HomeSettingsFragment.USE_CI_UPDATE_CHANNEL,
        HomeSettingsFragment.UPDATE_POPUP_INTERVAL_DAYS,
    )
    private var previousValues: Map<String, Any?> = emptyMap()

    @Before fun savePreferences() {
        previousValues = preferences.all.filterKeys { it in keys }
    }

    @After fun restorePreferences() {
        val editor = preferences.edit()
        keys.forEach { key ->
            when (val value = previousValues[key]) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                else -> editor.remove(key)
            }
        }
        assertTrue(editor.commit())
    }

    @Test fun missingPreferenceKeepsAutomaticUpdatesEnabled() {
        assertTrue(preferences.edit().remove(HomeSettingsFragment.DISABLE_AUTO_UPDATE).commit())

        assertFalse(Preferences.disableAutoUpdate)
    }

    @Test fun disabledPreferenceIsPersistedAndCanBeReenabled() {
        assertTrue(preferences.edit()
            .putBoolean(HomeSettingsFragment.DISABLE_AUTO_UPDATE, true).commit())

        assertTrue(Preferences.disableAutoUpdate)
        assertTrue(PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(HomeSettingsFragment.DISABLE_AUTO_UPDATE, false))

        assertTrue(preferences.edit()
            .putBoolean(HomeSettingsFragment.DISABLE_AUTO_UPDATE, false).commit())

        assertFalse(Preferences.disableAutoUpdate)
    }

    @Test fun disablingAutomaticUpdatesSuppressesAutomaticDialogs() {
        assertTrue(preferences.edit()
            .putBoolean(HomeSettingsFragment.DISABLE_AUTO_UPDATE, true).commit())

        assertFalse(Preferences.isUpdateDialogVisible)
    }

    @Test fun disablingDoesNotChangeChannelOrPopupInterval() {
        assertTrue(preferences.edit()
            .putBoolean(HomeSettingsFragment.USE_CI_UPDATE_CHANNEL, true)
            .putInt(HomeSettingsFragment.UPDATE_POPUP_INTERVAL_DAYS, 7)
            .putBoolean(HomeSettingsFragment.DISABLE_AUTO_UPDATE, true).commit())

        assertTrue(Preferences.useCIUpdateChannel)
        assertEquals(7, Preferences.updatePopupIntervalDays)

        assertTrue(preferences.edit()
            .putBoolean(HomeSettingsFragment.DISABLE_AUTO_UPDATE, false).commit())

        assertTrue(Preferences.useCIUpdateChannel)
        assertEquals(7, Preferences.updatePopupIntervalDays)
    }
}
