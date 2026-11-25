package com.yenaly.han1meviewer.logic.player

enum class PlaybackMode(val value: String) {
    LIST_LOOP("list_loop"), REPEAT_ONE("repeat_one"), PAUSE_AT_END("pause_at_end");

    companion object {
        fun fromValue(value: String?) = entries.firstOrNull { it.value == value } ?: PAUSE_AT_END
        fun fromLegacy(enabled: Boolean) = if (enabled) LIST_LOOP else PAUSE_AT_END
    }
}

enum class PlaybackAspectRatio(val value: String, val ratio: Double? = null) {
    FIT("fit"), RATIO_16_9("16_9", 16.0 / 9), RATIO_4_3("4_3", 4.0 / 3),
    FILL("fill"), CROP("crop");

    companion object {
        fun fromValue(value: String?) = entries.firstOrNull { it.value == value } ?: FIT
    }
}

data class PlaybackSettings(
    val mode: PlaybackMode = PlaybackMode.PAUSE_AT_END,
    val aspectRatio: PlaybackAspectRatio = PlaybackAspectRatio.FIT,
    val autoRotate: Boolean = false,
    val autoFullscreen: Boolean = false,
)

/** Global rule; a video without a series (including a download) is a one-item list. */
fun completionTarget(
    code: String, playlist: PlaybackPlaylist, mode: PlaybackMode,
    reverse: Boolean = false, fromDownload: Boolean = false,
): String? = when {
    code.isBlank() || mode == PlaybackMode.PAUSE_AT_END -> null
    mode == PlaybackMode.REPEAT_ONE || fromDownload || playlist.videos.isEmpty() -> code
    else -> {
        val ordered = playlist.orderedVideos(reverse)
        val index = ordered.indexOfFirst { it.videoCode == code }
        if (index < 0) null else ordered[(index + 1) % ordered.size].videoCode
    }
}

/** Unknown and square video sizes do not force a screen direction. */
fun videoIsLandscape(width: Int, height: Int): Boolean? =
    if (width <= 0 || height <= 0 || width == height) null else width > height

/** Dimensions of the texture, independent of the player's controls and hit targets. */
fun playbackTextureSize(
    availableWidth: Int, availableHeight: Int, videoWidth: Int, videoHeight: Int,
    aspect: PlaybackAspectRatio,
): Pair<Int, Int> {
    if (availableWidth <= 0 || availableHeight <= 0) return availableWidth.coerceAtLeast(0) to availableHeight.coerceAtLeast(0)
    if (aspect == PlaybackAspectRatio.FILL) return availableWidth to availableHeight
    val ratio = aspect.ratio ?: if (videoWidth > 0 && videoHeight > 0) videoWidth.toDouble() / videoHeight else return availableWidth to availableHeight
    val wider = availableWidth.toDouble() / availableHeight > ratio
    return if (wider == (aspect == PlaybackAspectRatio.CROP)) {
        availableWidth to (availableWidth / ratio).toInt().coerceAtLeast(1)
    } else {
        (availableHeight * ratio).toInt().coerceAtLeast(1) to availableHeight
    }
}
