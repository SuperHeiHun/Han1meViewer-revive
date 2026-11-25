package com.yenaly.han1meviewer

import android.net.Uri
import androidx.annotation.WorkerThread
import com.yenaly.han1meviewer.logic.DatabaseRepo
import com.yenaly.han1meviewer.logic.download.DownloadStorage
import com.yenaly.han1meviewer.logic.model.HanimeVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

/**
 * @project Han1meViewer
 * @author Yenaly Liew
 * @since 2025/3/5 20:11
 */
object HCacheManager {

    /**
     * 保存 HanimeVideo 信息，用于下载后直接在 APP 内观看
     */
    @OptIn(ExperimentalSerializationApi::class)
    @WorkerThread
    fun saveHanimeVideoInfo(folder: Uri, info: HanimeVideo) {
        val file = requireNotNull(DownloadStorage.file(folder, DownloadStorage.INFO_FILE, "application/json", create = true))
        DownloadStorage.output(file).use { HJson.encodeToStream(info, it) }
    }

    /**
     * 加载 HanimeVideo 信息，用于下载后直接在 APP 内观看
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun loadHanimeVideoInfo(videoCode: String, quality: String? = null): Flow<HanimeVideo?> {
        return flow {
            val entity = if (quality == null) DatabaseRepo.HanimeDownload.find(videoCode)
                else DatabaseRepo.HanimeDownload.find(videoCode, quality)
            if (entity != null) {
                val info = kotlin.runCatching {
                    val folder = DownloadStorage.folderForVideo(entity.videoUri, videoCode)
                    val cacheFile = folder?.let { DownloadStorage.file(it, DownloadStorage.INFO_FILE, "application/json", create = false) }
                    cacheFile?.let { uri -> DownloadStorage.input(uri).use { HJson.decodeFromStream<HanimeVideo?>(it) } }
                }.getOrNull()
                emit(
                    info?.copy(
                        videoUrls = linkedMapOf(
                            entity.quality to HanimeLink(
                                entity.videoUri, runCatching { DownloadStorage.suffix(entity.videoUri) }.getOrDefault(HFileManager.DEF_VIDEO_TYPE)
                            )
                        ),
                        coverUrl = entity.coverUri ?: entity.coverUrl
                    )
                )
            } else {
                emit(null)
            }
        }.flowOn(Dispatchers.IO)
    }
}
