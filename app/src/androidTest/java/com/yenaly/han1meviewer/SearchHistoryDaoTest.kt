package com.yenaly.han1meviewer

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yenaly.han1meviewer.logic.dao.HistoryDatabase
import com.yenaly.han1meviewer.logic.entity.SearchHistoryEntity
import com.yenaly.han1meviewer.logic.entity.WatchHistoryEntity
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SearchHistoryDaoTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "search-history-test-${UUID.randomUUID()}.db"
    private lateinit var database: HistoryDatabase
    private val dao get() = database.searchHistory

    private fun openDatabase() = Room.databaseBuilder(
        context, HistoryDatabase::class.java, databaseName
    ).build()

    @Before fun setUp() {
        database = openDatabase()
    }

    @After fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test fun startsWithNoHistory() = runBlocking {
        assertTrue(dao.loadAll().first().isEmpty())
    }

    @Test fun reusingAQueryMovesItToTheTopWithoutDuplicates() = runBlocking {
        dao.insertOrUpdate(SearchHistoryEntity("First title"))
        dao.insertOrUpdate(SearchHistoryEntity("1080p 60FPS"))
        dao.insertOrUpdate(SearchHistoryEntity("First title"))
        assertEquals(listOf("First title", "1080p 60FPS"), dao.loadAll().first().map { it.query })
    }

    @Test fun deleteRemovesOnlyTheSelectedEntry() = runBlocking {
        dao.insertOrUpdate(SearchHistoryEntity("First title"))
        dao.insertOrUpdate(SearchHistoryEntity("Second title"))
        dao.delete(dao.find("First title")!!)
        assertEquals(listOf("Second title"), dao.loadAll().first().map { it.query })
    }

    @Test fun clearSearchHistoryDoesNotDeleteWatchHistory() = runBlocking {
        dao.insertOrUpdate(SearchHistoryEntity("1080p 60FPS"))
        val watched = WatchHistoryEntity("cover", "Video", 0L, 1L, "1")
        database.watchHistory.insertOrUpdate(watched)
        dao.deleteAll()
        assertTrue(dao.loadAll().first().isEmpty())
        assertEquals(listOf("1"), database.watchHistory.loadAll().first().map { it.videoCode })
    }

    @Test fun historyPersistsIncludingTagAndTitleSyntax() = runBlocking {
        val query = "#\"Custom Tag\" 1080p \"ASMR title\""
        dao.insertOrUpdate(SearchHistoryEntity(query))
        database.close()
        database = openDatabase()
        assertEquals(listOf(query), dao.loadAll().first().map { it.query })
    }

    @Test fun suggestionsCanFilterTheSameRecordsUsedByTheActivity() = runBlocking {
        dao.insertOrUpdate(SearchHistoryEntity("1080p First title"))
        dao.insertOrUpdate(SearchHistoryEntity("60FPS Second title"))
        assertEquals(listOf("60FPS Second title"), dao.loadAll("Second").first().map { it.query })
    }

    @Test fun clearingNotifiesActiveHistoryObservers() = runBlocking {
        withTimeout(5_000) {
            dao.insertOrUpdate(SearchHistoryEntity("A title"))
            val cleared = async(start = CoroutineStart.UNDISPATCHED) {
                dao.loadAll().first { it.isEmpty() }
            }
            dao.deleteAll()
            assertTrue(cleared.await().isEmpty())
        }
    }
}
