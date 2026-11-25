package com.yenaly.han1meviewer.logic.search

internal data class TagCompletion(
    val tag: SearchTag, val source: String, val start: Int, val end: Int, val exact: Boolean,
) {
    fun apply(): Pair<String, Int> {
        val replacement = tagSearchText(tag.key)
        val needsSpace = end == source.length || !source[end].isWhitespace()
        val inserted = replacement + if (needsSpace) " " else ""
        // Place the caret after an existing separator too, ready for the next tag/title word.
        val caret = start + inserted.length + if (!needsSpace) 1 else 0
        return source.replaceRange(start, end, inserted) to caret
    }
}

/** Offline prefix completion of the token at the caret; never rewrites quoted title text. */
internal class TagCompleter(private val tags: List<SearchTag>) {
    private val aliases = tags.associateWith { tag -> tag.aliases.map(::normalizeSearchTag) }
    private val parser = SearchInputParser(tags.searchTagAliases())
    private val maxWords = aliases.values.flatten().maxOfOrNull { it.count { c -> c == ' ' } + 1 } ?: 1

    fun suggest(text: String, caret: Int, limit: Int = 8): List<TagCompletion> {
        if (caret !in 0..text.length || limit <= 0) return emptyList()
        val tokens = tokenizeSearchInput(text)
        val activeIndex = tokens.indexOfFirst { caret > it.start && caret <= it.end }
        if (activeIndex < 0) return emptyList()
        val active = tokens[activeIndex]
        if (active.quoted && !active.explicitTag) return emptyList()
        val typed = tokenizeSearchInput(text.substring(active.start, caret)).firstOrNull()
            ?: return emptyList()
        val explicit = active.explicitTag || typed.value == "#" || typed.value == "＃"
        val activePrefix = if (typed.value == "#" || typed.value == "＃") "" else typed.value
        val found = linkedMapOf<String, TagCompletion>()
        val lengths = if (explicit) 1 downTo 1 else minOf(maxWords, activeIndex + 1) downTo 1
        for (length in lengths) {
            val startIndex = activeIndex - length + 1
            val preceding = tokens.subList(startIndex, activeIndex)
            if (preceding.any { it.quoted || it.explicitTag }) continue
            val prefix = normalizeSearchTag((preceding.map { it.value } + activePrefix).joinToString(" "))
            if (prefix.isBlank() && !explicit) continue
            val start = tokens[startIndex].start
            val otherTags = parser.parse(text.removeRange(start, active.end)).tags
            for (tag in tags) {
                if (tag.key in found || tag.key in otherTags) continue
                val names = aliases.getValue(tag)
                if (names.any { it.startsWith(prefix) }) {
                    found[tag.key] = TagCompletion(tag, text, start, active.end, prefix in names)
                }
            }
        }
        return found.values.sortedByDescending { it.exact }.take(limit)
    }
}
