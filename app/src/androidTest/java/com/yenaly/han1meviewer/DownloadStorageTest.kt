package com.yenaly.han1meviewer

import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yenaly.han1meviewer.logic.download.DownloadStorage
import com.yenaly.han1meviewer.logic.entity.download.HanimeDownloadEntity
import com.yenaly.han1meviewer.ui.fragment.settings.DownloadSettingsFragment
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DownloadStorageTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val key = DownloadSettingsFragment.DOWNLOAD_PATH
    private lateinit var local: File
    private lateinit var tree: Uri
    private var oldSetting: String? = null

    @Before fun setUp() {
        oldSetting = Preferences.preferenceSp.getString(key, null)
        local = File(context.cacheDir, "download-storage-${UUID.randomUUID()}").also { it.mkdirs() }
        val root = DocumentsContract.buildTreeDocumentUri("com.yenaly.han1meviewer.test.downloads", "root")
        val rootDocument = DocumentsContract.buildDocumentUriUsingTree(root, "root")
        val case = requireNotNull(DocumentsContract.createDocument(context.contentResolver, rootDocument,
            Document.MIME_TYPE_DIR, "case-${UUID.randomUUID()}"))
        tree = DocumentsContract.buildTreeDocumentUri(requireNotNull(case.authority), DocumentsContract.getDocumentId(case))
    }

    @After fun tearDown() {
        Preferences.preferenceSp.edit().apply {
            if (oldSetting == null) remove(key) else putString(key, oldSetting)
        }.commit()
        if (::local.isInitialized) local.deleteRecursively()
        if (::tree.isInitialized) DocumentsContract.deleteDocument(context.contentResolver,
            DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)))
    }

    @Test fun fileTargetSupportsPauseAndResume() = checkResume(local.toUri().toString())
    @Test fun opaqueSafTargetSupportsPauseAndResume() = checkResume(tree.toString())

    private fun checkResume(root: String) {
        val uri = video(root)
        DownloadStorage.openVideo(uri).use {
            it.setLength(6)
            it.write(byteArrayOf(1, 2, 3), 0, 3)
            it.flush()
        }
        DownloadStorage.openVideo(uri).use {
            assertEquals(6L, it.length)
            it.seek(3)
            it.write(byteArrayOf(4, 5, 6), 0, 3)
            it.flush()
        }
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6), DownloadStorage.input(uri).use { it.readBytes() })
    }

    @Test fun queuedRootSnapshotDoesNotFollowSettingChanges() {
        setRoot(tree.toString())
        val queuedRoot = DownloadStorage.currentRoot
        setRoot(local.toUri().toString())
        val uri = video(queuedRoot)
        assertEquals("content", uri.scheme)
        assertFalse(File(local, HFileManager.HANIME_DOWNLOAD_FOLDER).exists())
    }

    @Test fun oldFileMetadataStaysBesideVideoAfterDirectoryChange() = checkMetadata(local.toUri().toString())
    @Test fun opaqueSafMetadataDoesNotTreatDocumentIdAsPath() = checkMetadata(tree.toString())

    private fun checkMetadata(root: String) {
        val uri = video(root)
        val folder = requireNotNull(DownloadStorage.folderForVideo(uri.toString(), "123"))
        val info = requireNotNull(DownloadStorage.file(folder, DownloadStorage.INFO_FILE, "application/json", true))
        DownloadStorage.output(info).use { it.write("cached info".toByteArray()) }
        setRoot(File(local, "another-root").toUri().toString())
        val originalFolder = requireNotNull(DownloadStorage.folderForVideo(uri.toString(), "123"))
        val loaded = requireNotNull(DownloadStorage.file(originalFolder, DownloadStorage.INFO_FILE, "application/json", false))
        assertEquals("cached info", DownloadStorage.input(loaded).bufferedReader().use { it.readText() })
    }

    @Test fun deletionDoesNotTouchNewRootOrAnotherResolution() {
        val old = video(tree.toString())
        val sibling = video(tree.toString(), "1080p")
        val otherRoot = video(local.toUri().toString())
        setRoot(local.toUri().toString())
        DownloadStorage.delete(old)
        DownloadStorage.cleanupSidecars(entity(old))
        assertFalse(DownloadStorage.exists(old))
        assertTrue(DownloadStorage.exists(sibling))
        assertTrue(DownloadStorage.exists(otherRoot))
    }

    @Test fun sharedMetadataSurvivesWhenAnotherResolutionExists() {
        val first = video(tree.toString())
        video(tree.toString(), "1080p")
        val folder = requireNotNull(DownloadStorage.folderForVideo(first.toString(), "123"))
        val info = requireNotNull(DownloadStorage.file(folder, DownloadStorage.INFO_FILE, "application/json", true))
        DownloadStorage.delete(first)
        DownloadStorage.cleanupSidecars(entity(first))
        assertTrue(DownloadStorage.exists(info))
    }

    @Test fun lastVideoCleanupRemovesOnlyKnownSidecars() {
        val first = video(tree.toString())
        val folder = requireNotNull(DownloadStorage.folderForVideo(first.toString(), "123"))
        val info = requireNotNull(DownloadStorage.file(folder, DownloadStorage.INFO_FILE, "application/json", true))
        DownloadStorage.delete(first)
        DownloadStorage.cleanupSidecars(entity(first))
        assertFalse(DownloadStorage.exists(info))
        assertFalse(DownloadStorage.exists(folder))
    }

    @Test fun cleanupLeavesUnrelatedFilesUntouched() {
        val first = video(local.toUri().toString())
        val folder = requireNotNull(DownloadStorage.folderForVideo(first.toString(), "123"))
        val unrelated = requireNotNull(DownloadStorage.file(folder, "keep.txt", "text/plain", true))
        DownloadStorage.delete(first)
        DownloadStorage.cleanupSidecars(entity(first))
        assertTrue(DownloadStorage.exists(unrelated))
    }

    @Test fun missingResumeTargetIsNotRecreated() {
        val missing = File(local, "missing.mp4")
        assertThrows(Exception::class.java) { DownloadStorage.openVideo(missing.toUri()).close() }
        assertFalse(missing.exists())
    }

    @Test fun cleanupNeverRecursivelyDeletesDirectoryNamedLikeSidecar() {
        val first = video(local.toUri().toString())
        val folder = requireNotNull(DownloadStorage.folderForVideo(first.toString(), "123"))
        val nested = File(requireNotNull(folder.path), DownloadStorage.INFO_FILE).also { it.mkdirs() }
        val keep = File(nested, "keep.txt").also { it.writeText("keep") }
        DownloadStorage.delete(first)
        DownloadStorage.cleanupSidecars(entity(first))
        assertTrue(keep.exists())
    }

    @Test fun missingSafResumeTargetIsNotRecreated() {
        val uri = video(tree.toString())
        DownloadStorage.delete(uri)
        assertThrows(Exception::class.java) { DownloadStorage.openVideo(uri).close() }
        assertFalse(DownloadStorage.exists(uri))
    }

    @Test fun invalidSelectionKeepsPreviousSetting() {
        setRoot(tree.toString())
        assertThrows(IllegalArgumentException::class.java) { DownloadStorage.selectTree(local.toUri()) }
        assertEquals(tree.toString(), DownloadStorage.currentRoot)
    }

    @Test fun newTaskCannotTruncateUntrackedFile() {
        val uri = video(tree.toString())
        DownloadStorage.output(uri).use { it.write(byteArrayOf(8, 9)) }
        assertThrows(Exception::class.java) { video(tree.toString()) }
        assertArrayEquals(byteArrayOf(8, 9), DownloadStorage.input(uri).use { it.readBytes() })
    }

    @Test fun opaqueUriSuffixUsesProviderDisplayName() {
        val uri = DownloadStorage.createVideo(tree.toString(), "123", "Title", "720p", "webm")
        assertFalse(requireNotNull(uri.lastPathSegment).contains('.'))
        assertEquals("webm", DownloadStorage.suffix(uri.toString()))
    }

    @Test fun unsafeVideoCodeCannotEscapeDownloadFolder() {
        assertThrows(IllegalArgumentException::class.java) {
            DownloadStorage.videoFolder(local.toUri().toString(), "../escape", true)
        }
        assertFalse(File(local, "escape").exists())
    }

    @Test fun resetUsesLegacyRootWithoutReleasingExistingGrants() {
        val grants = context.contentResolver.persistedUriPermissions.map { it.uri }.toSet()
        setRoot(tree.toString())
        DownloadStorage.reset()
        assertEquals(DownloadStorage.legacyRoot, DownloadStorage.currentRoot)
        assertEquals(grants, context.contentResolver.persistedUriPermissions.map { it.uri }.toSet())
    }

    private fun setRoot(root: String) { check(Preferences.preferenceSp.edit().putString(key, root).commit()) }
    private fun video(root: String, quality: String = "720p") = DownloadStorage.createVideo(root, "123", "Title", quality, "mp4")
    private fun entity(uri: Uri) = HanimeDownloadEntity("", null, "Title", 0L, "123", uri.toString(), "720p", "", 6L, 0L)
}
