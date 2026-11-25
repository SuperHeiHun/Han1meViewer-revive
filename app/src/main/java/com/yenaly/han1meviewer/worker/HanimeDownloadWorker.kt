package com.yenaly.han1meviewer.worker

import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmapOrNull
import androidx.core.net.toUri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.yenaly.han1meviewer.DOWNLOAD_NOTIFICATION_CHANNEL
import com.yenaly.han1meviewer.EMPTY_STRING
import com.yenaly.han1meviewer.HFileManager
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.DatabaseRepo
import com.yenaly.han1meviewer.logic.download.DownloadStorage
import com.yenaly.han1meviewer.logic.entity.download.HanimeDownloadEntity
import com.yenaly.han1meviewer.logic.network.ServiceCreator
import com.yenaly.han1meviewer.logic.state.DownloadState
import com.yenaly.han1meviewer.util.HImageMeower
import com.yenaly.han1meviewer.util.await
import com.yenaly.yenaly_libs.utils.showShortToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.internal.closeQuietly
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * @project Han1meViewer
 * @author Yenaly Liew
 * @time 2022/08/06 006 11:42
 */
class HanimeDownloadWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams), WorkerMixin {

    data class Args(
        val quality: String?,
        val downloadUrl: String?,
        val videoType: String?,
        val hanimeName: String,
        val videoCode: String,
        val coverUrl: String,
        val downloadRoot: String? = null,
        val videoUri: String? = null,
    ) {
        companion object {
            fun fromEntity(entity: HanimeDownloadEntity): Args {
                return Args(
                    quality = entity.quality,
                    downloadUrl = entity.videoUrl,
                    videoType = entity.suffix,
                    hanimeName = entity.title,
                    videoCode = entity.videoCode,
                    coverUrl = entity.coverUrl,
                    videoUri = entity.videoUri,
                )
            }
        }
    }

