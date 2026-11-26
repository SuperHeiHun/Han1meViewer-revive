package com.yenaly.han1meviewer.ui.activity

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup.MarginLayoutParams
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import cn.jzvd.JZMediaInterface
import cn.jzvd.Jzvd
import coil.load
import com.yenaly.han1meviewer.COMMENT_TYPE
import com.yenaly.han1meviewer.DOWNLOAD_QUALITY
import com.yenaly.han1meviewer.FROM_DOWNLOAD
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.VIDEO_CODE
import com.yenaly.han1meviewer.VIDEO_COMMENT_PREFIX
import com.yenaly.han1meviewer.databinding.ActivityVideoBinding
import com.yenaly.han1meviewer.getHanimeVideoLink
import com.yenaly.han1meviewer.logic.entity.HKeyframeEntity
import com.yenaly.han1meviewer.logic.exception.CloudFlareBlockedException
import com.yenaly.han1meviewer.logic.exception.ParseException
import com.yenaly.han1meviewer.logic.player.completionTarget
import com.yenaly.han1meviewer.logic.player.PlayerAutoFullscreen
import com.yenaly.han1meviewer.logic.player.PlayerAutoRotation
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.han1meviewer.ui.fragment.video.CommentFragment
import com.yenaly.han1meviewer.ui.fragment.video.ChildCommentPopupFragment
import com.yenaly.han1meviewer.ui.fragment.video.VideoIntroductionFragment
import com.yenaly.han1meviewer.ui.view.video.HMediaKernel
import com.yenaly.han1meviewer.ui.view.video.HanimeDataSource
import com.yenaly.han1meviewer.ui.viewmodel.CommentViewModel
import com.yenaly.han1meviewer.ui.viewmodel.VideoViewModel
import com.yenaly.han1meviewer.util.CloudflareVerificationManager
import com.yenaly.han1meviewer.util.getOrCreateBadgeOnTextViewAt
import com.yenaly.han1meviewer.util.showAlertDialog
import com.yenaly.yenaly_libs.base.YenalyActivity
import com.yenaly.yenaly_libs.utils.OrientationManager
import com.yenaly.yenaly_libs.utils.browse
import com.yenaly.yenaly_libs.utils.dp
import com.yenaly.yenaly_libs.utils.intentExtra
import com.yenaly.yenaly_libs.utils.makeBundle
import com.yenaly.yenaly_libs.utils.showShortToast
import com.yenaly.yenaly_libs.utils.startActivity
import com.yenaly.yenaly_libs.utils.view.attach
import com.yenaly.yenaly_libs.utils.view.setUpFragmentStateAdapter
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

