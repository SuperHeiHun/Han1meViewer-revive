package com.yenaly.han1meviewer.ui.activity

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.yenaly.han1meviewer.ADVANCED_SEARCH_MAP
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.databinding.ActivitySearchHistoryBinding
import com.yenaly.han1meviewer.logic.entity.SearchHistoryEntity
import com.yenaly.han1meviewer.ui.adapter.HanimeSearchHistoryRvAdapter
import com.yenaly.han1meviewer.ui.viewmodel.SearchHistoryViewModel
import com.yenaly.han1meviewer.util.setStateViewLayout
import com.yenaly.han1meviewer.util.showAlertDialog
import com.yenaly.yenaly_libs.base.YenalyActivity
import com.yenaly.yenaly_libs.utils.startActivity
import kotlinx.coroutines.launch

class SearchHistoryActivity : YenalyActivity<ActivitySearchHistoryBinding>() {
    private val viewModel by viewModels<SearchHistoryViewModel>()
    private val historyAdapter = HanimeSearchHistoryRvAdapter(useThemeColors = true)

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
        binding.rvHistory.apply {
            layoutManager = LinearLayoutManager(this@SearchHistoryActivity)
            adapter = historyAdapter
        }
        historyAdapter.setStateViewLayout(R.layout.layout_empty_view)
        historyAdapter.listener = object : HanimeSearchHistoryRvAdapter.OnItemViewClickListener {
            override fun onItemClickListener(v: View, history: SearchHistoryEntity?) {
                history ?: return
                startActivity<SearchActivity>(ADVANCED_SEARCH_MAP to history.query)
            }

            override fun onItemRemoveListener(v: View, history: SearchHistoryEntity?) {
                history?.let(viewModel::delete)
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    override fun bindDataObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.histories.collect { histories ->
                    historyAdapter.submitList(histories)
                    binding.toolbar.menu.findItem(R.id.tb_delete).isEnabled = histories.isNotEmpty()
                }
            }
        }
    }
}
