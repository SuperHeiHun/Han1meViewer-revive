package com.yenaly.han1meviewer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.jzvd.JZDataSource
import cn.jzvd.JZMediaInterface
import cn.jzvd.Jzvd
import com.yenaly.han1meviewer.logic.entity.HKeyframeEntity
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.player.PlaybackPlaylist
import com.yenaly.han1meviewer.ui.activity.SettingsActivity
import com.yenaly.han1meviewer.ui.view.video.HJzvdStd
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Fake media only: no video/network requests, volume or brightness changes. Not run automatically. */
@RunWith(AndroidJUnit4::class)
class PlayerVideoSwitchTest {
    private fun withPlayer(test: (HJzvdStd, FakeMedia) -> Unit) {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = FrameLayout(activity)
                activity.setContentView(root)
                val player = HJzvdStd(activity)
                root.addView(player, FrameLayout.LayoutParams(900, 600))
                player.videoCode = "a"
                // Match VideoActivity's first Loading -> Success without pre-setting a mode.
                assertEquals(-1, player.screen)
                player.prepareForVideoSwitch()
                player.setUp(JZDataSource("file:///not-opened-a.mp4", "A"), player.screen)
                val media = FakeMedia(player)
                player.mediaInterface = media
                player.onStatePause()
                Jzvd.CURRENT_JZVD = player
                try {
                    test(player, media)
                } finally {
                    if (player.screen == Jzvd.SCREEN_FULLSCREEN) player.gotoNormalScreen()
                    Jzvd.releaseAllVideos()
                }
            }
        }
    }

    @Test fun firstLoadDefaultsToNormalAndPlayingClearsPosterAndLoading() = withPlayer { player, _ ->
        assertEquals(Jzvd.SCREEN_NORMAL, player.screen)
        player.onStatePreparing()
        assertEquals(View.VISIBLE, player.posterImageView.visibility)
        assertEquals(View.VISIBLE, player.loadingProgressBar.visibility)
        player.onStatePlaying()
        assertEquals(View.INVISIBLE, player.posterImageView.visibility)
        assertEquals(View.INVISIBLE, player.loadingProgressBar.visibility)
        assertEquals(View.INVISIBLE, player.bottomContainer.visibility)
        player.onClickUiToggle()
        assertEquals(View.VISIBLE, player.bottomContainer.visibility)
        player.onClickUiToggle()
        assertEquals(View.INVISIBLE, player.bottomContainer.visibility)
        assertEquals(View.INVISIBLE, player.posterImageView.visibility)
        assertEquals(View.INVISIBLE, player.loadingProgressBar.visibility)
    }

    @Test fun retryAfterLoadingKeepsNormalModeAndRestoresStartControls() = withPlayer { player, _ ->
        player.prepareForVideoSwitch()
        player.setUp(JZDataSource("file:///not-opened-a.mp4", "A"), player.screen)
        assertEquals(Jzvd.SCREEN_NORMAL, player.screen)
        assertEquals(Jzvd.STATE_NORMAL, player.state)
        assertTrue(player.startButton.isEnabled)
        assertEquals(View.VISIBLE, player.startButton.visibility)
        assertEquals(View.VISIBLE, player.posterImageView.visibility)
        assertEquals(View.INVISIBLE, player.loadingProgressBar.visibility)
    }

    @Test fun switchingReleasesOldMediaButRetainsTheFullscreenViewAndContainer() = withPlayer { player, media ->
        player.fullscreenButton.performClick()
        val parent = player.parent
        val containers = Jzvd.CONTAINER_LIST.size
        player.prepareForVideoSwitch()
        assertEquals(1, media.releaseCount)
        assertNull(player.mediaInterface)
        assertEquals(Jzvd.SCREEN_FULLSCREEN, player.screen)
        assertSame(parent, player.parent)
        assertEquals(containers, Jzvd.CONTAINER_LIST.size)
        assertFalse(player.startButton.isEnabled)
    }

    @Test fun selectionImmediatelyAfterFullscreenIsNotLostToJzvdDebounce() = withPlayer { player, _ ->
        player.fullscreenButton.performClick()
        val parent = player.parent
        player.prepareForVideoSwitch()
        player.videoCode = "b"
        val next = JZDataSource("file:///not-opened-b.mp4", "B")
        player.setUp(next, player.screen)
        assertSame(next, player.jzDataSource)
        assertSame(parent, player.parent)
        assertEquals(Jzvd.SCREEN_FULLSCREEN, player.screen)
        assertTrue(player.startButton.isEnabled)
        assertEquals("B", player.titleTextView.text.toString())
        player.mediaInterface = FakeMedia(player)
        player.onStatePreparing()
        player.onStatePlaying()
        assertEquals(View.INVISIBLE, player.posterImageView.visibility)
        assertEquals(View.INVISIBLE, player.loadingProgressBar.visibility)
        player.onClickUiToggle()
        assertEquals(View.VISIBLE, player.bottomContainer.visibility)
        assertEquals(Jzvd.SCREEN_FULLSCREEN, player.screen)
        assertSame(parent, player.parent)
    }

    @Test fun failedLoadRetriesDetailsWithoutRestartingOldMedia() = withPlayer { player, media ->
        player.prepareForVideoSwitch()
        var retried = 0
        player.showVideoLoadFailure { retried++ }
        player.mRetryBtn.performClick()
        player.startVideo()
        assertEquals(1, retried)
        assertEquals(0, media.startCount)
        assertNull(player.mediaInterface)
    }

    @Test fun completionIsReportedOnceForTheCompletedVideo() = withPlayer { player, _ ->
        val completed = mutableListOf<String>()
        player.onVideoCompleted = { completed += it }
        player.onCompletion()
        player.onCompletion()
        assertEquals(listOf("a"), completed)
    }

    @Test fun completionKeepsTheFinalOutputAndDoesNotReleaseOrRestartTheMedia() = withPlayer { player, media ->
        val parent = player.parent
        player.onCompletion()
        assertEquals(Jzvd.STATE_AUTO_COMPLETE, player.state)
        assertSame(parent, player.parent)
        assertSame(media, player.mediaInterface)
        assertEquals(0, media.releaseCount)
        assertEquals(0, media.startCount)
    }

    @Test fun settingsEntryExistsInBothNormalAndFullscreenModes() = withPlayer { player, _ ->
        val button = player.findViewById<View>(R.id.playback_settings_button)
        assertEquals(View.VISIBLE, button.visibility)
        player.fullscreenButton.performClick()
        assertEquals(View.VISIBLE, button.visibility)
        assertTrue(button.hasOnClickListeners())
    }

    @Test fun switchingCancelsAReplayScheduledForThePreviousVideo() = withPlayer { player, media ->
        player.onCompletion()
        player.replayCompletedVideo()
        player.prepareForVideoSwitch()
        player.videoCode = "b"
        assertEquals(0, media.startCount)
        assertNull(player.mediaInterface)
    }

    @Test fun videoDimensionsAreNotCarriedIntoTheNextSource() = withPlayer { player, _ ->
        val originalOrientation = Jzvd.FULLSCREEN_ORIENTATION
        var callbacks = 0
        player.onVideoDimensionsChanged = { callbacks++ }
        try {
            player.onVideoSizeChanged(1920, 1080)
            assertEquals(1920 to 1080, player.videoDimensions)
            assertEquals(1, callbacks)
            player.prepareForVideoSwitch()
            assertNull(player.videoDimensions)
            player.onVideoSizeChanged(1080, 1920)
            assertNull(player.videoDimensions)
            assertEquals(1, callbacks)
        } finally {
            Jzvd.FULLSCREEN_ORIENTATION = originalOrientation
        }
    }

    @Test fun loadingResetsManualSeekAndOldKeyframes() = withPlayer { player, _ ->
        player.hKeyframe = HKeyframeEntity("a", "A", mutableListOf(HKeyframeEntity.Keyframe(1000, null)))
        player.seekToInAdvance = 90_000
        player.seekToManulPosition = 80
        player.prepareForVideoSwitch()
        player.videoCode = "b"
        assertNull(player.hKeyframe)
        assertEquals(0L, player.seekToInAdvance)
        assertEquals(-1, player.seekToManulPosition)
        assertEquals(0, player.progressBar.progress)
    }

    @Test fun retryOfSameVideoKeepsAlreadyObservedKeyframes() = withPlayer { player, _ ->
        val frames = HKeyframeEntity("a", "A", mutableListOf(HKeyframeEntity.Keyframe(1000, null)))
        player.hKeyframe = frames
        player.prepareForVideoSwitch()
        player.videoCode = "a"
        assertSame(frames, player.hKeyframe)
    }

    @Test fun playlistEntryIsFullscreenOnlyAndDisappearsWhenQueueIsCleared() = withPlayer { player, _ ->
        val playlist = PlaybackPlaylist("Series", listOf(
            HanimeInfo("A", "", "a", itemType = HanimeInfo.NORMAL),
            HanimeInfo("B", "", "b", itemType = HanimeInfo.NORMAL),
        ))
        val button = player.findViewById<View>(R.id.tv_playback_playlist)
        player.updatePlaybackPlaylist(playlist)
        assertEquals(View.GONE, button.visibility)
        player.fullscreenButton.performClick()
        assertEquals(View.VISIBLE, button.visibility)
        player.updatePlaybackPlaylist(PlaybackPlaylist())
        assertEquals(View.GONE, button.visibility)
    }

    @Test fun cleanupWithoutWifiRegistrationDoesNotUnregisterReceiver() = withPlayer { player, _ ->
        val context = RecordingWifiContext(player.context)
        player.unregisterWifiListener(context)
        assertEquals(0, context.unregisterCount)
    }

    @Test fun repeatedLoadingAndResetUnregisterWifiExactlyOnce() = withPlayer { player, _ ->
        val context = RecordingWifiContext(player.context)
        player.registerWifiListener(context)
        player.registerWifiListener(context)
        assertEquals(1, context.registerCount)
        player.prepareForVideoSwitch()
        player.prepareForVideoSwitch()
        player.reset()
        assertEquals(1, context.unregisterCount)
        player.registerWifiListener(context)
        player.reset()
        assertEquals(2, context.registerCount)
        assertEquals(2, context.unregisterCount)
    }

    /** Records the library's receiver calls without registering a real broadcast receiver. */
    private class RecordingWifiContext(base: Context) : ContextWrapper(base) {
        var registerCount = 0
        var unregisterCount = 0
        private var registeredReceiver: BroadcastReceiver? = null

        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?): Intent? {
            check(registeredReceiver == null)
            registeredReceiver = requireNotNull(receiver)
            registerCount++
            return null
        }

        override fun unregisterReceiver(receiver: BroadcastReceiver) {
            unregisterCount++
            check(receiver === registeredReceiver)
            registeredReceiver = null
        }
    }

    private class FakeMedia(player: HJzvdStd) : JZMediaInterface(player) {
        var releaseCount = 0
        var startCount = 0
        override fun prepare() = Unit
        override fun start() { startCount++ }
        override fun pause() = Unit
        override fun isPlaying() = false
        override fun seekTo(time: Long) = Unit
        override fun release() { releaseCount++ }
        override fun getCurrentPosition() = 42_000L
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
