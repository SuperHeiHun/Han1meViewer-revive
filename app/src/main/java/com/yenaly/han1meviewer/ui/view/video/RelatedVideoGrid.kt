package com.yenaly.han1meviewer.ui.view.video

import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.ui.adapter.HanimeVideoRvAdapter
import kotlin.math.roundToInt

internal data class RelatedVideoGridSpec(val columns: Int, val coverWidth: Int, val coverHeight: Int)

internal fun relatedVideoGridSpec(
    availableWidth: Int,
    preferredCoverWidth: Int,
    preferredCoverHeight: Int,
    minimumColumns: Int,
    horizontalInsets: Int,
): RelatedVideoGridSpec? {
    require(preferredCoverWidth > 0 && preferredCoverHeight > 0 && minimumColumns > 0 && horizontalInsets >= 0)
    if (availableWidth <= 0) return null
    val columns = (availableWidth / preferredCoverWidth).coerceAtLeast(minimumColumns)
    val width = (availableWidth / columns - horizontalInsets).coerceIn(1, preferredCoverWidth)
    val height = (preferredCoverHeight.toLong() * width / preferredCoverWidth).toInt().coerceAtLeast(1)
    return RelatedVideoGridSpec(columns, width, height)
}

/** Only the detail page's related grid follows its real viewport, including hidden fullscreen loads. */
internal class RelatedVideoGridController(
    private val list: RecyclerView,
    private val manager: GridLayoutManager,
    private val adapter: HanimeVideoRvAdapter,
) {
    private var itemType = HanimeInfo.SIMPLIFIED
    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> refresh() }

    init {
        list.addOnLayoutChangeListener(layoutListener)
        refresh()
    }

    fun setVideoType(type: Int) {
        itemType = type
        refresh()
    }

    private fun refresh() {
        val resources = list.resources
        val normal = itemType == HanimeInfo.NORMAL
        val spec = relatedVideoGridSpec(
            availableWidth = list.width - list.paddingLeft - list.paddingRight,
            preferredCoverWidth = resources.getDimensionPixelSize(
                if (normal) R.dimen.video_cover_width else R.dimen.video_cover_simplified_width,
            ),
            preferredCoverHeight = resources.getDimensionPixelSize(
                if (normal) R.dimen.video_cover_height else R.dimen.video_cover_simplified_height,
            ),
            minimumColumns = if (normal) 2 else 3,
            // Normal cards have 4dp padding on each side; simplified cards have 4dp margins.
            horizontalInsets = 2 * (4 * resources.displayMetrics.density).roundToInt(),
        ) ?: return
        if (manager.spanCount != spec.columns) manager.spanCount = spec.columns
        adapter.updateCoverSize(spec.coverWidth, spec.coverHeight)
    }

    fun dispose() {
        list.removeOnLayoutChangeListener(layoutListener)
    }
}
