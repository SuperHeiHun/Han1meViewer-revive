package com.yenaly.han1meviewer.logic.player

/** Observe each source in normal mode too, so entering fullscreen remains a manual choice. */
internal class PlayerAutoRotation {
    private var observedSession: Any? = null
    private var observedLandscape: Boolean? = null

    fun directionFor(session: Any, fullscreen: Boolean, width: Int, height: Int): Boolean? {
        val landscape = videoIsLandscape(width, height) ?: return null
        if (observedSession === session && observedLandscape == landscape) return null
        observedSession = session
        observedLandscape = landscape
        return landscape.takeIf { fullscreen }
    }

    fun reset() {
        observedSession = null
        observedLandscape = null
    }
}
