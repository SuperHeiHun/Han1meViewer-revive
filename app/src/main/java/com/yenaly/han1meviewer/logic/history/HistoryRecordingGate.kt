package com.yenaly.han1meviewer.logic.history

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A request started privately, or spanning a privacy-mode change, must never be recorded. */
internal class HistoryRecordingGate(private val isEnabled: () -> Boolean) {
    private val mutex = Mutex()
    @Volatile private var generation = 0L

    fun ticket(): Long? = generation.takeUnless { isEnabled() }

    suspend fun change(persist: () -> Unit) = mutex.withLock {
        persist()
        generation++
    }

    suspend fun record(ticket: Long?, insert: suspend () -> Unit) = mutex.withLock {
        if (ticket != null && ticket == generation && !isEnabled()) insert()
    }
}
