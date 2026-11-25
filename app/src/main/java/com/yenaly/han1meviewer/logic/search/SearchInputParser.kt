package com.yenaly.han1meviewer.logic.search

import java.text.Normalizer
import java.util.Locale

internal data class SearchInput(val query: String?, val tags: Set<String>)

internal fun quotedSearchText(text: String): String =
    "\"${text.replace("\\", "\\\\").replace("\"", "\\\"")}\""

internal fun tagSearchText(tag: String): String = "#${quotedSearchText(tag)}"

/** Recognize complete tag aliases only; quoted text always remains a title query. */
internal class SearchInputParser(tagAliases: Map<String, String>) {
    private val aliases = tagAliases.mapKeys { normalize(it.key) }
    private val maxTagWords = aliases.keys.maxOfOrNull { it.count { c -> c == ' ' } + 1 } ?: 1

    fun parse(text: String?): SearchInput {
        val tokens = tokenize(text.orEmpty())
        val title = mutableListOf<String>()
        val tags = linkedSetOf<String>()
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]
            if (token.explicitTag) {
                tags += aliases[normalize(token.value)] ?: token.value
                index++
                continue
            }
            if (!token.quoted) {
                // Longest match also supports localized tag names containing spaces.
                val length = (minOf(maxTagWords, tokens.size - index) downTo 1).firstOrNull { size ->
                    val part = tokens.subList(index, index + size)
                    part.none { it.quoted || it.explicitTag } &&
                            normalize(part.joinToString(" ") { it.value }) in aliases
                }
                if (length != null) {
                    tags += aliases.getValue(normalize(
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

    private data class Token(val value: String, val quoted: Boolean, val explicitTag: Boolean)

    private fun tokenize(text: String): List<Token> = buildList {
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
            if (value.isNotBlank()) {
                add(Token(value, quoted, explicitTag))
            } else if (!quoted) {
                // A standalone # is text, not an empty tag filter.
                add(Token(text.substring(start, index), false, false))
            }
        }
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .trim().replace(Regex("[\\s\\p{Z}]+"), " ").lowercase(Locale.ROOT)
}
