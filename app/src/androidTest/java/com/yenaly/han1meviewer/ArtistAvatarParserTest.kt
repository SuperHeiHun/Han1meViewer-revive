package com.yenaly.han1meviewer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yenaly.han1meviewer.logic.Parser
import com.yenaly.han1meviewer.logic.model.HanimeVideo
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArtistAvatarParserTest {
    private val defaultAvatar = "https://example.com/image/icon/artist_avatar_default.jpg"
    private val customAvatar = "https://example.com/image/avatar/author.jpeg"

    private fun parseArtist(avatarMarkup: String): HanimeVideo.Artist {
        val state = Parser.hanimeVideoVer2("""
            <div id="shareBtn-title">Test video</div>
            <div class="video-details-wrapper">
                <a href="https://example.com/user/123">
                    <div>$avatarMarkup</div>
                </a>
                <div>
                    <a id="video-artist-name">Test author</a>
                    <a href="https://example.com/search?genre=3DCG">3DCG</a>
                </div>
            </div>
            <img src="https://example.com/unrelated.jpg">
        """.trimIndent())
        val video = (state as VideoLoadingState.Success<HanimeVideo>).info
        assertNotNull(video.artist)
        return requireNotNull(video.artist)
    }

    @Test fun overlayAvatarIsPreferredAndSignedQueryIsPreserved() {
        val artist = parseArtist("""
            <img id="video-user-avatar" src="$defaultAvatar">
            <img style="position: absolute;" src="$customAvatar?secure=token==,123&amp;size=small">
        """.trimIndent())

        assertEquals("$customAvatar?secure=token==,123&size=small", artist.avatarUrl)
        assertEquals("Test author", artist.name)
        assertEquals("3DCG", artist.genre)
    }

    @Test fun legacyAvatarRemainsSupported() {
        val artist = parseArtist("""
            <img id="video-user-avatar" src="$customAvatar">
        """.trimIndent())

        assertEquals(customAvatar, artist.avatarUrl)
    }

    @Test fun missingCustomAvatarUsesDefaultAvatar() {
        val artist = parseArtist("""
            <img id="video-user-avatar" src="$defaultAvatar">
        """.trimIndent())

        assertEquals(defaultAvatar, artist.avatarUrl)
    }

    @Test fun blankCustomAvatarUsesDefaultAvatar() {
        val artist = parseArtist("""
            <img id="video-user-avatar" src="$defaultAvatar">
            <img src="">
        """.trimIndent())

        assertEquals(defaultAvatar, artist.avatarUrl)
    }

    @Test fun imageInsideNonImageSiblingDoesNotReplaceAuthorAvatar() {
        val artist = parseArtist("""
            <img id="video-user-avatar" src="$defaultAvatar">
            <span><img src="https://example.com/badge.png"></span>
        """.trimIndent())

        assertEquals(defaultAvatar, artist.avatarUrl)
    }
}
