package com.yenaly.han1meviewer

import com.yenaly.han1meviewer.logic.network.interceptor.GitHubAuthInterceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubAuthInterceptorTest {
    private fun requestHeaders(
        token: String,
        responseCodes: List<Int> = listOf(200),
        url: String = "https://api.github.com/repos/example/app/releases/latest",
        method: String = "GET",
    ): List<String?> {
        val headers = mutableListOf<String?>()
        val client = OkHttpClient.Builder()
            .addInterceptor(GitHubAuthInterceptor(token))
            .addInterceptor { chain ->
                val request = chain.request()
                headers += request.header("Authorization")
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(responseCodes[headers.lastIndex])
                    .message("Test response")
                    .body("{}".toResponseBody())
                    .build()
            }
            .build()
        val request = Request.Builder().url(url).method(method, null).build()
        client.newCall(request).execute().use { response ->
            assertEquals(responseCodes.last(), response.code)
        }
        return headers
    }

    @Test
    fun missingTokenDoesNotSendAuthorization() {
        assertNull(requestHeaders("").single())
    }

    @Test
    fun whitespaceTokenDoesNotSendAuthorization() {
        assertNull(requestHeaders(" \n\t ").single())
    }

    @Test
    fun configuredTokenIsTrimmedAndSentToGitHubApi() {
        assertEquals(listOf("Bearer test-token"), requestHeaders(" test-token \n"))
    }

    @Test
    fun invalidTokenRetriesOnceWithoutAuthorization() {
        assertEquals(
            listOf("Bearer expired-token", null),
            requestHeaders("expired-token", listOf(401, 200)),
        )
    }

    @Test
    fun unauthorizedAnonymousRequestIsNotRetried() {
        assertEquals(listOf<String?>(null), requestHeaders("", listOf(401)))
    }

    @Test
    fun unauthorizedFallbackIsNotRetriedAgain() {
        assertEquals(
            listOf("Bearer expired-token", null),
            requestHeaders("expired-token", listOf(401, 401)),
        )
    }

    @Test
    fun rateLimitIsNotRetriedWithoutAuthorization() {
        assertEquals(listOf("Bearer test-token"), requestHeaders("test-token", listOf(403)))
    }

    @Test
    fun tokenIsNotSentToReleaseDownloadHost() {
        assertNull(requestHeaders("test-token", url = "https://github.com/example/app/update.apk").single())
    }

    @Test
    fun tokenIsNotSentOverPlainHttp() {
        assertNull(requestHeaders("test-token", url = "http://api.github.com/releases/latest").single())
    }

    @Test
    fun headRequestsCanRetryWithoutAuthorization() {
        assertEquals(
            listOf("Bearer expired-token", null),
            requestHeaders("expired-token", listOf(401, 200), method = "HEAD"),
        )
    }
}
