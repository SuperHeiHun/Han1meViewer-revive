package com.yenaly.han1meviewer

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yenaly.han1meviewer.ui.view.video.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Uses a fake host: no video, network, window brightness or device volume is changed. */
@RunWith(AndroidJUnit4::class)
class PlayerGestureControllerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private class Host : PlayerGestureController.Host {
        override var mediaIdentity: Any? = Any()
        override val position = 42_000L // Deliberately stale, like an asynchronous media snapshot.
        override var duration = 120_000L
        override val normalSpeed = 1.25f
        override val canSeek get() = true
        override val canLongPress get() = playing
        override val canAdjustLevels = false
        override var canEnterFullscreen = false
        override var canEnterPortraitFullscreen = false
        val fullscreenRequests = mutableListOf<Boolean>()
        var playing = false
        var controls = 0
        var playbackToggles = 0
        val seeks = mutableListOf<Long>()
        val speeds = mutableListOf<Float>()
        var lastFeedback: PlayerGestureFeedback? = null
        var feedbackHidden = true
        var speedShown: CountDownLatch? = null
        override fun toggleControls() { controls++ }
        override fun togglePlayback() { playbackToggles++; playing = !playing }
        override fun enterFullscreen(portrait: Boolean) { fullscreenRequests += portrait }
        override fun seekTo(position: Long) { seeks += position }
        override fun setTemporarySpeed(speed: Float) { speeds += speed }
        override fun beginInteraction() = Unit
        override fun endInteraction() = Unit
        override fun showFeedback(feedback: PlayerGestureFeedback) {
            lastFeedback = feedback
            feedbackHidden = false
            if (feedback is PlayerGestureFeedback.Speed) speedShown?.countDown()
        }
        override fun finishFeedback(immediate: Boolean) { if (immediate) feedbackHidden = true }
    }

    private class Fixture(var config: PlayerGestureConfig) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val surface = View(context).apply { layout(0, 0, 1_000, 600) }
        val host = Host()
        val controller = PlayerGestureController(surface, { config }, host)
        private var time = SystemClock.uptimeMillis()
        private var downTime = time
        fun touch(action: Int, x: Float = 800f, y: Float = 300f) {
            time += 30
            if (action == MotionEvent.ACTION_DOWN) downTime = time
            val event = MotionEvent.obtain(downTime, time, action, x, y, 0)
            try { controller.onTouch(event) } finally { event.recycle() }
        }
        fun tap(x: Float = 800f) {
            touch(MotionEvent.ACTION_DOWN, x)
            touch(MotionEvent.ACTION_UP, x)
        }
    }

    private fun withFixture(
        config: PlayerGestureConfig = PlayerGestureConfig(doubleTapMode = PlayerDoubleTapMode.BOTH),
        test: Fixture.() -> Unit,
    ) = instrumentation.runOnMainSync {
        val fixture = Fixture(config)
        try { fixture.test() } finally { fixture.controller.cancel() }
    }

    @Test fun repeatedTapsAccumulateAgainstThePendingTargetAndKeepPause() = withFixture {
        tap(); tap(); tap()
        assertEquals(listOf(52_000L, 62_000L), host.seeks)
        assertEquals(62_000L, controller.displayPosition(42_000L))
        assertEquals(0, host.playbackToggles)
        assertFalse(host.playing)
    }

    @Test fun draggingPreviewsWithoutSeekingUntilTheSingleRelease() = withFixture {
        touch(MotionEvent.ACTION_DOWN, 400f)
        touch(MotionEvent.ACTION_MOVE, 600f)
        assertTrue(host.seeks.isEmpty())
        assertTrue(host.lastFeedback is PlayerGestureFeedback.Seek)
        touch(MotionEvent.ACTION_UP, 600f)
        assertEquals(listOf(46_800L), host.seeks)
        assertFalse(host.playing)
    }

    @Test fun canceledDragDoesNotCommitItsPreview() = withFixture {
        touch(MotionEvent.ACTION_DOWN, 400f)
        touch(MotionEvent.ACTION_MOVE, 600f)
        touch(MotionEvent.ACTION_CANCEL, 600f)
        touch(MotionEvent.ACTION_UP, 600f)
        assertTrue(host.seeks.isEmpty())
        assertTrue(host.feedbackHidden)
    }

    @Test fun aSecondPointerCancelsAnUncommittedSeek() = withFixture {
        touch(MotionEvent.ACTION_DOWN, 400f)
        touch(MotionEvent.ACTION_MOVE, 600f)
        touch(MotionEvent.ACTION_POINTER_DOWN, 600f)
        touch(MotionEvent.ACTION_UP, 600f)
        assertTrue(host.seeks.isEmpty())
    }

    @Test fun disabledHorizontalSeekingCannotCommitOnRelease() = withFixture {
        config = config.copy(seekEnabled = false)
        touch(MotionEvent.ACTION_DOWN, 400f)
        touch(MotionEvent.ACTION_MOVE, 600f)
        touch(MotionEvent.ACTION_UP, 600f)
        assertTrue(host.seeks.isEmpty())
    }

    @Test fun centerDownwardSwipeRequestsPortraitOnceOnRelease() = withFixture {
        host.canEnterFullscreen = true
        host.canEnterPortraitFullscreen = true
        touch(MotionEvent.ACTION_DOWN, 500f, 100f)
        touch(MotionEvent.ACTION_MOVE, 500f, 280f)
        assertTrue(host.fullscreenRequests.isEmpty())
        touch(MotionEvent.ACTION_UP, 500f, 350f)
        touch(MotionEvent.ACTION_UP, 500f, 380f)
        assertEquals(listOf(true), host.fullscreenRequests)
        assertTrue(host.seeks.isEmpty())
        assertEquals(0, host.playbackToggles)
        assertTrue(host.feedbackHidden)
    }

    @Test fun centerUpwardSwipeKeepsTheOriginalFullscreenAction() = withFixture {
        host.canEnterFullscreen = true
        host.canEnterPortraitFullscreen = true
        touch(MotionEvent.ACTION_DOWN, 500f, 450f)
        touch(MotionEvent.ACTION_UP, 500f, 100f)
        assertEquals(listOf(false), host.fullscreenRequests)
    }

    @Test fun cancelOrSecondPointerDiscardsPortraitFullscreen() = withFixture {
        host.canEnterFullscreen = true
        host.canEnterPortraitFullscreen = true
        for (cancel in listOf(MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN)) {
            touch(MotionEvent.ACTION_DOWN, 500f, 100f)
            touch(MotionEvent.ACTION_MOVE, 500f, 280f)
            touch(cancel, 500f, 280f)
            touch(MotionEvent.ACTION_UP, 500f, 350f)
        }
        assertTrue(host.fullscreenRequests.isEmpty())
        assertTrue(host.seeks.isEmpty())
    }

    @Test fun mediaReplacementOrLostPermissionPreventsPortraitCommit() = withFixture {
        host.canEnterFullscreen = true
        host.canEnterPortraitFullscreen = true
        touch(MotionEvent.ACTION_DOWN, 500f, 100f)
        touch(MotionEvent.ACTION_MOVE, 500f, 280f)
        host.mediaIdentity = Any()
        touch(MotionEvent.ACTION_UP, 500f, 350f)
        touch(MotionEvent.ACTION_DOWN, 500f, 100f)
        touch(MotionEvent.ACTION_MOVE, 500f, 280f)
        host.canEnterPortraitFullscreen = false
        touch(MotionEvent.ACTION_UP, 500f, 350f)
        assertTrue(host.fullscreenRequests.isEmpty())
    }

    @Test fun mediaReplacementClearsTheTapChainAndPendingTarget() = withFixture {
        tap(); tap()
        assertEquals(52_000L, controller.displayPosition(42_000L))
        host.mediaIdentity = Any()
        tap()
        assertEquals(listOf(52_000L), host.seeks)
        assertEquals(42_000L, controller.displayPosition(42_000L))
        assertEquals(0, host.playbackToggles)
    }

    @Test fun legacyDefaultStillTogglesPlaybackAtTheSide() = withFixture(PlayerGestureConfig()) {
        tap(100f); tap(100f)
        assertEquals(1, host.playbackToggles)
        assertTrue(host.seeks.isEmpty())
    }

    @Test fun unknownDurationDoesNotDisableCenterPlayPause() = withFixture {
        host.duration = 0
        tap(500f); tap(500f)
        assertEquals(1, host.playbackToggles)
        tap(800f); tap(800f)
        assertTrue(host.seeks.isEmpty())
    }

    @Test fun disabledDoubleTapLeavesSingleTapControlsAvailable() = withFixture(
        PlayerGestureConfig(doubleTapMode = PlayerDoubleTapMode.NONE),
    ) {
        tap(); tap()
        assertEquals(2, host.controls)
        assertEquals(0, host.playbackToggles)
    }

    private fun withLongPress(mode: PlayerLongPressMode, test: Fixture.() -> Unit) {
        lateinit var fixture: Fixture
        val started = CountDownLatch(1)
        instrumentation.runOnMainSync {
            fixture = Fixture(PlayerGestureConfig(longPressMode = mode))
            fixture.host.playing = true
            fixture.host.speedShown = started
            fixture.touch(MotionEvent.ACTION_DOWN, 500f)
        }
        try {
            assertTrue("Long press did not fire", started.await(3, TimeUnit.SECONDS))
            instrumentation.runOnMainSync { fixture.test() }
        } finally {
            instrumentation.runOnMainSync { fixture.controller.cancel() }
        }
    }

    @Test fun longPressRestoresTheOriginalNonDefaultSpeedOnRelease() = withLongPress(PlayerLongPressMode.LEGACY) {
        assertEquals(3.125f, host.speeds.last(), 0f)
        touch(MotionEvent.ACTION_UP, 500f)
        assertEquals(listOf(3.125f, 1.25f), host.speeds)
        assertTrue(host.feedbackHidden)
    }

    @Test fun horizontalMotionAfterLongPressChangesSpeedWithoutSeeking() = withLongPress(PlayerLongPressMode.VARIABLE) {
        val x = 500f + 96f * context.resources.displayMetrics.density
        touch(MotionEvent.ACTION_MOVE, x)
        assertEquals(2.2f, host.speeds.last(), 0.0001f)
        assertTrue(host.seeks.isEmpty())
        touch(MotionEvent.ACTION_CANCEL, x)
        assertEquals(1.25f, host.speeds.last(), 0f)
    }

    @Test fun pausingDuringLongPressRestoresSpeedWithoutResuming() = withLongPress(PlayerLongPressMode.FIXED) {
        host.playing = false
        controller.onPlaybackPaused()
        assertEquals(listOf(2f, 1.25f), host.speeds)
        assertFalse(host.playing)
        assertEquals(0, host.playbackToggles)
        assertTrue(host.feedbackHidden)
    }

    @Test fun restorationDoesNotWriteAnOldSpeedIntoAReplacementMedia() = withLongPress(PlayerLongPressMode.FIXED) {
        host.mediaIdentity = Any()
        controller.cancel()
        assertEquals(listOf(2f), host.speeds)
        assertFalse(controller.isTemporarySpeedActive)
    }

    @Test fun cancelClearsPendingSingleTapAndLongPressCallbacks() {
        lateinit var fixture: Fixture
        val drained = CountDownLatch(1)
        instrumentation.runOnMainSync {
            fixture = Fixture(PlayerGestureConfig())
            fixture.host.playing = true
            fixture.tap()
            fixture.touch(MotionEvent.ACTION_DOWN)
            fixture.controller.cancel()
            Handler(Looper.getMainLooper()).postDelayed({ drained.countDown() },
                maxOf(ViewConfiguration.getLongPressTimeout(), ViewConfiguration.getDoubleTapTimeout()).toLong() + 100)
        }
        try {
            assertTrue(drained.await(3, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                assertEquals(0, fixture.host.controls)
                assertTrue(fixture.host.speeds.isEmpty())
                assertTrue(fixture.host.seeks.isEmpty())
            }
        } finally {
            instrumentation.runOnMainSync { fixture.controller.cancel() }
        }
    }
}
