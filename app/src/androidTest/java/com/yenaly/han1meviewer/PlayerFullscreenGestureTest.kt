package com.yenaly.han1meviewer

import android.graphics.SurfaceTexture
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.os.SystemClock
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.getSystemService
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.jzvd.JZDataSource
import cn.jzvd.JZMediaInterface
import cn.jzvd.Jzvd
import com.yenaly.han1meviewer.ui.activity.SettingsActivity
import com.yenaly.han1meviewer.ui.view.video.HJzvdStd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerFullscreenGestureTest {
    private fun withPlayer(test: (HJzvdStd, TestMediaKernel) -> Unit) {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = FrameLayout(activity)
                activity.setContentView(root)
                val player = HJzvdStd(activity)
                root.addView(player, FrameLayout.LayoutParams(900, 600))
                player.setUp(JZDataSource("file:///gesture-test.mp4", "Gesture test"), Jzvd.SCREEN_NORMAL)
                val media = TestMediaKernel(player)
                player.mediaInterface = media
                player.onStatePause()
                Jzvd.CURRENT_JZVD = player
                root.measure(
                    View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                )
                root.layout(0, 0, 900, 600)
                val audioManager = activity.getSystemService<AudioManager>()!!
                val originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val originalOrientation = Jzvd.FULLSCREEN_ORIENTATION
                try {
                    test(player, media)
                } finally {
                    if (player.screen == Jzvd.SCREEN_FULLSCREEN) player.gotoNormalScreen()
                    Jzvd.releaseAllVideos()
                    Jzvd.FULLSCREEN_ORIENTATION = originalOrientation
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
                }
            }
        }
    }

    private fun HJzvdStd.touch(action: Int, x: Float, y: Float, downTime: Long, elapsed: Long) {
        val event = MotionEvent.obtain(downTime, downTime + elapsed, action, x, y, 0)
        try {
            dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    @Test
    fun upwardCenterSwipeEntersFullscreenWithoutChangingPlaybackOrLevels() = withPlayer { player, media ->
        val audioManager = player.context.getSystemService<AudioManager>()!!
        val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val activity = player.context as SettingsActivity
        val brightness = activity.window.attributes.screenBrightness
        val downTime = SystemClock.uptimeMillis()
        player.touch(MotionEvent.ACTION_DOWN, 450f, 450f, downTime, 0)
        player.touch(MotionEvent.ACTION_MOVE, 452f, 430f, downTime, 20)
        player.touch(MotionEvent.ACTION_MOVE, 455f, 200f, downTime, 80)
        assertEquals(Jzvd.SCREEN_NORMAL, player.screen)
        player.touch(MotionEvent.ACTION_UP, 455f, 150f, downTime, 120)
        assertEquals(Jzvd.SCREEN_FULLSCREEN, player.screen)
        assertEquals(Jzvd.STATE_PAUSE, player.state)
        assertSame(media, player.mediaInterface)
        assertEquals(42_000L, media.positionMs)
        assertEquals(0, media.seekCount)
        assertEquals(volume, audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
        assertEquals(brightness, activity.window.attributes.screenBrightness, 0f)
        player.backButton.performClick()
        assertEquals(Jzvd.SCREEN_NORMAL, player.screen)
        assertSame(media, player.mediaInterface)
        assertEquals(42_000L, media.positionMs)
    }

    @Test
    fun playingVideoStaysPlayingAfterFullscreenSwipe() = withPlayer { player, media ->
        media.start()
        player.onStatePlaying()
        val downTime = SystemClock.uptimeMillis()
        player.touch(MotionEvent.ACTION_DOWN, 450f, 450f, downTime, 0)
        player.touch(MotionEvent.ACTION_MOVE, 450f, 200f, downTime, 80)
        player.touch(MotionEvent.ACTION_UP, 450f, 150f, downTime, 120)
        assertEquals(Jzvd.SCREEN_FULLSCREEN, player.screen)
        assertEquals(Jzvd.STATE_PLAYING, player.state)
        assertEquals(true, media.isPlaying())
        assertSame(media, player.mediaInterface)
        assertEquals(42_000L, media.positionMs)
    }

    @Test
    fun downwardCenterSwipeUsesPortraitAndRestoresTheOriginalUpwardDirectionOnExit() = withPlayer { player, media ->
        assumeTrue(player.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
        val activity = player.context as SettingsActivity
        player.onVideoSizeChanged(1920, 1080)
        val downTime = SystemClock.uptimeMillis()
        player.touch(MotionEvent.ACTION_DOWN, 450f, 150f, downTime, 0)
        player.touch(MotionEvent.ACTION_MOVE, 450f, 330f, downTime, 80)
        assertEquals(Jzvd.SCREEN_NORMAL, player.screen)
        player.touch(MotionEvent.ACTION_UP, 450f, 450f, downTime, 120)
        assertEquals(Jzvd.SCREEN_FULLSCREEN, player.screen)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, activity.requestedOrientation)
        assertTrue(player.isPortraitFullscreenRequested)
        assertSame(media, player.mediaInterface)
        assertEquals(Jzvd.STATE_PAUSE, player.state)
        assertEquals(42_000L, media.positionMs)
        assertEquals(0, media.seekCount)
        player.onVideoSizeChanged(1920, 1080)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Jzvd.FULLSCREEN_ORIENTATION)
        player.backButton.performClick()
        assertEquals(Jzvd.SCREEN_NORMAL, player.screen)
        assertFalse(player.isPortraitFullscreenRequested)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, Jzvd.FULLSCREEN_ORIENTATION)
    }

    @Test
    fun portraitSwipeOverrideDoesNotApplyToTheNextVideoSession() = withPlayer { player, _ ->
        assumeTrue(player.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
        val downTime = SystemClock.uptimeMillis()
        player.touch(MotionEvent.ACTION_DOWN, 450f, 150f, downTime, 0)
        player.touch(MotionEvent.ACTION_UP, 450f, 450f, downTime, 120)
        assertTrue(player.isPortraitFullscreenRequested)
        player.prepareForVideoSwitch()
        player.setUp(JZDataSource("file:///next-not-opened.mp4", "Next"), player.screen)
        player.mediaInterface = TestMediaKernel(player)
        player.onStatePause()
        player.onVideoSizeChanged(1920, 1080)
        assertEquals(Jzvd.SCREEN_FULLSCREEN, player.screen)
        assertFalse(player.isPortraitFullscreenRequested)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, Jzvd.FULLSCREEN_ORIENTATION)
    }

    @Test
    fun canceledSwipeDoesNotEnterFullscreen() = withPlayer { player, _ ->
        val downTime = SystemClock.uptimeMillis()
        player.touch(MotionEvent.ACTION_DOWN, 450f, 450f, downTime, 0)
        player.touch(MotionEvent.ACTION_MOVE, 450f, 150f, downTime, 80)
        player.touch(MotionEvent.ACTION_CANCEL, 450f, 150f, downTime, 100)
        player.touch(MotionEvent.ACTION_UP, 450f, 100f, downTime, 120)
        assertEquals(Jzvd.SCREEN_NORMAL, player.screen)
    }

    @Test
    fun shortCenterSwipeDoesNotEnterFullscreenOrSeek() = withPlayer { player, media ->
        val downTime = SystemClock.uptimeMillis()
        player.touch(MotionEvent.ACTION_DOWN, 450f, 450f, downTime, 0)
        player.touch(MotionEvent.ACTION_MOVE, 450f, 425f, downTime, 30)
        player.touch(MotionEvent.ACTION_UP, 450f, 420f, downTime, 60)
        assertEquals(Jzvd.SCREEN_NORMAL, player.screen)
        assertEquals(0, media.seekCount)
    }

    @Test
    fun horizontalCenterSwipeStillSeeksInsteadOfEnteringFullscreen() = withPlayer { player, media ->
        val downTime = SystemClock.uptimeMillis()
        player.touch(MotionEvent.ACTION_DOWN, 450f, 450f, downTime, 0)
        player.touch(MotionEvent.ACTION_MOVE, 600f, 448f, downTime, 80)
        player.touch(MotionEvent.ACTION_UP, 600f, 448f, downTime, 120)
        assertEquals(Jzvd.SCREEN_NORMAL, player.screen)
        assertEquals(1, media.seekCount)
    }

    @Test
    fun secondPointerCancelsFullscreenSwipe() = withPlayer { player, _ ->
        val downTime = SystemClock.uptimeMillis()
        player.touch(MotionEvent.ACTION_DOWN, 450f, 450f, downTime, 0)
        player.touch(MotionEvent.ACTION_MOVE, 450f, 200f, downTime, 50)
        val properties = Array(2) { index ->
            MotionEvent.PointerProperties().apply {
                id = index
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }
        val coordinates = Array(2) { index ->
            MotionEvent.PointerCoords().apply {
                x = 450f + index * 20f
                y = 200f
                pressure = 1f
                size = 1f
            }
        }
        val event = MotionEvent.obtain(
            downTime, downTime + 60,
            MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            2, properties, coordinates, 0, 0, 1f, 1f, 0, 0, 0, 0,
        )
        try {
            player.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
        player.touch(MotionEvent.ACTION_UP, 450f, 150f, downTime, 100)
        assertEquals(Jzvd.SCREEN_NORMAL, player.screen)
    }

    private class TestMediaKernel(player: HJzvdStd) : JZMediaInterface(player) {
        var positionMs = 42_000L
        var seekCount = 0
        private var playing = false
        override fun prepare() = Unit
        override fun start() {
            playing = true
        }
        override fun pause() {
            playing = false
        }
        override fun isPlaying() = playing
        override fun seekTo(time: Long) {
            positionMs = time
            seekCount++
        }
        override fun release() = Unit
        override fun getCurrentPosition() = positionMs
        override fun getDuration() = 120_000L
        override fun setVolume(left: Float, right: Float) = Unit
        override fun setSpeed(speed: Float) = Unit
        override fun setSurface(surface: Surface?) = Unit
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) = Unit
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture) = true
        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
    }
}
