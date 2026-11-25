package com.yenaly.han1meviewer.ui.fragment.home

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.core.graphics.drawable.toBitmapOrNull
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.activityViewModels
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.ui.onNavDestinationSelected
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.yenaly.han1meviewer.ADVANCED_SEARCH_MAP
import com.yenaly.han1meviewer.AdvancedSearchMap
import com.yenaly.han1meviewer.HAdvancedSearch
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.VIDEO_CODE
import com.yenaly.han1meviewer.advancedSearchMapOf
import com.yenaly.han1meviewer.databinding.FragmentHomePageBinding
import com.yenaly.han1meviewer.logic.blocking.ContentBlocking
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.model.HomePage
import com.yenaly.han1meviewer.logic.state.WebsiteState
import com.yenaly.han1meviewer.ui.StateLayoutMixin
import com.yenaly.han1meviewer.ui.activity.MainActivity
import com.yenaly.han1meviewer.ui.activity.PreviewActivity
import com.yenaly.han1meviewer.ui.activity.SearchActivity
import com.yenaly.han1meviewer.ui.activity.SearchHistoryActivity
import com.yenaly.han1meviewer.ui.activity.VideoActivity
import com.yenaly.han1meviewer.ui.adapter.HanimeVideoRvAdapter
import com.yenaly.han1meviewer.ui.adapter.RvWrapper.Companion.wrappedWith
import com.yenaly.han1meviewer.ui.adapter.VideoColumnTitleAdapter
import com.yenaly.han1meviewer.ui.fragment.IToolbarFragment
import com.yenaly.han1meviewer.ui.viewmodel.MainViewModel
import com.yenaly.han1meviewer.util.addUpdateListener
import com.yenaly.han1meviewer.util.colorTransition
import com.yenaly.yenaly_libs.base.YenalyFragment
import com.yenaly.yenaly_libs.utils.startActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

/**
 * @project Hanime1
 * @author Yenaly Liew
 * @time 2022/06/12 012 12:31
 */
