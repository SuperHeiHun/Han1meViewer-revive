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
import com.yenaly.han1meviewer.USER_AGENT
import com.yenaly.han1meviewer.databinding.ActivityCloudflareVerificationBinding
import com.yenaly.han1meviewer.logic.network.HCookieJar
import com.yenaly.han1meviewer.util.CloudflareVerificationManager
import com.yenaly.yenaly_libs.base.YenalyActivity
import com.yenaly.yenaly_libs.utils.showShortToast
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 供用戶手動完成 Cloudflare 人機驗證的 WebView 頁面。
 *
 * 驗證成功後會把 WebView 中的 Cookie（尤其是 cf_clearance）同步到 OkHttp 的 [HCookieJar]，
 * 然後返回並讓原頁面重新載入。
 */
class CloudflareVerificationActivity :
    YenalyActivity<ActivityCloudflareVerificationBinding>() {

    private val cookieHandler = Handler(Looper.getMainLooper())
    private var verified = false

    private val cookieChecker = object : Runnable {
        override fun run() {
            if (verified || isFinishing || isDestroyed) return
            val url = binding.wvVerification.url ?: Preferences.baseUrl
            if (!finishIfVerified(url)) {
                cookieHandler.postDelayed(this, 700L)
            }
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
                userAgentString = USER_AGENT
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    binding.srlVerification.finishRefresh()
                    finishIfVerified(url)
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

    /**
     * @return true 表示已檢測到驗證 Cookie 並結束頁面
     */
    private fun finishIfVerified(url: String): Boolean {
        if (verified) return true
        val cookies = listOf(url, Preferences.baseUrl)
            .distinct()
            .mapNotNull { CookieManager.getInstance().getCookie(it) }
            .joinToString("; ")
        if (cookies.isBlank()) return false
        val hasClearance = cookies.split(';').any {
            it.trim().startsWith("cf_clearance=")
        }
        if (!hasClearance) return false

        verified = true
        cookieHandler.removeCallbacks(cookieChecker)
        HCookieJar.saveCookies(url, cookies)
        CookieManager.getInstance().flush()
        CloudflareVerificationManager.onVerificationFinished(true)
        showShortToast(R.string.cloudflare_verification_success)
        setResult(RESULT_OK)
        finish()
        return true
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
}
