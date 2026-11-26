package com.yenaly.han1meviewer.logic.search

import java.text.Normalizer
import java.util.Locale

internal data class SearchInput(val query: String?, val tags: Set<String>)

internal fun quotedSearchText(text: String): String =
    "\"${text.replace("\\", "\\\\").replace("\"", "\\\"")}\""

/** A clicked label is a normal search; decorative hash prefixes are not part of its name. */
internal fun tagClickSearchText(tag: String): String = cleanSearchTag(tag)

internal fun tagSearchText(tag: String): String = "#${quotedSearchText(cleanSearchTag(tag))}"

private fun cleanSearchTag(tag: String): String =
    tag.trim().trimStart { it == '#' || it == '＃' || it.isWhitespace() }

internal fun normalizeSearchTag(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
    .trim().replace(Regex("[\\s\\p{Z}]+"), " ").lowercase(Locale.ROOT)

internal data class SearchToken(
    val value: String, val quoted: Boolean, val explicitTag: Boolean,
    val start: Int, val end: Int,
)

/** Shared lexer keeps tag completion and submitted queries on the same quoting rules. */
internal fun tokenizeSearchInput(text: String): List<SearchToken> = buildList {
    var index = 0
    while (index < text.length) {
        if (text[index].isWhitespace()) {
            index++
            continue
        }
        val start = index
        val explicitTag = text[index] == '#' || text[index] == '＃'
        if (explicitTag) index++
        val quoted = index < text.length && text[index] == '"'
        val value = if (quoted) {
            index++
            buildString {
                while (index < text.length && text[index] != '"') {
                    if (text[index] == '\\' && index + 1 < text.length &&
                        (text[index + 1] == '"' || text[index + 1] == '\\')
                    ) index++
                    append(text[index++])
                }
                if (index < text.length) index++
            }
        } else {
            val valueStart = index
            while (index < text.length && !text[index].isWhitespace()) index++
            text.substring(valueStart, index)
        }
        if (value.isNotBlank() || quoted) {
            add(SearchToken(value, quoted, explicitTag, start, index))
        } else {
            // A standalone # is text when submitted, but can start tag completion.
            add(SearchToken(text.substring(start, index), false, false, start, index))
        }
    }
}

/** Recognize complete tag aliases only; quoted text always remains a title query. */
internal class SearchInputParser(tagAliases: Map<String, String>) {
    private val aliases = tagAliases.mapKeys { normalizeSearchTag(it.key) }
    private val maxTagWords = aliases.keys.maxOfOrNull { it.count { c -> c == ' ' } + 1 } ?: 1

    fun parse(text: String?): SearchInput {
        val tokens = tokenizeSearchInput(text.orEmpty())
        val title = mutableListOf<String>()
        val tags = linkedSetOf<String>()
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]
            if (token.value.isBlank()) {
                index++
                continue
            }
            if (token.explicitTag) {
                val name = cleanSearchTag(token.value)
                if (name.isNotBlank()) tags += aliases[normalizeSearchTag(name)] ?: name
                index++
                continue
            }
            if (!token.quoted) {
                // Longest match also supports localized tag names containing spaces.
                val length = (minOf(maxTagWords, tokens.size - index) downTo 1).firstOrNull { size ->
                    val part = tokens.subList(index, index + size)
                    part.none { it.quoted || it.explicitTag } &&
                            normalizeSearchTag(part.joinToString(" ") { it.value }) in aliases
                }
                if (length != null) {
                    tags += aliases.getValue(normalizeSearchTag(
                        tokens.subList(index, index + length).joinToString(" ") { it.value }
                    ))
                    index += length
                    continue
                }
            }
            title += token.value
            index++
        }
        return SearchInput(title.joinToString(" ").ifBlank { null }, tags)
    }
}
