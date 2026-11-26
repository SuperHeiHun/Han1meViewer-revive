package com.yenaly.han1meviewer.ui.adapter

import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.player.PlaybackPlaylistSettings
import com.yenaly.han1meviewer.ui.view.video.PlaybackPlaylistControls

internal class PlaybackPlaylistControlsAdapter(
    private val onOrderChanged: (Boolean) -> Unit,
    private val onExpand: (() -> Unit)? = null,
) : RecyclerView.Adapter<PlaybackPlaylistControlsAdapter.Holder>() {
    class Holder(view: View, onOrder: (Boolean) -> Unit) :
        RecyclerView.ViewHolder(view) {
        val controls = PlaybackPlaylistControls(view, onOrder)
    }

    private var settings = PlaybackPlaylistSettings()

    fun submitSettings(value: PlaybackPlaylistSettings) {
        if (settings == value) return
        settings = value
        notifyItemChanged(0)
    }

    override fun getItemCount() = 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.layout_playback_playlist_controls_row, parent, false),
        onOrderChanged,
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.controls.render(settings)
        holder.itemView.findViewById<View>(R.id.expand_portrait_playlist).apply {
            isVisible = onExpand != null && resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
            setOnClickListener {
                if (isVisible && resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) onExpand?.invoke()
            }
        }
    }
}
