package com.yenaly.han1meviewer

import com.yenaly.han1meviewer.logic.entity.WatchHistoryEntity
import com.yenaly.han1meviewer.logic.history.WatchHistoryRecorder
import com.yenaly.han1meviewer.logic.model.HanimeVideo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchHistoryRecorderTest {
    private val video = HanimeVideo(
        title = "Test video", coverUrl = "https://example.com/cover.jpg",
        chineseTitle = null, introduction = null, uploadTime = null,
        videoUrls = linkedMapOf(), tags = listOf("1080p"),
    )

    @Test fun recordCompletesBeforeReturningToTheVideoLoader() = runBlocking {
        val saved = mutableListOf<WatchHistoryEntity>()
        val recorder = WatchHistoryRecorder(insert = { saved += it }, now = { 123L })
        recorder.record("1", video, fromDownload = false)
        assertEquals(listOf(WatchHistoryEntity(video.coverUrl, video.title, 0L, 123L, "1")), saved)
    }

    @Test fun localDownloadDoesNotCreateWatchHistory() = runBlocking {
        val saved = mutableListOf<WatchHistoryEntity>()
        val recorder = WatchHistoryRecorder(insert = { saved += it })
        recorder.record("1", video, fromDownload = true)
        recorder.record("", video, fromDownload = false)
        assertTrue(saved.isEmpty())
    }

    @Test fun repeatedSuccessOrConcurrentCollectorsDoNotDuplicateTheRecord() = runBlocking {
        val saved = mutableListOf<WatchHistoryEntity>()
        val recorder = WatchHistoryRecorder(insert = { saved += it })
        List(3) { launch { recorder.record("1", video, fromDownload = false) } }.joinAll()
        assertEquals(1, saved.size)
    }

    @Test fun leavingThePageDoesNotCancelAnInFlightLocalSave() = runBlocking {
        withTimeout(5_000) {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val saved = mutableListOf<WatchHistoryEntity>()
            val recorder = WatchHistoryRecorder(insert = {
                entered.complete(Unit)
                release.await()
                saved += it
            })
            val leavingPage = launch(start = CoroutineStart.UNDISPATCHED) {
                recorder.record("1", video, fromDownload = false)
            }
            try {
                entered.await()
                leavingPage.cancel()
            } finally {
                release.complete(Unit)
            }
            leavingPage.join()
            assertEquals(listOf("1"), saved.map { it.videoCode })
        }
    }

    @Test fun failedSaveCanBeRetriedOnTheNextSuccessfulLoad() = runBlocking {
        var attempts = 0
        val saved = mutableListOf<WatchHistoryEntity>()
        val recorder = WatchHistoryRecorder(insert = {
            if (++attempts == 1) error("Storage unavailable")
            saved += it
        })
        val failure = runCatching { recorder.record("1", video, fromDownload = false) }
        assertTrue(failure.isFailure)
        recorder.record("1", video, fromDownload = false)
        assertEquals(2, attempts)
        assertEquals(1, saved.size)
    }

    @Test fun differentVideoCodesAreRecordedSeparately() = runBlocking {
        val saved = mutableListOf<WatchHistoryEntity>()
        val recorder = WatchHistoryRecorder(insert = { saved += it })
        val first = async { recorder.record("1", video, fromDownload = false) }
        first.await()
        recorder.record("2", video.copy(title = "Second video"), fromDownload = false)
        assertEquals(listOf("1", "2"), saved.map { it.videoCode })
    }
}
