package com.yenaly.han1meviewer.ui.view.video

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.appcompat.widget.AppCompatSpinner

/** Never forwards a held/dragged finger into the dropdown; keyboard/accessibility clicks remain native. */
internal class TapOnlySpinner @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.spinnerStyle,
) : AppCompatSpinner(context, attrs, defStyleAttr) {
    private val tap = PlayerSelectionTap(ViewConfiguration.get(context).scaledTouchSlop.toFloat())

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tap.down(event.x, event.y)
                isPressed = true
            }
            MotionEvent.ACTION_MOVE -> {
                tap.move(event.x, event.y)
                if (!tap.eligible) isPressed = false
            }
            MotionEvent.ACTION_UP -> {
                val click = tap.up(event.x, event.y, event.eventTime - event.downTime,
                    ViewConfiguration.getLongPressTimeout().toLong())
                isPressed = false
                if (click) performClick()
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> {
                tap.cancel()
                isPressed = false
            }
        }
        return true
    }
}
