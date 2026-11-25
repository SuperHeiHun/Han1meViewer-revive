package com.yenaly.han1meviewer

import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.jzvd.JZDataSource
import cn.jzvd.JZUtils
import cn.jzvd.Jzvd
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.player.PlaybackPlaylist
import com.yenaly.han1meviewer.ui.activity.SettingsActivity
import com.yenaly.han1meviewer.ui.view.video.HJzvdStd
import com.yenaly.han1meviewer.ui.view.video.playerPanelLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Local, nonplaying views only. No video/network requests or orientation/preference changes. */
@RunWith(AndroidJUnit4::class)
class PlayerBottomPanelTest {
    private fun withPlayer(test: (HJzvdStd) -> Unit) {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            lateinit var player: HJzvdStd
            scenario.onActivity { activity ->
                val root = FrameLayout(activity)
                activity.setContentView(root)
                player = HJzvdStd(activity)
                root.addView(player, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 160))
                player.videoCode = "a"
                player.setUp(JZDataSource("file:///not-opened.mp4", "Local"), Jzvd.SCREEN_NORMAL)
                player.updatePlaybackPlaylist(PlaybackPlaylist("Local series", listOf(
                    HanimeInfo("A", "", "a", itemType = HanimeInfo.NORMAL),
                    HanimeInfo("B", "", "b", itemType = HanimeInfo.NORMAL),
                )))
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                try {
                    test(player)
                } finally {
                    player.dismissPlayerPanels()
                    player.reset()
                }
            }
        }
    }

    private fun HJzvdStd.popup(field: String): PopupWindow? = HJzvdStd::class.java
        .getDeclaredField(field).apply { isAccessible = true }.get(this) as? PopupWindow

    private fun assertBottomHalf(player: HJzvdStd, popup: PopupWindow) {
        assertTrue(popup.isShowing)
        assertEquals(player.rootView.width, popup.width)
        assertEquals(player.rootView.height / 2, popup.height)
        assertEquals(R.style.PlayerBottomPanelAnimation, popup.animationStyle)
        val params = popup.contentView.rootView.layoutParams as WindowManager.LayoutParams
        assertEquals(Gravity.BOTTOM, params.gravity and Gravity.VERTICAL_GRAVITY_MASK)
    }

    @Test fun inlineSettingsUseTheWindowHalfInsteadOfTheShortPlayerHeight() = withPlayer { player ->
        player.clickPlaybackSettings()
        val popup = requireNotNull(player.popup("controlsPopup"))
        assertBottomHalf(player, popup)
        assertNotEquals(player.height / 2, popup.height)
    }

    @Test fun fullscreenSettingsAlsoUseBottomHalfAndKeepScrollableControls() = withPlayer { player ->
        player.setScreenFullscreen()
        player.clickPlaybackSettings()
        val popup = requireNotNull(player.popup("controlsPopup"))
        assertBottomHalf(player, popup)
        val mode = popup.contentView.findViewById<View>(R.id.playback_mode_choice)
        val scroll = (mode.parent as View).parent
        assertTrue(scroll is ScrollView)
        assertNotNull(popup.contentView.findViewById<View>(R.id.playback_aspect_choice))
        assertNotNull(popup.contentView.findViewById<View>(R.id.playback_auto_rotate))
    }

    @Test fun playlistChoosesTheCurrentWindowLayoutAndPreservesItsItems() = withPlayer { player ->
        player.setScreenFullscreen()
        player.clickPlaybackPlaylist()
        val popup = requireNotNull(player.popup("playlistPopup"))
        val expected = requireNotNull(playerPanelLayout(player.rootView.width, player.rootView.height,
            JZUtils.dip2px(player.context, 360f)))
        assertEquals(expected.width, popup.width)
        assertEquals(expected.height, popup.height)
        if (expected.fromBottom) assertBottomHalf(player, popup)
        val list = popup.contentView.findViewById<RecyclerView>(R.id.rv_playback_playlist)
        assertEquals(2, list.adapter?.itemCount)
        assertNotNull(popup.contentView.findViewById<View>(R.id.playback_playlist_controls))
    }

    @Test fun settingsAndPlaylistRemainMutuallyExclusive() = withPlayer { player ->
        player.setScreenFullscreen()
        player.clickPlaybackPlaylist()
        val playlist = requireNotNull(player.popup("playlistPopup"))
        player.clickPlaybackSettings()
        assertFalse(playlist.isShowing)
        assertNull(player.popup("playlistPopup"))
        val settings = requireNotNull(player.popup("controlsPopup"))
        player.clickPlaybackPlaylist()
        assertFalse(settings.isShowing)
        assertNull(player.popup("controlsPopup"))
        assertTrue(requireNotNull(player.popup("playlistPopup")).isShowing)
    }

    @Test fun closeButtonDoesNotStartOrSwitchTheMedia() = withPlayer { player ->
        val state = player.state
        player.clickPlaybackSettings()
        val popup = requireNotNull(player.popup("controlsPopup"))
        popup.contentView.findViewById<View>(R.id.close_playback_settings).performClick()
        assertFalse(popup.isShowing)
        assertNull(player.popup("controlsPopup"))
        assertNull(player.mediaInterface)
        assertEquals(state, player.state)
        assertEquals("a", player.videoCode)
    }

    @Test fun sizeChangesDismissTheOldPanelInsteadOfKeepingOldDimensions() = withPlayer { player ->
        player.clickPlaybackSettings()
        val popup = requireNotNull(player.popup("controlsPopup"))
        player.layout(player.left, player.top, player.right - 1, player.bottom)
        assertFalse(popup.isShowing)
        assertNull(player.popup("controlsPopup"))
    }
}
