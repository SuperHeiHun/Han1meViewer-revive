package com.yenaly.han1meviewer

import com.yenaly.han1meviewer.logic.entity.SearchHistoryEntity
import com.yenaly.han1meviewer.logic.search.filterSearchHistories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchHistoryFilterTest {
    private val histories = listOf(
        SearchHistoryEntity("1080p Second title", id = 3),
        SearchHistoryEntity("60FPS First title", id = 2),
        SearchHistoryEntity("#\"Custom Tag\" 100%_done", id = 1),
    )

    @Test fun blankInputRestoresAllRecordsInTheirOriginalOrder() {
        for (query in listOf("", "  ", "\t\u3000")) {
            assertEquals(histories, filterSearchHistories(histories, query))
        }
    }

    @Test fun filteringIgnoresCaseAndFullWidth() {
        assertEquals(listOf(histories[1]), filterSearchHistories(histories, "６０ｆｐｓ"))
    }

    @Test fun filteringNormalizesWhitespaceWithoutChangingStoredInput() {
        val records = listOf(SearchHistoryEntity("Title\u3000  with\tspaces"))
        assertEquals(records, filterSearchHistories(records, "title with spaces"))
        assertEquals("Title\u3000  with\tspaces", records.single().query)
    }

    @Test fun filteringPreservesTheOriginalRecencyOrder() {
        assertEquals(histories.take(2), filterSearchHistories(histories, "title"))
    }

    @Test fun percentAndUnderscoreAreLiteralNotDatabaseWildcards() {
        assertEquals(listOf(histories[2]), filterSearchHistories(histories, "%_"))
        assertTrue(filterSearchHistories(histories, "%%").isEmpty())
    }

    @Test fun noMatchesDoNotDeleteOrModifyTheOriginalRecords() {
        val original = histories.toList()
        assertTrue(filterSearchHistories(histories, "unmatched").isEmpty())
        assertEquals(original, histories)
        assertEquals(original, filterSearchHistories(histories, ""))
    }

    @Test fun filteringPreservesExplicitTagAndQuotedTitleSyntax() {
        assertEquals(listOf(histories[2]), filterSearchHistories(histories, "#\"Custom Tag\""))
    }
}
