package com.yenaly.han1meviewer

import com.yenaly.han1meviewer.logic.backup.*
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class PlayerGestureBackupTest {
    @Test fun newDefaultsRetainOldPlaybackAndMultiplierBehavior() {
        val defaults = BackupSettingRules.defaults
        assertEquals(JsonPrimitive("play_pause"), defaults["gesture_double_tap_mode"])
        assertEquals(JsonPrimitive("legacy"), defaults["gesture_long_press_mode"])
        assertEquals(JsonPrimitive("2.5"), defaults["long_press_speed_times"])
        defaults.filterKeys { it.startsWith("gesture_") }.forEach { (key, value) ->
            assertTrue(key, BackupSettingRules.isValid(key, value))
        }
    }

    @Test fun portableGestureOptionsRoundTripWithTheirOriginalTypes() {
        val settings = BackupSettingRules.defaults.filterKeys { it.startsWith("gesture_") }
        val archive = BackupArchive(createdAt = 0, settings = BackupSettings(settings))
        assertEquals(archive, BackupCodec.decode(BackupCodec.encode(archive)))
        assertFalse(BackupSettingRules.isValid("gesture_seek_step", JsonPrimitive(10)))
        assertFalse(BackupSettingRules.isValid("gesture_temporary_speed", JsonPrimitive(2f)))
        assertFalse(BackupSettingRules.isValid("gesture_seek_enabled", JsonPrimitive("true")))
    }

    @Test fun unknownModesAndUnlistedSeekStepsAreRejected() {
        assertFalse(BackupSettingRules.isValid("gesture_double_tap_mode", JsonPrimitive("future")))
        assertFalse(BackupSettingRules.isValid("gesture_long_press_mode", JsonPrimitive("2.5")))
        for (step in listOf("0", "1", "60", "-10")) {
            assertFalse(BackupSettingRules.isValid("gesture_seek_step", JsonPrimitive(step)))
        }
        for (step in listOf("5", "10", "15", "30")) {
            assertTrue(BackupSettingRules.isValid("gesture_seek_step", JsonPrimitive(step)))
        }
    }

    @Test fun temporarySpeedMustBeFiniteAndWithinTheSupportedRange() {
        for (speed in listOf("NaN", "Infinity", "-Infinity", "0", "0.4", "4.1")) {
            assertFalse(speed, BackupSettingRules.isValid("gesture_temporary_speed", JsonPrimitive(speed)))
        }
        for (speed in listOf("0.5", "2.0", "4.0")) {
            assertTrue(speed, BackupSettingRules.isValid("gesture_temporary_speed", JsonPrimitive(speed)))
        }
    }

    @Test fun levelSensitivitiesRejectOutOfRangeValuesAndWrongTypes() {
        for (key in listOf("gesture_brightness_sensitivity", "gesture_volume_sensitivity")) {
            assertTrue(BackupSettingRules.isValid(key, JsonPrimitive(1)))
            assertTrue(BackupSettingRules.isValid(key, JsonPrimitive(9)))
            assertFalse(BackupSettingRules.isValid(key, JsonPrimitive(0)))
            assertFalse(BackupSettingRules.isValid(key, JsonPrimitive(10)))
            assertFalse(BackupSettingRules.isValid(key, JsonPrimitive("5")))
        }
    }
}
