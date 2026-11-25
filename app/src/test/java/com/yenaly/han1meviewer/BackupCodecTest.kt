package com.yenaly.han1meviewer

import com.yenaly.han1meviewer.logic.backup.*
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class BackupCodecTest {
    private val video = BackupVideo("12", "示例 title", "https://example.com/cover?q=1", 123L)
    private fun archive() = BackupArchive(createdAt = 123L,
        watchHistory = listOf(BackupWatch("12", "Title", video.coverUrl, -1, 10)),
        searchHistory = listOf("#\"Custom Tag\" \"Title\"", "1080p 60FPS"),
        settings = BackupSettings(BackupSettingRules.defaults, setOf("#标签"), setOf("Author")),
        favorites = listOf(video), watchLater = listOf(video))

    @Test fun allFiveCategoriesRoundTripWithoutLosingSyntaxOrSignedUrls() {
        val original = archive()
        assertEquals(original, BackupCodec.decode(BackupCodec.encode(original)))
    }

    @Test fun selectingOneCategoryOmitsEveryOtherCategory() {
        val selected = archive().selected(setOf(BackupItem.WATCH_LATER))
        assertNull(selected.watchHistory)
        assertNull(selected.searchHistory)
        assertNull(selected.settings)
        assertNull(selected.favorites)
        assertEquals(listOf(video), selected.watchLater)
    }

    @Test fun missingCategoriesRemainDistinctFromSelectedEmptyCategories() {
        val empty = BackupArchive(createdAt = 0, favorites = emptyList())
        val decoded = BackupCodec.decode(BackupCodec.encode(empty))
        assertEquals(0, decoded.count(BackupItem.FAVORITES))
        assertNull(decoded.count(BackupItem.WATCH_LATER))
    }

    @Test fun formatMarkerAndVersionAreRequired() {
        assertThrows(InvalidBackupException::class.java) {
            BackupCodec.decode("""{"createdAt":0,"searchHistory":[]}""".toByteArray())
        }
    }

    @Test fun wrongFormatsAndFutureVersionsAreRejected() {
        for (bad in listOf(archive().copy(format = "Other"), archive().copy(version = 2))) {
            assertThrows(InvalidBackupException::class.java) { BackupCodec.encode(bad) }
        }
    }

    @Test fun emptyArchivesAndMalformedJsonAreRejected() {
        assertThrows(InvalidBackupException::class.java) { BackupCodec.encode(BackupArchive(createdAt = 0)) }
        assertThrows(InvalidBackupException::class.java) { BackupCodec.decode("not json".toByteArray()) }
    }

    @Test fun duplicateQueriesAreRejectedBeforeAnyRestore() {
        assertThrows(InvalidBackupException::class.java) { BackupCodec.encode(archive().copy(searchHistory = listOf("a", "a"))) }
    }

    @Test fun duplicateVideosWithinAListAreRejectedButSeparateListsAreAllowed() {
        assertThrows(InvalidBackupException::class.java) { BackupCodec.encode(archive().copy(favorites = listOf(video, video))) }
        BackupCodec.encode(archive())
    }

    @Test fun blankQueriesAndNonVideoIdentifiersAreRejected() {
        assertThrows(InvalidBackupException::class.java) { BackupCodec.encode(archive().copy(searchHistory = listOf(" "))) }
        assertThrows(InvalidBackupException::class.java) { BackupCodec.encode(archive().copy(favorites = listOf(video.copy(videoCode = "../12")))) }
    }

    @Test fun localAndIntentCoverUrlsAreRejected() {
        for (cover in listOf("file:///secret", "content://private/1", "intent://x")) {
            assertThrows(InvalidBackupException::class.java) {
                BackupCodec.encode(archive().copy(favorites = listOf(video.copy(coverUrl = cover))))
            }
        }
    }

    @Test fun credentialsAndArbitraryPreferenceKeysCannotBeImported() {
        for (key in listOf("login_cookie", "already_login", "credentials", "download_path", "random")) {
            assertFalse(BackupSettingRules.isValid(key, JsonPrimitive("secret")))
            assertThrows(InvalidBackupException::class.java) {
                BackupCodec.encode(archive().copy(settings = BackupSettings(mapOf(key to JsonPrimitive("secret")))))
            }
        }
    }

    @Test fun settingsDefaultsAreValidAndWrongTypesAreRejected() {
        BackupSettingRules.defaults.forEach { (key, value) -> assertTrue(key, BackupSettingRules.isValid(key, value)) }
        assertFalse(BackupSettingRules.isValid("privacy_mode", JsonPrimitive("true")))
        assertFalse(BackupSettingRules.isValid("slide_sensitivity", JsonPrimitive("5")))
        assertFalse(BackupSettingRules.isValid("video_language", JsonPrimitive(true)))
    }

    @Test fun unsafeSettingRangesAndDomainsAreRejected() {
        assertFalse(BackupSettingRules.isValid("slide_sensitivity", JsonPrimitive(0)))
        assertFalse(BackupSettingRules.isValid("download_speed_limit", JsonPrimitive(999)))
        assertFalse(BackupSettingRules.isValid("proxy_port", JsonPrimitive(65536)))
        assertFalse(BackupSettingRules.isValid("player_speed", JsonPrimitive("NaN")))
        assertFalse(BackupSettingRules.isValid("domain_name", JsonPrimitive("https://untrusted.example/")))
    }

    @Test fun meaninglessBlockRulesAreRejected() {
        assertThrows(InvalidBackupException::class.java) {
            BackupCodec.encode(archive().copy(settings = BackupSettings(emptyMap(), setOf("＃＃"))))
        }
    }

    @Test fun boundedStreamReadingRejectsOversizedFiles() {
        val input = ByteArrayInputStream(ByteArray(BackupCodec.MAX_BYTES + 1))
        assertThrows(BackupTooLargeException::class.java) { input.use(BackupCodec::read) }
    }

    @Test fun boundedStreamReadingPreservesExactBytes() {
        val bytes = BackupCodec.encode(archive())
        assertArrayEquals(bytes, ByteArrayInputStream(bytes).use(BackupCodec::read))
    }
}
