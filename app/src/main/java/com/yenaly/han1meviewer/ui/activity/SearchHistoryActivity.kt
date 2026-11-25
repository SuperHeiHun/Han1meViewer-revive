package com.yenaly.han1meviewer.ui.activity

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.yenaly.han1meviewer.ADVANCED_SEARCH_MAP
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.databinding.ActivitySearchHistoryBinding
import com.yenaly.han1meviewer.logic.entity.SearchHistoryEntity
import com.yenaly.han1meviewer.logic.search.TagCompletion
import com.yenaly.han1meviewer.ui.adapter.HanimeSearchHistoryRvAdapter
import com.yenaly.han1meviewer.ui.viewmodel.SearchHistoryViewModel
import com.yenaly.han1meviewer.util.showAlertDialog
import com.yenaly.yenaly_libs.base.YenalyActivity
import com.yenaly.yenaly_libs.utils.startActivity
import com.yenaly.yenaly_libs.utils.view.hideIme
import com.yenaly.yenaly_libs.utils.view.showIme
import kotlinx.coroutines.launch

class SearchHistoryActivity : YenalyActivity<ActivitySearchHistoryBinding>() {
    private val viewModel by viewModels<SearchHistoryViewModel>()
    private val historyAdapter = HanimeSearchHistoryRvAdapter()
    private var completions: List<TagCompletion> = emptyList()
    private lateinit var emptyText: TextView

    override fun getViewBinding(layoutInflater: LayoutInflater): ActivitySearchHistoryBinding =
        ActivitySearchHistoryBinding.inflate(layoutInflater)

    override fun setUiStyle() {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
    }

    override fun initData(savedInstanceState: Bundle?) {
        binding.toolbar.apply {
            setTitle(R.string.search_history)
            setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
            setNavigationContentDescription(R.string.back)
            setNavigationOnClickListener { finish() }
            inflateMenu(R.menu.menu_search_history_toolbar)
            menu.findItem(R.id.tb_delete).isEnabled = false
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.tb_delete -> {
                        showAlertDialog {
                            setTitle(R.string.clear_search_history)
                            setMessage(R.string.sure_to_clear_search_history)
                            setPositiveButton(R.string.confirm) { _, _ -> viewModel.deleteAll() }
                            setNegativeButton(R.string.cancel, null)
                        }
                        true
                    }
                    R.id.tb_help -> {
                        showAlertDialog {
                            setTitle(R.string.search_history)
                            setMessage(R.string.search_history_help)
                            setPositiveButton(R.string.ok, null)
                        }
                        true
                    }
                    else -> false
                }
            }
        }
        binding.etQuery.apply {
            setText(viewModel.query)
            setSelection(
                viewModel.selectionStart.coerceIn(0, length()),
                viewModel.selectionEnd.coerceIn(0, length()),
            )
            addTextChangedListener { updateInput() }
            onSelectionChangedListener = { _, _ -> updateInput() }
            setOnFocusChangeListener { _, focused ->
                binding.tagSuggestions.isVisible = focused && completions.isNotEmpty()
            }
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEARCH) {
                    search(text?.toString().orEmpty())
                    true
                } else false
            }
        }
        binding.btnSearch.setOnClickListener { search(binding.etQuery.text?.toString().orEmpty()) }
        binding.rvHistory.apply {
            layoutManager = LinearLayoutManager(this@SearchHistoryActivity)
            adapter = historyAdapter
        }
        val emptyView = layoutInflater.inflate(R.layout.layout_empty_view, binding.rvHistory, false)
        emptyText = emptyView.findViewById(R.id.tv_empty)
        historyAdapter.stateView = emptyView
        historyAdapter.listener = object : HanimeSearchHistoryRvAdapter.OnItemViewClickListener {
            override fun onItemClickListener(v: View, history: SearchHistoryEntity?) {
                history ?: return
                search(history.query)
            }

            override fun onItemRemoveListener(v: View, history: SearchHistoryEntity?) {
                history?.let(viewModel::delete)
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    override fun bindDataObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.historyState.collect { state ->
                        emptyText.setText(
                            if (state.isFiltering) R.string.no_matching_search_history else R.string.here_is_empty
                        )
                        historyAdapter.submitList(state.histories)
                        // Clear always means all history, even when the current filter has no matches.
                        binding.toolbar.menu.findItem(R.id.tb_delete).isEnabled = state.hasHistories
                    }
                }
                launch {
                    viewModel.tagCompletions.collect(::showTagCompletions)
                }
            }
        }
    }

    private fun updateInput() = with(binding.etQuery) {
        viewModel.updateInput(text?.toString().orEmpty(), selectionStart, selectionEnd)
    }

    private fun search(query: String) {
        binding.etQuery.hideIme(window)
        startActivity<SearchActivity>(ADVANCED_SEARCH_MAP to query.trim())
    }

    private fun showTagCompletions(items: List<TagCompletion>) {
        completions = items
        binding.tagSuggestions.isVisible = items.isNotEmpty() && binding.etQuery.hasFocus()
        binding.tagChips.removeAllViews()
        items.forEach { suggestion ->
            binding.tagChips.addView(Chip(this).apply {
                text = suggestion.tag.label
                isCheckable = false
                setOnClickListener {
                    val input = binding.etQuery
                    val result = viewModel.completeTag(
                        suggestion.tag.key, input.text?.toString().orEmpty(),
                        input.selectionStart, input.selectionEnd,
                    ) ?: return@setOnClickListener
                    input.setText(result.first)
                    input.setSelection(result.second)
                    input.showIme(window)
                }
            })
        }
    }
}
