package com.yenaly.han1meviewer.ui.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.model.HanimeInfo

/** A fullscreen-only view of the same series shown in the introduction. */
internal class PlaybackPlaylistAdapter(
    private val videos: List<HanimeInfo>,
    private val selectedVideoCode: String?,
    private val onSelect: (String) -> Unit,
) : RecyclerView.Adapter<PlaybackPlaylistAdapter.Holder>() {
    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val cover: ImageView = view.findViewById(R.id.cover)
        val title: TextView = view.findViewById(R.id.title)
        val playing: TextView = view.findViewById(R.id.is_playing)
        val position: TextView = view.findViewById(R.id.playlist_position)
    }

    override fun getItemCount() = videos.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_playback_playlist, parent, false),
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val video = videos[position]
        val selected = video.videoCode == selectedVideoCode
        holder.cover.load(video.coverUrl)
        holder.title.text = video.title
        holder.playing.isVisible = selected
        holder.position.text = holder.itemView.context.getString(
            R.string.playback_playlist_position, position + 1, videos.size,
        )
        holder.itemView.isSelected = selected
        holder.itemView.setBackgroundColor(if (selected) Color.argb(32, 255, 207, 128) else Color.TRANSPARENT)
        holder.itemView.setOnClickListener { onSelect(video.videoCode) }
    }
}
