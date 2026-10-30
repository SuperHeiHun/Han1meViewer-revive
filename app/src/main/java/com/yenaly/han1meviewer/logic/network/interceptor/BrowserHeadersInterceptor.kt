package com.yenaly.han1meviewer.logic.network.interceptor

import com.yenaly.han1meviewer.Preferences
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 給站點請求補上與瀏覽器一致的請求頭。
 *
 * Cloudflare 會對比 UA、Client Hints 與其他頭部的一致性；
 * 只改 UA 而缺少 sec-ch-ua 等頭部很容易被判定為機器人。
 */
object BrowserHeadersInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header("Accept") != null) return chain.proceed(request)

        val ua = Preferences.effectiveUserAgent
        val chromeVersion = CHROME_VERSION_REGEX.find(ua)?.groupValues?.get(1)
        val isMobile = ua.contains("Mobile")
        val isAndroid = ua.contains("Android")
        val isDocumentRequest = request.method == "GET" && request.body == null

        val builder = request.newBuilder()
        if (chromeVersion != null) {
            builder.header(
                "sec-ch-ua",
                "\"Chromium\";v=\"$chromeVersion\", \"Not A(Brand\";v=\"24\", \"Google Chrome\";v=\"$chromeVersion\""
            )
            builder.header("sec-ch-ua-mobile", if (isMobile) "?1" else "?0")
            builder.header("sec-ch-ua-platform", if (isAndroid) "\"Android\"" else "\"Windows\"")
        }
        builder.header(
            "Accept",
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8"
        )
        builder.header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        if (isDocumentRequest) {
            builder.header("Sec-Fetch-Dest", "document")
            builder.header("Sec-Fetch-Mode", "navigate")
            builder.header("Sec-Fetch-Site", "none")
            builder.header("Sec-Fetch-User", "?1")
        } else {
            builder.header("Sec-Fetch-Dest", "empty")
            builder.header("Sec-Fetch-Mode", "cors")
            builder.header("Sec-Fetch-Site", "same-origin")
        }
        builder.header("Upgrade-Insecure-Requests", "1")

        return chain.proceed(builder.build())
    }

    private val CHROME_VERSION_REGEX = Regex("""Chrome/(\d+)""")
}
