package com.yenaly.han1meviewer.ui.view.video

import android.content.res.ColorStateList
import android.view.View
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.appcompat.widget.SwitchCompat
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.player.PlaybackPlaylistSettings

/** Separate playback enablement from an explicit, mutually exclusive order choice. */
internal class PlaybackPlaylistControls(
    view: View,
    private val onAutoPlayNextChanged: (Boolean) -> Unit,
    private val onOrderChanged: (Boolean) -> Unit,
    textColor: Int? = null,
) {
    private val autoplay = view.findViewById<SwitchCompat>(R.id.auto_play_next)
    private val order = view.findViewById<RadioGroup>(R.id.playback_playlist_order)

    init {
        if (textColor != null) {
            autoplay.setTextColor(textColor)
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
        autoplay.setOnCheckedChangeListener(null)
        order.setOnCheckedChangeListener(null)
        autoplay.isChecked = settings.autoPlayNext
        order.check(if (settings.reversed) R.id.playback_playlist_reverse else R.id.playback_playlist_forward)
        autoplay.setOnCheckedChangeListener { _, enabled -> onAutoPlayNextChanged(enabled) }
        order.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.playback_playlist_forward -> onOrderChanged(false)
                R.id.playback_playlist_reverse -> onOrderChanged(true)
            }
        }
    }
}
