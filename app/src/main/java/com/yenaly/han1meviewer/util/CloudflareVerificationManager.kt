package com.yenaly.han1meviewer.util

import android.os.SystemClock
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.activity.CloudflareVerificationActivity
import com.yenaly.han1meviewer.ui.activity.LoginActivity
import com.yenaly.yenaly_libs.ActivityManager
import com.yenaly.yenaly_libs.utils.startActivity

/**
 * Cloudflare 人機驗證的統一入口。
 *
 * 網絡層檢測到 Cloudflare 挑戰頁時調用 [onChallengeDetected]，
 * 由當前 Activity 彈出對話框，引導用戶前往 [CloudflareVerificationActivity] 手動完成驗證。
 */
object CloudflareVerificationManager {

    private const val PROMPT_INTERVAL_MS = 15_000L

    @Volatile
    private var lastPromptAt = 0L

    @Volatile
    private var verificationRunning = false

    @Volatile
    private var pendingRetry = false

    fun onChallengeDetected() {
        if (verificationRunning) return

        val now = SystemClock.elapsedRealtime()
        synchronized(this) {
            if (verificationRunning) return
            if (now - lastPromptAt < PROMPT_INTERVAL_MS) return
            lastPromptAt = now
        }

        val activity = ActivityManager.currentActivity.get() ?: return
        if (activity is CloudflareVerificationActivity || activity is LoginActivity) return
        if (activity.isFinishing || activity.isDestroyed) return

        activity.runOnUiThread {
            if (verificationRunning || activity.isFinishing || activity.isDestroyed) {
                return@runOnUiThread
            }
            activity.showAlertDialog {
                setTitle(R.string.cloudflare_verification_title)
                setMessage(R.string.cloudflare_verification_message)
                setPositiveButton(R.string.cloudflare_verification_start) { _, _ ->
                    activity.startActivity<CloudflareVerificationActivity>()
                }
                setNegativeButton(R.string.cancel, null)
            }
        }
    }

    fun onVerificationStarted() {
        verificationRunning = true
    }

    fun onVerificationFinished(success: Boolean) {
        verificationRunning = false
        if (success) {
            pendingRetry = true
        } else {
            // 用戶取消或驗證失敗時，允許下一次請求立即再次提示。
            lastPromptAt = 0L
        }
    }

    /**
     * 驗證成功後由當前頁面消費一次重試機會。
     */
    fun consumePendingRetry(): Boolean {
        val retry = pendingRetry
        pendingRetry = false
        return retry
    }
}
