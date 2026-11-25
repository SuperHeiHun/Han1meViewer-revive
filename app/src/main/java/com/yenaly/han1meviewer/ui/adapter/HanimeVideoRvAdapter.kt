package com.yenaly.han1meviewer.ui.adapter

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import coil.load
import com.chad.library.adapter4.BaseDifferAdapter
import com.chad.library.adapter4.viewholder.QuickViewHolder
import com.itxca.spannablex.spannable
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.VIDEO_CODE
import com.yenaly.han1meviewer.VIDEO_LAYOUT_MATCH_PARENT
import com.yenaly.han1meviewer.VIDEO_LAYOUT_WRAP_CONTENT
import com.yenaly.han1meviewer.VideoCoverSize
import com.yenaly.han1meviewer.getHanimeShareText
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.util.showVideoCardMenu
import com.yenaly.han1meviewer.ui.activity.MainActivity
import com.yenaly.han1meviewer.ui.activity.PreviewActivity
import com.yenaly.han1meviewer.ui.activity.SearchActivity
import com.yenaly.han1meviewer.ui.activity.VideoActivity
import com.yenaly.han1meviewer.ui.fragment.home.HomePageFragment
import com.yenaly.yenaly_libs.utils.activity
import com.yenaly.yenaly_libs.utils.copyTextToClipboard
import com.yenaly.yenaly_libs.utils.dp
import com.yenaly.yenaly_libs.utils.showShortToast
import com.yenaly.yenaly_libs.utils.startActivity

/**
 * @project Han1meViewer
 * @author Yenaly Liew
 * @time 2023/11/26 026 17:15
 */
