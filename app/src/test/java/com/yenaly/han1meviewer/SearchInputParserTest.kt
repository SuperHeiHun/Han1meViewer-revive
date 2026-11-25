package com.yenaly.han1meviewer

import com.yenaly.han1meviewer.logic.search.SearchInputParser
import com.yenaly.han1meviewer.logic.search.quotedSearchText
import com.yenaly.han1meviewer.logic.search.tagSearchText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchInputParserTest {
    private val parser = SearchInputParser(mapOf(
        "1080p" to "1080p",
        "60FPS" to "60FPS",
        "ASMR" to "ASMR",
        "中文字幕" to "中文字幕",
        "Chinese Subtitle" to "中文字幕",
        "示例標籤" to "示例標籤",
        "示例标签" to "示例標籤",
    ))

    @Test fun emptyInputHasNoQueryOrTags() {
        for (text in listOf(null, "", " \t\u3000")) {
            val result = parser.parse(text)
            assertNull(result.query)
            assertTrue(result.tags.isEmpty())
        }
    }

    @Test fun spaceSeparatedTagsUseSeparateFilters() {
        val result = parser.parse("1080p 60FPS")
        assertNull(result.query)
        assertEquals(setOf("1080p", "60FPS"), result.tags)
    }

    @Test fun mixedInputKeepsTitleWordsInOrder() {
        val result = parser.parse("A title 1080p with 60FPS spaces")
        assertEquals("A title with spaces", result.query)
        assertEquals(setOf("1080p", "60FPS"), result.tags)
    }

    @Test fun ordinaryMultiwordTitleRemainsATitle() {
        val result = parser.parse("A title with spaces")
        assertEquals("A title with spaces", result.query)
        assertTrue(result.tags.isEmpty())
    }

    @Test fun tagsAreNotInferredFromTitleSubstrings() {
        val result = parser.parse("ASMRcollection episode1080p")
        assertEquals("ASMRcollection episode1080p", result.query)
        assertTrue(result.tags.isEmpty())
    }

    @Test fun quotedTitleCannotBecomeATag() {
        val result = parser.parse("1080p \"ASMR Chinese Subtitle\"")
        assertEquals("ASMR Chinese Subtitle", result.query)
        assertEquals(setOf("1080p"), result.tags)
    }

    @Test fun longestAliasRecognizesEnglishTagNames() {
        val result = parser.parse("Chinese Subtitle 60fps A title")
        assertEquals("A title", result.query)
        assertEquals(setOf("中文字幕", "60FPS"), result.tags)
    }

    @Test fun simplifiedAndTraditionalAliasesUseCanonicalKeys() {
        val result = parser.parse("示例标签 示例標籤")
        assertNull(result.query)
        assertEquals(setOf("示例標籤"), result.tags)
    }

    @Test fun fullWidthAndRepeatedWhitespaceAreSupported() {
        val result = parser.parse("１０８０Ｐ\u3000\u3000６０ｆｐｓ\tTitle")
        assertEquals("Title", result.query)
        assertEquals(setOf("1080p", "60FPS"), result.tags)
    }

    @Test fun unknownTagsRequireAnExplicitPrefix() {
        val result = parser.parse("#新标签 ＃另一标签 title")
        assertEquals("title", result.query)
        assertEquals(setOf("新标签", "另一标签"), result.tags)
    }

    @Test fun explicitQuotedTagSupportsSpaces() {
        val result = parser.parse("#\"Custom Tag\" #\"Chinese Subtitle\" title")
        assertEquals("title", result.query)
        assertEquals(setOf("Custom Tag", "中文字幕"), result.tags)
    }

    @Test fun duplicateAliasesAndExplicitTagsAreDeduplicated() {
        val result = parser.parse("1080p #1080p １０８０Ｐ")
        assertEquals(setOf("1080p"), result.tags)
    }

    @Test fun standaloneHashRemainsTitleText() {
        val result = parser.parse("# title")
        assertEquals("# title", result.query)
        assertTrue(result.tags.isEmpty())
    }

    @Test fun generatedTagInputRoundTripsSpacesQuotesAndBackslashes() {
        val tag = "Custom \"Tag\" \\ demo"
        val result = parser.parse(tagSearchText(tag))
        assertNull(result.query)
        assertEquals(setOf(tag), result.tags)
    }

    @Test fun generatedTitleInputDoesNotTurnAnArtistNameIntoTags() {
        val name = "ASMR \"Chinese Subtitle\" \\ Artist"
        val result = parser.parse(quotedSearchText(name))
        assertEquals(name, result.query)
        assertTrue(result.tags.isEmpty())
    }

    @Test fun emptyQuotedTitlesDoNotAffectTheRemainingInput() {
        val result = parser.parse("\"\" 1080p Title")
        assertEquals("Title", result.query)
        assertEquals(setOf("1080p"), result.tags)
    }

    @Test fun emptyExplicitQuotedTagDoesNotCreateAnEmptyTagFilter() {
        val result = parser.parse("#\"\" Title")
        assertEquals("Title", result.query)
        assertTrue(result.tags.isEmpty())
    }
}
