package com.yenaly.han1meviewer

import com.yenaly.han1meviewer.logic.search.SearchInputParser
import com.yenaly.han1meviewer.logic.search.SearchTag
import com.yenaly.han1meviewer.logic.search.TagCompleter
import com.yenaly.han1meviewer.logic.search.searchTagAliases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TagCompleterTest {
    private val tags = listOf(
        SearchTag("1080p", "1080p", setOf("1080p")),
        SearchTag("60FPS", "60FPS", setOf("60FPS")),
        SearchTag("ASMR extended", "ASMR extended", setOf("ASMR extended")),
        SearchTag("ASMR", "ASMR", setOf("ASMR")),
        SearchTag("中文字幕", "中文字幕", setOf("中文字幕", "Chinese Subtitle")),
        SearchTag("示例標籤", "示例标签", setOf("示例标签", "示例標籤", "Example Tag")),
    )
    private val completer = TagCompleter(tags)
    private val parser = SearchInputParser(tags.searchTagAliases())

    @Test fun emptyInputAndWhitespaceDoNotSuggestUnrequestedTags() {
        for (text in listOf("", " ", "title ")) {
            assertTrue(completer.suggest(text, text.length).isEmpty())
        }
    }

    @Test fun prefixMatchingIgnoresCaseAndFullWidth() {
        assertEquals(listOf("60FPS"), completer.suggest("６０ｆ", 3).map { it.tag.key })
    }

    @Test fun simplifiedAndTraditionalPrefixesResolveToTheSameTag() {
        for (text in listOf("示例标", "示例標")) {
            assertEquals(listOf("示例標籤"), completer.suggest(text, text.length).map { it.tag.key })
        }
    }

    @Test fun matchingMultipleAliasesDoesNotDuplicateSuggestions() {
        assertEquals(listOf("示例標籤"), completer.suggest("示例", 2).map { it.tag.key })
    }

    @Test fun exactAliasIsRankedBeforeLongerPrefixes() {
        assertEquals("ASMR", completer.suggest("asmr", 4).first().tag.key)
    }

    @Test fun englishMultiwordCompletionPreservesThePrecedingTitle() {
        val text = "A title Chinese Sub"
        val result = completer.suggest(text, text.length).single().apply()
        assertEquals("A title #\"中文字幕\" ", result.first)
        assertEquals("A title", parser.parse(result.first).query)
        assertEquals(setOf("中文字幕"), parser.parse(result.first).tags)
    }

    @Test fun multiwordCompletionAcceptsFullWidthSeparators() {
        val text = "Chinese\u3000Sub"
        assertEquals("#\"中文字幕\" ", completer.suggest(text, text.length).single().apply().first)
    }

    @Test fun quotedTitlesIncludingUnclosedQuotesAreNeverReplaced() {
        for (text in listOf("\"AS\"", "\"AS", "1080p \"Chinese Sub", "\"\"")) {
            assertTrue(completer.suggest(text, text.length).isEmpty())
        }
    }

    @Test fun explicitTagCanCompleteInsideQuotes() {
        val text = "Title #\"Chinese Sub\" 60FPS"
        val caret = text.indexOf("Sub") + 3
        val result = completer.suggest(text, caret).single().apply()
        assertEquals("Title #\"中文字幕\" 60FPS", result.first)
        assertEquals(setOf("中文字幕", "60FPS"), parser.parse(result.first).tags)
    }

    @Test fun standaloneHashBrowsesSuggestionsAndHonorsTheLimit() {
        for (text in listOf("#", "＃")) {
            assertEquals(2, completer.suggest(text, text.length, limit = 2).size)
        }
    }

    @Test fun unknownPrefixesDoNotRewriteOrdinaryTitleWords() {
        assertTrue(completer.suggest("unmatched title", 15).isEmpty())
    }

    @Test fun selectedTagsBeforeAndAfterTheCaretAreExcluded() {
        val text = "1080p # 60FPS"
        val results = completer.suggest(text, text.indexOf('#') + 1)
        assertTrue(results.none { it.tag.key == "1080p" || it.tag.key == "60FPS" })
    }

    @Test fun selectedAliasesAreExcludedByCanonicalKey() {
        val text = "Chinese Subtitle #中"
        assertTrue(completer.suggest(text, text.length).none { it.tag.key == "中文字幕" })
    }

    @Test fun replacingTheActiveTokenPreservesBothSurroundingRanges() {
        val text = "1080p Title ChiSuffix Other title"
        val caret = text.indexOf("ChiSuffix") + 3
        val result = completer.suggest(text, caret).single().apply()
        assertEquals("1080p Title #\"中文字幕\" Other title", result.first)
        assertEquals("Title Other title", parser.parse(result.first).query)
        assertEquals(setOf("1080p", "中文字幕"), parser.parse(result.first).tags)
    }

    @Test fun completionAppendsASpaceAtTheEndAndPlacesTheCaretAfterIt() {
        val result = completer.suggest("10", 2).single().apply()
        assertEquals("#\"1080p\" ", result.first)
        assertEquals(result.first.length, result.second)
    }

    @Test fun completionReusesAnExistingSeparatorAndAdvancesTheCaret() {
        val result = completer.suggest("10 title", 2).single().apply()
        assertEquals("#\"1080p\" title", result.first)
        assertEquals("#\"1080p\" ".length, result.second)
    }

    @Test fun completionPreservesRepeatedWhitespaceOutsideTheActiveToken() {
        val text = "Title  10\t  tail"
        val result = completer.suggest(text, text.indexOf("10") + 2).single().apply()
        assertEquals("Title  #\"1080p\"\t  tail", result.first)
    }

    @Test fun completionDoesNotCrossQuotedOrExplicitTagBoundaries() {
        for (text in listOf("\"Chinese\" Sub", "#Chinese Sub")) {
            assertTrue(completer.suggest(text, text.length).isEmpty())
        }
    }

    @Test fun invalidCaretsAndNonpositiveLimitsHaveNoSuggestions() {
        assertTrue(completer.suggest("10", -1).isEmpty())
        assertTrue(completer.suggest("10", 3).isEmpty())
        assertTrue(completer.suggest("10", 2, limit = 0).isEmpty())
    }

    @Test fun tagNamesWithQuotesAndBackslashesRoundTripThroughCompletion() {
        val key = "Custom \"Tag\" \\ demo"
        val customTags = listOf(SearchTag(key, key, setOf(key)))
        val result = TagCompleter(customTags).suggest("#Custom", 7).single().apply()
        val input = SearchInputParser(customTags.searchTagAliases()).parse(result.first)
        assertEquals(setOf(key), input.tags)
        assertEquals(null, input.query)
    }

    @Test fun suggestionsWithTheSameCanonicalKeyAppearOnlyOnce() {
        val duplicate = SearchTag("1080p", "HD", setOf("1080p", "HD"))
        val results = TagCompleter(tags + duplicate).suggest("#", 1)
        assertEquals(1, results.count { it.tag.key == "1080p" })
    }
}
