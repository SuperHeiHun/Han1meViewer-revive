package com.yenaly.han1meviewer.logic.network

import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.util.toLoginCookieList
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
        @JvmStatic
        val cookieMap: MutableMap<String, MutableList<Cookie>> = mutableMapOf()

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
        val loginCookies = Preferences.loginCookieStateFlow.value.toLoginCookieList(url.host)
        val storedCookies = cookieMap[url.host].orEmpty()
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
