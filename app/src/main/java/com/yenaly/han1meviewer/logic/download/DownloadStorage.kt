package com.yenaly.han1meviewer.logic.download

import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.system.Os
import android.system.OsConstants
import android.webkit.MimeTypeMap
import androidx.core.net.toFile
import androidx.core.net.toUri
import com.yenaly.han1meviewer.HFileManager
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.entity.download.HanimeDownloadEntity
import com.yenaly.han1meviewer.ui.fragment.settings.DownloadSettingsFragment
import com.yenaly.yenaly_libs.utils.applicationContext
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** File URIs remain valid for old downloads; selected directories use opaque SAF URIs. */
object DownloadStorage {
    const val INFO_FILE = "info.json"
    private val resolver get() = applicationContext.contentResolver
    private const val GRANTS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    /** Capture once when adding a task, never re-read the setting when resuming it. */
    val currentRoot: String
        get() = Preferences.downloadTreeUri.ifBlank { legacyRoot }

    val legacyRoot: String get() = HFileManager.defaultDownloadFolder.toUri().toString()

    val locationLabel: String
        get() = Preferences.downloadTreeUri.takeIf(String::isNotBlank)?.let {
            runCatching { DocumentsContract.getTreeDocumentId(it.toUri()) }.getOrDefault(it)
        } ?: HFileManager.defaultDownloadFolder.path

    /** Keep old grants on change/reset: old downloads and paused tasks still need them. */
    @Synchronized
    fun selectTree(tree: Uri) {
        require(tree.scheme == "content" && DocumentsContract.isTreeUri(tree))
        val alreadyGranted = resolver.persistedUriPermissions.any { it.uri == tree }
        resolver.takePersistableUriPermission(tree, GRANTS)
        try {
            val root = rootDocument(tree)
            require(entry(root)?.mime == Document.MIME_TYPE_DIR)
            val probeDirectory = directory(root, ".han1meviewer-${UUID.randomUUID()}", create = true)
                ?: throw unavailable()
            try {
                val probe = createFile(probeDirectory, "probe.tmp", "application/octet-stream")
                try {
                    openVideo(probe).use {
                        it.setLength(2)
                        it.seek(1)
                        it.write(byteArrayOf(7), 0, 1)
                        it.flush()
                        it.seek(0)
                    }
                    input(probe).use { check(it.read() == 0 && it.read() == 7) }
                    output(probe).use { it.write(byteArrayOf(9)) }
                    input(probe).use { check(it.read() == 9 && it.read() == -1) }
                } finally {
                    delete(probe)
                }
            } finally {
                delete(probeDirectory)
            }
            saveRoot(tree.toString())
        } catch (e: Exception) {
            if (!alreadyGranted) runCatching { resolver.releasePersistableUriPermission(tree, GRANTS) }
            throw e
        }
    }

    @Synchronized
    fun reset() = saveRoot(null)

    private fun saveRoot(root: String?) {
        val sp = Preferences.preferenceSp
        val key = DownloadSettingsFragment.DOWNLOAD_PATH
        val previous = sp.getString(key, null)
        if (!sp.edit().putString(key, root).commit()) {
            // commit() can fail after changing the in-memory value; restore that too.
            sp.edit().putString(key, previous).commit()
            throw unavailable()
        }
    }

    fun videoFolder(root: String, videoCode: String, create: Boolean): Uri? {
        require(videoCode.isNotBlank() && videoCode != "." && videoCode != ".." &&
            videoCode.none { it == '/' || it == '\\' || it == '\u0000' })
        val parent = rootDocument(root.toUri())
        if (parent.scheme == "file" && create) {
            val file = parent.toFile()
            check(file.isDirectory || file.mkdirs())
            File(file, ".nomedia").let { if (!it.exists()) it.createNewFile() }
        }
        val downloads = directory(parent, HFileManager.HANIME_DOWNLOAD_FOLDER, create) ?: return null
        if (create) file(downloads, ".nomedia", "application/octet-stream", create = true)
        return directory(downloads, videoCode, create)
    }

    /** The tree portion is retained in every SAF child URI; no document ID is parsed as a path. */
    fun folderForVideo(videoUri: String, videoCode: String): Uri? {
        val uri = videoUri.toUri()
        return when (uri.scheme) {
            "file" -> uri.toFile().parentFile?.toUri()
            "content" -> videoFolder(uri.toString(), videoCode, create = false)
            else -> throw unavailable()
        }
    }

    fun createVideo(root: String, videoCode: String, title: String, quality: String, suffix: String): Uri {
        val folder = videoFolder(root, videoCode, create = true) ?: throw unavailable()
        val name = HFileManager.createVideoName(title, quality, suffix)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(suffix.lowercase()) ?: "video/mp4"
        // Never truncate an untracked file left at the same destination.
        if (child(folder, name) != null) throw IOException(applicationContext.getString(R.string.this_data_exists))
        return createFile(folder, name, mime)
    }

    fun file(folder: Uri, name: String, mime: String, create: Boolean): Uri? =
        child(folder, name)?.also { require(it.mime != Document.MIME_TYPE_DIR) }?.uri
            ?: if (create) createFile(folder, name, mime) else null

    fun input(uri: Uri): InputStream = when (uri.scheme) {
        "file" -> uri.toFile().inputStream()
        "content" -> resolver.openInputStream(uri) ?: throw unavailable()
        else -> throw unavailable()
    }

