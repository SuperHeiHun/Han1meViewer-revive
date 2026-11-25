package com.yenaly.han1meviewer.logic.player

import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.model.HanimeVideo

/** The website's series order, never recommendations or a saved user list. */
data class PlaybackPlaylist(
    val title: String? = null,
    val videos: List<HanimeInfo> = emptyList(),
) {
    fun nextAfter(videoCode: String): HanimeInfo? {
        val index = videos.indexOfFirst { it.videoCode == videoCode }
        return if (index >= 0) videos.getOrNull(index + 1) else null
    }

    fun forVideo(videoCode: String) = copy(
        videos = videos.map { it.copy(isPlaying = it.videoCode == videoCode) },
    )

    fun updated(series: HanimeVideo.Playlist?, videoCode: String): PlaybackPlaylist {
        val items = series?.video.orEmpty().filter { it.videoCode.isNotBlank() }.distinctBy { it.videoCode }
        val playlist = when {
            items.isNotEmpty() -> PlaybackPlaylist(series?.playlistName, items)
            videos.any { it.videoCode == videoCode } -> this
            else -> PlaybackPlaylist()
        }
        return playlist.forVideo(videoCode)
    }
}
