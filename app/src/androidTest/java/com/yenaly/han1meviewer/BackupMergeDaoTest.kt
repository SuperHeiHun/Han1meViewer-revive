package com.yenaly.han1meviewer

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yenaly.han1meviewer.logic.dao.HistoryDatabase
import com.yenaly.han1meviewer.logic.dao.LocalVideoDatabase
import com.yenaly.han1meviewer.logic.entity.LocalVideoEntity
import com.yenaly.han1meviewer.logic.entity.SearchHistoryEntity
import com.yenaly.han1meviewer.logic.entity.WatchHistoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupMergeDaoTest {
    private lateinit var history: HistoryDatabase
    private lateinit var local: LocalVideoDatabase

    @Before fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        history = Room.inMemoryDatabaseBuilder(context, HistoryDatabase::class.java).build()
        local = Room.inMemoryDatabaseBuilder(context, LocalVideoDatabase::class.java).build()
    }

    @After fun tearDown() { history.close(); local.close() }

    @Test fun searchMergePreservesBackupRecencyAndExistingRecords() = runBlocking {
        history.searchHistory.insert(SearchHistoryEntity("existing"))
        history.searchHistory.mergeBackup(listOf(SearchHistoryEntity("newest", 1), SearchHistoryEntity("older", 1)))
        assertEquals(listOf("newest", "older", "existing"), history.searchHistory.snapshot().map { it.query })
    }

    @Test fun repeatedSearchRestoreDoesNotDuplicateOrReorderMatches() = runBlocking {
        history.searchHistory.insert(SearchHistoryEntity("existing"))
        val rows = listOf(SearchHistoryEntity("new"), SearchHistoryEntity("existing"))
        history.searchHistory.mergeBackup(rows)
        val first = history.searchHistory.snapshot()
        history.searchHistory.mergeBackup(rows)
        assertEquals(first, history.searchHistory.snapshot())
    }

    @Test fun watchMergeKeepsExistingMetadataAndAvoidsPrimaryKeyCollisions() = runBlocking {
        history.watchHistory.insert(WatchHistoryEntity("", "Current", 0, 100, "1", 1))
        history.watchHistory.mergeBackup(listOf(WatchHistoryEntity("", "Other", 0, 10, "2", 1),
            WatchHistoryEntity("", "Old", 0, 1, "1", 1)))
        assertEquals(listOf("2", "1"), history.watchHistory.snapshot().map { it.videoCode })
        assertEquals("Current", history.watchHistory.findBy("1")?.title)
    }

    @Test fun localListsRemainIndependentAndRestoreKeepsQueueOrder() = runBlocking {
        val later = LocalVideoEntity.WATCH_LATER
        val favorite = LocalVideoEntity.FAVORITES
        local.localVideoDao.insert(LocalVideoEntity("3", later, "existing", "", 300))
        local.localVideoDao.mergeBackup(listOf(LocalVideoEntity("1", later, "first", "", 100),
            LocalVideoEntity("2", later, "second", "", 200), LocalVideoEntity("1", favorite, "fav", "", 100)))
        assertEquals(listOf("1", "2", "3"), local.localVideoDao.observeList(later).first().map { it.videoCode })
        assertEquals(listOf("1"), local.localVideoDao.observeList(favorite).first().map { it.videoCode })
    }

    @Test fun duplicateLocalVideosDoNotMoveOrOverwriteExistingEntries() = runBlocking {
        val type = LocalVideoEntity.WATCH_LATER
        val original = LocalVideoEntity("1", type, "Current", "", 100)
        local.localVideoDao.insert(original)
        repeat(2) { local.localVideoDao.mergeBackup(listOf(original.copy(title = "Backup", addedAt = 900))) }
        assertEquals(listOf(original), local.localVideoDao.snapshot(type))
    }

    @Test fun historyRestoreTransactionsRollBackTogetherOnFailure() = runBlocking {
        history.searchHistory.insert(SearchHistoryEntity("kept"))
        try {
            history.withTransaction {
                history.searchHistory.mergeBackup(listOf(SearchHistoryEntity("new")))
                history.watchHistory.mergeBackup(listOf(WatchHistoryEntity("", "Video", 0, 0, "1")))
                throw IllegalStateException()
            }
        } catch (_: IllegalStateException) { }
        assertEquals(listOf("kept"), history.searchHistory.snapshot().map { it.query })
        assertTrue(history.watchHistory.snapshot().isEmpty())
    }
}