    fun output(uri: Uri): OutputStream = when (uri.scheme) {
        "file" -> uri.toFile().outputStream()
        "content" -> resolver.openOutputStream(uri, "wt") ?: throw unavailable()
        else -> throw unavailable()
    }

    fun openVideo(uri: Uri): SeekableOutput {
        val descriptor = when (uri.scheme) {
            "file" -> {
                if (!uri.toFile().isFile) throw unavailable()
                ParcelFileDescriptor.open(uri.toFile(), ParcelFileDescriptor.MODE_READ_WRITE)
            }
            "content" -> resolver.openFileDescriptor(uri, "rw") ?: throw unavailable()
            else -> throw unavailable()
        }
        return try {
            SeekableOutput(descriptor).also { it.seek(0) }
        } catch (e: Exception) {
            descriptor.close()
            throw e
        }
    }

    fun exists(uri: Uri): Boolean = entry(uri) != null

    fun delete(uri: Uri) {
        if (!exists(uri)) return
        val deleted = when (uri.scheme) {
            "file" -> uri.toFile().delete()
            "content" -> DocumentsContract.deleteDocument(resolver, uri)
            else -> false
        }
        if (!deleted) throw unavailable()
    }

    /** Remove sidecars only when no other file (including another resolution) is present. */
    fun cleanupSidecars(entity: HanimeDownloadEntity) {
        val folder = folderForVideo(entity.videoUri, entity.videoCode) ?: return
        val entries = children(folder)
        if (entries.any { it.mime == Document.MIME_TYPE_DIR ||
                (it.uri.toString() != entity.coverUri && it.name != INFO_FILE && it.name != ".nomedia") }) return
        entries.forEach { delete(it.uri) }
        if (children(folder).isEmpty()) delete(folder)
    }

    fun suffix(videoUri: String): String {
        val name = entry(videoUri.toUri())?.name ?: return HFileManager.DEF_VIDEO_TYPE
        return name.substringAfterLast('.', HFileManager.DEF_VIDEO_TYPE)
    }

    private fun rootDocument(uri: Uri): Uri = when (uri.scheme) {
        "file" -> uri
        "content" -> DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
        else -> throw unavailable()
    }

    private fun directory(parent: Uri, name: String, create: Boolean): Uri? {
        val existing = child(parent, name)
        if (existing != null) {
            require(existing.mime == Document.MIME_TYPE_DIR)
            return existing.uri
        }
        if (!create) return null
        return if (parent.scheme == "file") {
            File(parent.toFile(), name).also { check(it.isDirectory || it.mkdirs()) }.toUri()
        } else {
            DocumentsContract.createDocument(resolver, parent, Document.MIME_TYPE_DIR, name) ?: throw unavailable()
        }
    }

    private fun createFile(parent: Uri, name: String, mime: String): Uri {
        require(name.isNotBlank() && name != "." && name != ".." && name.none { it == '/' || it == '\\' || it == '\u0000' })
        return if (parent.scheme == "file") {
            File(parent.toFile(), name).also { check(it.createNewFile()) }.toUri()
        } else {
            DocumentsContract.createDocument(resolver, parent, mime, name) ?: throw unavailable()
        }
    }

    private data class Entry(val uri: Uri, val name: String, val mime: String)

    private fun child(parent: Uri, name: String): Entry? = children(parent).firstOrNull { it.name == name }

    private fun entry(uri: Uri): Entry? {
        if (uri.scheme == "file") {
            val file = uri.toFile()
            return if (file.exists()) Entry(uri, file.name, if (file.isDirectory) Document.MIME_TYPE_DIR else "") else null
        }
        require(uri.scheme == "content")
        val cursor = resolver.query(uri, arrayOf(Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE), null, null, null)
            ?: throw unavailable()
        return cursor.use { if (it.moveToFirst()) Entry(uri, it.getString(0), it.getString(1)) else null }
    }

    private fun children(parent: Uri): List<Entry> {
        if (parent.scheme == "file") {
            val folder = parent.toFile()
            if (!folder.exists()) return emptyList()
            return (folder.listFiles() ?: throw unavailable()).map {
                Entry(it.toUri(), it.name, if (it.isDirectory) Document.MIME_TYPE_DIR else "")
            }
        }
        require(parent.scheme == "content")
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(parent, DocumentsContract.getDocumentId(parent))
        return resolver.query(uri, arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(Entry(
                    DocumentsContract.buildDocumentUriUsingTree(parent, cursor.getString(0)),
                    cursor.getString(1), cursor.getString(2)))
            }
        } ?: throw unavailable()
    }

    private fun unavailable() = IOException(applicationContext.getString(R.string.download_path_unavailable))

    class SeekableOutput internal constructor(private val descriptor: ParcelFileDescriptor) : Closeable {
        private val fd get() = descriptor.fileDescriptor
        val length: Long get() = Os.fstat(fd).st_size
        fun setLength(length: Long) { Os.ftruncate(fd, length) }
        fun seek(offset: Long) { check(Os.lseek(fd, offset, OsConstants.SEEK_SET) == offset) }
        fun flush() { Os.fsync(fd) }
        fun write(bytes: ByteArray, offset: Int, count: Int) {
            var written = 0
            while (written < count) {
                val n = Os.write(fd, bytes, offset + written, count - written)
                if (n <= 0) throw IOException("Unable to write download")
                written += n
            }
        }
        override fun close() = descriptor.close()
    }
}
