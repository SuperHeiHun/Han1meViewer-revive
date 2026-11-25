package com.yenaly.han1meviewer.ui.fragment.home

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.VideoCoverSize
import com.yenaly.han1meviewer.databinding.FragmentPageListBinding
import com.yenaly.han1meviewer.logic.entity.LocalVideoEntity
import com.yenaly.han1meviewer.ui.StateLayoutMixin
import com.yenaly.han1meviewer.ui.activity.MainActivity
import com.yenaly.han1meviewer.ui.adapter.HanimeMyListVideoAdapter
import com.yenaly.han1meviewer.ui.fragment.IToolbarFragment
import com.yenaly.han1meviewer.ui.viewmodel.LocalVideoListViewModel
import com.yenaly.han1meviewer.util.showAlertDialog
import com.yenaly.han1meviewer.util.showVideoCardMenu
import com.yenaly.yenaly_libs.base.YenalyFragment
import com.yenaly.yenaly_libs.utils.showShortToast
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class LocalVideoListFragment : YenalyFragment<FragmentPageListBinding>(),
    IToolbarFragment<MainActivity>, StateLayoutMixin {

    private val viewModel by activityViewModels<LocalVideoListViewModel>()
    private val adapter = HanimeMyListVideoAdapter()
    private val listType by lazy {
        requireNotNull(requireArguments().getString("listType")).also {
            require(it == LocalVideoEntity.FAVORITES || it == LocalVideoEntity.WATCH_LATER)
        }
    }
    private val titleRes: Int
        get() = if (listType == LocalVideoEntity.FAVORITES) {
            R.string.local_favorites
        } else {
            R.string.local_watch_later
        }

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentPageListBinding.inflate(inflater, container, false)

    override fun initData(savedInstanceState: Bundle?) {
        (activity as MainActivity).setupToolbar()
        binding.state.init()
        binding.rvPageList.apply {
            layoutManager = GridLayoutManager(context, VideoCoverSize.Simplified.videoInOneLine)
            adapter = this@LocalVideoListFragment.adapter
        }
        // Room keeps these lists up to date without network requests or pagination.
        binding.srlPageList.setEnableRefresh(false)
        binding.srlPageList.setEnableLoadMore(false)
        adapter.setOnItemLongClickListener { _, _, position ->
            val item = adapter.getItem(position) ?: return@setOnItemLongClickListener true
            requireContext().showVideoCardMenu(item, viewLifecycleOwner.lifecycleScope,
                getString(R.string.local_list_remove), includeWatchLater = listType != LocalVideoEntity.WATCH_LATER) {
                requireContext().showAlertDialog {
                    setTitle(R.string.local_list_remove)
                    setMessage(getString(R.string.sure_to_delete_s, item.title))
                    setPositiveButton(R.string.confirm) { _, _ -> viewModel.remove(listType, item.videoCode) }
                    setNegativeButton(R.string.cancel, null)
                }
            }
            true
        }
    }

    override fun bindDataObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.observeList(listType).catch {
                    binding.state.showError(it)
                }.collect { videos ->
                    adapter.submitList(videos)
                    if (videos.isEmpty()) binding.state.showEmpty() else binding.state.showContent()
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.errors.collect {
                    showShortToast(R.string.local_list_save_failed)
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        binding.rvPageList.layoutManager =
            GridLayoutManager(context, VideoCoverSize.Simplified.videoInOneLine)
    }

    override fun MainActivity.setupToolbar() {
        val toolbar = this@LocalVideoListFragment.binding.toolbar
        setSupportActionBar(toolbar)
        supportActionBar!!.setSubtitle(titleRes)
        this@LocalVideoListFragment.addMenu(
            R.menu.menu_watch_history_toolbar, viewLifecycleOwner
        ) { item ->
            when (item.itemId) {
                R.id.tb_delete -> {
                    requireContext().showAlertDialog {
                        setTitle(R.string.sure_to_delete)
                        setMessage(getString(R.string.local_list_clear_confirm, getString(titleRes)))
                        setPositiveButton(R.string.confirm) { _, _ ->
                            this@LocalVideoListFragment.viewModel.clear(listType)
                        }
                        setNegativeButton(R.string.cancel, null)
                    }
                    true
                }

                R.id.tb_help -> {
                    requireContext().showAlertDialog {
                        setTitle(R.string.attention)
                        setMessage(R.string.local_list_help)
                        setPositiveButton(R.string.ok, null)
                    }
                    true
                }

                else -> false
            }
        }
        toolbar.setupWithMainNavController()
    }
}
