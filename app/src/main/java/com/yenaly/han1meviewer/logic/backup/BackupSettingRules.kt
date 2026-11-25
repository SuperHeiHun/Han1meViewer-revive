package com.yenaly.han1meviewer.logic.backup

import com.yenaly.han1meviewer.HANIME_ALTER_BASE_URL
import com.yenaly.han1meviewer.HANIME_MAIN_BASE_URL
import com.yenaly.han1meviewer.logic.player.PlaybackMode
import com.yenaly.han1meviewer.logic.player.PlaybackAspectRatio
import com.yenaly.han1meviewer.ui.view.video.PlayerDoubleTapMode
import com.yenaly.han1meviewer.ui.view.video.PlayerLongPressMode
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/** Only portable, user-facing settings are allowed. No cookies, credentials, paths or transient flags. */
object BackupSettingRules {
    val defaults: Map<String, JsonPrimitive> = buildMap {
        mapOf(
            "video_language" to "zh-CHT", "switch_player_kernel" to "ExoPlayer",
            "player_speed" to "1.0", "long_press_speed_times" to "2.5",
            "playback_mode" to "pause_at_end", "playback_aspect_ratio" to "fit",
            "gesture_double_tap_mode" to "play_pause", "gesture_seek_step" to "10",
            "gesture_long_press_mode" to "legacy", "gesture_temporary_speed" to "2.0",
            "domain_name" to HANIME_MAIN_BASE_URL, "proxy_ip" to "",
        ).forEach { (key, value) -> put(key, JsonPrimitive(value)) }
        mapOf(
            "show_bottom_progress" to true, "use_built_in_hosts" to false,
            "playback_auto_rotate" to false, "playback_playlist_reversed" to false,
            "auto_fullscreen" to false,
            "gesture_seek_enabled" to true, "gesture_brightness_enabled" to true, "gesture_volume_enabled" to true,
            "h_keyframes_enable" to true, "shared_h_keyframes_enable" to true,
            "shared_h_keyframes_use_first" to false, "show_comment_when_countdown" to false,
            "use_ci_update_channel" to false, "disable_auto_update" to false, "privacy_mode" to false,
        ).forEach { (key, value) -> put(key, JsonPrimitive(value)) }
        mapOf(
            "slide_sensitivity" to 5, "when_countdown_remind" to 10,
            "gesture_brightness_sensitivity" to 5, "gesture_volume_sensitivity" to 5,
            "proxy_type" to 1, "proxy_port" to -1, "download_count_limit" to 2,
            "download_speed_limit" to 0, "update_popup_interval_days" to 0,
        ).forEach { (key, value) -> put(key, JsonPrimitive(value)) }
    }

    fun isValid(key: String, value: JsonPrimitive): Boolean {
        // Read old version-1 backups, but never export or keep the retired switch.
        if (key == "auto_play_next") return !value.isString && value.booleanOrNull != null
        val default = defaults[key] ?: return false
        if (default.isString != value.isString) return false
        if (default.booleanOrNull != null) return value.booleanOrNull != null && !value.isString
        return when (key) {
            "video_language" -> value.content in setOf("zh-CHT", "zh-CHS")
            "switch_player_kernel" -> value.content in setOf("ExoPlayer", "MediaPlayer")
            "playback_mode" -> PlaybackMode.entries.any { it.value == value.content }
            "playback_aspect_ratio" -> PlaybackAspectRatio.entries.any { it.value == value.content }
            "player_speed" -> value.content.toFloatOrNull()?.let { it.isFinite() && it in 0.5f..3f } == true
            "long_press_speed_times" -> value.content.toFloatOrNull()?.let { it.isFinite() && it in 1f..4f } == true
            "gesture_double_tap_mode" -> PlayerDoubleTapMode.entries.any { it.value == value.content }
            "gesture_seek_step" -> value.content in setOf("5", "10", "15", "30")
            "gesture_long_press_mode" -> PlayerLongPressMode.entries.any { it.value == value.content }
            "gesture_temporary_speed" -> value.content.toFloatOrNull()?.let { it.isFinite() && it in 0.5f..4f } == true
            "domain_name" -> value.content in setOf(HANIME_MAIN_BASE_URL, HANIME_ALTER_BASE_URL)
            "proxy_ip" -> value.content.length <= 255 && value.content.none { it.isISOControl() }
            "slide_sensitivity", "gesture_brightness_sensitivity", "gesture_volume_sensitivity" -> value.intOrNull in 1..9
            "when_countdown_remind" -> value.intOrNull in 5..30
            "proxy_type" -> value.intOrNull in 0..3
            "proxy_port" -> value.intOrNull == -1 || value.intOrNull in 1..65535
            "download_count_limit" -> value.intOrNull in 0..10
            "download_speed_limit" -> value.intOrNull in 0..8
            "update_popup_interval_days" -> value.intOrNull in 0..30
            else -> false
        }
    }

    fun migratePlaybackSettings(values: Map<String, JsonPrimitive>): Map<String, JsonPrimitive> =
        (values - "auto_play_next").toMutableMap().apply {
            if ("playback_mode" !in this) values["auto_play_next"]?.booleanOrNull?.let {
                put("playback_mode", JsonPrimitive(PlaybackMode.fromLegacy(it).value))
            }
        }
}
