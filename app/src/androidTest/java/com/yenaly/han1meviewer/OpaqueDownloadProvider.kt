package com.yenaly.han1meviewer

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID

/** Minimal document contract fixture: IDs deliberately contain neither paths nor extensions. */
class OpaqueDownloadProvider : ContentProvider() {
    private data class Node(val file: File, val mime: String)
    private val nodes = linkedMapOf<String, Node>()

    override fun onCreate(): Boolean {
        val root = File(requireNotNull(context).cacheDir, "opaque-download-fixture")
        root.mkdirs()
        nodes["root"] = Node(root, Document.MIME_TYPE_DIR)
        return true
    }

    @Synchronized
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                       selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val columns = projection ?: arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE)
        val cursor = MatrixCursor(columns)
        val id = DocumentsContract.getDocumentId(uri)
        val node = nodes[id] ?: return cursor
        if (uri.lastPathSegment == "children") {
            nodes.filterValues { it.file.exists() && it.file.parentFile == node.file }.forEach { (key, value) ->
                addRow(cursor, columns, key, value)
            }
        } else if (node.file.exists()) {
            addRow(cursor, columns, id, node)
        }
        return cursor
    }

    private fun addRow(cursor: MatrixCursor, columns: Array<out String>, id: String, node: Node) {
        cursor.addRow(columns.map {
            when (it) {
                Document.COLUMN_DOCUMENT_ID -> id
                Document.COLUMN_DISPLAY_NAME -> node.file.name
                Document.COLUMN_MIME_TYPE -> node.mime
                Document.COLUMN_SIZE -> node.file.length()
                Document.COLUMN_FLAGS -> Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE or
                        (if (node.file.isDirectory) Document.FLAG_DIR_SUPPORTS_CREATE else 0)
                else -> null
            }
        }.toTypedArray())
    }

    @Suppress("DEPRECATION")
    @Synchronized
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val args = requireNotNull(extras)
        val uri = args.getParcelable<Uri>("uri") ?: throw IllegalArgumentException()
        val id = DocumentsContract.getDocumentId(uri)
        val node = nodes[id] ?: throw FileNotFoundException()
        return when (method) {
            "android:createDocument" -> {
                val name = requireNotNull(args.getString(Document.COLUMN_DISPLAY_NAME))
                require(name != "." && name != ".." && '/' !in name && '\\' !in name)
                val mime = requireNotNull(args.getString(Document.COLUMN_MIME_TYPE))
                val file = File(node.file, name)
                check(if (mime == Document.MIME_TYPE_DIR) file.mkdir() else file.createNewFile())
                val newId = "opaque-${UUID.randomUUID()}"
                nodes[newId] = Node(file, mime)
                Bundle().apply { putParcelable("uri", DocumentsContract.buildDocumentUriUsingTree(uri, newId)) }
            }
            "android:deleteDocument" -> {
                check(node.file.deleteRecursively())
                Bundle()
            }
            else -> throw UnsupportedOperationException(method)
        }
    }

    @Synchronized
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = nodes[DocumentsContract.getDocumentId(uri)]?.file ?: throw FileNotFoundException()
        if (!file.isFile) throw FileNotFoundException()
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    @Synchronized
    override fun getType(uri: Uri): String? = nodes[DocumentsContract.getDocumentId(uri)]?.mime
    override fun insert(uri: Uri, values: ContentValues?): Uri = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}
