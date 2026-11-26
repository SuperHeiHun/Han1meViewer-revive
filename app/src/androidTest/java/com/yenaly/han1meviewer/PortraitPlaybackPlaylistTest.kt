package com.yenaly.han1meviewer

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.View
import android.widget.FrameLayout
import android.widget.PopupWindow
import android.widget.RelativeLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.jzvd.JZDataSource
import cn.jzvd.JZMediaInterface
import cn.jzvd.Jzvd
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.player.PlaybackPlaylist
import com.yenaly.han1meviewer.ui.activity.SettingsActivity
import com.yenaly.han1meviewer.ui.view.video.HJzvdStd
import com.yenaly.han1meviewer.ui.view.video.PlayerPanelBounds
import com.yenaly.han1meviewer.ui.view.video.playerDetailsPanelBounds
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Local portrait geometry and fake media only; no video, network, preference or orientation changes. */
@RunWith(AndroidJUnit4::class)
class PortraitPlaybackPlaylistTest {
    private val series = PlaybackPlaylist("Local series", listOf(
        HanimeInfo("A", "", "a", itemType = HanimeInfo.NORMAL),
        HanimeInfo("B", "", "b", itemType = HanimeInfo.NORMAL),
    ))

    private fun withPlayer(test: (HJzvdStd, FakeMedia, FrameLayout) -> Unit) {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            lateinit var player: HJzvdStd
            lateinit var root: FrameLayout
            lateinit var details: FrameLayout
            scenario.onActivity { activity ->
                root = FrameLayout(activity)
                activity.setContentView(root)
                player = HJzvdStd(activity)
                root.addView(player, FrameLayout.LayoutParams(-1, 250))
                details = FrameLayout(activity)
                root.addView(details, FrameLayout.LayoutParams(-1, -1).apply { topMargin = 320 })
                player.playbackDetailsAnchor = details
                player.videoCode = "a"
                player.setUp(JZDataSource("file:///not-opened.mp4", "Local"), Jzvd.SCREEN_NORMAL)
                player.updatePlaybackPlaylist(series)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                player.rootView.layout(0, 0, 600, 1000)
                root.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, 600, 900)
                details.layout(0, 320, 600, 900)
                val media = FakeMedia(player)
                player.mediaInterface = media
                player.onStatePause()
                try { test(player, media, details) } finally {
                    player.dismissPlayerPanels()
                    player.reset()
                }
            }
        }
    }

    private fun HJzvdStd.popup(field: String = "playlistPopup"): PopupWindow? = HJzvdStd::class.java
        .getDeclaredField(field).apply { isAccessible = true }.get(this) as? PopupWindow

    private fun HJzvdStd.episode(code: String): RecyclerView.ViewHolder {
        val list = requireNotNull(popup()).contentView.findViewById<RecyclerView>(R.id.rv_playback_playlist)
        @Suppress("UNCHECKED_CAST")
        val adapter = requireNotNull(list.adapter) as RecyclerView.Adapter<RecyclerView.ViewHolder>
        val index = series.orderedVideos(Preferences.playbackPlaylistReversed).indexOfFirst { it.videoCode == code }
        return adapter.createViewHolder(list, adapter.getItemViewType(index)).also { adapter.bindViewHolder(it, index) }
    }

    @Test fun expandedListMatchesTheVisibleDetailsAreaBelowTheVideo() = withPlayer { player, media, details ->
        val position = IntArray(2)
        val origin = IntArray(2)
        details.getLocationInWindow(position)
        player.rootView.getLocationInWindow(origin)
        val expected = requireNotNull(playerDetailsPanelBounds(player.rootView.width, player.rootView.height,
            PlayerPanelBounds(position[0] - origin[0], position[1] - origin[1], details.width, details.height)))
        assertTrue(player.expandPortraitPlaybackPlaylist())
        val popup = requireNotNull(player.popup())
        assertEquals(expected.width, popup.width)
        assertEquals(expected.height, popup.height)
        assertEquals(255, popup.contentView.background.alpha)
        assertEquals(R.style.PlayerBottomPanelAnimation, popup.animationStyle)
        assertEquals(0, media.commands)
    }

    @Test fun openingAndClosingDoNotResizeReparentOrRestartThePlayer() = withPlayer { player, media, _ ->
        val surface = View(player.context)
        player.textureViewContainer.addView(surface)
        val session = player.playbackSession
        val parent = player.parent
        val height = player.height
        val width = player.width
        val state = player.state
        player.expandPortraitPlaybackPlaylist()
        requireNotNull(player.popup()).contentView.findViewById<View>(R.id.close_playlist).performClick()
        assertNull(player.popup())
        assertSame(parent, player.parent)
        assertSame(surface, player.textureViewContainer.getChildAt(0))
        assertSame(media, player.mediaInterface)
        assertSame(session, player.playbackSession)
        assertEquals(height, player.height)
        assertEquals(width, player.width)
        assertEquals(state, player.state)
        assertEquals(0, media.commands)
    }

    @Test fun selectingAnEpisodeReportsItOnceAndClosesTheExpandedList() = withPlayer { player, media, _ ->
        val selections = mutableListOf<String>()
        player.onPlaylistVideoSelected = { selections += it }
        player.expandPortraitPlaybackPlaylist()
        val episode = player.episode("b")
        episode.itemView.performClick()
        episode.itemView.performClick()
        assertEquals(listOf("b"), selections)
        assertNull(player.popup())
        assertEquals(0, media.commands)
    }

    @Test fun currentEpisodeIsMarkedAndDoesNotRestartPlayback() = withPlayer { player, media, _ ->
        var selections = 0
        player.onPlaylistVideoSelected = { selections++ }
        player.expandPortraitPlaybackPlaylist()
        val episode = player.episode("a")
        assertEquals(View.VISIBLE, episode.itemView.findViewById<View>(R.id.is_playing).visibility)
        episode.itemView.performClick()
        assertTrue(requireNotNull(player.popup()).isShowing)
        assertEquals(0, selections)
        assertEquals(0, media.commands)
    }

    @Test fun reopeningRejectsSelectionsFromTheOldPopup() = withPlayer { player, _, _ ->
        var selections = 0
        player.onPlaylistVideoSelected = { selections++ }
        player.expandPortraitPlaybackPlaylist()
        val old = requireNotNull(player.popup())
        val episode = player.episode("b")
        player.expandPortraitPlaybackPlaylist()
        episode.itemView.performClick()
        assertFalse(old.isShowing)
        assertNotSame(old, player.popup())
        assertEquals(0, selections)
    }

    @Test fun switchingOrLoadingClosesThePopupAndRejectsOldSelections() = withPlayer { player, _, _ ->
        var selections = 0
        player.onPlaylistVideoSelected = { selections++ }
        player.expandPortraitPlaybackPlaylist()
        val episode = player.episode("b")
        player.prepareForVideoSwitch()
        player.videoCode = "b"
        episode.itemView.performClick()
        assertNull(player.popup())
        assertFalse(player.expandPortraitPlaybackPlaylist())
        assertEquals(0, selections)
    }

    @Test fun absentHiddenAndUnmeasuredDetailsNeverCoverTheVideo() = withPlayer { player, _, details ->
        details.visibility = View.GONE
        assertFalse(player.expandPortraitPlaybackPlaylist())
        details.visibility = View.VISIBLE
        details.layout(0, 320, 0, 900)
        assertFalse(player.expandPortraitPlaybackPlaylist())
        player.playbackDetailsAnchor = null
        assertFalse(player.expandPortraitPlaybackPlaylist())
        assertNull(player.popup())
    }

    @Test fun landscapeKeepsTheOriginalFullscreenSidePopupAndPlayerLayout() = withPlayer { player, media, _ ->
        player.rootView.layout(0, 0, 1200, 600)
        assertFalse(player.expandPortraitPlaybackPlaylist())
        player.setScreenFullscreen()
        player.clickPlaybackPlaylist()
        val popup = requireNotNull(player.popup())
        assertTrue(popup.width <= player.rootView.width / 2)
        assertEquals(player.rootView.height, popup.height)
        assertEquals(cn.jzvd.R.style.pop_animation, popup.animationStyle)
        assertTrue(player.textureViewContainer.parent is RelativeLayout)
        assertEquals(0, media.commands)
    }

    @Test fun detailsResizeDismissesTheOldExpandedPanel() = withPlayer { player, media, details ->
        player.expandPortraitPlaybackPlaylist()
        val popup = requireNotNull(player.popup())
        details.layout(details.left, details.top, details.right, details.bottom - 1)
        assertFalse(popup.isShowing)
        assertNull(player.popup())
        assertEquals(0, media.commands)
    }

    @Test fun settingsAndExpandedPlaylistRemainMutuallyExclusive() = withPlayer { player, media, _ ->
        player.expandPortraitPlaybackPlaylist()
        val list = requireNotNull(player.popup())
        player.clickPlaybackSettings()
        val settings = requireNotNull(player.popup("controlsPopup"))
        assertFalse(list.isShowing)
        player.expandPortraitPlaybackPlaylist()
        assertFalse(settings.isShowing)
        assertTrue(requireNotNull(player.popup()).isShowing)
        assertEquals(0, media.commands)
    }

    @Test fun emptySeriesOrFullscreenCannotOpenAnInlineExpandedList() = withPlayer { player, _, _ ->
        player.setScreenFullscreen()
        assertFalse(player.expandPortraitPlaybackPlaylist())
        player.setScreenNormal()
        player.updatePlaybackPlaylist(PlaybackPlaylist())
        assertFalse(player.expandPortraitPlaybackPlaylist())
        assertNull(player.popup())
    }

    private class FakeMedia(player: HJzvdStd) : JZMediaInterface(player) {
        var commands = 0
        override fun prepare() { commands++ }
        override fun start() { commands++ }
        override fun pause() { commands++ }
        override fun isPlaying() = false
        override fun seekTo(time: Long) { commands++ }
        override fun release() { commands++ }
        override fun getCurrentPosition() = 42_000L
        override fun getDuration() = 120_000L
        override fun setVolume(left: Float, right: Float) { commands++ }
        override fun setSpeed(speed: Float) { commands++ }
        override fun setSurface(surface: Surface?) = Unit
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) = Unit
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture) = true
        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
    }
}
