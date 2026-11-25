package com.yenaly.han1meviewer.logic.blocking

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale

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

    suspend fun mergeBackup(tags: Set<String>, authors: Set<String>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            fun merge(existing: Set<String>, added: Set<String>, kind: BlockKind): Set<String> {
                val names = linkedMapOf<String, String>()
                (existing + added).forEach { name ->
                    val clean = BlockRules.cleanName(name, kind)
                    require(clean.isNotBlank())
                    names.putIfAbsent(clean.lowercase(Locale.ROOT), clean)
                }
                return names.values.toSet()
            }
            val updated = BlockRules(merge(_rules.value.tags, tags, BlockKind.TAG),
                merge(_rules.value.authors, authors, BlockKind.AUTHOR))
            check(preferences.edit().putStringSet(TAGS, updated.tags).putStringSet(AUTHORS, updated.authors).commit())
            _rules.value = updated
        }
    }

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
