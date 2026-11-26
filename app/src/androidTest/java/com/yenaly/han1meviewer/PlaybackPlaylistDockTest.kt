package com.yenaly.han1meviewer

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.View
import android.widget.FrameLayout
import android.widget.PopupWindow
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
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic landscape layout and fake media only; no orientation, preference or network changes. */
@RunWith(AndroidJUnit4::class)
class PlaybackPlaylistDockTest {
    private val series = PlaybackPlaylist("Local series", listOf(
        HanimeInfo("A", "", "a", itemType = HanimeInfo.NORMAL),
        HanimeInfo("B", "", "b", itemType = HanimeInfo.NORMAL),
    ))

    private fun withPlayer(test: (HJzvdStd, FakeMedia) -> Unit) {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            lateinit var player: HJzvdStd
            scenario.onActivity { activity ->
                val root = FrameLayout(activity)
                activity.setContentView(root)
                player = HJzvdStd(activity)
                root.addView(player, FrameLayout.LayoutParams(-1, -1))
                player.videoCode = "a"
                player.setUp(JZDataSource("file:///not-opened.mp4", "Local"), Jzvd.SCREEN_NORMAL)
                player.updatePlaybackPlaylist(series)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                player.rootView.layout(0, 0, 1200, 600)
                player.measure(View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
                player.layout(0, 0, 1200, 600)
                player.setScreenFullscreen()
                val media = FakeMedia(player)
                player.mediaInterface = media
                player.onStatePause()
                try { test(player, media) } finally {
                    player.dismissPlayerPanels()
                    player.reset()
                }
            }
        }
    }

    private fun HJzvdStd.popup(field: String): PopupWindow? = HJzvdStd::class.java
        .getDeclaredField(field).apply { isAccessible = true }.get(this) as? PopupWindow

    @Test fun expandingAndCollapsingOnlyResizeTheNativeViewport() = withPlayer { player, media ->
        val surface = View(player.context)
        player.textureViewContainer.addView(surface)
        val session = player.playbackSession
        val parent = player.parent
        val state = player.state
        assertTrue(player.expandPlaybackPlaylist())
        val dock = player.findViewById<View>(R.id.playback_playlist_dock)
        assertEquals(View.VISIBLE, dock.visibility)
        assertTrue(dock.layoutParams.width <= player.rootView.width / 3)
        assertSame(surface, player.textureViewContainer.getChildAt(0))
        assertSame(media, player.mediaInterface)
        assertSame(session, player.playbackSession)
        assertSame(parent, player.parent)
        assertEquals(state, player.state)
        assertTrue(player.collapsePlaybackPlaylist())
        assertEquals(View.GONE, dock.visibility)
        assertEquals(0, media.commands)
    }

    @Test fun landscapePopupCanExpandAndCloseWithoutSelectingAVideo() = withPlayer { player, media ->
        var selections = 0
        player.onPlaylistVideoSelected = { selections++ }
        player.clickPlaybackPlaylist()
        val popup = requireNotNull(player.popup("playlistPopup"))
        val expand = popup.contentView.findViewById<View>(R.id.expand_playlist)
        assertEquals(View.VISIBLE, expand.visibility)
        expand.performClick()
        assertFalse(popup.isShowing)
        assertTrue(player.isPlaylistExpanded)
        player.findViewById<FrameLayout>(R.id.playback_playlist_dock)
            .findViewById<View>(R.id.close_playlist).performClick()
        assertFalse(player.isPlaylistExpanded)
        assertEquals(0, selections)
        assertEquals(0, media.commands)
    }

    @Test fun loadingKeepsTheExpandedPanelButRejectsOldSelections() = withPlayer { player, _ ->
        var selections = 0
        player.onPlaylistVideoSelected = { selections++ }
        player.expandPlaybackPlaylist()
        val dock = player.findViewById<FrameLayout>(R.id.playback_playlist_dock)
        val list = dock.findViewById<RecyclerView>(R.id.rv_playback_playlist)
        val old = requireNotNull(list.adapter)
        val holder = old.createViewHolder(list, old.getItemViewType(1))
        @Suppress("UNCHECKED_CAST")
        (old as RecyclerView.Adapter<RecyclerView.ViewHolder>).bindViewHolder(holder, 1)
        player.prepareForVideoSwitch()
        player.videoCode = "b"
        holder.itemView.performClick()
        assertTrue(player.isPlaylistExpanded)
        assertEquals(0, selections)
    }

    @Test fun switchingWithinASeriesKeepsTheDockAndUpdatesTheCurrentItem() = withPlayer { player, _ ->
        player.expandPlaybackPlaylist()
        player.prepareForVideoSwitch()
        player.videoCode = "b"
        player.setUp(JZDataSource("file:///not-opened-b.mp4", "B"), Jzvd.SCREEN_FULLSCREEN)
        player.updatePlaybackPlaylist(series)
        assertTrue(player.isPlaylistExpanded)
        val list = player.findViewById<FrameLayout>(R.id.playback_playlist_dock)
            .findViewById<RecyclerView>(R.id.rv_playback_playlist)
        @Suppress("UNCHECKED_CAST")
        val adapter = requireNotNull(list.adapter) as RecyclerView.Adapter<RecyclerView.ViewHolder>
        val holder = adapter.createViewHolder(list, adapter.getItemViewType(1))
        adapter.bindViewHolder(holder, 1)
        assertEquals(View.VISIBLE, holder.itemView.findViewById<View>(R.id.is_playing).visibility)
    }

    @Test fun openingSettingsCollapsesTheDockWithoutTouchingTheMedia() = withPlayer { player, media ->
        player.expandPlaybackPlaylist()
        player.clickPlaybackSettings()
        assertFalse(player.isPlaylistExpanded)
        assertTrue(requireNotNull(player.popup("controlsPopup")).isShowing)
        assertEquals(0, media.commands)
    }

    @Test fun normalModeAndEmptySeriesCannotKeepAnExpandedPlaylist() = withPlayer { player, _ ->
        player.expandPlaybackPlaylist()
        player.setScreenNormal()
        assertFalse(player.isPlaylistExpanded)
        assertFalse(player.expandPlaybackPlaylist())
        player.setScreenFullscreen()
        player.updatePlaybackPlaylist(PlaybackPlaylist(null, emptyList()))
        assertFalse(player.expandPlaybackPlaylist())
    }

    @Test fun backAndDismissCollapseThePanelBeforeLeavingFullscreen() = withPlayer { player, media ->
        player.expandPlaybackPlaylist()
        player.clickBack()
        assertFalse(player.isPlaylistExpanded)
        assertEquals(Jzvd.SCREEN_FULLSCREEN, player.screen)
        player.expandPlaybackPlaylist()
        player.dismissPlayerPanels()
        assertFalse(player.isPlaylistExpanded)
        assertFalse(player.collapsePlaybackPlaylist())
        assertEquals(0, media.commands)
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