class HomePageFragment : YenalyFragment<FragmentHomePageBinding>(),
    IToolbarFragment<MainActivity>, StateLayoutMixin {

    companion object {
        private val animInterpolator = FastOutSlowInInterpolator()
        private const val ANIM_DURATION = 300L
    }

    val viewModel by activityViewModels<MainViewModel>()

    private val latestHanimeAdapter = HanimeVideoRvAdapter()
    private val latestReleaseAdapter = HanimeVideoRvAdapter()
    private val latestUploadAdapter = HanimeVideoRvAdapter()
    private val chineseSubtitleAdapter = HanimeVideoRvAdapter()
    private val hanimeTheyWatchedAdapter = HanimeVideoRvAdapter()
    private val hanimeCurrentAdapter = HanimeVideoRvAdapter()
    private val hotHanimeMonthlyAdapter = HanimeVideoRvAdapter()

    private val concatAdapter = ConcatAdapter(
        VideoColumnTitleAdapter(R.string.latest_hanime).apply {
            onMoreHanimeListener = {
                toSearchActivity(advancedSearchMapOf(HAdvancedSearch.GENRE to "裏番"))
            }
        },
        latestHanimeAdapter.wrappedWith {
            LinearLayoutManager(
                context, LinearLayoutManager.HORIZONTAL, false
            )
        },
        VideoColumnTitleAdapter(R.string.latest_release).apply {
            onMoreHanimeListener = {
                toSearchActivity(advancedSearchMapOf(HAdvancedSearch.SORT to "最新上市"))
            }
        },
        latestReleaseAdapter.wrappedWith {
            LinearLayoutManager(
                context, LinearLayoutManager.HORIZONTAL, false
            )
        },
        VideoColumnTitleAdapter(R.string.latest_upload).apply {
            onMoreHanimeListener = {
                toSearchActivity(advancedSearchMapOf(HAdvancedSearch.SORT to "最新上傳"))
            }
        },
        latestUploadAdapter.wrappedWith {
            LinearLayoutManager(
                context, LinearLayoutManager.HORIZONTAL, false
            )
        },
        VideoColumnTitleAdapter(R.string.chinese_subtitle).apply {
            onMoreHanimeListener = {
                toSearchActivity(
                    advancedSearchMapOf(
                        HAdvancedSearch.TAGS to hashMapOf<Int, Any>(R.string.video_attr to "中文字幕"),
                        HAdvancedSearch.SORT to "最新上傳"
                    )
                )
            }
        },
        chineseSubtitleAdapter.wrappedWith {
            LinearLayoutManager(
                context, LinearLayoutManager.HORIZONTAL, false
            )
        },
        VideoColumnTitleAdapter(R.string.they_watched).apply {
            onMoreHanimeListener = {
                toSearchActivity(advancedSearchMapOf(HAdvancedSearch.SORT to "他們在看"))
            }
        },
        hanimeTheyWatchedAdapter.wrappedWith {
            LinearLayoutManager(
                context, LinearLayoutManager.HORIZONTAL, false
            )
        },
        VideoColumnTitleAdapter(R.string.ranking_today).apply {
            onMoreHanimeListener = {
                toSearchActivity(advancedSearchMapOf(HAdvancedSearch.SORT to "本日排行"))
            }
        },
        hanimeCurrentAdapter.wrappedWith {
            LinearLayoutManager(
                context, LinearLayoutManager.HORIZONTAL, false
            )
        },
        VideoColumnTitleAdapter(R.string.ranking_this_month).apply {
            onMoreHanimeListener = {
                toSearchActivity(advancedSearchMapOf(HAdvancedSearch.SORT to "本月排行"))
            }
        },
        hotHanimeMonthlyAdapter.wrappedWith {
            LinearLayoutManager(
                context, LinearLayoutManager.HORIZONTAL, false
            )
        }
    )

    /**
     * 用於判斷是否需要 setExpanded，防止重複喚出 AppBar
     */
    private var isAfterRefreshing = false
    private var blockFilterJob: Job? = null

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentHomePageBinding {
        return FragmentHomePageBinding.inflate(inflater, container, false)
    }

    /**
     * 初始化数据
     */
    override fun initData(savedInstanceState: Bundle?) {

        (activity as MainActivity).setupToolbar()
        binding.state.init()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            easterEgg()
        }

        binding.rv.layoutManager = LinearLayoutManager(context)
        binding.rv.adapter = concatAdapter
        binding.rv.clipToPadding = false
        ViewCompat.setOnApplyWindowInsetsListener(binding.rv) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.updatePadding(bottom = systemBars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        binding.homePageSrl.apply {
            setOnRefreshListener {
                isAfterRefreshing = false
                // will enter here firstly. cuz the flow's def value is Loading.
                viewModel.getHomePage()
            }
            setEnableLoadMore(false)
        }
    }

    @SuppressLint("SetTextI18n")
    override fun bindDataObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                viewModel.homePageFlow.collect { state ->
                    binding.rv.isGone = state !is WebsiteState.Success
                    binding.banner.isVisible =
                        state is WebsiteState.Success || binding.banner.isVisible // 只有在刚开始的时候是不可见的
                    if (!isAfterRefreshing) {
                        binding.appBar.setExpanded(state is WebsiteState.Success, true)
                    }
                    when (state) {
                        is WebsiteState.Loading -> {
                            blockFilterJob?.cancel()
                            binding.homePageSrl.autoRefresh()
                            binding.rv.isGone = latestHanimeAdapter.items.isEmpty()
                        }

                        is WebsiteState.Success -> {
                            isAfterRefreshing = true
                            binding.homePageSrl.finishRefresh()
                            if (!ContentBlocking.rules.value.isEmpty) binding.banner.isVisible = false
                            initBanner(state.info)
                            observeBlockedContent(state.info)
                            binding.state.showContent()
                        }

                        is WebsiteState.Error -> {
                            binding.homePageSrl.finishRefresh()
                            binding.state.showError(state.throwable)
                        }
                    }
                }
            }
        }
    }

    private fun observeBlockedContent(info: HomePage) {
        blockFilterJob?.cancel()
        val sections = listOf(
            latestHanimeAdapter to info.latestHanime,
            latestUploadAdapter to info.latestUpload,
            hotHanimeMonthlyAdapter to info.hotHanimeMonthly,
            hanimeCurrentAdapter to info.hanimeCurrent,
            hanimeTheyWatchedAdapter to info.hanimeTheyWatched,
            latestReleaseAdapter to info.latestRelease,
            chineseSubtitleAdapter to info.chineseSubtitle,
        )
        val banner = info.banner
        val bannerItem = banner?.videoCode?.let { code ->
            HanimeInfo(banner.title, banner.picUrl, code, itemType = HanimeInfo.SIMPLIFIED)
        }
        val all = sections.flatMap { it.second } + listOfNotNull(bannerItem)
        blockFilterJob = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ContentBlocking.observe(all).collect { visible ->
                    val codes = visible.mapTo(hashSetOf()) { it.videoCode }
                    sections.forEach { (adapter, source) ->
                        adapter.submitList(source.filter { it.videoCode in codes })
                    }
                    // Non-video advertisements cannot be verified against content rules.
                    binding.banner.isVisible = banner != null && when {
                        ContentBlocking.rules.value.isEmpty -> true
                        bannerItem == null -> false
                        else -> bannerItem.videoCode in codes
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        blockFilterJob?.cancel()
        binding.rv.adapter = null
        super.onDestroyView()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    private fun initBanner(info: HomePage) {
        info.banner?.let { banner ->
            binding.tvBannerTitle.text = banner.title
            binding.tvBannerDesc.text = banner.description
            binding.cover.load(banner.picUrl) {
                crossfade(true)
                allowHardware(false)
                target(
                    onStart = binding.cover::setImageDrawable,
                    onError = binding.cover::setImageDrawable,
                    onSuccess = {
                        binding.cover.setImageDrawable(it)
                        it.toBitmapOrNull()?.let(Palette::Builder)?.generate { p ->
                            p?.let(::handlePalette)
                        }
                    }
                )
            }
            binding.btnBanner.isEnabled = banner.videoCode != null
            binding.btnBanner.setOnClickListener {
                banner.videoCode?.let { videoCode ->
                    requireActivity().startActivity<VideoActivity>(VIDEO_CODE to videoCode)
                }
            }
        }
    }

    // #issue-160: 修复字段销毁后调用引发的错误
    private fun handlePalette(p: Palette) {
        bindingOrNull?.let { binding ->
            val lightVibrant = p.getLightVibrantColor(Color.RED)

            val buttonBgColor =
                p.darkVibrantSwatch?.rgb ?: p.darkMutedSwatch?.rgb ?: Color.TRANSPARENT
            val darkVibrantForContentScrim =
                p.darkVibrantSwatch?.rgb ?: p.darkMutedSwatch?.rgb ?: p.lightVibrantSwatch?.rgb
                ?: p.lightMutedSwatch?.rgb ?: Color.BLACK
            binding.collapsingToolbar.setContentScrimColor(darkVibrantForContentScrim)
            binding.btnBanner.background = GradientDrawable().apply {
                colors = intArrayOf(Color.TRANSPARENT, buttonBgColor)
                orientation = GradientDrawable.Orientation.LEFT_RIGHT
            }
            binding.ivBanner.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
            colorTransition(
                fromColor = (binding.aColor.background as ColorDrawable).color,
                toColor = lightVibrant
            ) {
                interpolator = animInterpolator
                duration = ANIM_DURATION
                addUpdateListener(this@HomePageFragment) {
                    val color = it.animatedValue as Int
                    binding.aColor.setBackgroundColor(color)
                }
            }
        }
    }

    private fun toSearchActivity(advancedSearchMap: AdvancedSearchMap) {
        startActivity<SearchActivity>(ADVANCED_SEARCH_MAP to advancedSearchMap)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private var easterEggCount = 1f

    @RequiresApi(Build.VERSION_CODES.S)
    private fun easterEgg() {
        binding.cover.setOnClickListener {
            binding.cover.setRenderEffect(
                RenderEffect.createBlurEffect(
                    easterEggCount,
                    easterEggCount,
                    Shader.TileMode.CLAMP
                )
            )
            easterEggCount++
        }
        binding.cover.setOnLongClickListener {
            binding.cover.setRenderEffect(null)
            easterEggCount = 1f
            true
        }
    }

    override fun MainActivity.setupToolbar() {
        val toolbar = this@HomePageFragment.binding.toolbar
        setSupportActionBar(toolbar)
        this@HomePageFragment.addMenu(R.menu.menu_main_toolbar, viewLifecycleOwner) { item ->
            when (item.itemId) {
                R.id.tb_search -> {
                    startActivity<SearchHistoryActivity>()
                    return@addMenu true
                }

                R.id.tb_previews -> {
                    startActivity<PreviewActivity>()
                    return@addMenu true
                }
            }
            return@addMenu item.onNavDestinationSelected(navController)
        }

        toolbar.setupWithMainNavController()
    }
}
