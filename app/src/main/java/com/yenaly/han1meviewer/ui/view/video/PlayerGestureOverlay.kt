package com.yenaly.han1meviewer.ui.view.video

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import cn.jzvd.JZUtils
import com.yenaly.han1meviewer.R
import kotlin.math.abs

/** Non-clickable feedback only: player buttons underneath retain their own touch targets. */
class PlayerGestureOverlay @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {

    private val density = resources.displayMetrics.density
    private val title = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 18f
        gravity = Gravity.CENTER
        maxLines = 2
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val detail = TextView(context).apply {
        setTextColor(Color.LTGRAY)
        textSize = 13f
        gravity = Gravity.CENTER
        maxLines = 2
    }
    private val panel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(10), dp(16), dp(10))
        background = GradientDrawable().apply {
            setColor(0xCC101010.toInt())
            cornerRadius = dp(8).toFloat()
        }
        addView(title, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(detail, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }
    private val hide = Runnable { visibility = View.GONE }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(panel, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        visibility = View.GONE
    }

    internal fun show(feedback: PlayerGestureFeedback) {
        removeCallbacks(hide)
        val params = panel.layoutParams as LayoutParams
        params.gravity = Gravity.CENTER
        params.topMargin = 0
        params.leftMargin = dp(12)
        params.rightMargin = dp(12)
        detail.visibility = View.GONE
        when (feedback) {
            is PlayerGestureFeedback.Seek -> {
                title.text = context.getString(
                    R.string.gesture_seek_target,
                    JZUtils.stringForTime(feedback.target), JZUtils.stringForTime(feedback.duration),
                )
                detail.text = context.getString(
                    if (feedback.offset < 0) R.string.gesture_seek_backward else R.string.gesture_seek_forward,
                    JZUtils.stringForTime(abs(feedback.offset)),
                )
                detail.visibility = View.VISIBLE
                if (feedback.discrete) {
                    params.gravity = Gravity.CENTER_VERTICAL or if (feedback.offset < 0) Gravity.LEFT else Gravity.RIGHT
                }
            }
            is PlayerGestureFeedback.Volume -> title.text = context.getString(R.string.gesture_volume, feedback.percent)
            is PlayerGestureFeedback.Brightness -> title.text = context.getString(R.string.gesture_brightness, feedback.percent)
            is PlayerGestureFeedback.Speed -> {
                title.text = context.getString(R.string.gesture_temporary_speed, feedback.speed)
                params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                params.topMargin = dp(64)
            }
        }
        panel.layoutParams = params
        visibility = View.VISIBLE
    }

    internal fun finish(immediate: Boolean) {
        removeCallbacks(hide)
        if (immediate) visibility = View.GONE else postDelayed(hide, 700L)
    }

    override fun onDetachedFromWindow() {
        finish(true)
        super.onDetachedFromWindow()
    }

    private fun dp(value: Int) = (value * density + 0.5f).toInt()
}
