package com.yenaly.han1meviewer.logic.network.interceptor

import com.yenaly.han1meviewer.Preferences
import okhttp3.Interceptor
import okhttp3.Response

object UserAgentInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        // cf_clearance 與解決挑戰時的 UA 綁定，這裡必須和驗證 WebView 保持一致。
        val request = chain.request().newBuilder().addHeader(
            "User-Agent", Preferences.effectiveUserAgent
        ).build()
        return chain.proceed(request)
    }
}
