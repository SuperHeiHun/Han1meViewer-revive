package com.yenaly.han1meviewer.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.logic.DatabaseRepo
import com.yenaly.han1meviewer.logic.entity.SearchHistoryEntity
import com.yenaly.han1meviewer.logic.model.SearchOption
import com.yenaly.han1meviewer.logic.search.TagCompleter
import com.yenaly.han1meviewer.logic.search.filterSearchHistories
import com.yenaly.han1meviewer.logic.search.toSearchTags
import com.yenaly.han1meviewer.util.loadAssetAs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal data class SearchHistoryState(
    val histories: List<SearchHistoryEntity>, val hasHistories: Boolean, val isFiltering: Boolean,
)

class SearchHistoryViewModel(private val savedState: SavedStateHandle) : ViewModel() {
    private data class Input(val text: String, val start: Int, val end: Int)

    private val input = MutableStateFlow(
        Input(
            savedState["query"] ?: "",
            savedState["selection_start"] ?: 0,
            savedState["selection_end"] ?: 0,
        )
    )
    private val tagCompleter by lazy {
        val options = loadAssetAs<Map<String, List<SearchOption>>>("search_options/tags.json")
        TagCompleter(options.orEmpty().values.flatten().toSearchTags())
    }

    val query: String get() = input.value.text
    val selectionStart: Int get() = input.value.start
    val selectionEnd: Int get() = input.value.end

    internal val historyState = combine(
        DatabaseRepo.SearchHistory.loadAll(), input.map { it.text }.distinctUntilChanged(),
    ) { histories, text ->
        SearchHistoryState(filterSearchHistories(histories, text), histories.isNotEmpty(), text.isNotBlank())
    }.flowOn(Dispatchers.Default)

    internal val tagCompletions = input.map {
        if (it.start == it.end) tagCompleter.suggest(it.text, it.start) else emptyList()
    }.distinctUntilChanged().flowOn(Dispatchers.Default)

    fun updateInput(text: String, start: Int, end: Int) {
        val current = Input(text, start.coerceIn(0, text.length), end.coerceIn(0, text.length))
        if (input.value == current) return
        input.value = current
        savedState["query"] = current.text
        savedState["selection_start"] = current.start
        savedState["selection_end"] = current.end
    }

    internal fun completeTag(key: String, text: String, start: Int, end: Int): Pair<String, Int>? {
        if (start != end) return null
        // Re-resolve against the current caret so an old suggestion cannot overwrite newer input.
        return tagCompleter.suggest(text, start).find { it.tag.key == key }?.apply()
    }

    fun delete(history: SearchHistoryEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            DatabaseRepo.SearchHistory.delete(history)
        }
    }

    fun deleteAll() {
        viewModelScope.launch(Dispatchers.IO) {
            DatabaseRepo.SearchHistory.deleteAll()
        }
    }
}
