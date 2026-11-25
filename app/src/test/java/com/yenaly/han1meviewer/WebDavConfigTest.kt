package com.yenaly.han1meviewer

import com.yenaly.han1meviewer.logic.backup.InvalidWebDavConfigException
import com.yenaly.han1meviewer.logic.backup.WebDavConfig
import org.junit.Assert.*
import org.junit.Test

class WebDavConfigTest {
    @Test fun completeHttpsFileUrlsAreSupported() {
        val config = WebDavConfig("https://example.com/remote.php/dav/files/user/backup.json", "user", "secret")
        assertEquals("example.com", config.endpoint().host)
    }

    @Test fun anonymousWebDavIsSupportedWithoutEmbeddingCredentials() {
        WebDavConfig("https://example.com/backup.json", "", "").endpoint()
    }

    @Test fun encodedUnicodePathsRemainEncoded() {
        val url = WebDavConfig("https://example.com/%E5%A4%87%E4%BB%BD/backup.json", "", "").endpoint()
        assertTrue(url.encodedPath.startsWith("/%E5%A4%87%E4%BB%BD/"))
    }

    @Test fun insecureAndNonHttpSchemesAreRejected() {
        for (url in listOf("http://example.com/a.json", "file:///a.json", "ftp://example.com/a.json")) {
            assertThrows(InvalidWebDavConfigException::class.java) { WebDavConfig(url, "", "").endpoint() }
        }
    }

    @Test fun directoriesQueriesAndFragmentsAreRejected() {
        for (suffix in listOf("/", "/backups/", "/a.json?q=token", "/a.json#fragment")) {
            assertThrows(InvalidWebDavConfigException::class.java) { WebDavConfig("https://example.com$suffix", "", "").endpoint() }
        }
    }

    @Test fun credentialsInsideUrlsAndInvalidUsernamesAreRejected() {
        assertThrows(InvalidWebDavConfigException::class.java) { WebDavConfig("https://user:secret@example.com/a.json", "", "").endpoint() }
        assertThrows(InvalidWebDavConfigException::class.java) { WebDavConfig("https://example.com/a.json", "user:name", "").endpoint() }
    }

    @Test fun newlineCredentialsAreRejected() {
        assertThrows(InvalidWebDavConfigException::class.java) { WebDavConfig("https://example.com/a.json", "user\n", "").endpoint() }
        assertThrows(InvalidWebDavConfigException::class.java) { WebDavConfig("https://example.com/a.json", "user", "secret\r\n").endpoint() }
    }

    @Test fun debugStringsNeverExposeEndpointOrPassword() {
        val text = WebDavConfig("https://private.example/a.json", "private-user", "private-password").toString()
        assertFalse(text.contains("private"))
        assertEquals("WebDavConfig(redacted)", text)
    }
}
