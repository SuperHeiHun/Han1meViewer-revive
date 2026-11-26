package com.yenaly.han1meviewer.ui.view.video

internal data class PlayerPanelLayout(val width: Int, val height: Int, val fromBottom: Boolean)

/** Use the app window, not the inline player's short viewport or the physical display. */
internal fun playerPanelLayout(
    windowWidth: Int,
    windowHeight: Int,
    maximumSideWidth: Int,
): PlayerPanelLayout? {
    require(maximumSideWidth > 0)
    if (windowWidth <= 0 || windowHeight <= 0) return null
    val bottom = windowHeight >= windowWidth
    return if (bottom) {
        PlayerPanelLayout(windowWidth, (windowHeight / 2).coerceAtLeast(1), true)
    } else {
        PlayerPanelLayout(minOf(maximumSideWidth, (windowWidth / 2).coerceAtLeast(1)), windowHeight, false)
    }
}

internal data class PlayerPanelBounds(val left: Int, val top: Int, val width: Int, val height: Int)

/** Inline portrait settings replace the details viewport; fullscreen uses a right-hand panel. */
internal fun playerSettingsPanelBounds(
    windowWidth: Int,
    windowHeight: Int,
    maximumSideWidth: Int,
    details: PlayerPanelBounds? = null,
): PlayerPanelBounds? {
    require(maximumSideWidth > 0)
    if (windowWidth <= 0 || windowHeight <= 0) return null
    if (windowHeight >= windowWidth && details != null && details.left >= 0 && details.top >= 0 &&
        details.left < windowWidth && details.top < windowHeight && details.width > 0 && details.height > 0) {
        return details.copy(width = minOf(details.width, windowWidth - details.left),
            height = minOf(details.height, windowHeight - details.top))
    }
    val width = minOf(maximumSideWidth, if (windowWidth > windowHeight) windowWidth / 2 else windowWidth)
        .coerceAtLeast(1)
    return PlayerPanelBounds(windowWidth - width, 0, width, windowHeight)
}

/** Leave at least two thirds of a landscape window for the existing video viewport. */
internal fun playbackPlaylistDockWidth(width: Int, height: Int, maximumWidth: Int): Int? {
    require(maximumWidth > 0)
    if (width <= height || height <= 0) return null
    return minOf(maximumWidth, (width / 3).coerceAtLeast(1))
}
