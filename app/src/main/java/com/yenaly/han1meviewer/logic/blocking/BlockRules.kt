package com.yenaly.han1meviewer.logic.blocking

import java.text.Normalizer
import java.util.Locale

enum class BlockKind { TAG, AUTHOR }

/** Exact names, not title substrings. Keep display names separate from comparison keys. */
data class BlockRules(
    val tags: Set<String> = emptySet(),
    val authors: Set<String> = emptySet(),
) {
    private val tagKeys = tags.mapTo(hashSetOf()) { key(it, BlockKind.TAG) }
    private val authorKeys = authors.mapTo(hashSetOf()) { key(it, BlockKind.AUTHOR) }
    val isEmpty: Boolean get() = tags.isEmpty() && authors.isEmpty()

    fun contains(kind: BlockKind, name: String): Boolean =
        key(name, kind) in if (kind == BlockKind.TAG) tagKeys else authorKeys

    fun blocks(metadata: VideoBlockMetadata): Boolean =
        metadata.tags.any { contains(BlockKind.TAG, it) } ||
                metadata.author?.let { contains(BlockKind.AUTHOR, it) } == true

    fun updated(kind: BlockKind, name: String, blocked: Boolean): BlockRules {
        val displayName = cleanName(name, kind)
        require(displayName.isNotBlank())
        val names = (if (kind == BlockKind.TAG) tags else authors)
            .filterNot { key(it, kind) == key(displayName, kind) }.toMutableSet()
        if (blocked) names.add(displayName)
        return if (kind == BlockKind.TAG) copy(tags = names) else copy(authors = names)
    }

    companion object {
        fun cleanName(name: String, kind: BlockKind): String {
            val normalized = Normalizer.normalize(name, Normalizer.Form.NFKC).trim()
            return if (kind == BlockKind.TAG) normalized.trimStart('#').trim() else normalized
        }

        private fun key(name: String, kind: BlockKind): String =
            cleanName(name, kind).lowercase(Locale.ROOT)
    }
}

data class VideoBlockMetadata(val tags: Set<String>, val author: String?)
