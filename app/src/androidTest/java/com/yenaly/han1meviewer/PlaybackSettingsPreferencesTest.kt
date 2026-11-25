package com.yenaly.han1meviewer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yenaly.han1meviewer.logic.player.*
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Preferences only; preserve the user's values and never start playback. */
@RunWith(AndroidJUnit4::class)
class PlaybackSettingsPreferencesTest {
    private fun isolated(test: () -> Unit) {
        val sp = Preferences.preferenceSp
        val keys = listOf(Preferences.PLAYBACK_MODE, Preferences.PLAYBACK_ASPECT_RATIO,
            Preferences.PLAYBACK_AUTO_ROTATE, Preferences.AUTO_FULLSCREEN, Preferences.LEGACY_AUTO_PLAY_NEXT)
        val original = sp.all.filterKeys { it in keys }
        try {
            sp.edit().apply { keys.forEach { remove(it) } }.commit()
            test()
        } finally {
            sp.edit().apply {
                keys.forEach { remove(it) }
                original.forEach { (key, value) -> when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                } }
            }.commit()
        }
    }

    @Test fun defaultsArePauseFitAndNoAutomaticRotation() = isolated {
        assertEquals(PlaybackSettings(), Preferences.playbackSettings)
    }

    @Test fun valuesPersistGloballyAndAreNotAssociatedWithAVideoCode() = isolated {
        Preferences.playbackMode = PlaybackMode.REPEAT_ONE
        Preferences.playbackAspectRatio = PlaybackAspectRatio.RATIO_4_3
        Preferences.playbackAutoRotate = true
        assertEquals("repeat_one", Preferences.preferenceSp.getString(Preferences.PLAYBACK_MODE, null))
        assertEquals(PlaybackSettings(PlaybackMode.REPEAT_ONE, PlaybackAspectRatio.RATIO_4_3, true), Preferences.playbackSettings)
    }

    @Test fun upgradeReadsLegacyChoiceUntilANewModeIsSelected() = isolated {
        Preferences.preferenceSp.edit().putBoolean(Preferences.LEGACY_AUTO_PLAY_NEXT, true).commit()
        assertEquals(PlaybackMode.LIST_LOOP, Preferences.playbackMode)
        Preferences.playbackMode = PlaybackMode.PAUSE_AT_END
        assertFalse(Preferences.preferenceSp.contains(Preferences.LEGACY_AUTO_PLAY_NEXT))
        assertEquals(PlaybackMode.PAUSE_AT_END, Preferences.playbackMode)
    }

    @Test fun newModeTakesPrecedenceEvenIfAnOldValueIsPresent() = isolated {
        Preferences.preferenceSp.edit().putString(Preferences.PLAYBACK_MODE, "repeat_one")
            .putBoolean(Preferences.LEGACY_AUTO_PLAY_NEXT, true).commit()
        assertEquals(PlaybackMode.REPEAT_ONE, Preferences.playbackMode)
    }

    @Test fun observerIncludesAllPlaybackSettingsAndDirectRestore() = isolated {
        runBlocking {
            val values = Channel<PlaybackSettings>(Channel.UNLIMITED)
            val job = launch(start = CoroutineStart.UNDISPATCHED) {
                Preferences.observePlaybackSettings().collect { values.send(it) }
            }
            try {
                assertEquals(PlaybackSettings(), withTimeout(5_000) { values.receive() })
                Preferences.playbackMode = PlaybackMode.LIST_LOOP
                assertEquals(PlaybackMode.LIST_LOOP, withTimeout(5_000) { values.receive() }.mode)
                Preferences.playbackAspectRatio = PlaybackAspectRatio.CROP
                assertEquals(PlaybackAspectRatio.CROP, withTimeout(5_000) { values.receive() }.aspectRatio)
                Preferences.playbackAutoRotate = true
                assertTrue(withTimeout(5_000) { values.receive() }.autoRotate)
                Preferences.autoFullscreen = true
                assertTrue(withTimeout(5_000) { values.receive() }.autoFullscreen)
                Preferences.preferenceSp.edit().putBoolean(Preferences.AUTO_FULLSCREEN, false).commit()
                assertFalse(withTimeout(5_000) { values.receive() }.autoFullscreen)
                Preferences.preferenceSp.edit().putBoolean(Preferences.PLAYBACK_AUTO_ROTATE, false).commit()
                assertFalse(withTimeout(5_000) { values.receive() }.autoRotate)
            } finally {
                job.cancelAndJoin()
                values.close()
            }
        }
    }

    @Test fun aFreshSubscriptionReadsTheLatestSettings() = isolated {
        runBlocking {
            assertEquals(PlaybackSettings(), Preferences.observePlaybackSettings().first())
            Preferences.playbackMode = PlaybackMode.REPEAT_ONE
            assertEquals(PlaybackMode.REPEAT_ONE, Preferences.observePlaybackSettings().first().mode)
        }
    }

    @Test fun automaticFullscreenPersistsWithoutEnablingAutoRotate() = isolated {
        assertFalse(Preferences.autoFullscreen)
        Preferences.autoFullscreen = true
        assertTrue(Preferences.preferenceSp.getBoolean(Preferences.AUTO_FULLSCREEN, false))
        assertTrue(Preferences.playbackSettings.autoFullscreen)
        assertFalse(Preferences.playbackAutoRotate)
        Preferences.autoFullscreen = false
        assertFalse(Preferences.playbackSettings.autoFullscreen)
    }

    @Test fun aNewSubscriptionReadsARestoredAutomaticFullscreenValue() = isolated {
        Preferences.preferenceSp.edit().putBoolean(Preferences.AUTO_FULLSCREEN, true).commit()
        runBlocking {
            assertTrue(Preferences.observePlaybackSettings().first().autoFullscreen)
        }
    }
}