class HanimeVideoRvAdapter(private val videoWidthType: Int = -1) : // videoWidthType is VIDEO_LAYOUT_MATCH_PARENT or VIDEO_LAYOUT_WRAP_CONTENT or nothing
    BaseDifferAdapter<HanimeInfo, QuickViewHolder>(COMPARATOR) {

    private var coverSize: Pair<Int, Int>? = null

    init {
        isStateViewEnable = true
    }

    companion object {
        private object CoverSizePayload

        val COMPARATOR = object : DiffUtil.ItemCallback<HanimeInfo>() {
            override fun areItemsTheSame(
                oldItem: HanimeInfo,
                newItem: HanimeInfo,
            ): Boolean {
                return oldItem.videoCode == newItem.videoCode
            }

            override fun areContentsTheSame(
                oldItem: HanimeInfo,
                newItem: HanimeInfo,
            ): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun getItemViewType(position: Int, list: List<HanimeInfo>): Int {
        return list[position].itemType
    }

    /** Opt-in viewport sizing for the detail related grid; other adapter instances keep their sizing. */
    internal fun updateCoverSize(width: Int, height: Int) {
        require(width > 0 && height > 0)
        val size = width to height
        if (coverSize == size) return
        coverSize = size
        if (itemCount > 0) notifyItemRangeChanged(0, itemCount, CoverSizePayload)
    }

    private fun applyCoverSize(holder: QuickViewHolder) {
        val (width, height) = coverSize ?: return
        val cover = holder.getView<ViewGroup>(R.id.cover_wrapper)
        if (cover.layoutParams.width == width && cover.layoutParams.height == height) return
        cover.updateLayoutParams {
            this.width = width
            this.height = height
        }
    }

    override fun onBindViewHolder(holder: QuickViewHolder, position: Int, item: HanimeInfo?, payloads: List<Any>) {
        if (payloads.isNotEmpty() && payloads.all { it === CoverSizePayload }) {
            applyCoverSize(holder)
        } else {
            super.onBindViewHolder(holder, position, item, payloads)
        }
    }

    override fun onBindViewHolder(holder: QuickViewHolder, position: Int, item: HanimeInfo?) {
        item ?: return
        applyCoverSize(holder)
        // stackoverflow-64362192
        when (getItemViewType(position)) {
            HanimeInfo.SIMPLIFIED -> {
                holder.getView<ImageView>(R.id.cover).load(item.coverUrl) {
                    crossfade(true)
                }
                holder.getView<TextView>(R.id.title).text = item.title
            }

            HanimeInfo.NORMAL -> {
                holder.getView<TextView>(R.id.title).text = item.title
                holder.getView<ImageView>(R.id.cover).load(item.coverUrl) {
                    crossfade(true)
                }
                holder.getView<TextView>(R.id.is_playing).isVisible = item.isPlaying
                holder.getView<TextView>(R.id.duration).text = item.duration
                holder.getView<TextView>(R.id.time).apply {
                    if (item.uploadTime != null) {
                        holder.getView<View>(R.id.icon_time).isGone = false
                        text = item.uploadTime
                    } else {
                        holder.getView<View>(R.id.icon_time).isGone = true
                    }
                }
                holder.getView<TextView>(R.id.views).apply {
                    if (item.views != null) {
                        holder.getView<View>(R.id.icon_views).isGone = false
                        text = item.views
                    } else {
                        holder.getView<View>(R.id.icon_views).isGone = true
                    }
                }
                holder.getView<TextView>(R.id.genre_and_uploader).apply {
                    if (item.genre == null && item.uploader == null) {
                        isGone = true
                        return@apply
                    }
                    isGone = false
                    text = spannable {
                        item.genre.span {
                            margin(4.dp)
                            when (item.genre) {
                                "3D" -> color(Color.rgb(245, 171, 53))
                                "COS" -> color(Color.rgb(165, 55, 253))
                                "同人" -> color(Color.rgb(241, 130, 141))
                                else -> color(Color.RED)
                            }
                        }
                        item.uploader.text()
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int,
    ): QuickViewHolder {
        return if (viewType == HanimeInfo.NORMAL) {
            QuickViewHolder(R.layout.item_hanime_video, parent)
        } else {
            QuickViewHolder(R.layout.item_hanime_video_simplified, parent)
        }.also { viewHolder ->
            when (viewType) {
                HanimeInfo.SIMPLIFIED -> {
                    when (context) {
                        is SearchActivity -> {
                            viewHolder.getView<View>(R.id.frame).widthMatchParent()
                        }

                        is VideoActivity -> when (videoWidthType) {
                            VIDEO_LAYOUT_MATCH_PARENT ->
                                viewHolder.getView<View>(R.id.frame).widthMatchParent()

                            VIDEO_LAYOUT_WRAP_CONTENT ->
                                viewHolder.getView<View>(R.id.frame).widthWrapContent()
                        }
                    }
                    with(VideoCoverSize.Simplified) {
                        viewHolder.getView<ViewGroup>(R.id.cover_wrapper).resizeForVideoCover()
                    }
                }

                HanimeInfo.NORMAL -> {
                    when (context) {
                        is VideoActivity -> when (videoWidthType) {
                            VIDEO_LAYOUT_MATCH_PARENT ->
                                viewHolder.getView<View>(R.id.frame).widthMatchParent()

                            VIDEO_LAYOUT_WRAP_CONTENT ->
                                viewHolder.getView<View>(R.id.frame).widthWrapContent()
                        }

                        is MainActivity -> {
                            val activity = context
                            val fragment = activity.currentFragment
                            if (fragment is HomePageFragment) {
                                viewHolder.getView<View>(R.id.frame).widthWrapContent()
                            }
                        }
                    }
                    with(VideoCoverSize.Normal) {
                        viewHolder.getView<ViewGroup>(R.id.cover_wrapper).resizeForVideoCover()
                    }
                }
            }
            viewHolder.itemView.apply {
                if (context !is PreviewActivity) {
                    setOnClickListener {
                        val position = viewHolder.bindingAdapterPosition
                        val item = getItem(position) ?: return@setOnClickListener
                        if (item.isPlaying) {
                            showShortToast(R.string.watching_this_video_now)
                        } else {
                            val videoCode = item.videoCode
                            context.startVideoActivity(videoCode)
                        }
                    }
                    setOnLongClickListener {
                        val position = viewHolder.bindingAdapterPosition
                        val item = getItem(position) ?: return@setOnLongClickListener true
                        val owner = context.activity as? LifecycleOwner
                        if (owner == null) {
                            copyTextToClipboard(getHanimeShareText(item.title, item.videoCode))
                            showShortToast(R.string.copy_to_clipboard)
                        } else context.showVideoCardMenu(item, owner.lifecycleScope)
                        return@setOnLongClickListener true
                    }
                }
            }
        }
    }

    private fun View.widthMatchParent() = apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun View.widthWrapContent() = apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun Context.startVideoActivity(videoCode: String) {
        val playerActivity = activity as? VideoActivity
        if (playerActivity?.playVideo(videoCode) == true) return
        if (this is SearchActivity) {
            val intent = Intent(this, VideoActivity::class.java).apply {
                putExtra(VIDEO_CODE, videoCode)
            }
            this.subscribeLauncher.launch(intent)
            return
        }
        activity?.startActivity<VideoActivity>(VIDEO_CODE to videoCode)
    }
}
