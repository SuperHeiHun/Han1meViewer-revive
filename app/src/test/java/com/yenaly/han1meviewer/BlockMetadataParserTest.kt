package com.yenaly.han1meviewer

import com.yenaly.han1meviewer.logic.Parser
import org.junit.Assert.*
import org.junit.Test

class BlockMetadataParserTest {
    @Test fun readsAuthorWithoutRequiringAvatarLoginOrVideoUrl() {
        val result = Parser.videoBlockMetadata("""
            <div id="shareBtn-title">A video</div>
            <a id="video-artist-name"> Studio Name </a>
            <div class="single-video-tag"><a href="/search">#標籤 (12)</a></div>
            <div class="single-video-tag"><a href="/search">另一標籤</a></div>
        """.trimIndent())!!
        assertEquals("Studio Name", result.author)
        assertEquals(setOf("標籤", "另一標籤"), result.tags)
    }

    @Test fun errorChallengeAndLoginPagesAreNotAcceptedAsSafeVideos() {
        listOf("", "<title>Just a moment...</title>", "<form>Login</form>", "<h1>Not Found</h1>")
            .forEach { assertNull(Parser.videoBlockMetadata(it)) }
    }

    @Test fun validPageCanHaveNoAuthorOrTags() {
        val result = Parser.videoBlockMetadata("<div id='shareBtn-title'>A video</div>")!!
        assertNull(result.author)
        assertTrue(result.tags.isEmpty())
    }

    @Test fun duplicateAndBlankTagsAreRemoved() {
        val result = Parser.videoBlockMetadata("""
            <div id="shareBtn-title">A video</div>
            <div class="single-video-tag"><a href="/search">#tag (1)</a></div>
            <div class="single-video-tag"><a href="/search">tag</a></div>
            <div class="single-video-tag"><a href="/search"> </a></div>
            <div class="single-video-tag"><span>not a tag link</span></div>
        """.trimIndent())!!
        assertEquals(setOf("tag"), result.tags)
    }
}
