package com.yenaly.han1meviewer.logic.blocking

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Dedicated preferences: clearing caches, signing out or editing online lists cannot erase rules. */
class BlocklistStore(private val preferences: SharedPreferences) {
    private val mutex = Mutex()
    private val _rules = MutableStateFlow(
        BlockRules(
            tags = preferences.getStringSet(TAGS, emptySet()).orEmpty().toSet(),
            authors = preferences.getStringSet(AUTHORS, emptySet()).orEmpty().toSet(),
        )
    )
    val rules = _rules.asStateFlow()

    suspend fun setBlocked(kind: BlockKind, name: String, blocked: Boolean) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val updated = _rules.value.updated(kind, name, blocked)
                if (updated != _rules.value) {
                    check(preferences.edit().putStringSet(TAGS, updated.tags)
                        .putStringSet(AUTHORS, updated.authors).commit()) {
                        "Cannot save content block rules"
                    }
                    _rules.value = updated
                }
            }
        }

    private companion object {
        const val TAGS = "tags"
        const val AUTHORS = "authors"
    }
}
