package com.yenaly.han1meviewer.ui.fragment.settings

import android.os.Bundle
import androidx.annotation.IntRange
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.activity.SettingsActivity
import com.yenaly.han1meviewer.ui.fragment.IToolbarFragment
import com.yenaly.han1meviewer.ui.view.pref.MaterialDialogPreference
import com.yenaly.han1meviewer.ui.view.video.HJzvdStd
import com.yenaly.han1meviewer.ui.view.video.HMediaKernel
import com.yenaly.han1meviewer.ui.view.video.PlayerDoubleTapMode
import com.yenaly.han1meviewer.ui.view.video.PlayerLongPressMode
import com.yenaly.han1meviewer.util.setSummaryConverter
import com.yenaly.yenaly_libs.base.settings.YenalySettingsFragment
import com.yenaly.yenaly_libs.utils.toStringArray

/**
 * @project Han1meViewer
 * @author Yenaly Liew
 * @time 2023/09/04 004 16:28
 */
class PlayerSettingsFragment : YenalySettingsFragment(R.xml.settings_player),
    IToolbarFragment<SettingsActivity> {

    companion object {
        const val SWITCH_PLAYER_KERNEL = "switch_player_kernel"
        const val SHOW_BOTTOM_PROGRESS = "show_bottom_progress"
        const val AUTO_PLAY_NEXT = "auto_play_next"
        const val PLAYER_SPEED = "player_speed"
        const val SLIDE_SENSITIVITY = "slide_sensitivity"
        const val LONG_PRESS_SPEED_TIMES = "long_press_speed_times"
        const val GESTURE_DOUBLE_TAP_MODE = "gesture_double_tap_mode"
        const val GESTURE_SEEK_STEP = "gesture_seek_step"
        const val GESTURE_LONG_PRESS_MODE = "gesture_long_press_mode"
        const val GESTURE_TEMPORARY_SPEED = "gesture_temporary_speed"
        const val GESTURE_SEEK_ENABLED = "gesture_seek_enabled"
        const val GESTURE_BRIGHTNESS_ENABLED = "gesture_brightness_enabled"
        const val GESTURE_VOLUME_ENABLED = "gesture_volume_enabled"
        const val GESTURE_BRIGHTNESS_SENSITIVITY = "gesture_brightness_sensitivity"
        const val GESTURE_VOLUME_SENSITIVITY = "gesture_volume_sensitivity"
    }

    private val switchPlayerKernel
            by safePreference<MaterialDialogPreference>(SWITCH_PLAYER_KERNEL)
    private val showBottomProgressPref
            by safePreference<SwitchPreferenceCompat>(SHOW_BOTTOM_PROGRESS)
    private val playerSpeed
            by safePreference<MaterialDialogPreference>(PLAYER_SPEED)
    private val slideSensitivity
            by safePreference<SeekBarPreference>(SLIDE_SENSITIVITY)
    private val longPressSpeedTimesPref
            by safePreference<MaterialDialogPreference>(LONG_PRESS_SPEED_TIMES)
    private val doubleTapMode by safePreference<MaterialDialogPreference>(GESTURE_DOUBLE_TAP_MODE)
    private val seekStep by safePreference<MaterialDialogPreference>(GESTURE_SEEK_STEP)
    private val longPressMode by safePreference<MaterialDialogPreference>(GESTURE_LONG_PRESS_MODE)
    private val temporarySpeed by safePreference<MaterialDialogPreference>(GESTURE_TEMPORARY_SPEED)
    private val brightnessSensitivity by safePreference<SeekBarPreference>(GESTURE_BRIGHTNESS_SENSITIVITY)
    private val volumeSensitivity by safePreference<SeekBarPreference>(GESTURE_VOLUME_SENSITIVITY)

    override fun onStart() {
        super.onStart()
        (activity as SettingsActivity).setupToolbar()
    }

    override fun onPreferencesCreated(savedInstanceState: Bundle?) {
        switchPlayerKernel.apply {
            val kernelNames = HMediaKernel.Type.entries.map { it.name }.toTypedArray()
            entries = kernelNames
            entryValues = kernelNames
            if (value == null) setValueIndex(HMediaKernel.Type.ExoPlayer.ordinal)
        }
        playerSpeed.apply {
            entries = HJzvdStd.speedStringArray
            entryValues = HJzvdStd.speedArray.toStringArray()
            // 不能直接用 defaultValue 设置，没效果
            if (value == null) setValueIndex(HJzvdStd.DEF_SPEED_INDEX)
        }
        slideSensitivity.apply {
            setSummaryConverter(
                defValue = HJzvdStd.DEF_PROGRESS_SLIDE_SENSITIVITY,
                converter = ::toPrettySensitivityString
            )
        }
        longPressSpeedTimesPref.apply {
            entries = arrayOf(
                getString(R.string.d_speed_times, 1f),
                getString(R.string.d_speed_times, 1.5f),
                getString(R.string.d_speed_times, 2f),
                "${getString(R.string.d_speed_times, 2.5f)} (${getString(R.string.default_)})",
                getString(R.string.d_speed_times, 2.8f),
                getString(R.string.d_speed_times, 3f),
                getString(R.string.d_speed_times, 3.2f),
                getString(R.string.d_speed_times, 3.5f),
                getString(R.string.d_speed_times, 3.8f),
                getString(R.string.d_speed_times, 4f)
            )
            entryValues = arrayOf(
                "1", "1.5", "2", "2.5", "2.8",
                "3", "3.2", "3.5", "3.8", "4"
            )
            if (value == null) setValueIndex(3)
        }
        doubleTapMode.apply {
            entries = arrayOf(
                getString(R.string.gesture_double_tap_play_pause),
                getString(R.string.gesture_double_tap_seek),
                getString(R.string.gesture_double_tap_both),
                getString(R.string.gesture_disabled),
            )
            entryValues = PlayerDoubleTapMode.entries.map { it.value }.toTypedArray()
            value = PlayerDoubleTapMode.fromValue(value).value
            setOnPreferenceChangeListener { _, newValue ->
                updateSeekStepEnabled(PlayerDoubleTapMode.fromValue(newValue as? String))
                true
            }
        }
        seekStep.apply {
            val seconds = listOf(5, 10, 15, 30)
            entries = seconds.map { getString(R.string.gesture_seek_seconds, it) }.toTypedArray()
            entryValues = seconds.map { it.toString() }.toTypedArray()
            if (value !in seconds.map { it.toString() }) value = "10"
        }
        longPressMode.apply {
            entries = arrayOf(
                getString(R.string.gesture_long_press_legacy),
                getString(R.string.gesture_long_press_fixed),
                getString(R.string.gesture_long_press_variable),
                getString(R.string.gesture_disabled),
            )
            entryValues = PlayerLongPressMode.entries.map { it.value }.toTypedArray()
            value = PlayerLongPressMode.fromValue(value).value
            setOnPreferenceChangeListener { _, newValue ->
                updateLongPressOptions(PlayerLongPressMode.fromValue(newValue as? String))
                true
            }
        }
        temporarySpeed.apply {
            val speeds = (5..40).map { it / 10f }
            entries = speeds.map { getString(R.string.d_speed_times, it) }.toTypedArray()
            entryValues = speeds.map { it.toString() }.toTypedArray()
            if (value !in speeds.map { it.toString() }) value = "2.0"
        }
        listOf(brightnessSensitivity, volumeSensitivity).forEach {
            it.setSummaryConverter(defValue = 5, converter = ::toPrettySensitivityString)
        }
        updateSeekStepEnabled(PlayerDoubleTapMode.fromValue(doubleTapMode.value))
        updateLongPressOptions(PlayerLongPressMode.fromValue(longPressMode.value))
    }

    private fun updateSeekStepEnabled(mode: PlayerDoubleTapMode) {
        seekStep.isEnabled = mode == PlayerDoubleTapMode.SEEK || mode == PlayerDoubleTapMode.BOTH
    }

    private fun updateLongPressOptions(mode: PlayerLongPressMode) {
        longPressSpeedTimesPref.isEnabled = mode == PlayerLongPressMode.LEGACY
        temporarySpeed.isEnabled = mode == PlayerLongPressMode.FIXED || mode == PlayerLongPressMode.VARIABLE
    }

    /**
     * 將數字靈敏度轉換為漂亮的字串
     */
    private fun toPrettySensitivityString(@IntRange(from = 1, to = 9) value: Int): String {
        val pretty = when (value) {
            1, 2 -> getString(R.string.high)
            3, 4 -> getString(R.string.moderately_high)
            5 -> getString(R.string.moderate)
            6 -> getString(R.string.slightly_low)
            7 -> getString(R.string.low)
            8 -> getString(R.string.very_low)
            9 -> getString(R.string.extremely_low)
            else -> throw IllegalStateException("Invalid sensitivity value: $this")
        }
        return getString(R.string.current_slide_sensitivity, pretty)
    }

    override fun SettingsActivity.setupToolbar() {
        supportActionBar!!.setTitle(R.string.player_settings)
    }
}
