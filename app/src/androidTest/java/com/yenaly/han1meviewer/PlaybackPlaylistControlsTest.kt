package com.yenaly.han1meviewer

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.appcompat.widget.SwitchCompat
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

    @Test fun autoplayAndOrderAreSeparateControlsWithAnExplicitOrderLabel() = withControls { view ->
        val autoplay = view.findViewById<SwitchCompat>(R.id.auto_play_next)
        val order = view.findViewById<RadioGroup>(R.id.playback_playlist_order)
        val label = view.findViewById<TextView>(R.id.playback_playlist_order_label)
        assertNotSame(autoplay, order)
        assertEquals(R.id.playback_playlist_order, label.labelFor)
        assertEquals(view.context.getString(R.string.playback_playlist_order_label), label.text.toString())
        assertEquals(2, order.childCount)
    }

    @Test fun renderingSyncedSettingsNeverFiresUserCallbacks() = withControls { view ->
        var changes = 0
        val controls = PlaybackPlaylistControls(view, { changes++ }, { changes++ })
        controls.render(PlaybackPlaylistSettings())
        controls.render(PlaybackPlaylistSettings(autoPlayNext = true, reversed = true))
        controls.render(PlaybackPlaylistSettings(autoPlayNext = false, reversed = false))
        assertEquals(0, changes)
        assertFalse(view.findViewById<SwitchCompat>(R.id.auto_play_next).isChecked)
        assertEquals(R.id.playback_playlist_forward, view.findViewById<RadioGroup>(R.id.playback_playlist_order).checkedRadioButtonId)
    }

    @Test fun selectingReverseDoesNotToggleAutoplay() = withControls { view ->
        var autoplayChanges = 0
        val orderChanges = mutableListOf<Boolean>()
        PlaybackPlaylistControls(view, { autoplayChanges++ }, { orderChanges += it })
            .render(PlaybackPlaylistSettings(autoPlayNext = true))
        view.findViewById<AppCompatRadioButton>(R.id.playback_playlist_reverse).performClick()
        assertEquals(listOf(true), orderChanges)
        assertEquals(0, autoplayChanges)
        assertTrue(view.findViewById<SwitchCompat>(R.id.auto_play_next).isChecked)
    }

    @Test fun selectingForwardIsNotAnotherReverseToggle() = withControls { view ->
        val orders = mutableListOf<Boolean>()
        PlaybackPlaylistControls(view, {}, { orders += it })
            .render(PlaybackPlaylistSettings(reversed = true))
        val forward = view.findViewById<AppCompatRadioButton>(R.id.playback_playlist_forward)
        forward.performClick()
        forward.performClick()
        assertEquals(listOf(false), orders)
        assertEquals(R.id.playback_playlist_forward, view.findViewById<RadioGroup>(R.id.playback_playlist_order).checkedRadioButtonId)
    }

    @Test fun togglingAutoplayDoesNotChangeOrder() = withControls { view ->
        val autoplay = mutableListOf<Boolean>()
        var orderChanges = 0
        PlaybackPlaylistControls(view, { autoplay += it }, { orderChanges++ })
            .render(PlaybackPlaylistSettings(reversed = true))
        view.findViewById<SwitchCompat>(R.id.auto_play_next).performClick()
        assertEquals(listOf(true), autoplay)
        assertEquals(0, orderChanges)
        assertEquals(R.id.playback_playlist_reverse, view.findViewById<RadioGroup>(R.id.playback_playlist_order).checkedRadioButtonId)
    }

    @Test fun portraitRowAndDarkPopupRenderTheSameSettings() = withControls { view ->
        val settings = PlaybackPlaylistSettings(autoPlayNext = true, reversed = true)
        PlaybackPlaylistControls(view, {}, {}, textColor = Color.WHITE).render(settings)
        val adapter = PlaybackPlaylistControlsAdapter({}, {})
        adapter.submitSettings(settings)
        val holder = adapter.createViewHolder(FrameLayout(view.context), 0)
        adapter.bindViewHolder(holder, 0)
        assertTrue(holder.itemView.findViewById<SwitchCompat>(R.id.auto_play_next).isChecked)
        assertEquals(
            view.findViewById<RadioGroup>(R.id.playback_playlist_order).checkedRadioButtonId,
            holder.itemView.findViewById<RadioGroup>(R.id.playback_playlist_order).checkedRadioButtonId,
        )
        assertEquals(Color.WHITE, view.findViewById<TextView>(R.id.playback_playlist_order_label).currentTextColor)
        adapter.submitSettings(PlaybackPlaylistSettings())
        adapter.bindViewHolder(holder, 0)
        assertFalse(holder.itemView.findViewById<SwitchCompat>(R.id.auto_play_next).isChecked)
        assertEquals(R.id.playback_playlist_forward, holder.itemView.findViewById<RadioGroup>(R.id.playback_playlist_order).checkedRadioButtonId)
    }
}
