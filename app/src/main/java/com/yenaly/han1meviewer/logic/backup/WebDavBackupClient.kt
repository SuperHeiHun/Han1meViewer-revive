package com.yenaly.han1meviewer.logic.backup

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable
data class WebDavConfig(val url: String, val username: String, val password: String) {
    // Do not expose credentials through accidental logging of a data class.
    override fun toString() = "WebDavConfig(redacted)"

    fun endpoint(): HttpUrl {
        val parsed = url.toHttpUrlOrNull() ?: throw InvalidWebDavConfigException()
        if (!parsed.isHttps || parsed.username.isNotEmpty() || parsed.password.isNotEmpty() ||
            parsed.query != null || parsed.fragment != null || parsed.encodedPath.endsWith('/') ||
            username.contains(':') || username.any { it.isISOControl() } ||
            password.any { it == '\r' || it == '\n' } || url.length > 8192 || username.length > 1024 || password.length > 4096
        ) throw InvalidWebDavConfigException()
        return parsed
    }
}

class InvalidWebDavConfigException : IllegalArgumentException()
class WebDavHttpException(val status: Int) : IOException()

/** Isolated from website cookies, Cloudflare headers, interceptors and login state. */
class WebDavBackupClient {
    private val client = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS).callTimeout(90, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    suspend fun upload(config: WebDavConfig, bytes: ByteArray) {
        if (bytes.size > BackupCodec.MAX_BYTES) throw BackupTooLargeException()
        transfer(request(config).put(bytes.toRequestBody("application/json; charset=utf-8".toMediaType())).build(), false)
    }

    suspend fun download(config: WebDavConfig): ByteArray = transfer(request(config).get().build(), true)

    private fun request(config: WebDavConfig): Request.Builder = Request.Builder()
        .url(config.endpoint()).header("Accept", "application/json").header("Cache-Control", "no-store")
        .apply {
            if (config.username.isNotEmpty() || config.password.isNotEmpty()) {
                header("Authorization", Credentials.basic(config.username, config.password, Charsets.UTF_8))
            }
        }

    private suspend fun transfer(request: Request, download: Boolean): ByteArray = suspendCancellableCoroutine { cont ->
        val call = client.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val bytes = response.use {
                        // A partial/multi-status response is not a complete backup transfer.
                        val success = if (download) it.code == 200 else it.code in setOf(200, 201, 204)
                        if (!success) throw WebDavHttpException(it.code)
                        if (download) {
                            val body = it.body ?: throw InvalidBackupException()
                            if (body.contentLength() > BackupCodec.MAX_BYTES) throw BackupTooLargeException()
                            body.byteStream().use(BackupCodec::read)
                        } else ByteArray(0)
                    }
                    if (cont.isActive) cont.resume(bytes)
                } catch (e: Exception) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
            }
        })
    }
}
