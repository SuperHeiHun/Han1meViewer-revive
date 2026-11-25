package com.yenaly.han1meviewer.ui.view.video

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.SystemClock
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
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
    private val labels = listOf(title, detail)
    private val enterInterpolator = DecelerateInterpolator()
    private val exitInterpolator = AccelerateInterpolator()
    private var animationGeneration = 0
    private var hiding = false
    private var lastPulseTime = 0L
    private val hide = Runnable { animateOut() }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(panel, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        visibility = View.GONE
    }

    internal fun show(feedback: PlayerGestureFeedback) {
        removeCallbacks(hide)
        val appearing = visibility != View.VISIBLE
        val params = panel.layoutParams as LayoutParams
        var panelGravity = Gravity.CENTER
        var topMargin = 0
        var detailText: String? = null
        val titleText = when (feedback) {
            is PlayerGestureFeedback.Seek -> {
                detailText = context.getString(
                    if (feedback.offset < 0) R.string.gesture_seek_backward else R.string.gesture_seek_forward,
                    JZUtils.stringForTime(abs(feedback.offset)),
                )
                if (feedback.discrete) {
                    panelGravity = Gravity.CENTER_VERTICAL or if (feedback.offset < 0) Gravity.LEFT else Gravity.RIGHT
                }
                context.getString(
                    R.string.gesture_seek_target,
                    JZUtils.stringForTime(feedback.target), JZUtils.stringForTime(feedback.duration),
                )
            }
            is PlayerGestureFeedback.Volume -> context.getString(R.string.gesture_volume, feedback.percent)
            is PlayerGestureFeedback.Brightness -> context.getString(R.string.gesture_brightness, feedback.percent)
            is PlayerGestureFeedback.Speed -> {
                panelGravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = dp(64)
                context.getString(R.string.gesture_temporary_speed, feedback.speed)
            }
        }
        val titleChanged = title.text.toString() != titleText
        val detailChanged = detailText != null && detail.text.toString() != detailText
        val detailVisibility = if (detailText == null) View.GONE else View.VISIBLE
        val contentChanged = titleChanged || detailChanged || detail.visibility != detailVisibility
        // Values stay exact; only their presentation animates, never the seek or speed itself.
        if (titleChanged) title.text = titleText
        if (detailChanged) detail.text = detailText
        detail.visibility = detailVisibility
        val moved = params.gravity != panelGravity || params.topMargin != topMargin
        val margin = dp(12)
        if (moved || params.leftMargin != margin || params.rightMargin != margin) {
            params.gravity = panelGravity
            params.topMargin = topMargin
            params.leftMargin = margin
            params.rightMargin = margin
            panel.layoutParams = params
        }
        visibility = View.VISIBLE
        when {
            !shouldAnimateFeedback() -> resetAnimations()
            appearing || moved -> animateIn(feedback, restart = true)
            hiding -> animateIn(feedback, restart = false)
            contentChanged || feedback is PlayerGestureFeedback.Seek && feedback.discrete -> pulse(feedback)
        }
    }

    internal fun finish(immediate: Boolean) {
        removeCallbacks(hide)
        if (immediate || !isAttachedToWindow) {
            hideImmediately()
        } else if (visibility == View.VISIBLE && !hiding) {
            postDelayed(hide, 700L)
        }
    }

    private fun animateIn(feedback: PlayerGestureFeedback, restart: Boolean) {
        animationGeneration++
        hiding = false
        panel.animate().cancel()
        resetLabels()
        if (restart) {
            panel.alpha = 0f
            panel.scaleX = 0.94f
            panel.scaleY = 0.94f
            panel.translationX = if (feedback is PlayerGestureFeedback.Seek && feedback.discrete) {
                dp(6) * feedback.offset.compareTo(0L).toFloat()
            } else 0f
            panel.translationY = if (feedback is PlayerGestureFeedback.Speed) -dp(4).toFloat() else dp(4).toFloat()
        }
        lastPulseTime = SystemClock.uptimeMillis()
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f).translationX(0f).translationY(0f)
            .setStartDelay(0L).setDuration(160L).setInterpolator(enterInterpolator).withEndAction(null).start()
    }

    private fun pulse(feedback: PlayerGestureFeedback) {
        val discrete = feedback is PlayerGestureFeedback.Seek && feedback.discrete
        val now = SystemClock.uptimeMillis()
        // Motion events can arrive every frame; do not continually restart the value animation.
        if (!discrete && now - lastPulseTime < 140L) return
        lastPulseTime = now
        for (label in labels) {
            if (label.visibility != View.VISIBLE) continue
            label.animate().cancel()
            label.alpha = 0.82f
            label.scaleX = if (discrete) 1.06f else 1.025f
            label.scaleY = label.scaleX
            label.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setStartDelay(0L).setDuration(140L).setInterpolator(enterInterpolator).start()
        }
    }

    private fun animateOut() {
        if (visibility != View.VISIBLE || hiding) return
        if (!shouldAnimateFeedback()) {
            hideImmediately()
            return
        }
        val generation = ++animationGeneration
        hiding = true
        panel.animate().cancel()
        resetLabels()
        panel.animate().alpha(0f).scaleX(0.97f).scaleY(0.97f).translationY(dp(2).toFloat())
            .setStartDelay(0L).setDuration(140L).setInterpolator(exitInterpolator).withEndAction {
                // A newer gesture must not be hidden by an older fade-out callback.
                if (generation == animationGeneration && hiding) hideImmediately()
            }.start()
    }

    private fun hideImmediately() {
        removeCallbacks(hide)
        resetAnimations()
        visibility = View.GONE
    }

    private fun resetAnimations() {
        animationGeneration++
        hiding = false
        lastPulseTime = 0L
        panel.animate().cancel()
        panel.alpha = 1f
        panel.scaleX = 1f
        panel.scaleY = 1f
        panel.translationX = 0f
        panel.translationY = 0f
        resetLabels()
    }

    private fun resetLabels() {
        for (label in labels) {
            label.animate().cancel()
            label.alpha = 1f
            label.scaleX = 1f
            label.scaleY = 1f
        }
    }

    private fun shouldAnimateFeedback() = isAttachedToWindow &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled())

    override fun onDetachedFromWindow() {
        finish(true)
        super.onDetachedFromWindow()
    }

    private fun dp(value: Int) = (value * density + 0.5f).toInt()
}
