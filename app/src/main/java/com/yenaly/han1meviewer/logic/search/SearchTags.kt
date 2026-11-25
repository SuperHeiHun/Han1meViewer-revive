package com.yenaly.han1meviewer.logic.search

import com.yenaly.han1meviewer.logic.model.SearchOption

internal data class SearchTag(val key: String, val label: String, val aliases: Set<String>)

internal fun Iterable<SearchOption>.toSearchTags(): List<SearchTag> = mapNotNull { option ->
    val key = option.searchKey?.takeIf(String::isNotBlank) ?: return@mapNotNull null
    SearchTag(
        key = key,
        label = option.value.ifBlank { key },
        aliases = listOfNotNull(
            key, option.name, option.lang?.zhrCN, option.lang?.zhrTW, option.lang?.en,
        ).filter(String::isNotBlank).toSet(),
    )
}

internal fun List<SearchTag>.searchTagAliases(): Map<String, String> = buildMap {
    this@searchTagAliases.forEach { tag -> tag.aliases.forEach { put(it, tag.key) } }
}
