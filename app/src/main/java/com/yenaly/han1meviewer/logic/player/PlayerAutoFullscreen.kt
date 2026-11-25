package com.yenaly.han1meviewer.logic.player

/** Consume each media session once, so buffering/resume/replay cannot undo a manual exit. */
internal class PlayerAutoFullscreen {
    private var handledMedia: Any? = null

    fun shouldEnter(media: Any?, enabled: Boolean, eligible: Boolean, alreadyFullscreen: Boolean): Boolean {
        if (!enabled) {
            reset()
            return false
        }
        if (media == null || !eligible || handledMedia === media) return false
        handledMedia = media
        return !alreadyFullscreen
    }

    fun reset() {
        handledMedia = null
    }
}
