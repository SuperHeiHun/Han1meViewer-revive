package com.yenaly.han1meviewer.ui.view.video

/** Main-thread ownership of one kernel's output, retained only across view reparenting. */
internal class PlayerSurfaceState<T : Any> {
    var surface: T? = null
        private set
    private var attached = false

    fun available(incoming: T): T {
        val retained = surface ?: incoming.also { surface = it }
        attached = true
        return retained
    }

    /** False transfers responsibility for the retained surface from the view to this owner. */
    fun destroyed(outgoing: T): Boolean {
        if (outgoing !== surface) return true
        attached = false
        return false
    }

    /** An attached surface is still owned by the view; only a detached one needs disposal. */
    fun clear(): T? {
        val detached = surface?.takeUnless { attached }
        surface = null
        attached = false
        return detached
    }
}
