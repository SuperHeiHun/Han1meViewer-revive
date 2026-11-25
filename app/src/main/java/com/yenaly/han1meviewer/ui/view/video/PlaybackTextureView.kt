package com.yenaly.han1meviewer.ui.view.video

import android.content.Context
import cn.jzvd.JZTextureView
import com.yenaly.han1meviewer.logic.player.PlaybackAspectRatio
import com.yenaly.han1meviewer.logic.player.playbackTextureSize

/** Keeps aspect changes local to this output, without replacing its surface or media. */
internal class PlaybackTextureView(context: Context) : JZTextureView(context) {
    var aspect = PlaybackAspectRatio.FIT
        set(value) { if (field != value) { field = value; requestLayout() } }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val rotated = rotation.toInt().mod(180) != 0
        val width = MeasureSpec.getSize(if (rotated) heightMeasureSpec else widthMeasureSpec)
        val height = MeasureSpec.getSize(if (rotated) widthMeasureSpec else heightMeasureSpec)
        val size = playbackTextureSize(width, height, currentVideoWidth, currentVideoHeight, aspect)
        setMeasuredDimension(size.first, size.second)
    }
}