    companion object {
        const val TAG = "HanimeDownloadWorker"

        const val RESPONSE_INTERVAL = 500L

        const val BACKOFF_DELAY = 10_000L

        const val FAST_PATH_CANCEL = "fast_path_cancel"
        const val DELETE = "delete"
        const val QUALITY = "quality"
        const val DOWNLOAD_URL = "download_url"
        const val VIDEO_TYPE = "video_type"
        const val HANIME_NAME = "hanime_name"
        const val VIDEO_CODE = "video_code"
        const val COVER_URL = "cover_url"
        const val DOWNLOAD_ROOT = "download_root"
        const val SAVED_VIDEO_URI = "saved_video_uri"
        const val REDOWNLOAD = "redownload"
        const val IN_WAITING_QUEUE = "in_waiting_queue"
        // const val RELEASE_DATE = "release_date"
        // const val COVER_DOWNLOAD = "cover_download"

        const val PROGRESS = "progress"
        // const val FAILED_REASON = "failed_reason"

        /**
         * 方便统一管理下载 Worker 的创建
         */
        inline fun build(
            constraintsRequired: Boolean = true,
            action: OneTimeWorkRequest.Builder.() -> Unit = {}
        ): OneTimeWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresStorageNotLow(true)
                .build()
            return OneTimeWorkRequestBuilder<HanimeDownloadWorker>()
                .addTag(TAG)
                .let { builder ->
                    if (constraintsRequired) {
                        builder.setConstraints(constraints)
                    } else {
                        builder
                    }
                }.setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    BACKOFF_DELAY, TimeUnit.MILLISECONDS
                ).apply(action).build()
        }

        fun getRunningWorkInfoCount(context: Context): Flow<Int> {
            return WorkManager.getInstance(context)
                .getWorkInfosByTagFlow(TAG)
                .map { workInfos ->
                    workInfos.count {
                        it.state == WorkInfo.State.RUNNING
                    }
                }.distinctUntilChanged()
        }
    }

    private val notificationManager = NotificationManagerCompat.from(context)

    private val hanimeName by inputData(HANIME_NAME, EMPTY_STRING)
    private val downloadUrl by inputData(DOWNLOAD_URL, EMPTY_STRING)
    private val videoType by inputData(VIDEO_TYPE, HFileManager.DEF_VIDEO_TYPE)
    private val quality by inputData(QUALITY, EMPTY_STRING)
    private val videoCode by inputData(VIDEO_CODE, EMPTY_STRING)
    private val coverUrl by inputData(COVER_URL, EMPTY_STRING)
    // Requests persisted before directory selection existed belong to the legacy root.
    private val downloadRoot by inputData(DOWNLOAD_ROOT, DownloadStorage.legacyRoot)
    private val savedVideoUri by inputData(SAVED_VIDEO_URI, EMPTY_STRING)

    private val fastPathCancel by inputData(FAST_PATH_CANCEL, false)
    private val shouldDelete by inputData(DELETE, false)
    private val shouldRedownload by inputData(REDOWNLOAD, false)
    private val isInWaitingQueue by inputData(IN_WAITING_QUEUE, false)

    private val downloadId = Random.nextInt()

    private val mainScope = CoroutineScope(Dispatchers.Main.immediate)

    override suspend fun doWork(): Result {
        if (fastPathCancel) return Result.success()
        setForeground(createForegroundInfo())
        return download()
    }

    private suspend fun createDownload(existing: HanimeDownloadEntity?): HanimeDownloadEntity {
        val request = Request.Builder().url(downloadUrl).get().build()
        ServiceCreator.downloadClient.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw IOException(response.message)
            val len = response.body?.contentLength() ?: -1L
            if (len <= 0) throw IOException(context.getString(R.string.get_file_info_failed))
            val uri = existing?.videoUri?.toUri() ?: DownloadStorage.createVideo(
                downloadRoot, videoCode, hanimeName, quality, videoType)
            try {
                DownloadStorage.openVideo(uri).use { it.setLength(len) }
            } catch (e: Exception) {
                // Only a newly created target may be cleaned up; never delete sibling resolutions.
                if (existing == null) runCatching { DownloadStorage.delete(uri) }
                throw e
            }
            val entity = HanimeDownloadEntity(
                coverUrl = coverUrl, coverUri = existing?.coverUri,
                title = hanimeName, addDate = System.currentTimeMillis(), videoCode = videoCode,
                videoUri = uri.toString(), quality = quality, videoUrl = downloadUrl,
                length = len, downloadedLength = 0, state = DownloadState.Queued,
                id = existing?.id ?: 0)
            if (existing == null) DatabaseRepo.HanimeDownload.insert(entity)
            else DatabaseRepo.HanimeDownload.update(entity)
            return DatabaseRepo.HanimeDownload.find(videoCode, quality)
                ?: throw IOException(context.getString(R.string.get_file_info_failed))
        }
    }

    private suspend fun download(): Result {
        return withContext(Dispatchers.IO) {
            val entity = try {
                val existing = DatabaseRepo.HanimeDownload.find(videoCode, quality)
                if (savedVideoUri.isNotBlank() && existing != null && existing.videoUri != savedVideoUri) {
                    // A stale resume/delete request must never operate on a replacement task.
                    throw IOException(context.getString(R.string.download_path_unavailable))
                }
                if (shouldDelete) {
                    if (existing != null) {
                        DownloadStorage.delete(existing.videoUri.toUri())
                        DatabaseRepo.HanimeDownload.delete(existing)
                        runCatching { DownloadStorage.cleanupSidecars(existing) }
                    }
                    cancelDownloadNotification()
                    return@withContext Result.success()
                }
                if (existing == null && savedVideoUri.isNotBlank()) {
                    // A removed task must not silently reappear in the newly selected directory.
                    throw IOException(context.getString(R.string.download_path_unavailable))
                }
                val prepared = if (existing == null || shouldRedownload) createDownload(existing) else existing
                if (prepared.coverUri == null) updateCoverImage(prepared) else prepared
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showFailureNotification(e.localizedMessage)
                mainScope.launch { showShortToast(R.string.download_path_unavailable) }
                return@withContext Result.failure(workDataOf(DownloadState.STATE to DownloadState.Failed.mask))
            }
            if (isInWaitingQueue) {
                DatabaseRepo.HanimeDownload.update(
                    entity.copy(state = DownloadState.Queued)
                )
                return@withContext Result.success()
            }

            var downloadedLength = entity.downloadedLength
            val needRange = entity.downloadedLength > 0
            var raf: DownloadStorage.SeekableOutput? = null
            var response: Response? = null
            var body: ResponseBody? = null
            var bodyStream: InputStream? = null

            var result: Result = Result.failure()
            try {
                raf = DownloadStorage.openVideo(entity.videoUri.toUri())
                if (raf.length < entity.downloadedLength) {
                    throw IOException(context.getString(R.string.download_file_incomplete))
                }
                val request = Request.Builder().url(downloadUrl)
                    .also { if (needRange) it.header("Range", "bytes=${entity.downloadedLength}-") }
                    .get().build()
                response = ServiceCreator.downloadClient.newCall(request).await()
                raf.seek(entity.downloadedLength)
                if ((needRange && response.code == 206) || (!needRange && response.isSuccessful)) {
                    var delayTime = 0L
                    body = response.body
                    if (body != null) {
                        bodyStream = body.byteStream()
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var len: Int = bodyStream.read(buffer)
                        while (len != -1) {
                            raf.write(buffer, 0, len)
                            downloadedLength += len
                            if (System.currentTimeMillis() - delayTime > RESPONSE_INTERVAL) {
                                raf.flush()
                                val progress = downloadedLength * 100 / entity.length
                                setProgress(workDataOf(PROGRESS to progress.toInt()))
                                updateDownloadNotification(progress.toInt())
                                DatabaseRepo.HanimeDownload.update(
                                    entity.copy(
                                        downloadedLength = downloadedLength,
                                        // isDownloading = true,
                                        state = DownloadState.Downloading
                                    )
                                )
                                delayTime = System.currentTimeMillis()
                            }
                            len = bodyStream.read(buffer)
                        }
                    }
                    if (downloadedLength != entity.length) throw IOException(context.getString(R.string.download_file_incomplete))
                    raf.flush()
                    showSuccessNotification()
                    result = Result.success(
                        workDataOf(DownloadState.STATE to DownloadState.Finished.mask)
                    )
                } else {
                    Log.d(TAG, "response failed: ${response.message}")
                    showFailureNotification(response.message)
                    mainScope.launch {
                        showShortToast(
                            context.getString(R.string.download_task_failed_s, hanimeName)
                        )
                    }
                    result = Result.failure(
                        workDataOf(DownloadState.STATE to DownloadState.Failed.mask)
                    )
                }
            } catch (e: Exception) {
                result = if (e is CancellationException) {
                    // cancellation exception block 是代表用户暂停
                    cancelDownloadNotification()
                    Result.success(
                        workDataOf(DownloadState.STATE to DownloadState.Paused.mask)
                    )
                } else {
                    showFailureNotification(e.localizedMessage)
                    e.printStackTrace()
                    mainScope.launch {
                        showShortToast(e.localizedMessage)
                    }
                    Result.failure(
                        workDataOf(DownloadState.STATE to DownloadState.Failed.mask)
                    )
                }
            } finally {
                val state = DownloadState.from(
                    result.outputData.getInt(DownloadState.STATE, DownloadState.Unknown.mask)
                )
                try {
                    // Finish persisting a pause before this worker exits.
                    withContext(NonCancellable + Dispatchers.IO) {
                        runCatching { raf?.flush() }
                        val newEntity = entity.copy(
                            state = state,
                            downloadedLength = downloadedLength
                        )
                        DatabaseRepo.HanimeDownload.update(newEntity)
                        Log.d(TAG, "finally -> $newEntity")
                    }
                } finally {
                    raf?.closeQuietly()
                    response?.closeQuietly()
                    body?.closeQuietly()
                    bodyStream?.closeQuietly()
                }
            }
            return@withContext result
        }
    }

    private suspend fun updateCoverImage(entity: HanimeDownloadEntity): HanimeDownloadEntity {
        return try {
            val imgRes = HImageMeower.execute(entity.coverUrl)
            val bitmap = imgRes.drawable?.toBitmapOrNull() ?: return entity
            val folder = DownloadStorage.folderForVideo(entity.videoUri, entity.videoCode) ?: return entity
            val file = DownloadStorage.file(folder, HFileManager.createVideoCoverName(entity.title), "image/png", create = true)
                ?: return entity
            val saved = DownloadStorage.output(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (!saved) return entity
            entity.copy(coverUri = file.toString()).also { DatabaseRepo.HanimeDownload.update(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // A missing cover must not prevent the video download.
            entity
        }
    }

    private fun createDownloadNotification(progress: Int = 0): Notification {
        return NotificationCompat.Builder(context, DOWNLOAD_NOTIFICATION_CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentTitle(context.getString(R.string.downloading_s, hanimeName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentText("$progress%")
            .setProgress(100, progress, false)
            .build()
    }

    private fun cancelDownloadNotification() {
        notificationManager.cancel(downloadId)
    }

    @SuppressLint("MissingPermission")
    private fun updateDownloadNotification(progress: Int) {
        notificationManager.notify(downloadId, createDownloadNotification(progress))
    }

    private fun createForegroundInfo(progress: Int = 0): ForegroundInfo {
        val notification = createDownloadNotification(progress)
        return ForegroundInfo(
            downloadId, notification,
            // #issue-34: 這裡的參數是為了讓 Android 14 以上的系統可以正常顯示前景通知
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else 0
        )
    }

    @SuppressLint("MissingPermission")
    private fun showSuccessNotification() {
        notificationManager.notify(
            downloadId, NotificationCompat.Builder(context, DOWNLOAD_NOTIFICATION_CHANNEL)
                .setSmallIcon(R.drawable.ic_baseline_check_circle_24)
                .setContentTitle(context.getString(R.string.download_task_completed))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentText(context.getString(R.string.download_completed_s, hanimeName))
                .build()
        )
    }

    @SuppressLint("MissingPermission")
    private fun showFileExistsFailureNotification(fileName: String) {
        notificationManager.notify(
            downloadId, NotificationCompat.Builder(context, DOWNLOAD_NOTIFICATION_CHANNEL)
                .setSmallIcon(R.drawable.ic_baseline_cancel_24)
                .setContentTitle(context.getString(R.string.this_data_exists))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentText(context.getString(R.string.download_failed_s_exists, fileName))
                .build()
        )
    }

    @SuppressLint("MissingPermission")
    private fun showFailureNotification(errMsg: String? = null) {
        notificationManager.notify(
            downloadId, NotificationCompat.Builder(context, DOWNLOAD_NOTIFICATION_CHANNEL)
                .setSmallIcon(R.drawable.ic_baseline_cancel_24)
                .setContentTitle(context.getString(R.string.download_task_failed))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentText(
                    context.getString(
                        R.string.download_task_failed_s_reason_s,
                        hanimeName, errMsg ?: context.getString(R.string.unknown_download_error)
                    )
                )
                .build()
        )
    }
}
