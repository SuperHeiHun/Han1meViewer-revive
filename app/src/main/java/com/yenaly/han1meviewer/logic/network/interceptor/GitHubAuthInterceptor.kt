package com.yenaly.han1meviewer.logic.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/** Public update checks must also work without a bundled GitHub token. */
class GitHubAuthInterceptor(token: String) : Interceptor {
    private val token = token.trim()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (token.isEmpty() || !request.url.isHttps || request.url.host != "api.github.com") {
            return chain.proceed(request)
        }

        val authenticatedRequest = request.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        val response = chain.proceed(authenticatedRequest)
        if (response.code != 401 || request.method !in listOf("GET", "HEAD")) {
            return response
        }

        // A revoked or expired build-time token must not block public releases.
        response.close()
        return chain.proceed(request.newBuilder().removeHeader("Authorization").build())
    }
}
