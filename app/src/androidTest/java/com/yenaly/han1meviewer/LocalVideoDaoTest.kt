package com.yenaly.han1meviewer

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yenaly.han1meviewer.logic.dao.LocalVideoDatabase
import com.yenaly.han1meviewer.logic.entity.LocalVideoEntity
import com.yenaly.han1meviewer.logic.entity.LocalVideoEntity.Companion.FAVORITES
import com.yenaly.han1meviewer.logic.entity.LocalVideoEntity.Companion.WATCH_LATER
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
class LocalVideoDaoTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "local-video-test-${UUID.randomUUID()}.db"
    private lateinit var database: LocalVideoDatabase
    private val dao get() = database.localVideoDao

    private fun openDatabase() = Room.databaseBuilder(
        context, LocalVideoDatabase::class.java, databaseName
    ).build()

    @Before
    fun setUp() {
        database = openDatabase()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    private fun video(code: String, list: String, time: Long = 1L) =
        LocalVideoEntity(code, list, "Video $code", "https://example.com/$code.jpg", time)

    @Test
    fun newListsAreEmpty() = runBlocking {
        assertTrue(dao.observeList(FAVORITES).first().isEmpty())
        assertTrue(dao.observeList(WATCH_LATER).first().isEmpty())
        assertTrue(dao.observeMembership("1").first().isEmpty())
    }

    @Test
    fun duplicateAddsPreserveOriginalEntryAndQueuePosition() = runBlocking {
        val original = video("1", WATCH_LATER)
        dao.insert(original)
        dao.insert(original.copy(title = "Duplicate", addedAt = 100L))
        assertEquals(listOf(original), dao.observeList(WATCH_LATER).first())
    }

    @Test
    fun favoritesAndWatchLaterAreIndependent() = runBlocking {
        dao.insert(video("1", FAVORITES))
        dao.insert(video("1", WATCH_LATER))
        assertEquals(setOf(FAVORITES, WATCH_LATER), dao.observeMembership("1").first().toSet())
        dao.delete(FAVORITES, "1")
        assertTrue(dao.observeList(FAVORITES).first().isEmpty())
        assertEquals(listOf(WATCH_LATER), dao.observeMembership("1").first())
    }

    @Test
    fun favoritesAreNewestFirstAndWatchLaterIsOldestFirst() = runBlocking {
        for (list in listOf(FAVORITES, WATCH_LATER)) {
            dao.insert(video("2", list, 20L))
            dao.insert(video("1", list, 10L))
            dao.insert(video("3", list, 30L))
        }
        assertEquals(listOf("3", "2", "1"), dao.observeList(FAVORITES).first().map { it.videoCode })
        assertEquals(listOf("1", "2", "3"), dao.observeList(WATCH_LATER).first().map { it.videoCode })
    }

    @Test
    fun pendingVideosWithTheSameTimestampRetainInsertionOrder() = runBlocking {
        dao.insert(video("2", WATCH_LATER))
        dao.insert(video("1", WATCH_LATER))
        assertEquals(listOf("2", "1"), dao.observeList(WATCH_LATER).first().map { it.videoCode })
    }

    @Test
    fun removingAndAddingAgainMovesVideoToEndOfQueue() = runBlocking {
        dao.insert(video("1", WATCH_LATER, 10L))
        dao.insert(video("2", WATCH_LATER, 20L))
        dao.delete(WATCH_LATER, "1")
        dao.insert(video("1", WATCH_LATER, 30L))
        assertEquals(listOf("2", "1"), dao.observeList(WATCH_LATER).first().map { it.videoCode })
    }

    @Test
    fun clearingOneListLeavesTheOtherUntouched() = runBlocking {
        dao.insert(video("1", FAVORITES))
        dao.insert(video("1", WATCH_LATER))
        dao.insert(video("2", WATCH_LATER))
        dao.clear(WATCH_LATER)
        assertTrue(dao.observeList(WATCH_LATER).first().isEmpty())
        assertEquals(listOf(video("1", FAVORITES)), dao.observeList(FAVORITES).first())
    }

    @Test
    fun listsSurviveDatabaseReopen() = runBlocking {
        val favorite = video("1", FAVORITES)
        val pending = video("2", WATCH_LATER)
        dao.insert(favorite)
        dao.insert(pending)
        database.close()
        database = openDatabase()
        assertEquals(listOf(favorite), dao.observeList(FAVORITES).first())
        assertEquals(listOf(pending), dao.observeList(WATCH_LATER).first())
    }

    @Test
    fun observersReceiveNewEntries() = runBlocking {
        withTimeout(5_000) {
            val nextEntry = async(start = CoroutineStart.UNDISPATCHED) {
                dao.observeList(FAVORITES).first { it.isNotEmpty() }
            }
            val favorite = video("1", FAVORITES)
            dao.insert(favorite)
            assertEquals(listOf(favorite), nextEntry.await())
        }
    }
}
