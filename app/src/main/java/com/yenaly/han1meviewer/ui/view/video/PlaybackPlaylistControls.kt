package com.yenaly.han1meviewer.ui.view.video

import android.content.res.ColorStateList
import android.view.View
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatRadioButton
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.player.PlaybackPlaylistSettings

/** Explicit, mutually exclusive series order; playback rules live in the player settings. */
internal class PlaybackPlaylistControls(
    view: View,
    private val onOrderChanged: (Boolean) -> Unit,
    textColor: Int? = null,
) {
    private val order = view.findViewById<RadioGroup>(R.id.playback_playlist_order)

    init {
        if (textColor != null) {
            view.findViewById<TextView>(R.id.playback_playlist_order_label).setTextColor(textColor)
            listOf(R.id.playback_playlist_forward, R.id.playback_playlist_reverse).forEach { id ->
                view.findViewById<AppCompatRadioButton>(id).apply {
                    setTextColor(textColor)
                    supportButtonTintList = ColorStateList.valueOf(textColor)
                }
            }
        }
    }

    fun render(settings: PlaybackPlaylistSettings) {
        // Rebinding a recycled portrait row or syncing the popup must never change settings.
        order.setOnCheckedChangeListener(null)
        order.check(if (settings.reversed) R.id.playback_playlist_reverse else R.id.playback_playlist_forward)
        order.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.playback_playlist_forward -> onOrderChanged(false)
                R.id.playback_playlist_reverse -> onOrderChanged(true)
            }
        }
    }
}
