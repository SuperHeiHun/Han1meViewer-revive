package com.yenaly.han1meviewer.ui.view.video

internal data class PlayerPanelLayout(val width: Int, val height: Int, val fromBottom: Boolean)

/** Use the app window, not the inline player's short viewport or the physical display. */
internal fun playerPanelLayout(
    windowWidth: Int,
    windowHeight: Int,
    maximumSideWidth: Int,
    alwaysFromBottom: Boolean = false,
): PlayerPanelLayout? {
    require(maximumSideWidth > 0)
    if (windowWidth <= 0 || windowHeight <= 0) return null
    val bottom = alwaysFromBottom || windowHeight >= windowWidth
    return if (bottom) {
        PlayerPanelLayout(windowWidth, (windowHeight / 2).coerceAtLeast(1), true)
    } else {
        PlayerPanelLayout(minOf(maximumSideWidth, (windowWidth / 2).coerceAtLeast(1)), windowHeight, false)
    }
}
