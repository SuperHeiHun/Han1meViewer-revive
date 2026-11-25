package com.yenaly.han1meviewer.logic.history

import com.yenaly.han1meviewer.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

object HistoryPrivacy {
    const val KEY = "privacy_mode"
    val enabled: Boolean get() = Preferences.preferenceSp.getBoolean(KEY, false)
    private val gate = HistoryRecordingGate { enabled }

    fun ticket(): Long? = gate.ticket()

    suspend fun setEnabled(enabled: Boolean) = withContext(NonCancellable + Dispatchers.IO) {
        gate.change {
            check(Preferences.preferenceSp.edit().putBoolean(KEY, enabled).commit())
        }
    }

    suspend fun record(ticket: Long?, insert: suspend () -> Unit) =
        withContext(NonCancellable + Dispatchers.IO) { gate.record(ticket, insert) }
}
