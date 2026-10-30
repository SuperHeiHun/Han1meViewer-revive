package com.yenaly.han1meviewer.ui.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.databinding.ActivityCloudflareVerificationBinding
import com.yenaly.han1meviewer.logic.network.HCookieJar
import com.yenaly.han1meviewer.util.CloudflareVerificationManager
import com.yenaly.yenaly_libs.base.YenalyActivity
import com.yenaly.yenaly_libs.utils.showShortToast
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 供用戶手動完成 Cloudflare 人機驗證的 WebView 頁面。
 *
 * 驗證流程：等待挑戰頁真正跳轉到站點頁面後，再把 WebView 中的 Cookie
 * （尤其是 cf_clearance）同步到 OkHttp 的 [HCookieJar]，然後返回並讓原頁面重新載入。
 *
 * 注意：不能在 cf_clearance 一出現就立即結束——挑戰尚未完成時拿到的 Cookie 是無效的，
 * 必須等挑戰頁自己跳轉到站點正常頁面後再同步。
 */
class CloudflareVerificationActivity :
    YenalyActivity<ActivityCloudflareVerificationBinding>() {

    private val cookieHandler = Handler(Looper.getMainLooper())
    private var verified = false

    /**
     * WebView 中是否已出現 cf_clearance（僅代表挑戰流程進行中，不代表已完成）
     */
    private var clearanceSeen = false

    private val cookieChecker = object : Runnable {
        override fun run() {
            if (verified || isFinishing || isDestroyed) return
            val url = binding.wvVerification.url ?: Preferences.baseUrl
            if (!clearanceSeen && hasClearanceCookie(url)) {
                clearanceSeen = true
                // 若頁面在 Cookie 出現前就已加載完成，這裡補一次頁面判斷。
                cookieHandler.postDelayed({
                    if (!verified && !isFinishing && !isDestroyed) {
                        checkPageReady(binding.wvVerification, url)
                    }
                }, 500L)
            }
            cookieHandler.postDelayed(this, 700L)
        }
    }

    override fun getViewBinding(
        layoutInflater: LayoutInflater,
    ): ActivityCloudflareVerificationBinding =
        ActivityCloudflareVerificationBinding.inflate(layoutInflater)

    override fun setUiStyle() {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
    }

    override fun initData(savedInstanceState: Bundle?) {
        CloudflareVerificationManager.onVerificationStarted()

        setSupportActionBar(binding.toolbar)
        supportActionBar?.let {
            it.title = getString(R.string.cloudflare_verification_title)
            it.setDisplayHomeAsUpEnabled(true)
            it.setHomeActionContentDescription(R.string.back)
        }
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        initWebView()
        binding.srlVerification.setOnRefreshListener {
            clearanceSeen = false
            loadVerificationPage()
        }
        loadVerificationPage()
        cookieHandler.postDelayed(cookieChecker, 700L)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        val cookieManager = CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(binding.wvVerification, true)
        }

        binding.wvVerification.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                // 不要修改 User-Agent：Turnstile 要求會話內 UA 與瀏覽器特徵一致，
                // 改成桌面 UA 會導致挑戰失敗或無限循環。
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    binding.srlVerification.finishRefresh()
                    checkPageReady(view, url)
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    if (request.isForMainFrame) {
                        binding.srlVerification.finishRefresh()
                    }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    if (!request.isForMainFrame) return false
                    val siteHost = Preferences.baseUrl.toHttpUrlOrNull()?.host ?: return false
                    val targetHost = request.url.host ?: return false
                    if (targetHost != siteHost && !targetHost.endsWith(".$siteHost")) {
                        runCatching {
                            startActivity(Intent(Intent.ACTION_VIEW, request.url))
                        }
                        return true
                    }
                    return false
                }
            }
            webChromeClient = WebChromeClient()
            cookieManager.flush()
        }
    }

    private fun loadVerificationPage() {
        binding.wvVerification.loadUrl(Preferences.baseUrl)
    }

    private fun hasClearanceCookie(url: String): Boolean {
        val cookies = collectCookies(url)
        return cookies.contains(CLEARANCE_PREFIX)
    }

    private fun collectCookies(url: String): String {
        return listOf(url, Preferences.baseUrl)
            .distinct()
            .mapNotNull { CookieManager.getInstance().getCookie(it) }
            .joinToString("; ")
    }

    /**
     * 判斷當前頁面是否已經是站點正常頁面（而不是 Cloudflare 挑戰頁）。
     * 只有站點頁面 + cf_clearance 同時成立時才同步 Cookie 並返回。
     */
    private fun checkPageReady(view: WebView, url: String) {
        if (verified) return
        val siteHost = Preferences.baseUrl.toHttpUrlOrNull()?.host ?: return
        val httpUrl = url.toHttpUrlOrNull() ?: return
        val host = httpUrl.host
        if (host != siteHost && !host.endsWith(".$siteHost")) return
        if (url.contains("/cdn-cgi/", ignoreCase = true)) return
        if (!clearanceSeen) return

        view.evaluateJavascript(CHALLENGE_DETECTION_JS) { result ->
            if (verified) return@evaluateJavascript
            val isChallenge = result?.contains("challenge", ignoreCase = true) == true
            if (isChallenge) return@evaluateJavascript

            val cookies = collectCookies(url)
            if (!cookies.contains(CLEARANCE_PREFIX)) return@evaluateJavascript
            // 稍等片刻，等挑戰完成後可能輪換的 cf_clearance 等 Cookie 全部寫入。
            cookieHandler.postDelayed({
                if (verified || isFinishing || isDestroyed) return@postDelayed
                val finalCookies = collectCookies(url)
                if (!finalCookies.contains(CLEARANCE_PREFIX)) return@postDelayed
                completeVerification(url, finalCookies)
            }, COOKIE_SETTLE_DELAY_MS)
        }
    }

    private fun completeVerification(url: String, cookies: String) {
        verified = true
        cookieHandler.removeCallbacks(cookieChecker)
        // 記錄挑戰時實際使用的 UA：cf_clearance 與它綁定，後續請求需要保持一致。
        Preferences.cloudflareUserAgent = binding.wvVerification.settings.userAgentString.orEmpty()
        HCookieJar.saveCookies(url, cookies)
        CookieManager.getInstance().flush()
        CloudflareVerificationManager.onVerificationFinished(true)
        showShortToast(R.string.cloudflare_verification_success)
        setResult(RESULT_OK)
        finish()
    }

    override fun onDestroy() {
        cookieHandler.removeCallbacks(cookieChecker)
        if (!verified) {
            CloudflareVerificationManager.onVerificationFinished(false)
        }
        binding.wvVerification.apply {
            stopLoading()
            removeAllViews()
            destroy()
        }
        super.onDestroy()
    }

    companion object {

        private const val CLEARANCE_PREFIX = "cf_clearance="
        private const val COOKIE_SETTLE_DELAY_MS = 800L

        /**
         * 在頁面內判斷是否仍處於 Cloudflare 挑戰頁。
         * 注意：普通站點頁面也會加載 challenge-platform 腳本，不能以此作為判據。
         */
        private val CHALLENGE_DETECTION_JS = """
            (function() {
              try {
                var title = (document.title || '').toLowerCase();
                var text = (document.body ? document.body.innerText : '').toLowerCase();
                if (title.indexOf('just a moment') >= 0) return 'challenge';
                if (title.indexOf('attention required') >= 0) return 'challenge';
                if (text.indexOf('正在进行安全验证') >= 0) return 'challenge';
                if (text.indexOf('正在進行安全驗證') >= 0) return 'challenge';
                if (text.indexOf('enable javascript and cookies') >= 0) return 'challenge';
                if (text.indexOf('checking your browser') >= 0) return 'challenge';
                if (text.indexOf('verifying you are human') >= 0) return 'challenge';
                return 'ok';
              } catch (e) { return 'unknown'; }
            })()
        """.trimIndent()
    }
}