class VideoActivity : YenalyActivity<ActivityVideoBinding>(),
    OrientationManager.OrientationChangeListener {

    val viewModel by viewModels<VideoViewModel>()
    private val commentViewModel by viewModels<CommentViewModel>()

    private val kernel = HMediaKernel.Type.fromString(Preferences.switchPlayerKernel)

    private val fromDownload by intentExtra(FROM_DOWNLOAD, false)
    private val videoCode by intentExtra<String>(VIDEO_CODE)
    private var videoTitle: String? = null
    private var videoCodeByWebsite: String? = null
    private var startSelectedVideo = false
    private var hasSwitchedVideo = false
    private val automaticRotation = PlayerAutoRotation()
    private val automaticFullscreen = PlayerAutoFullscreen()
    private var automaticFullscreenRequest: Runnable? = null

    private val tabNameArray = intArrayOf(R.string.introduction, R.string.comment)

    override fun getViewBinding(layoutInflater: LayoutInflater): ActivityVideoBinding =
        ActivityVideoBinding.inflate(layoutInflater)

    override fun setUiStyle() {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
    }

    override fun initData(savedInstanceState: Bundle?) {
        if (intent.action == Intent.ACTION_VIEW) {
            val uri = intent.data
            uri?.let {
                videoCodeByWebsite = it.getQueryParameter("v")
            }
        }

        onBackPressedDispatcher.addCallback(this) {
            if (Jzvd.CURRENT_JZVD === binding.videoPlayer && Jzvd.backPress()) {
                return@addCallback
            }
            finish()
        }

        // 必须 fromDownload 在前，videoCode 在后
        viewModel.fromDownload = fromDownload
        viewModel.downloadQuality = intent.getStringExtra(DOWNLOAD_QUALITY)
        requireNotNull(savedInstanceState?.getString(VIDEO_CODE) ?: videoCodeByWebsite ?: videoCode).let {
            commentViewModel.code = it
            binding.videoPlayer.videoCode = it
            if (viewModel.videoCode != it) viewModel.videoCode = it
        }
        binding.videoPlayer.onPlaylistVideoSelected = { playVideo(it) }
        binding.videoPlayer.onVideoDimensionsChanged = {
            applyVideoOrientation()
            requestAutomaticFullscreen()
        }
        binding.videoPlayer.onPlaybackStarted = { requestAutomaticFullscreen() }
        binding.videoPlayer.onVideoCompleted = { completedCode ->
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                completedCode == viewModel.videoCode &&
                viewModel.hanimeVideoStateFlow.value is VideoLoadingState.Success) {
                completionTarget(
                    completedCode, viewModel.playbackPlaylist.value, Preferences.playbackMode,
                    reverse = Preferences.playbackPlaylistReversed, fromDownload = viewModel.fromDownload,
                )?.let { target ->
                    if (target == completedCode) binding.videoPlayer.replayCompletedVideo()
                    else playVideo(target)
                }
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.videoPlayer) { v, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updateLayoutParams<MarginLayoutParams> {
                topMargin = navBar.top
            }
            WindowInsetsCompat.CONSUMED
        }

        lifecycle.addObserver(OrientationManager(this))
        initViewPager()
        initHKeyframe()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun bindDataObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                var previousRotation = Preferences.playbackAutoRotate
                Preferences.observePlaybackSettings().collect { settings ->
                    binding.videoPlayer.applyPlaybackSettings()
                    if (previousRotation != settings.autoRotate) {
                        automaticRotation.reset()
                        previousRotation = settings.autoRotate
                    }
                    if (settings.autoRotate) applyVideoOrientation()
                    // Read the same global snapshot after returning from the application settings.
                    requestAutomaticFullscreen()
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                viewModel.hanimeVideoStateFlow.collect { state ->
                    when (state) {
                        is VideoLoadingState.Error -> {
                            // Cloudflare 驗證期間保留頁面，驗證成功後由 onResume 重新載入
                            if (state.throwable is CloudFlareBlockedException) {
                                showShortToast(state.throwable.localizedMessage)
                                binding.videoPlayer.showVideoLoadFailure(::retryVideo)
                                return@collect
                            }
                            showShortToast(state.throwable.localizedMessage)
                            if (hasSwitchedVideo) {
                                binding.videoPlayer.showVideoLoadFailure(::retryVideo)
                            } else {
                                if (state.throwable is ParseException) {
                                    browse(getHanimeVideoLink(viewModel.videoCode))
                                }
                                finish()
                            }
                        }

                        is VideoLoadingState.Loading -> {
                            cancelAutomaticFullscreen()
                            automaticFullscreen.reset()
                            // The introduction fragment may not be attached to this pager yet.
                            binding.videoVp.isUserInputEnabled = true
                            binding.videoPlayer.prepareForVideoSwitch()
                        }

                        is VideoLoadingState.Success -> {
                            videoTitle = state.info.title

                            if (state.info.videoUrls.isEmpty()) {
                                binding.videoPlayer.showVideoLoadFailure {
                                    showShortToast(R.string.fail_to_get_video_link)
                                    browse(getHanimeVideoLink(viewModel.videoCode))
                                }
                            } else {
                                binding.videoPlayer.setUp(
                                    HanimeDataSource(state.info.title, state.info.videoUrls),
                                    binding.videoPlayer.screen, kernel
                                )
                            }
                            binding.videoPlayer.posterImageView.load(state.info.coverUrl) {
                                crossfade(true)
                            }
                            startSelectedVideoIfReady()
                        }

                        is VideoLoadingState.NoContent -> {
                            showShortToast(R.string.video_might_not_exist)
                            if (hasSwitchedVideo) binding.videoPlayer.showVideoLoadFailure(::retryVideo)
                            else finish()
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.videoCodeFlow.flatMapLatest { code ->
                viewModel.observeKeyframe(code) ?: flowOf(null)
            }.flowWithLifecycle(lifecycle).collect {
                if (it != null && it.videoCode != viewModel.videoCode) return@collect
                binding.videoPlayer.hKeyframe = it
                viewModel.hKeyframes = it
            }
        }

        lifecycleScope.launch {
            viewModel.playbackPlaylist.collect { playlist ->
                binding.videoPlayer.updatePlaybackPlaylist(playlist)
            }
        }

        lifecycleScope.launch {
            viewModel.modifyHKeyframeFlow.collect { (_, reason) ->
                showShortToast(reason)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!fromDownload && CloudflareVerificationManager.consumePendingRetry()) {
            retryVideo()
        }
        startSelectedVideoIfReady()
        applyVideoOrientation()
        requestAutomaticFullscreen()
    }

    override fun onStop() {
        cancelAutomaticFullscreen()
        super.onStop()
        binding.videoPlayer.dismissPlayerPanels()
        // A related video may already own the global player when this activity stops.
        if (Jzvd.CURRENT_JZVD === binding.videoPlayer) Jzvd.goOnPlayOnPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(VIDEO_CODE, viewModel.videoCode)
        super.onSaveInstanceState(outState)
    }

    /** Reuse this activity and the very same fullscreen player view. */
    fun playVideo(code: String): Boolean {
        if (viewModel.fromDownload || code.isBlank() || isFinishing || isDestroyed) return false
        if (code == viewModel.videoCode) {
            showShortToast(R.string.watching_this_video_now)
            return true
        }
        hasSwitchedVideo = true
        cancelAutomaticFullscreen()
        startSelectedVideo = true
        supportFragmentManager.fragments.filterIsInstance<ChildCommentPopupFragment>()
            .forEach { it.dismissAllowingStateLoss() }
        videoTitle = null
        binding.videoPlayer.prepareForVideoSwitch()
        binding.videoPlayer.videoCode = code
        viewModel.videoCode = code
        commentViewModel.switchVideo(code)
        showRedDotCount(0)
        intent = Intent(intent).apply {
            action = null
            data = null
            putExtra(VIDEO_CODE, code)
        }
        return true
    }

    private fun retryVideo() {
        binding.videoPlayer.prepareForVideoSwitch()
        viewModel.getHanimeVideo(viewModel.videoCode)
    }

    private fun startSelectedVideoIfReady() {
        val video = (viewModel.hanimeVideoStateFlow.value as? VideoLoadingState.Success)?.info ?: return
        if (!startSelectedVideo || video.videoUrls.isEmpty() ||
            !binding.videoPlayer.startButton.isEnabled ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        startSelectedVideo = false
        // Keep the existing mobile-data confirmation instead of calling the kernel directly.
        binding.videoPlayer.startButton.performClick()
    }

    override fun onDestroy() {
        cancelAutomaticFullscreen()
        super.onDestroy()
        // Finishing a previous playback page must not release a newer activity's media.
        if (Jzvd.CURRENT_JZVD === binding.videoPlayer) Jzvd.releaseAllVideos()
    }

    override fun onOrientationChanged(orientation: OrientationManager.ScreenOrientation) {
        // Content-driven rotation must not fight the physical-device orientation listener.
        if (Preferences.playbackAutoRotate) return
        if (Jzvd.CURRENT_JZVD === binding.videoPlayer
            && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            && (binding.videoPlayer.state == Jzvd.STATE_PLAYING || binding.videoPlayer.state == Jzvd.STATE_PAUSE)
            && binding.videoPlayer.screen != Jzvd.SCREEN_TINY
            && Jzvd.FULLSCREEN_ORIENTATION != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        ) {
            if (orientation.isLandscape && binding.videoPlayer.screen == Jzvd.SCREEN_NORMAL) {
                changeScreenFullLandscape(orientation)
            } else if (orientation === OrientationManager.ScreenOrientation.PORTRAIT
                && binding.videoPlayer.screen == Jzvd.SCREEN_FULLSCREEN
            ) {
                changeScreenNormal()
            }
        }
    }

    private fun cancelAutomaticFullscreen() {
        automaticFullscreenRequest?.let { binding.videoPlayer.removeCallbacks(it) }
        automaticFullscreenRequest = null
    }

    private fun requestAutomaticFullscreen() {
        cancelAutomaticFullscreen()
        if (!Preferences.autoFullscreen) {
            automaticFullscreen.reset()
            return
        }
        val player = binding.videoPlayer
        val media = player.mediaInterface ?: return
        val session = player.playbackSession
        val code = player.videoCode
        val request = Runnable {
            automaticFullscreenRequest = null
            if (isFinishing || isDestroyed || player.mediaInterface !== media || player.playbackSession !== session || player.videoCode != code ||
                code != viewModel.videoCode) return@Runnable
            val eligible = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                    viewModel.hanimeVideoStateFlow.value is VideoLoadingState.Success &&
                    player.state == Jzvd.STATE_PLAYING &&
                    (player.screen == Jzvd.SCREEN_NORMAL || player.screen == Jzvd.SCREEN_FULLSCREEN)
            if (automaticFullscreen.shouldEnter(session, Preferences.autoFullscreen, eligible,
                    player.screen == Jzvd.SCREEN_FULLSCREEN)) {
                // Known dimensions already select the existing manual fullscreen direction.
                // With no dimensions, retain this window's direction instead of a previous video's.
                if (player.videoDimensions == null) {
                    Jzvd.FULLSCREEN_ORIENTATION = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
                player.gotoFullscreen()
            }
        }
        automaticFullscreenRequest = request
        player.post(request)
    }

    private fun applyVideoOrientation() {
        if (!Preferences.playbackAutoRotate || isFinishing || isDestroyed ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        val player = binding.videoPlayer
        if (player.mediaInterface == null || player.videoCode != viewModel.videoCode ||
            viewModel.hanimeVideoStateFlow.value !is VideoLoadingState.Success) return
        val (width, height) = player.videoDimensions ?: return
        val landscape = automaticRotation.directionFor(
            player.playbackSession,
            player.screen == Jzvd.SCREEN_FULLSCREEN && !player.isPortraitFullscreenRequested,
            width, height,
        ) ?: return
        requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    /**
     * 竖屏并退出全屏
     */
    private fun changeScreenNormal() {
        if (binding.videoPlayer.screen == Jzvd.SCREEN_FULLSCREEN) {
            binding.videoPlayer.gotoNormalScreen()
        }
    }

    /**
     * 横屏
     */
    private fun changeScreenFullLandscape(orientation: OrientationManager.ScreenOrientation) {
        //从竖屏状态进入横屏
        if (binding.videoPlayer.screen != Jzvd.SCREEN_FULLSCREEN) {
            if (System.currentTimeMillis() - Jzvd.lastAutoFullscreenTime > 2000) {
                binding.videoPlayer.autoFullscreen(orientation)
                Jzvd.lastAutoFullscreenTime = System.currentTimeMillis()
            }
        }
    }

    private fun initViewPager() {
        binding.videoVp.offscreenPageLimit = 1

        binding.videoVp.setUpFragmentStateAdapter(this) {
            addFragment { VideoIntroductionFragment() }
            if (!fromDownload) {
                addFragment { CommentFragment().makeBundle(COMMENT_TYPE to VIDEO_COMMENT_PREFIX) }
            }
        }

        binding.videoTl.attach(binding.videoVp) { tab, position ->
            tab.setText(tabNameArray[position])
        }
    }

    private fun initHKeyframe() {
        binding.videoPlayer.onGoHomeClickListener = { _ ->
            // singleTask 直接把所有 VideoActivity 都 finish 掉
            startActivity<MainActivity>()
        }
        binding.videoPlayer.onKeyframeClickListener = { v ->
            binding.videoPlayer.clickHKeyframe(v)
        }
        binding.videoPlayer.onKeyframeLongClickListener = {
            val mi: JZMediaInterface? = binding.videoPlayer.mediaInterface
            if (mi != null && !mi.isPlaying) {
                val currentPosition = binding.videoPlayer.currentPositionWhenPlaying
                val code = viewModel.videoCode
                val title = videoTitle ?: "Untitled"
                it.context.showAlertDialog {
                    setTitle(R.string.add_to_h_keyframe)
                    setMessage(buildString {
                        appendLine(getString(R.string.sure_to_add_to_h_keyframe))
                        append(getString(R.string.current_position_d_ms, currentPosition))
                    })
                    setPositiveButton(R.string.confirm) { _, _ ->
                        if (viewModel.videoCode != code) return@setPositiveButton
                        viewModel.appendHKeyframe(
                            code,
                            title,
                            HKeyframeEntity.Keyframe(
                                position = currentPosition,
                                prompt = null // 這裏不要給太多負擔，保存就行了沒必要寫comment
                            )
                        )
                    }
                    setNegativeButton(R.string.cancel, null)
                }
            } else {
                showShortToast(R.string.pause_then_long_press)
            }
        }
    }

    fun showRedDotCount(count: Int) {
        binding.videoTl.getOrCreateBadgeOnTextViewAt(
            tabNameArray.indexOf(R.string.comment),
            null, Gravity.START, 2.dp
        ) {
            isVisible = count > 0
            number = count
        }
    }
}
