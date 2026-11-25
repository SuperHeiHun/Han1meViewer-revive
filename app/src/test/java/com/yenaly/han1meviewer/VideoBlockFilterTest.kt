package com.yenaly.han1meviewer

import com.yenaly.han1meviewer.logic.blocking.BlockRules
import com.yenaly.han1meviewer.logic.blocking.VideoBlockFilter
import com.yenaly.han1meviewer.logic.blocking.VideoBlockMetadata
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class VideoBlockFilterTest {
    private fun item(code: String, author: String? = null) =
        HanimeInfo("title $code", "cover", code, uploader = author, itemType = HanimeInfo.NORMAL)
    private val allowed = VideoBlockMetadata(setOf("ok"), "allowed")
    private val blocked = VideoBlockMetadata(setOf("blocked"), "blocked")

    @Test fun emptyRulesKeepAllCardsWithoutLookups() = runBlocking {
        val filter = VideoBlockFilter({ error("No cache lookup expected") }, { error("No network expected") })
        val source = listOf(item("1"), item("2"))
        assertEquals(source, filter.observe(source, BlockRules()).first())
    }

    @Test fun authorKnownOnCardNeedsNoLookup() = runBlocking {
        val filter = VideoBlockFilter({ null }, { error("No network expected") })
        val source = listOf(item("1", "bad"), item("2", "good"))
        assertEquals(listOf(source[1]), filter.observe(source, BlockRules(authors = setOf("bad"))).first())
    }

    @Test fun cachedMetadataBlocksUnseenTitles() = runBlocking {
        val filter = VideoBlockFilter({ blocked }, { error("Cache must be used") })
        assertTrue(filter.observe(listOf(item("1")), BlockRules(tags = setOf("blocked"))).first().isEmpty())
    }

    @Test fun unknownTagsAreVerifiedEvenWhenAuthorIsKnown() = runBlocking {
        var calls = 0
        val filter = VideoBlockFilter({ null }, { calls++; blocked })
        val results = filter.observe(listOf(item("1", "good")), BlockRules(tags = setOf("blocked"))).toList()
        assertTrue(results.all { it.isEmpty() })
        assertEquals(1, calls)
    }

    @Test fun failedLookupsStayHiddenAndCanBeRetried() = runBlocking {
        var calls = 0
        val filter = VideoBlockFilter({ null }, { if (++calls == 1) null else allowed })
        val source = listOf(item("1"))
        val rules = BlockRules(tags = setOf("blocked"))
        assertTrue(filter.observe(source, rules).toList().last().isEmpty())
        assertEquals(source, filter.observe(source, rules).toList().last())
    }

    @Test fun duplicateCardsOnlyResolveOnce() = runBlocking {
        var calls = 0
        val source = listOf(item("1"), item("1"))
        val filter = VideoBlockFilter({ null }, { calls++; allowed })
        assertEquals(source, filter.observe(source, BlockRules(tags = setOf("blocked"))).toList().last())
        assertEquals(1, calls)
    }

    @Test fun knownBlockedAuthorAlsoHidesLessDetailedCopiesOfTheSameVideo() = runBlocking {
        val source = listOf(item("1"), item("1", "blocked"))
        val filter = VideoBlockFilter({ allowed }, { error("Known blocked author needs no request") })
        assertTrue(filter.observe(source, BlockRules(authors = setOf("blocked"))).toList().last().isEmpty())
    }

    @Test fun completionOrderDoesNotChangeSourceOrder() = runBlocking {
        val first = CompletableDeferred<VideoBlockMetadata?>()
        val second = CompletableDeferred<VideoBlockMetadata?>()
        val filter = VideoBlockFilter({ null }, { if (it == "1") first.await() else second.await() })
        val source = listOf(item("1"), item("2"))
        val results = mutableListOf<List<HanimeInfo>>()
        val secondShown = CompletableDeferred<Unit>()
        val job = launch {
            filter.observe(source, BlockRules(tags = setOf("blocked"))).collect {
                results.add(it)
                if (it == listOf(source[1])) secondShown.complete(Unit)
            }
        }
        second.complete(allowed)
        withTimeout(5_000) { secondShown.await() }
        first.complete(allowed)
        job.join()
        assertTrue(results.first().isEmpty())
        assertEquals(source, results.last())
    }

    @Test fun clearingRulesRestoresOriginalDataWithoutDestructiveMutation() = runBlocking {
        val source = listOf(item("1"), item("2"))
        val filter = VideoBlockFilter({ blocked }, { null })
        assertTrue(filter.observe(source, BlockRules(tags = setOf("blocked"))).first().isEmpty())
        assertEquals(source, filter.observe(source, BlockRules()).first())
        assertEquals(2, source.size)
    }

    @Test fun cancellationPropagatesToPendingRequests() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val filter = VideoBlockFilter({ null }, {
            started.complete(Unit)
            try { CompletableDeferred<VideoBlockMetadata?>().await() }
            finally { cancelled.complete(Unit) }
        })
        val job = launch { filter.observe(listOf(item("1")), BlockRules(tags = setOf("blocked"))).collect() }
        withTimeout(5_000) { started.await() }
        job.cancel(CancellationException("Screen stopped"))
        job.join()
        assertTrue(cancelled.isCompleted)
    }

    @Test fun missingCardAuthorIsResolvedForAuthorRules() = runBlocking {
        val filter = VideoBlockFilter({ null }, { blocked })
        assertTrue(filter.observe(listOf(item("1")), BlockRules(authors = setOf("blocked")))
            .toList().last().isEmpty())
    }
}
