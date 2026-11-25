package com.yenaly.han1meviewer

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yenaly.han1meviewer.logic.player.PlaybackPlaylistSettings
import com.yenaly.han1meviewer.ui.activity.SettingsActivity
import com.yenaly.han1meviewer.ui.adapter.PlaybackPlaylistControlsAdapter
import com.yenaly.han1meviewer.ui.view.video.PlaybackPlaylistControls
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Detached controls and recording callbacks only; never opens a video or changes preferences. */
@RunWith(AndroidJUnit4::class)
class PlaybackPlaylistControlsTest {
    private fun withControls(test: (View) -> Unit) {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                test(LayoutInflater.from(activity).inflate(R.layout.layout_playback_playlist_controls, null))
            }
        }
    }

    @Test fun orderHasAnExplicitLabelAndNoRetiredAutoplaySwitch() = withControls { view ->
        val order = view.findViewById<RadioGroup>(R.id.playback_playlist_order)
        val label = view.findViewById<TextView>(R.id.playback_playlist_order_label)
        assertEquals(2, (view as android.view.ViewGroup).childCount)
        assertEquals(R.id.playback_playlist_order, label.labelFor)
        assertEquals(view.context.getString(R.string.playback_playlist_order_label), label.text.toString())
        assertEquals(2, order.childCount)
    }

    @Test fun renderingSyncedSettingsNeverFiresUserCallbacks() = withControls { view ->
        var changes = 0
        val controls = PlaybackPlaylistControls(view, { changes++ })
        controls.render(PlaybackPlaylistSettings())
        controls.render(PlaybackPlaylistSettings(reversed = true))
        controls.render(PlaybackPlaylistSettings(reversed = false))
        assertEquals(0, changes)
        assertEquals(R.id.playback_playlist_forward, view.findViewById<RadioGroup>(R.id.playback_playlist_order).checkedRadioButtonId)
    }

    @Test fun selectingReverseReportsAnExplicitTrueValue() = withControls { view ->
        val orderChanges = mutableListOf<Boolean>()
        PlaybackPlaylistControls(view, { orderChanges += it })
            .render(PlaybackPlaylistSettings())
        view.findViewById<AppCompatRadioButton>(R.id.playback_playlist_reverse).performClick()
        assertEquals(listOf(true), orderChanges)
    }

    @Test fun selectingForwardIsNotAnotherReverseToggle() = withControls { view ->
        val orders = mutableListOf<Boolean>()
        PlaybackPlaylistControls(view, { orders += it })
            .render(PlaybackPlaylistSettings(reversed = true))
        val forward = view.findViewById<AppCompatRadioButton>(R.id.playback_playlist_forward)
        forward.performClick()
        forward.performClick()
        assertEquals(listOf(false), orders)
        assertEquals(R.id.playback_playlist_forward, view.findViewById<RadioGroup>(R.id.playback_playlist_order).checkedRadioButtonId)
    }

    @Test fun selectingTheAlreadySelectedReverseDoesNotToggleIt() = withControls { view ->
        var orderChanges = 0
        PlaybackPlaylistControls(view, { orderChanges++ })
            .render(PlaybackPlaylistSettings(reversed = true))
        view.findViewById<AppCompatRadioButton>(R.id.playback_playlist_reverse).performClick()
        assertEquals(0, orderChanges)
        assertEquals(R.id.playback_playlist_reverse, view.findViewById<RadioGroup>(R.id.playback_playlist_order).checkedRadioButtonId)
    }

    @Test fun portraitRowAndDarkPopupRenderTheSameSettings() = withControls { view ->
        val settings = PlaybackPlaylistSettings(reversed = true)
        PlaybackPlaylistControls(view, {}, textColor = Color.WHITE).render(settings)
        val adapter = PlaybackPlaylistControlsAdapter({})
        adapter.submitSettings(settings)
        val holder = adapter.createViewHolder(FrameLayout(view.context), 0)
        adapter.bindViewHolder(holder, 0)
        assertEquals(
            view.findViewById<RadioGroup>(R.id.playback_playlist_order).checkedRadioButtonId,
            holder.itemView.findViewById<RadioGroup>(R.id.playback_playlist_order).checkedRadioButtonId,
        )
        assertEquals(Color.WHITE, view.findViewById<TextView>(R.id.playback_playlist_order_label).currentTextColor)
        adapter.submitSettings(PlaybackPlaylistSettings())
        adapter.bindViewHolder(holder, 0)
        assertEquals(R.id.playback_playlist_forward, holder.itemView.findViewById<RadioGroup>(R.id.playback_playlist_order).checkedRadioButtonId)
    }
}
