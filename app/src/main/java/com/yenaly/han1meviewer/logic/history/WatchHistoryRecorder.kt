package com.yenaly.han1meviewer.logic.history

import com.yenaly.han1meviewer.logic.entity.WatchHistoryEntity
import com.yenaly.han1meviewer.logic.model.HanimeVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/** Persist before exposing the video to the UI, including navigation straight to tag search. */
internal class WatchHistoryRecorder(
    private val insert: suspend (WatchHistoryEntity) -> Unit,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val mutex = Mutex()
    private var recordedCode: String? = null

    suspend fun record(videoCode: String, video: HanimeVideo, fromDownload: Boolean) {
        if (fromDownload || videoCode.isBlank()) return
        // Only the short local transaction survives cancellation; network/UI work does not.
        withContext(NonCancellable + Dispatchers.IO) {
            mutex.withLock {
                if (recordedCode == videoCode) return@withLock
                insert(
                    WatchHistoryEntity(
                        coverUrl = video.coverUrl,
                        title = video.title,
                        releaseDate = video.uploadTimeMillis,
                        watchDate = now(),
                        videoCode = videoCode,
                    )
                )
                recordedCode = videoCode
            }
        }
    }
}
