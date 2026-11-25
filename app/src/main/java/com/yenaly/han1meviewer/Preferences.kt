package com.yenaly.han1meviewer

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.yenaly.han1meviewer.logic.network.HProxySelector
import com.yenaly.han1meviewer.logic.network.interceptor.SpeedLimitInterceptor
import com.yenaly.han1meviewer.logic.player.PlaybackPlaylistSettings
import com.yenaly.han1meviewer.logic.player.PlaybackSettings
import com.yenaly.han1meviewer.logic.player.PlaybackMode
import com.yenaly.han1meviewer.logic.player.PlaybackAspectRatio
import com.yenaly.han1meviewer.ui.fragment.settings.DownloadSettingsFragment
import com.yenaly.han1meviewer.ui.fragment.settings.HKeyframeSettingsFragment
import com.yenaly.han1meviewer.ui.fragment.settings.HomeSettingsFragment
import com.yenaly.han1meviewer.ui.fragment.settings.NetworkSettingsFragment
import com.yenaly.han1meviewer.ui.fragment.settings.PlayerSettingsFragment
import com.yenaly.han1meviewer.ui.view.video.HJzvdStd
import com.yenaly.han1meviewer.ui.view.video.HMediaKernel
import com.yenaly.han1meviewer.ui.view.video.PlayerDoubleTapMode
import com.yenaly.han1meviewer.ui.view.video.PlayerGestureConfig
import com.yenaly.han1meviewer.ui.view.video.PlayerLongPressMode
import com.yenaly.han1meviewer.util.CookieString
import com.yenaly.han1meviewer.worker.HanimeDownloadManagerV2
import com.yenaly.yenaly_libs.utils.applicationContext
import com.yenaly.yenaly_libs.utils.getSpValue
import com.yenaly.yenaly_libs.utils.putSpValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days

object Preferences {
    /**
     * [Preference][androidx.preference.PreferenceFragmentCompat]自帶的SP
     */
    val preferenceSp: SharedPreferences
        get() = PreferenceManager.getDefaultSharedPreferences(
            applicationContext
        )

    // app 相關

    /**
     * 是否登入，一般跟[loginCookie]一起賦值
     */
    var isAlreadyLogin: Boolean
        get() = getSpValue(ALREADY_LOGIN, false)
        set(value) {
            loginStateFlow.value = value
            putSpValue(ALREADY_LOGIN, value)
        }

    val loginStateFlow = MutableStateFlow(isAlreadyLogin)

    /**
     * 保存的string格式的登入cookie
     */
    var loginCookie
        get() = CookieString(getSpValue(LOGIN_COOKIE, EMPTY_STRING))
        set(value) {
            loginCookieStateFlow.value = value
            putSpValue(LOGIN_COOKIE, value.cookie)
        }

    val loginCookieStateFlow = MutableStateFlow(loginCookie)

    // 更新 相關

    private const val UPDATE_NODE_ID = "update_node_id"

    var updateNodeId: String
        get() = getSpValue(UPDATE_NODE_ID, EMPTY_STRING)
        set(value) = putSpValue(UPDATE_NODE_ID, value)

    var lastUpdatePopupTime
        get() = getSpValue(HomeSettingsFragment.LAST_UPDATE_POPUP_TIME, 0L)
        set(value) = putSpValue(HomeSettingsFragment.LAST_UPDATE_POPUP_TIME, value)

    val updatePopupIntervalDays
        get() = preferenceSp.getInt(HomeSettingsFragment.UPDATE_POPUP_INTERVAL_DAYS, 0)

    val useCIUpdateChannel
        get() = preferenceSp.getBoolean(HomeSettingsFragment.USE_CI_UPDATE_CHANNEL, false)

    val disableAutoUpdate: Boolean
        get() = preferenceSp.getBoolean(HomeSettingsFragment.DISABLE_AUTO_UPDATE, false)

    // Check if show update dialog.
    val isUpdateDialogVisible: Boolean
        get() {
            if (disableAutoUpdate) return false
            val now = Clock.System.now()
            val lastCheckTime = Instant.fromEpochSeconds(lastUpdatePopupTime)
            val interval = updatePopupIntervalDays
            return now > lastCheckTime + interval.days
        }

    // 設定 相關

    val switchPlayerKernel: String
        get() = preferenceSp.getString(
            PlayerSettingsFragment.SWITCH_PLAYER_KERNEL,
            HMediaKernel.Type.ExoPlayer.name
        ) ?: HMediaKernel.Type.ExoPlayer.name

    val showBottomProgress: Boolean
        get() = preferenceSp.getBoolean(
            PlayerSettingsFragment.SHOW_BOTTOM_PROGRESS,
            true
        )

