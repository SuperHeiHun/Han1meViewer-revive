package com.yenaly.han1meviewer

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yenaly.han1meviewer.ui.view.video.PlayerDoubleTapMode
import com.yenaly.han1meviewer.ui.view.video.PlayerGestureMath
import com.yenaly.han1meviewer.ui.view.video.PlayerLongPressMode
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerGesturePreferencesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val keys = listOf(
        "gesture_double_tap_mode", "gesture_seek_step", "gesture_long_press_mode", "gesture_temporary_speed",
        "gesture_seek_enabled", "gesture_brightness_enabled", "gesture_volume_enabled",
        "gesture_brightness_sensitivity", "gesture_volume_sensitivity", "slide_sensitivity", "long_press_speed_times",
    )
    private var previous: Map<String, Any?> = emptyMap()

    @Before fun saveAndResetPreferences() {
        previous = preferences.all.filterKeys { it in keys }
        val editor = preferences.edit()
        keys.forEach { editor.remove(it) }
        assertTrue(editor.commit())
    }

    @After fun restorePreferences() {
        val editor = preferences.edit()
        keys.forEach { key ->
            when (val value = previous[key]) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                else -> editor.remove(key)
            }
        }
        assertTrue(editor.commit())
    }

    @Test fun upgradingWithoutNewKeysKeepsTheOldGestureDefaults() {
        val config = Preferences.playerGestures
        assertEquals(PlayerDoubleTapMode.PLAY_PAUSE, config.doubleTapMode)
        assertEquals(PlayerLongPressMode.LEGACY, config.longPressMode)
        assertEquals(2.5f, config.legacySpeedMultiplier, 0f)
        assertEquals(2f, config.temporarySpeed, 0f)
        assertTrue(config.seekEnabled && config.brightnessEnabled && config.volumeEnabled)
    }

    @Test fun oldMultiplierRemainsRelativeAndIsNotOverwritten() {
        assertTrue(preferences.edit().putString("long_press_speed_times", "3.8").commit())
        val config = Preferences.playerGestures
        assertEquals(PlayerLongPressMode.LEGACY, config.longPressMode)
        assertEquals(1.25f * 3.8f, PlayerGestureMath.temporarySpeed(1.25f, config), 0.0001f)
        assertEquals("3.8", preferences.getString("long_press_speed_times", null))
    }

    @Test fun newSettingsAreReadAgainWithoutRecreatingThePlayer() {
        assertEquals(PlayerDoubleTapMode.PLAY_PAUSE, Preferences.playerGestures.doubleTapMode)
        assertTrue(preferences.edit()
            .putString("gesture_double_tap_mode", "both")
            .putString("gesture_seek_step", "15")
            .putString("gesture_long_press_mode", "variable")
            .putString("gesture_temporary_speed", "1.7")
            .putBoolean("gesture_seek_enabled", false)
            .putBoolean("gesture_brightness_enabled", false)
            .putInt("gesture_volume_sensitivity", 8).commit())
        val config = Preferences.playerGestures
        assertEquals(PlayerDoubleTapMode.BOTH, config.doubleTapMode)
        assertEquals(PlayerLongPressMode.VARIABLE, config.longPressMode)
        assertEquals(15, config.seekStepSeconds)
        assertEquals(1.7f, config.temporarySpeed, 0f)
        assertFalse(config.seekEnabled || config.brightnessEnabled)
        assertTrue(config.volumeEnabled)
        assertEquals(8, config.volumeSensitivity)
    }

    @Test fun malformedValuesFallBackWithoutChangingTheStoredLegacySetting() {
        assertTrue(preferences.edit()
            .putString("gesture_double_tap_mode", "future")
            .putString("gesture_long_press_mode", "future")
            .putString("gesture_seek_step", "999")
            .putString("gesture_temporary_speed", "NaN")
            .putString("long_press_speed_times", "Infinity")
            .putInt("gesture_brightness_sensitivity", 0)
            .putInt("gesture_volume_sensitivity", 100).commit())
        val config = Preferences.playerGestures
        assertEquals(PlayerDoubleTapMode.PLAY_PAUSE, config.doubleTapMode)
        assertEquals(PlayerLongPressMode.LEGACY, config.longPressMode)
        assertEquals(10, config.seekStepSeconds)
        assertEquals(2f, config.temporarySpeed, 0f)
        assertEquals(2.5f, config.legacySpeedMultiplier, 0f)
        assertEquals(1, config.brightnessSensitivity)
        assertEquals(9, config.volumeSensitivity)
        assertEquals("Infinity", preferences.getString("long_press_speed_times", null))
    }
}
