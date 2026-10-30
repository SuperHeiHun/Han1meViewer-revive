package com.yenaly.han1meviewer.logic.network

import com.yenaly.han1meviewer.EMPTY_STRING
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.util.toLoginCookieList
import com.yenaly.yenaly_libs.utils.getSpValue
import com.yenaly.yenaly_libs.utils.putSpValue
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 用於管理 Cookie。
 *
 * #issue-71: 我竟然栽倒在 Cookie 管理上好幾年了！你去看我以前的管理方式，
 * 是完全錯誤的，竟然還能維持應用正常運行，太離譜了！怪不得切換簡體繁體一直不起作用！
 *
 * @project Han1meViewer
 * @author Yenaly Liew
 * @time 2024/03/13 013 15:20
 */
class HCookieJar : CookieJar {

    companion object {
        private const val SP_KEY_RAW_COOKIES = "hcookiejar_raw_cookies"
        private const val HOST_SEPARATOR = '\u0001'
        private const val COOKIE_SEPARATOR = '\u0002'

        @JvmStatic
        val cookieMap: MutableMap<String, MutableList<Cookie>> = mutableMapOf()

        /**
         * 各 host 最近一次從 WebView 同步的原始 Cookie 字符串，用於本地持久化。
         */
        private val rawCookieStrings = mutableMapOf<String, String>()

        /**
         * 把 WebView 中的 Cookie 同步給 OkHttp，主要用於 Cloudflare 的 cf_clearance。
         */
        @JvmStatic
        fun saveCookies(url: String, cookieString: String) {
            val httpUrl = url.toHttpUrlOrNull() ?: return
            val parsedCookies = cookieString.split(';').mapNotNull { cookie ->
                Cookie.parse(httpUrl, cookie.trim())
            }
            if (parsedCookies.isEmpty()) return
            val loginCookies =
                Preferences.loginCookieStateFlow.value.toLoginCookieList(httpUrl.host)
            cookieMap[httpUrl.host] = mergeCookies(
                cookieMap[httpUrl.host].orEmpty(),
                parsedCookies + loginCookies
            )
            rawCookieStrings[httpUrl.host] = cookieString
            persistRawCookies()
        }

        /**
         * 從本地恢復上次同步的 Cookie（主要是 cf_clearance），
         * 讓應用重啟後在驗證仍有效時不必再次彈出驗證頁。
         */
        @JvmStatic
        fun restore() {
            if (cookieMap.isNotEmpty()) return
            val data = getSpValue(SP_KEY_RAW_COOKIES, EMPTY_STRING)
            if (data.isBlank()) return
            data.split(HOST_SEPARATOR).forEach { entry ->
                val separatorIndex = entry.indexOf(COOKIE_SEPARATOR)
                if (separatorIndex <= 0) return@forEach
                val host = entry.substring(0, separatorIndex)
                val cookieString = entry.substring(separatorIndex + 1)
                val httpUrl = "https://$host/".toHttpUrlOrNull() ?: return@forEach
                val parsedCookies = cookieString.split(';').mapNotNull { cookie ->
                    Cookie.parse(httpUrl, cookie.trim())
                }
                if (parsedCookies.isEmpty()) return@forEach
                rawCookieStrings[host] = cookieString
                cookieMap[host] = mergeCookies(
                    cookieMap[host].orEmpty(),
                    parsedCookies + Preferences.loginCookieStateFlow.value.toLoginCookieList(host)
                )
            }
        }

        private fun persistRawCookies() {
            val data = rawCookieStrings.entries.joinToString(HOST_SEPARATOR.toString()) {
                "${it.key}$COOKIE_SEPARATOR${it.value}"
            }
            putSpValue(SP_KEY_RAW_COOKIES, data)
        }

        private fun mergeCookies(
            existing: List<Cookie>,
            incoming: List<Cookie>,
        ): MutableList<Cookie> {
            val merged = LinkedHashMap<String, Cookie>()
            (existing + incoming).forEach { cookie ->
                merged["${cookie.name}|${cookie.domain}|${cookie.path}"] = cookie
            }
            return merged.values.toMutableList()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val loginCookies = Preferences.loginCookieStateFlow.value.toLoginCookieList(url.host)
        val storedCookies = cookieMap[url.host].orEmpty().filter { it.expiresAt > now }
        if (storedCookies.isEmpty()) return loginCookies
        return mergeCookies(storedCookies, loginCookies)
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val loginCookies = Preferences.loginCookieStateFlow.value.toLoginCookieList(url.host)
        cookieMap[url.host] = mergeCookies(
            cookieMap[url.host].orEmpty(),
            cookies + loginCookies
        )
    }
}
