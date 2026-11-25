package com.yenaly.han1meviewer

import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.widget.AppCompatSpinner
import androidx.appcompat.widget.SwitchCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yenaly.han1meviewer.logic.player.*
import com.yenaly.han1meviewer.ui.activity.SettingsActivity
import com.yenaly.han1meviewer.ui.view.video.PlaybackSettingsControls
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Detached UI with recording callbacks, never mutates preferences or opens media. */
@RunWith(AndroidJUnit4::class)
class PlaybackSettingsControlsTest {
    private fun withControls(test: (View) -> Unit) {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                test(LayoutInflater.from(activity).inflate(R.layout.jz_layout_playback_settings, null))
            }
        }
    }

    @Test fun panelContainsOnlyThreeModesFiveRatiosAndOneRotationSwitch() = withControls { view ->
        PlaybackSettingsControls(view, {}, {}, {}).render(PlaybackSettings())
        assertEquals(3, view.findViewById<AppCompatSpinner>(R.id.playback_mode_choice).count)
        assertEquals(5, view.findViewById<AppCompatSpinner>(R.id.playback_aspect_choice).count)
        assertFalse(view.findViewById<SwitchCompat>(R.id.playback_auto_rotate).isChecked)
    }

    @Test fun rebindingDoesNotWriteBackSettings() = withControls { view ->
        var changes = 0
        val controls = PlaybackSettingsControls(view, { changes++ }, { changes++ }, { changes++ })
        controls.render(PlaybackSettings(PlaybackMode.LIST_LOOP, PlaybackAspectRatio.CROP, true))
        controls.render(PlaybackSettings())
        val mode = view.findViewById<AppCompatSpinner>(R.id.playback_mode_choice)
        mode.onItemSelectedListener!!.onItemSelected(mode, null, PlaybackMode.PAUSE_AT_END.ordinal, 0)
        assertEquals(0, changes)
    }

    @Test fun modeSelectionDoesNotAlterRatioOrRotation() = withControls { view ->
        val modes = mutableListOf<PlaybackMode>()
        var otherChanges = 0
        PlaybackSettingsControls(view, { modes += it }, { otherChanges++ }, { otherChanges++ })
            .render(PlaybackSettings())
        val mode = view.findViewById<AppCompatSpinner>(R.id.playback_mode_choice)
        mode.onItemSelectedListener!!.onItemSelected(mode, null, PlaybackMode.REPEAT_ONE.ordinal, 0)
        assertEquals(listOf(PlaybackMode.REPEAT_ONE), modes)
        assertEquals(0, otherChanges)
    }

    @Test fun ratioSelectionDoesNotAlterTheModeOrRotation() = withControls { view ->
        val ratios = mutableListOf<PlaybackAspectRatio>()
        var otherChanges = 0
        PlaybackSettingsControls(view, { otherChanges++ }, { ratios += it }, { otherChanges++ })
            .render(PlaybackSettings())
        val ratio = view.findViewById<AppCompatSpinner>(R.id.playback_aspect_choice)
        ratio.onItemSelectedListener!!.onItemSelected(ratio, null, PlaybackAspectRatio.RATIO_16_9.ordinal, 0)
        assertEquals(listOf(PlaybackAspectRatio.RATIO_16_9), ratios)
        assertEquals(0, otherChanges)
    }

    @Test fun rotationSwitchDoesNotChangeTheOtherTwoSettings() = withControls { view ->
        val rotations = mutableListOf<Boolean>()
        var otherChanges = 0
        PlaybackSettingsControls(view, { otherChanges++ }, { otherChanges++ }, { rotations += it })
            .render(PlaybackSettings(PlaybackMode.REPEAT_ONE, PlaybackAspectRatio.RATIO_4_3))
        view.findViewById<SwitchCompat>(R.id.playback_auto_rotate).performClick()
        assertEquals(listOf(true), rotations)
        assertEquals(0, otherChanges)
    }
}
