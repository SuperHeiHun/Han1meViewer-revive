package com.yenaly.han1meviewer.ui.viewmodel

import android.app.Application
import android.util.Log
import android.util.SparseArray
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.logic.DatabaseRepo
import com.yenaly.han1meviewer.logic.NetworkRepo
import com.yenaly.han1meviewer.logic.entity.SearchHistoryEntity
import com.yenaly.han1meviewer.logic.history.HistoryPrivacy
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.model.SearchOption
import com.yenaly.han1meviewer.logic.search.SearchInputParser
import com.yenaly.han1meviewer.logic.search.searchTagAliases
import com.yenaly.han1meviewer.logic.search.toSearchTags
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.util.loadAssetAs
import com.yenaly.yenaly_libs.base.YenalyViewModel
import com.yenaly.yenaly_libs.utils.unsafeLazy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * @project Hanime1
 * @author Yenaly Liew
 * @time 2022/06/13 013 22:29
 */
class SearchViewModel(application: Application) : YenalyViewModel(application) {

    var page: Int = 1
    var query: String? = null

    // START: Use in [ChildCommentPopupFragment.kt]

    var genre: String? = null
    var sort: String? = null
    var broad: Boolean = false
    var year: Int? = null
    var month: Int? = null
    var duration: String? = null

    /**
     * 新版網站使用單一的 date 參數，例如 "2026 年 3 月"
     */
    val date: String?
        get() {
            val year = year
            val month = month
            return when {
                year != null && month != null -> "$year 年 $month 月"
                year != null -> "$year 年"
                month != null -> "$month 月"
                else -> null
            }
        }

    var subscriptionBrand: String? = null

    var tagMap = SparseArray<Set<SearchOption>>()
    var brandMap = SparseArray<Set<SearchOption>>()

    // END: Use in [ChildCommentPopupFragment.kt]

    // START: Use in [SearchOptionsPopupFragment.kt]

    val genres by unsafeLazy {
        loadAssetAs<List<SearchOption>>("search_options/genre.json").orEmpty()
    }

    val tags by unsafeLazy {
        loadAssetAs<Map<String, List<SearchOption>>>("search_options/tags.json").orEmpty()
    }

    private val searchInputParser by unsafeLazy {
        SearchInputParser(tags.values.flatten().toSearchTags().searchTagAliases())
    }

    val brands by unsafeLazy {
        loadAssetAs<List<SearchOption>>("search_options/brands.json").orEmpty()
    }

    val sortOptions by unsafeLazy {
        loadAssetAs<List<SearchOption>>("search_options/sort_option.json").orEmpty()
    }

    val durations by unsafeLazy {
        loadAssetAs<List<SearchOption>>("search_options/duration.json").orEmpty()
    }

    // END: Use in [SearchOptionsPopupFragment.kt]

    private val _searchStateFlow =
        MutableStateFlow<PageLoadingState<List<HanimeInfo>>>(PageLoadingState.Loading)
    val searchStateFlow = _searchStateFlow.asStateFlow()

    private val _searchFlow = MutableStateFlow(emptyList<HanimeInfo>())
    val searchFlow = _searchFlow.asStateFlow()
    private var searchJob: Job? = null

    fun clearHanimeSearchResult() {
        searchJob?.cancel()
        _searchFlow.value = emptyList()
        _searchStateFlow.value = PageLoadingState.Loading
    }

    fun getHanimeSearchResult(
        page: Int, query: String?, genre: String?,
        sort: String?, broad: Boolean, date: String?,
        duration: String?, tags: Set<String>,
    ) {
        searchJob?.cancel()
        val historyTicket = HistoryPrivacy.ticket()
        searchJob = viewModelScope.launch {
            val input = searchInputParser.parse(query)
            // Every entry point (including a tag/author intent and history replay) comes here.
            if (page == 1 && !query.isNullOrBlank()) {
                try {
                    HistoryPrivacy.record(historyTicket) {
                        DatabaseRepo.SearchHistory.insert(SearchHistoryEntity(query.trim()))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("insert_search_hty", "Unable to save search history", e)
                }
            }
            NetworkRepo.getHanimeSearchResult(
                page, input.query, genre,
                sort, broad, date,
                duration, tags + input.tags
            ).collect { state ->
                val prev = _searchStateFlow.getAndUpdate { state }
                if (prev is PageLoadingState.Loading) _searchFlow.value = emptyList()
                _searchFlow.update { prevList ->
                    when (state) {
                        is PageLoadingState.Success -> prevList + state.info
                        is PageLoadingState.Loading -> emptyList()
                        else -> prevList
                    }
                }
            }
        }
    }
}
