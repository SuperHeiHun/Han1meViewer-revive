package com.yenaly.han1meviewer.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.player.PlaybackPlaylistSettings
import com.yenaly.han1meviewer.ui.view.video.PlaybackPlaylistControls

internal class PlaybackPlaylistControlsAdapter(
    private val onAutoPlayNextChanged: (Boolean) -> Unit,
    private val onOrderChanged: (Boolean) -> Unit,
) : RecyclerView.Adapter<PlaybackPlaylistControlsAdapter.Holder>() {
    class Holder(view: View, onAutoplay: (Boolean) -> Unit, onOrder: (Boolean) -> Unit) :
        RecyclerView.ViewHolder(view) {
        val controls = PlaybackPlaylistControls(view, onAutoplay, onOrder)
    }

    private var settings = PlaybackPlaylistSettings()

    fun submitSettings(value: PlaybackPlaylistSettings) {
        if (settings == value) return
        settings = value
        notifyItemChanged(0)
    }

    override fun getItemCount() = 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.layout_playback_playlist_controls, parent, false),
        onAutoPlayNextChanged, onOrderChanged,
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.controls.render(settings)
}
