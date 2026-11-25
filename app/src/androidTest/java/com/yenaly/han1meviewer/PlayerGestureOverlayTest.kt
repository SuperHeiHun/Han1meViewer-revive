package com.yenaly.han1meviewer

import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.jzvd.JZUtils
import com.yenaly.han1meviewer.ui.view.video.PlayerGestureFeedback
import com.yenaly.han1meviewer.ui.view.video.PlayerGestureOverlay
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Unattached views exercise the no-animation fallback without a player or device setting changes. */
@RunWith(AndroidJUnit4::class)
class PlayerGestureOverlayTest {
    private class Fixture {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val overlay = PlayerGestureOverlay(context)
        val panel = overlay.getChildAt(0) as LinearLayout
        val title = panel.getChildAt(0) as TextView
        val detail = panel.getChildAt(1) as TextView

        fun assertRestingTransforms() {
            for (view in listOf(panel, title, detail)) {
                assertEquals(1f, view.alpha, 0f)
                assertEquals(1f, view.scaleX, 0f)
                assertEquals(1f, view.scaleY, 0f)
            }
            assertEquals(0f, panel.translationX, 0f)
            assertEquals(0f, panel.translationY, 0f)
        }
    }

    private fun withOverlay(test: Fixture.() -> Unit) =
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val fixture = Fixture()
            try { fixture.test() } finally { fixture.overlay.finish(true) }
        }

    @Test fun feedbackStartsHiddenAndNeverConsumesTouch() = withOverlay {
        assertEquals(View.GONE, overlay.visibility)
        overlay.show(PlayerGestureFeedback.Volume(50))
        assertFalse(overlay.isClickable)
        assertFalse(overlay.isFocusable)
        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 10f, 10f, 0)
        try { assertFalse(overlay.dispatchTouchEvent(event)) } finally { event.recycle() }
    }

    @Test fun rapidlyChangingValuesAreDisplayedExactlyWithoutCountingAnimations() = withOverlay {
        for (percent in listOf(12, 54, 81, 43)) {
            overlay.show(PlayerGestureFeedback.Brightness(percent))
            assertEquals(context.getString(R.string.gesture_brightness, percent), title.text.toString())
        }
        overlay.show(PlayerGestureFeedback.Volume(67))
        assertEquals(context.getString(R.string.gesture_volume, 67), title.text.toString())
        overlay.show(PlayerGestureFeedback.Speed(2.3f))
        assertEquals(context.getString(R.string.gesture_temporary_speed, 2.3f), title.text.toString())
        assertRestingTransforms()
    }

    @Test fun identicalFeedbackDoesNotReplaceUnchangedText() = withOverlay {
        overlay.show(PlayerGestureFeedback.Volume(50))
        val text = title.text
        overlay.show(PlayerGestureFeedback.Volume(50))
        assertSame(text, title.text)
        assertRestingTransforms()
    }

    @Test fun placementAndDetailsResetWhenTheFeedbackTypeChanges() = withOverlay {
        overlay.show(PlayerGestureFeedback.Seek(32_000L, 120_000L, -10_000L, true))
        assertEquals(Gravity.CENTER_VERTICAL or Gravity.LEFT,
            (panel.layoutParams as FrameLayout.LayoutParams).gravity)
        assertEquals(View.VISIBLE, detail.visibility)
        assertEquals(context.getString(R.string.gesture_seek_target,
            JZUtils.stringForTime(32_000L), JZUtils.stringForTime(120_000L)), title.text.toString())
        overlay.show(PlayerGestureFeedback.Seek(52_000L, 120_000L, 20_000L, true))
        assertEquals(Gravity.CENTER_VERTICAL or Gravity.RIGHT,
            (panel.layoutParams as FrameLayout.LayoutParams).gravity)
        assertEquals(context.getString(R.string.gesture_seek_forward,
            JZUtils.stringForTime(20_000L)), detail.text.toString())
        overlay.show(PlayerGestureFeedback.Speed(2f))
        assertEquals(Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            (panel.layoutParams as FrameLayout.LayoutParams).gravity)
        overlay.show(PlayerGestureFeedback.Volume(70))
        val params = panel.layoutParams as FrameLayout.LayoutParams
        assertEquals(Gravity.CENTER, params.gravity)
        assertEquals(0, params.topMargin)
        assertEquals(View.GONE, detail.visibility)
        assertRestingTransforms()
    }

    @Test fun immediateFinishRestoresPartialAnimationPropertiesBeforeReuse() = withOverlay {
        overlay.show(PlayerGestureFeedback.Speed(2f))
        panel.alpha = 0.3f
        panel.scaleX = 0.95f
        panel.scaleY = 0.95f
        panel.translationX = 6f
        panel.translationY = 4f
        title.alpha = 0.82f
        title.scaleX = 1.06f
        title.scaleY = 1.06f
        overlay.finish(true)
        assertEquals(View.GONE, overlay.visibility)
        assertRestingTransforms()
        overlay.show(PlayerGestureFeedback.Volume(80))
        assertEquals(View.VISIBLE, overlay.visibility)
        assertRestingTransforms()
    }

    @Test fun detachedFinishLeavesNoInvisibleAnimationOrDelayedHideToReuse() = withOverlay {
        overlay.show(PlayerGestureFeedback.Volume(20))
        overlay.finish(false)
        overlay.finish(true)
        assertEquals(View.GONE, overlay.visibility)
        overlay.show(PlayerGestureFeedback.Brightness(90))
        assertEquals(View.VISIBLE, overlay.visibility)
        assertEquals(context.getString(R.string.gesture_brightness, 90), title.text.toString())
        assertRestingTransforms()
    }
}