    const val PLAYBACK_MODE = "playback_mode"
    const val PLAYBACK_ASPECT_RATIO = "playback_aspect_ratio"
    const val PLAYBACK_AUTO_ROTATE = "playback_auto_rotate"
    internal const val LEGACY_AUTO_PLAY_NEXT = "auto_play_next"

    var playbackMode: PlaybackMode
        get() {
            val sp = preferenceSp
            return if (sp.contains(PLAYBACK_MODE)) PlaybackMode.fromValue(sp.getString(PLAYBACK_MODE, null))
            else PlaybackMode.fromLegacy(sp.getBoolean(LEGACY_AUTO_PLAY_NEXT, false))
        }
        set(value) {
            preferenceSp.edit().putString(PLAYBACK_MODE, value.value).remove(LEGACY_AUTO_PLAY_NEXT).apply()
        }

    var playbackAspectRatio: PlaybackAspectRatio
        get() = PlaybackAspectRatio.fromValue(preferenceSp.getString(PLAYBACK_ASPECT_RATIO, null))
        set(value) { preferenceSp.edit().putString(PLAYBACK_ASPECT_RATIO, value.value).apply() }

    var playbackAutoRotate: Boolean
        get() = preferenceSp.getBoolean(PLAYBACK_AUTO_ROTATE, false)
        set(value) { preferenceSp.edit().putBoolean(PLAYBACK_AUTO_ROTATE, value).apply() }

    val playbackSettings: PlaybackSettings
        get() = PlaybackSettings(playbackMode, playbackAspectRatio, playbackAutoRotate)

    fun observePlaybackSettings() = callbackFlow {
        val sp = preferenceSp
        val keys = setOf(PLAYBACK_MODE, PLAYBACK_ASPECT_RATIO, PLAYBACK_AUTO_ROTATE, LEGACY_AUTO_PLAY_NEXT)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key in keys) trySend(playbackSettings)
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        trySend(playbackSettings)
        awaitClose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    private const val PLAYBACK_PLAYLIST_REVERSED = "playback_playlist_reversed"

    var playbackPlaylistReversed: Boolean
        get() = preferenceSp.getBoolean(PLAYBACK_PLAYLIST_REVERSED, false)
        set(value) {
            preferenceSp.edit().putBoolean(PLAYBACK_PLAYLIST_REVERSED, value).apply()
        }

    val playbackPlaylistSettings: PlaybackPlaylistSettings
        get() = PlaybackPlaylistSettings(playbackPlaylistReversed)

    fun observePlaybackPlaylistSettings() = callbackFlow {
        val sp = preferenceSp
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == PLAYBACK_PLAYLIST_REVERSED) {
                trySend(playbackPlaylistSettings)
            }
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        trySend(playbackPlaylistSettings)
        awaitClose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    val playerSpeed: Float
        get() = preferenceSp.getString(
            PlayerSettingsFragment.PLAYER_SPEED,
            HJzvdStd.DEF_SPEED.toString()
        )?.toFloat() ?: HJzvdStd.DEF_SPEED

    val slideSensitivity: Int
        get() = preferenceSp.getInt(
            PlayerSettingsFragment.SLIDE_SENSITIVITY,
            HJzvdStd.DEF_PROGRESS_SLIDE_SENSITIVITY
        )

    val longPressSpeedTime: Float
        get() = preferenceSp.getString(
            PlayerSettingsFragment.LONG_PRESS_SPEED_TIMES,
            HJzvdStd.DEF_LONG_PRESS_SPEED_TIMES.toString()
        )?.toFloatOrNull()?.takeIf { it.isFinite() && it in 1f..4f }
            ?: HJzvdStd.DEF_LONG_PRESS_SPEED_TIMES

    internal val playerGestures: PlayerGestureConfig
        get() {
            val sp = preferenceSp
            return PlayerGestureConfig(
                doubleTapMode = PlayerDoubleTapMode.fromValue(sp.getString(PlayerSettingsFragment.GESTURE_DOUBLE_TAP_MODE, null)),
                seekStepSeconds = sp.getString(PlayerSettingsFragment.GESTURE_SEEK_STEP, "10")
                    ?.toIntOrNull()?.takeIf { it in setOf(5, 10, 15, 30) } ?: 10,
                longPressMode = PlayerLongPressMode.fromValue(sp.getString(PlayerSettingsFragment.GESTURE_LONG_PRESS_MODE, null)),
                temporarySpeed = sp.getString(PlayerSettingsFragment.GESTURE_TEMPORARY_SPEED, "2.0")
                    ?.toFloatOrNull()?.takeIf { it.isFinite() && it in 0.5f..4f } ?: 2f,
                legacySpeedMultiplier = longPressSpeedTime,
                seekEnabled = sp.getBoolean(PlayerSettingsFragment.GESTURE_SEEK_ENABLED, true),
                brightnessEnabled = sp.getBoolean(PlayerSettingsFragment.GESTURE_BRIGHTNESS_ENABLED, true),
                volumeEnabled = sp.getBoolean(PlayerSettingsFragment.GESTURE_VOLUME_ENABLED, true),
                seekSensitivity = slideSensitivity.coerceIn(1, 9),
                brightnessSensitivity = sp.getInt(PlayerSettingsFragment.GESTURE_BRIGHTNESS_SENSITIVITY, 5).coerceIn(1, 9),
                volumeSensitivity = sp.getInt(PlayerSettingsFragment.GESTURE_VOLUME_SENSITIVITY, 5).coerceIn(1, 9),
            )
        }

