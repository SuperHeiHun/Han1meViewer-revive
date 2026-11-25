package com.yenaly.han1meviewer.logic.search

import com.yenaly.han1meviewer.logic.entity.SearchHistoryEntity

/** Literal local filtering only; never changes the stored query or its recency. */
internal fun filterSearchHistories(
    histories: List<SearchHistoryEntity>, text: String,
): List<SearchHistoryEntity> {
    val keyword = normalizeSearchTag(text)
    return if (keyword.isEmpty()) histories else histories.filter {
        normalizeSearchTag(it.query).contains(keyword)
    }
}
