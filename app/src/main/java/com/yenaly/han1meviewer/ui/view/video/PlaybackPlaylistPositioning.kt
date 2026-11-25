package com.yenaly.han1meviewer.ui.view.video

import androidx.core.view.doOnAttach
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import java.util.WeakHashMap

/** Bind a portrait series before its first layout; detached/prefetched rows keep the pending anchor. */
internal class PlaybackSeriesPositioner(private val adapter: RecyclerView.Adapter<*>) {
    private class Request(val position: Int, val itemCount: Int, val isCurrent: () -> Boolean)

    private var request: Request? = null
    private val positioned = WeakHashMap<RecyclerView, Request>()

    fun clear() {
        request = null
        positioned.clear()
    }

    fun submit(videos: List<HanimeInfo>, videoCode: String?, isCurrent: () -> Boolean) {
        clear()
        if (videoCode.isNullOrBlank()) return
        val position = videos.indexOfFirst { it.videoCode == videoCode }
        if (position >= 0) request = Request(position, videos.size, isCurrent)
    }

    fun position(list: RecyclerView) {
        val pending = request ?: return
        if (positioned[list] === pending || list.adapter !== adapter ||
            adapter.itemCount != pending.itemCount || !pending.isCurrent()) return
        val manager = list.layoutManager as? LinearLayoutManager ?: return
        list.stopScroll()
        // Native pending scroll survives measurement, detachment and later attachment.
        // Do not wait for a one-shot View.post callback that can miss an offscreen series row.
        manager.scrollToPositionWithOffset(pending.position, 0)
        positioned[list] = pending
    }
}

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