    val videoLanguage: String
        get() = preferenceSp.getString(HomeSettingsFragment.VIDEO_LANGUAGE, "zh-CHT") ?: "zh-CHT"

    val baseUrl: String
        get() = preferenceSp.getString(NetworkSettingsFragment.DOMAIN_NAME, HANIME_MAIN_BASE_URL)
            ?: HANIME_MAIN_BASE_URL

    val useBuiltInHosts: Boolean
        get() = preferenceSp.getBoolean(NetworkSettingsFragment.USE_BUILT_IN_HOSTS, false)

    // 關鍵H幀 相關

    val whenCountdownRemind: Int
        get() = preferenceSp.getInt(
            HKeyframeSettingsFragment.WHEN_COUNTDOWN_REMIND,
            HJzvdStd.DEF_COUNTDOWN_SEC
        ) * 1_000 // 越不了界，最大就30_000ms而已

    val showCommentWhenCountdown: Boolean
        get() = preferenceSp.getBoolean(
            HKeyframeSettingsFragment.SHOW_COMMENT_WHEN_COUNTDOWN,
            false
        )

    val hKeyframesEnable: Boolean
        get() = preferenceSp.getBoolean(
            HKeyframeSettingsFragment.H_KEYFRAMES_ENABLE,
            true
        )

    val sharedHKeyframesEnable: Boolean
        get() = preferenceSp.getBoolean(
            HKeyframeSettingsFragment.SHARED_H_KEYFRAMES_ENABLE,
            true
        )

    val sharedHKeyframesUseFirst: Boolean
        get() = preferenceSp.getBoolean(
            HKeyframeSettingsFragment.SHARED_H_KEYFRAMES_USE_FIRST,
            false
        )

    // 代理 相關

    val proxyType: Int
        get() = preferenceSp.getInt(
            NetworkSettingsFragment.PROXY_TYPE,
            HProxySelector.TYPE_SYSTEM
        )

    val proxyIp: String
        get() = preferenceSp.getString(NetworkSettingsFragment.PROXY_IP, EMPTY_STRING).orEmpty()

    val proxyPort: Int
        get() = preferenceSp.getInt(NetworkSettingsFragment.PROXY_PORT, -1)

    // Cloudflare 驗證相關

    private const val CLOUDFLARE_USER_AGENT = "cloudflare_user_agent"

    /**
     * 手動完成 Cloudflare 驗證時 WebView 實際使用的 User-Agent。
     *
     * cf_clearance 與解決挑戰時的 UA 綁定，站點請求需要保持一致；
     * 未驗證過時為空，此時沿用 [USER_AGENT]。
     */
    var cloudflareUserAgent: String
        get() = getSpValue(CLOUDFLARE_USER_AGENT, EMPTY_STRING)
        set(value) = putSpValue(CLOUDFLARE_USER_AGENT, value)

    /**
     * 站點請求實際使用的 User-Agent。
     */
    val effectiveUserAgent: String
        get() = cloudflareUserAgent.ifBlank { USER_AGENT }

    // 下载 相關

    /** Device-specific SAF grant; deliberately excluded from portable backups. */
    val downloadTreeUri: String
        get() = preferenceSp.getString(DownloadSettingsFragment.DOWNLOAD_PATH, EMPTY_STRING).orEmpty()

    val downloadCountLimit: Int
        get() = preferenceSp.getInt(
            DownloadSettingsFragment.DOWNLOAD_COUNT_LIMIT,
            // HanimeDownloadManager.MAX_CONCURRENT_DOWNLOAD_DEF
            HanimeDownloadManagerV2.MAX_CONCURRENT_DOWNLOAD_DEF
        )

    /**
     * 对应关系详见 [SpeedLimitInterceptor.SPEED_BYTES]
     */
    val downloadSpeedLimit: Long
        get() {
            val index = preferenceSp.getInt(
                DownloadSettingsFragment.DOWNLOAD_SPEED_LIMIT,
                SpeedLimitInterceptor.NO_LIMIT_INDEX
            )
            return SpeedLimitInterceptor.SPEED_BYTES[index]
        }
}
