package com.yenaly.han1meviewer.logic.backup

import com.yenaly.han1meviewer.logic.entity.LocalVideoEntity
import com.yenaly.han1meviewer.logic.entity.WatchHistoryEntity
import com.yenaly.han1meviewer.logic.blocking.BlockKind
import com.yenaly.han1meviewer.logic.blocking.BlockRules
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.InputStream

enum class BackupItem(val key: String) {
    WATCH_HISTORY("backup_watch_history"), SEARCH_HISTORY("backup_search_history"),
    SETTINGS("backup_app_settings"), FAVORITES("backup_favorites"), WATCH_LATER("backup_watch_later"),
}

@Serializable
data class BackupArchive(
    @Required val format: String = "Han1meViewerBackup",
    @Required val version: Int = 1,
    val createdAt: Long,
    val watchHistory: List<BackupWatch>? = null,
    val searchHistory: List<String>? = null,
    val settings: BackupSettings? = null,
    val favorites: List<BackupVideo>? = null,
    val watchLater: List<BackupVideo>? = null,
) {
    fun count(item: BackupItem): Int? = when (item) {
        BackupItem.WATCH_HISTORY -> watchHistory?.size
        BackupItem.SEARCH_HISTORY -> searchHistory?.size
        BackupItem.SETTINGS -> settings?.let { it.values.size + it.blockedTags.size + it.blockedAuthors.size }
        BackupItem.FAVORITES -> favorites?.size
        BackupItem.WATCH_LATER -> watchLater?.size
    }

    fun selected(items: Set<BackupItem>) = copy(
        watchHistory = watchHistory.takeIf { BackupItem.WATCH_HISTORY in items },
        searchHistory = searchHistory.takeIf { BackupItem.SEARCH_HISTORY in items },
        settings = settings.takeIf { BackupItem.SETTINGS in items },
        favorites = favorites.takeIf { BackupItem.FAVORITES in items },
        watchLater = watchLater.takeIf { BackupItem.WATCH_LATER in items },
    )
}

@Serializable
data class BackupWatch(
    val videoCode: String, val title: String, val coverUrl: String,
    val releaseDate: Long, val watchDate: Long,
) {
    fun toEntity() = WatchHistoryEntity(coverUrl, title, releaseDate, watchDate, videoCode)
    companion object {
        fun from(entity: WatchHistoryEntity) = with(entity) {
            BackupWatch(videoCode, title, coverUrl, releaseDate, watchDate)
        }
    }
}

@Serializable
data class BackupVideo(val videoCode: String, val title: String, val coverUrl: String, val addedAt: Long) {
    fun toEntity(type: String) = LocalVideoEntity(videoCode, type, title, coverUrl, addedAt)
    companion object {
        fun from(entity: LocalVideoEntity) = with(entity) { BackupVideo(videoCode, title, coverUrl, addedAt) }
    }
}

@Serializable
data class BackupSettings(
    val values: Map<String, JsonPrimitive>,
    val blockedTags: Set<String> = emptySet(),
    val blockedAuthors: Set<String> = emptySet(),
)

class InvalidBackupException : IllegalArgumentException()
class BackupTooLargeException : IllegalArgumentException()

/** Versioned JSON only: never deserialize Java objects, database files or arbitrary preference keys. */
object BackupCodec {
    const val MAX_BYTES = 20 * 1024 * 1024
    private const val MAX_ROWS = 100_000
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    fun encode(archive: BackupArchive): ByteArray {
        validate(archive)
        return json.encodeToString(archive).toByteArray(Charsets.UTF_8).also {
            if (it.size > MAX_BYTES) throw BackupTooLargeException()
        }
    }

    fun decode(bytes: ByteArray): BackupArchive {
        if (bytes.size > MAX_BYTES) throw BackupTooLargeException()
        return try {
            json.decodeFromString<BackupArchive>(bytes.toString(Charsets.UTF_8)).also(::validate)
        } catch (e: InvalidBackupException) {
            throw e
        } catch (_: Exception) {
            throw InvalidBackupException()
        }
    }

    fun read(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > MAX_BYTES) throw BackupTooLargeException()
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    fun validate(archive: BackupArchive) {
        fun requireValid(valid: Boolean) { if (!valid) throw InvalidBackupException() }
        fun validVideo(code: String, title: String, cover: String) {
            requireValid(code.matches(Regex("[0-9]{1,32}")) && title.length <= 8192 && cover.length <= 8192)
            requireValid(cover.isEmpty() || cover.startsWith("https://", true) || cover.startsWith("http://", true))
        }
        requireValid(archive.format == "Han1meViewerBackup" && archive.version == 1 && archive.createdAt >= 0)
        requireValid(BackupItem.entries.any { archive.count(it) != null })
        requireValid(BackupItem.entries.sumOf { archive.count(it) ?: 0 } <= MAX_ROWS)
        archive.watchHistory?.let { rows ->
            requireValid(rows.distinctBy { it.videoCode }.size == rows.size)
            rows.forEach {
                validVideo(it.videoCode, it.title, it.coverUrl)
                requireValid(it.watchDate >= 0)
            }
        }
        archive.searchHistory?.let { rows ->
            requireValid(rows.distinct().size == rows.size)
            rows.forEach { requireValid(it.isNotBlank() && it.length <= 8192) }
        }
        listOfNotNull(archive.favorites, archive.watchLater).forEach { rows ->
            requireValid(rows.distinctBy { it.videoCode }.size == rows.size)
            rows.forEach {
                validVideo(it.videoCode, it.title, it.coverUrl)
                requireValid(it.addedAt >= 0)
            }
        }
        archive.settings?.let { settings ->
            requireValid(settings.values.size <= 100)
            settings.values.forEach { (key, value) -> requireValid(BackupSettingRules.isValid(key, value)) }
            settings.blockedTags.forEach {
                requireValid(it.length <= 8192 && BlockRules.cleanName(it, BlockKind.TAG).isNotBlank())
            }
            settings.blockedAuthors.forEach {
                requireValid(it.length <= 8192 && BlockRules.cleanName(it, BlockKind.AUTHOR).isNotBlank())
            }
        }
    }
}
