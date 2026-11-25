package com.yenaly.han1meviewer.ui.view.video

import androidx.core.view.doOnAttach
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yenaly.han1meviewer.logic.model.HanimeInfo

/** Position only committed data, after attachment, layout and restored scroll state. */
internal fun RecyclerView.positionPlaybackVideo(
    videos: List<HanimeInfo>,
    videoCode: String?,
    isCurrent: () -> Boolean,
) {
    if (videoCode.isNullOrBlank()) return
    val position = videos.indexOfFirst { it.videoCode == videoCode }
    if (position < 0) return
    val expectedAdapter = adapter ?: return
    val list = this
    doOnAttach {
        if (!isCurrent()) return@doOnAttach
        list.doOnLayout {
            // Run outside the layout/restore pass. No fixed delay or repeated auto-scrolling.
            list.post {
                if (!list.isAttachedToWindow || list.adapter !== expectedAdapter ||
                    expectedAdapter.itemCount != videos.size || !isCurrent()) return@post
                val manager = list.layoutManager as? LinearLayoutManager ?: return@post
                list.stopScroll()
                manager.scrollToPositionWithOffset(position, 0)
            }
        }
    }
}
