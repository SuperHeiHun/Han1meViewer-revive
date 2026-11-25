package com.yenaly.han1meviewer.ui.view.video

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.jzvd.JZDataSource
import cn.jzvd.JZUtils
import cn.jzvd.JzvdStd
import com.itxca.spannablex.spannable
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.entity.HKeyframeEntity
import com.yenaly.han1meviewer.logic.player.PlaybackPlaylist
import com.yenaly.han1meviewer.ui.adapter.HKeyframeRvAdapter
import com.yenaly.han1meviewer.ui.adapter.VideoSpeedAdapter
import com.yenaly.han1meviewer.ui.adapter.PlaybackPlaylistAdapter
import com.yenaly.han1meviewer.util.setStateViewLayout
import com.yenaly.han1meviewer.util.showAlertDialog
import com.yenaly.yenaly_libs.utils.OrientationManager
import com.yenaly.yenaly_libs.utils.activity
import com.yenaly.yenaly_libs.utils.navBarHeight
import com.yenaly.yenaly_libs.utils.statusBarHeight
import com.yenaly.yenaly_libs.utils.view.removeItself
import java.util.Timer

/**
 * @project Hanime1
 * @author Yenaly Liew
 * @time 2022/06/18 018 15:54
 */
class HJzvdStd @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : JzvdStd(context, attrs), OnLongClickListener {

    companion object {
        // 相當於重寫了
        /**
         * 默認滑動調整進度條的靈敏度 越大播放进度条滑动越慢
         */
        const val DEF_PROGRESS_SLIDE_SENSITIVITY = 5

        const val DEF_COUNTDOWN_SEC = 10

        /**
         * 默認速度
         */
        const val DEF_SPEED = 1.0F

        /**
         * 默認速度的索引
         */
        const val DEF_SPEED_INDEX = 2

        /**
         * 默認長按速度是原先速度的幾倍
         */
        const val DEF_LONG_PRESS_SPEED_TIMES = 2.5F

        /**
         * 速度列表
         */
        val speedArray = floatArrayOf(
            0.5F, 0.75F,
            1.0F, 1.25F, 1.5F, 1.75F,
            2.0F, 2.25F, 2.5F, 2.75F,
            3.0F,
        )

        /**
         * 速度列表的字符串
         */
        val speedStringArray = Array(speedArray.size) { "${speedArray[it]}x" }
    }

    /**
     * 用戶定義的是否顯示底部進度條
     */
    private val showBottomProgress = Preferences.showBottomProgress

    /**
     * 用戶定義的默認速度
     */
    private val userDefSpeed = Preferences.playerSpeed

    /**
     * 用戶定義的默認速度的索引
     */
    private val userDefSpeedIndex = speedArray.indexOfFirst { it == userDefSpeed }

    /**
     * 用戶定義的倒數提醒毫秒數
     */
    private val userDefWhenCountdownRemind = Preferences.whenCountdownRemind

    /**
     * 用戶定義的是否在倒數時顯示評論
     */
    private val userDefShowCommentWhenCountdown = Preferences.showCommentWhenCountdown

    /**
     * 用戶定義的是否啟用關鍵H幀
     */
    private val isHKeyframeEnabled = Preferences.hKeyframesEnable

    /**
     * 當前速度的索引，如果设置速度的话，修改这个，别动 [videoSpeed]
     */
    private var currentSpeedIndex = userDefSpeedIndex
        @SuppressLint("SetTextI18n")
        set(value) {
            field = value
            if (value == DEF_SPEED_INDEX) {
                tvSpeed.text = context.getString(R.string.speed)
            } else {
                tvSpeed.text = speedStringArray[value]
            }
            videoSpeed = speedArray[value]
            // #issue-14: 有些机器到这里可能会报空指针异常，所以加了个判断，但是不知道为什么会报空指针异常
            if (jzDataSource.objects == null) {
                jzDataSource.objects = arrayOf(userDefSpeedIndex)
            }
            jzDataSource.objects[0] = value
        }

    private lateinit var tvSpeed: TextView
    private lateinit var tvPlaybackPlaylist: TextView
    private lateinit var tvKeyframe: TextView
    private lateinit var tvTimer: TextView
    private lateinit var btnGoHome: ImageView
    private lateinit var layoutTop: View
    private lateinit var layoutBottom: View
    private lateinit var gestureFeedback: PlayerGestureOverlay

    var hKeyframe: HKeyframeEntity? = null
        set(value) {
            field = value
            val adapter = if (value == null) keyframeAdapter else hKeyframeAdapter
            adapter?.submitList(value?.keyframes)
            adapter?.isLocal = value?.let { it.author == null } ?: true
        }

    var videoCode: String? = null
        set(value) {
            if (field != value) {
                keyframeAdapter = null
                hKeyframe = null
            }
            field = value
        }

    private var keyframeAdapter: HKeyframeRvAdapter? = null
    private val hKeyframeAdapter: HKeyframeRvAdapter
        get() = keyframeAdapter ?: initHKeyframeAdapter().also { keyframeAdapter = it }

    private var playbackPlaylist: PlaybackPlaylist? = null
    private var playlistPopup: PopupWindow? = null
    private var controlsPopup: PopupWindow? = null
    private var isVideoLoading = false
    var onPlaylistVideoSelected: ((String) -> Unit)? = null
    var onVideoCompleted: ((String) -> Unit)? = null

    /**
     * 初始化關鍵H幀的 Adapter，最好不用 lazy
     *
     * 但我還是最終用了 lazy，要不然首次 submitList 收不到
     */
    private fun initHKeyframeAdapter() = run {
        val videoCode = checkNotNull(this.videoCode) {
            "If you want to use HKeyframeAdapter, you must set videoCode first."
        }
        HKeyframeRvAdapter(videoCode).apply {
            setOnItemClickListener { _, _, position ->
                val keyframe = getItem(position) ?: return@setOnItemClickListener
                mediaInterface?.seekTo(keyframe.position)
                startProgressTimer()
            }
        }
    }

    /**
     * 關鍵H幀的點擊事件
     *
     * 作用：打開 Dialog，顯示關鍵H幀的列表
     */
    var onKeyframeClickListener: ((View) -> Unit)? = null

    /**
     * 回到主頁的點擊事件
     *
     * 作用：關閉所有的 VideoActivity
     */
    var onGoHomeClickListener: ((View) -> Unit)? = null

    /**
     * 關鍵H幀的長按事件
     *
     * 作用：將當前時刻加入關鍵H幀
     */
    var onKeyframeLongClickListener: ((View) -> Unit)? = null

    private var videoSpeed: Float = userDefSpeed
        set(value) {
            field = value
            mediaInterface?.setSpeed(value)
        }

    // Nullable because the superclass calls overridable lifecycle methods during construction.
    private var playerGestureController: PlayerGestureController? = null

    init {
        playerGestureController = PlayerGestureController(
            textureViewContainer,
            configProvider = { Preferences.playerGestures },
            host = object : PlayerGestureController.Host {
                override val mediaIdentity: Any? get() = mediaInterface
                override val position: Long get() = currentPositionWhenPlaying
                override val duration: Long get() = this@HJzvdStd.duration
                override val normalSpeed: Float get() = videoSpeed
                override val canSeek: Boolean get() = screen != SCREEN_TINY && mediaInterface != null &&
                        (state == STATE_PLAYING || state == STATE_PAUSE || state == STATE_PREPARING_PLAYING)
                override val canLongPress: Boolean get() = screen != SCREEN_TINY &&
                        (state == STATE_PLAYING || state == STATE_PREPARING_PLAYING) &&
                        mediaInterface?.isPlaying == true
                override val canAdjustLevels: Boolean get() = screen != SCREEN_TINY
                override val canEnterFullscreen: Boolean get() = canSwipeToFullscreen()

                override fun toggleControls() {
                    onClickUiToggle()
                    if (state == STATE_PLAYING) startDismissControlViewTimer()
                }

                override fun togglePlayback() {
                    if (state == STATE_PLAYING || state == STATE_PAUSE) startButton.performClick()
                }

                override fun enterFullscreen() {
                    fullscreenButton.performClick()
                }

                override fun seekTo(position: Long) {
                    val media = mediaInterface ?: return
                    val keepPaused = state == STATE_PAUSE
                    media.seekTo(position)
                    if (keepPaused) media.pause()
                    val total = this@HJzvdStd.duration
                    val progress = if (total > 0) (position.toDouble() * 100 / total).toInt().coerceIn(0, 100) else 0
                    progressBar.progress = progress
                    bottomProgressBar.progress = progress
                    currentTimeTextView.text = JZUtils.stringForTime(position)
                }

                override fun setTemporarySpeed(speed: Float) = setSpeedInternal(speed)

                override fun beginInteraction() {
                    cancelDismissControlViewTimer()
                }

                override fun endInteraction() {
                    if (state == STATE_PLAYING || state == STATE_PREPARING_PLAYING) startDismissControlViewTimer()
                }

                override fun showFeedback(feedback: PlayerGestureFeedback) = gestureFeedback.show(feedback)
                override fun finishFeedback(immediate: Boolean) = gestureFeedback.finish(immediate)
            },
        )
    }

    override fun getLayoutId() = R.layout.layout_jzvd_with_speed

    override fun init(context: Context?) {
        super.init(context)
        SAVE_PROGRESS = false
        tvSpeed = findViewById(R.id.tv_speed)
        tvPlaybackPlaylist = findViewById(R.id.tv_playback_playlist)
        tvKeyframe = findViewById(R.id.tv_keyframe)
        tvTimer = findViewById(R.id.tv_timer)
        btnGoHome = findViewById(R.id.go_home)
        layoutTop = findViewById(R.id.layout_top)
        layoutBottom = findViewById(R.id.layout_bottom)
        gestureFeedback = findViewById(R.id.gesture_feedback)
        textureViewContainer.isHapticFeedbackEnabled = true
        tvSpeed.setOnClickListener(this)
        tvPlaybackPlaylist.setOnClickListener(this)
        tvKeyframe.setOnClickListener(this)
        tvKeyframe.setOnLongClickListener(this)
        btnGoHome.setOnClickListener(this)
    }

    override fun setUp(jzDataSource: JZDataSource?, screen: Int) {
        setUp(jzDataSource, screen, ExoMediaKernel::class.java)
    }

    fun setUp(jzDataSource: JZDataSource?, screen: Int, kernel: HMediaKernel.Type) {
        setUp(jzDataSource, screen, kernel.clazz)
    }

    override fun setUp(jzDataSource: JZDataSource?, screen: Int, clazz: Class<*>) {
        playerGestureController?.cancel()
        if (isVideoLoading) {
            // JzvdStd silently ignores setUp within 200 ms of a fullscreen transition.
            // A selected video's data source must never be discarded by that debounce.
            gobakFullscreenTime = 0L
            gotoFullscreenTime = 0L
        }
        isVideoLoading = false
        startButton.isEnabled = true
        clarity.isEnabled = true
        startButton.setOnClickListener(this)
        mRetryBtn.setOnClickListener(this)
        super.setUp(jzDataSource, screen, clazz)
        Log.d("CustomJzvdStd-Settings", buildString {
            append("showBottomProgress: ")
            appendLine(showBottomProgress)
            append("userDefSpeed: ")
            appendLine(userDefSpeed)
            append("userDefSpeedIndex: ")
            appendLine(userDefSpeedIndex)
            append("userDefSlideSensitivity: ")
            appendLine(Preferences.slideSensitivity)
        })
        titleTextView.isInvisible = screen != SCREEN_FULLSCREEN
        if (bottomProgressBar != null && !showBottomProgress) {
            bottomProgressBar.removeItself()
            bottomProgressBar = ProgressBar(context)
        }
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (v.id != R.id.surface_container) return super.onTouch(v, event)
        return playerGestureController?.onTouch(event) ?: true
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
        playerGestureController?.cancel()
        super.onStartTrackingTouch(seekBar)
    }

    override fun changeUrl(jzDataSource: JZDataSource?, seekToInAdvance: Long) {
        playerGestureController?.cancel()
        super.changeUrl(jzDataSource, seekToInAdvance)
    }

    override fun onStateNormal() {
        playerGestureController?.cancel()
        super.onStateNormal()
    }

    override fun onStatePause() {
        super.onStatePause()
        playerGestureController?.onPlaybackPaused()
    }

    override fun onStateError() {
        playerGestureController?.cancel()
        super.onStateError()
    }

    override fun onStateAutoComplete() {
        playerGestureController?.cancel()
        super.onStateAutoComplete()
    }

    override fun reset() {
        playerGestureController?.cancel()
        dismissPlaybackPlaylist()
        controlsPopup?.dismiss()
        super.reset()
    }

    /** Release only this media session, without reparenting the fullscreen view. */
    fun prepareForVideoSwitch() {
        if (!isVideoLoading) reset()
        isVideoLoading = true
        dismissPlaybackPlaylist()
        controlsPopup?.dismiss()
        clarityPopWindow?.dismiss()
        mediaInterface = null
        seekToInAdvance = 0L
        seekToManulPosition = -1
        resetProgressAndTime()
        posterImageView.setImageDrawable(null)
        titleTextView.text = null
        tvTimer.isInvisible = true
        startButton.isEnabled = false
        startButton.isGone = true
        startButton.setOnClickListener(this)
        clarity.isEnabled = false
        mRetryLayout.isGone = true
        loadingProgressBar.isVisible = true
    }

    fun showVideoLoadFailure(onRetry: () -> Unit) {
        if (!isVideoLoading) prepareForVideoSwitch()
        loadingProgressBar.isGone = true
        mRetryLayout.isVisible = true
        mRetryBtn.setOnClickListener { onRetry() }
    }

    override fun startVideo() {
        if (!isVideoLoading) super.startVideo()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) playerGestureController?.cancel()
    }

    override fun onDetachedFromWindow() {
        playerGestureController?.cancel()
        cancelDismissControlViewTimer()
        dismissPlaybackPlaylist()
        controlsPopup?.dismiss()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) {
            playerGestureController?.cancel()
            dismissPlaybackPlaylist()
            controlsPopup?.dismiss()
        }
    }

    private fun canSwipeToFullscreen(): Boolean = screen == SCREEN_NORMAL &&
            (state == STATE_PLAYING || state == STATE_PAUSE || state == STATE_PREPARING ||
                    state == STATE_PREPARING_PLAYING || state == STATE_PREPARING_CHANGE_URL)

    fun autoFullscreen(orientation: OrientationManager.ScreenOrientation) {
        autoFullscreen(if (orientation === OrientationManager.ScreenOrientation.LANDSCAPE) 1.0f else -1.0f)
    }

    override fun onClickUiToggle() {
        if (isVideoLoading) return
        if (!bottomContainer.isVisible) {
            setSystemTimeAndBattery()
            clarity.text = jzDataSource.currentKey.toString()
        }
        when (state) {
            STATE_PREPARING -> {
                changeUiToPreparing()
                if (!bottomContainer.isVisible) {
                    setSystemTimeAndBattery()
                }
            }

            STATE_PLAYING -> {
                if (bottomContainer.isVisible) {
                    changeUiToPlayingClear()
                } else {
                    changeUiToPlayingShow()
                }
            }

            STATE_PAUSE -> {
                if (bottomContainer.isVisible) {
                    changeUiToPauseClear()
                } else {
                    changeUiToPauseShow()
                }
            }

            STATE_PREPARING_PLAYING -> {
                if (bottomContainer.isVisible) {
                    changeUiToPreparingPlayingClear()
                } else {
                    changeUiToPreparingPlayingShow()
                }
            }
        }
    }

    override fun onStatePreparingPlaying() {
        super.onStatePreparingPlaying()
        applyNormalSpeed()
    }

    override fun onPrepared() {
        super.onPrepared()
        applyNormalSpeed()
    }

    private fun applyNormalSpeed() {
        if (playerGestureController?.isTemporarySpeedActive == true) return
        if (jzDataSource.objects == null) {
            jzDataSource.objects = arrayOf(userDefSpeedIndex)
            currentSpeedIndex = userDefSpeedIndex
        } else {
            currentSpeedIndex = jzDataSource.objects.first() as Int
        }
    }

    // #issue-232: 快进滑动一加载就会出现操作栏，很影响观看体验
    override fun changeUIToPreparingPlaying() {
        when (screen) {
            SCREEN_FULLSCREEN -> {
                setAllControlsVisiblity(
                    INVISIBLE, INVISIBLE, INVISIBLE,
                    VISIBLE, INVISIBLE, INVISIBLE, INVISIBLE
                )
                updateStartImage()
            }
        }
    }

    override fun setScreenNormal() {
        playerGestureController?.cancel()
        super.setScreenNormal()
        backButton.isVisible = true
        tvSpeed.isVisible = false
        tvPlaybackPlaylist.isVisible = false
        dismissPlaybackPlaylist()
        controlsPopup?.dismiss()
        tvKeyframe.isVisible = false
        titleTextView.isInvisible = true
        tvTimer.isInvisible = true
        btnGoHome.isVisible = true

        layoutTop.updatePadding(left = 0, right = 0)
        layoutBottom.updatePadding(left = 0, right = 0)
        tvTimer.updatePadding(left = 0, right = 0)
        bottomProgressBar.updatePadding(left = 0, right = 0)
    }

    override fun setScreenFullscreen() {
        playerGestureController?.cancel()
        super.setScreenFullscreen()
        tvSpeed.isVisible = true
        tvPlaybackPlaylist.isVisible = playbackPlaylist?.videos?.isNotEmpty() == true
        if (isHKeyframeEnabled) tvKeyframe.isVisible = true
        titleTextView.isVisible = true
        // btnGoHome.isVisible = false

        val statusBarHeight = statusBarHeight
        val navBarHeight = navBarHeight
        layoutTop.updatePadding(left = statusBarHeight, right = navBarHeight)
        layoutBottom.updatePadding(left = statusBarHeight, right = navBarHeight)
        tvTimer.updatePadding(left = statusBarHeight)
        bottomProgressBar.updatePadding(left = statusBarHeight, right = navBarHeight)
    }

    override fun clickBack() {
        Log.i(TAG, "backPress")
        when {
            CONTAINER_LIST.isNotEmpty() && CURRENT_JZVD != null -> { //判断条件，因为当前所有goBack都是回到普通窗口
                CURRENT_JZVD.gotoNormalScreen()
            }

            CONTAINER_LIST.isEmpty() && CURRENT_JZVD != null && CURRENT_JZVD.screen != SCREEN_NORMAL -> { //退出直接进入的全屏
                CURRENT_JZVD.clearFloatScreen()
            }

            else -> { //剩餘情況直接退出
                context.activity?.finish()
            }
        }
    }

    override fun onClick(v: View) {
        playerGestureController?.cancel()
        super.onClick(v)
        when (v.id) {
            R.id.tv_playback_playlist -> clickPlaybackPlaylist()
            R.id.tv_speed -> clickSpeed()
            R.id.tv_keyframe -> onKeyframeClickListener?.invoke(v)
            R.id.go_home -> onGoHomeClickListener?.invoke(v)
        }
    }

    override fun onLongClick(v: View): Boolean {
        return when (v.id) {
            R.id.tv_keyframe -> {
                onKeyframeLongClickListener?.invoke(v)
                return true
            }

            else -> false
        }
    }

    override fun onCompletion() {
        if (isVideoLoading || state == STATE_AUTO_COMPLETE) return
        val completedCode = videoCode
        if (screen == SCREEN_FULLSCREEN) {
            onStateAutoComplete()
        } else {
            super.onCompletion()
        }
        posterImageView.isGone = true
        completedCode?.let { onVideoCompleted?.invoke(it) }
    }

    override fun gotoNormalScreen() {
        gobakFullscreenTime = System.currentTimeMillis()
        val vg = JZUtils.scanForActivity(context).window.decorView as ViewGroup
        vg.removeView(this)
        // #issue-crashlytics-1b3cebd1278de6fc52230eb5517be879:
        // 你永远要给不更新的 JZVD 擦屁股才能保持稳定
        CONTAINER_LIST.peekLast()?.apply {
            removeViewAt(blockIndex)
            addView(this@HJzvdStd, blockIndex, blockLayoutParams)
        }
        CONTAINER_LIST.poll()
        setScreenNormal()
        JZUtils.showStatusBar(jzvdContext)
        JZUtils.setRequestedOrientation(jzvdContext, NORMAL_ORIENTATION)
        JZUtils.showSystemUI(jzvdContext)
    }

    override fun onStatePreparingChangeUrl() {
        playerGestureController?.cancel()
        Log.i(TAG, "onStatePreparingChangeUrl " + " [" + this.hashCode() + "] ")
        state = STATE_PREPARING_CHANGE_URL

        // 原方法直接使用下面的方法，會導致全屏切換清晰度返回正常界面時重置影片。
        // 所以重寫，只抄過調用的方法的一部分。
        // releaseAllVideos()
        CURRENT_JZVD?.let {
            it.reset()
            CURRENT_JZVD = null
        }

        startVideo()
    }

    override fun showWifiDialog() {
        jzvdContext.showAlertDialog {
            setTitle("Warning!")
            setMessage(cn.jzvd.R.string.tips_not_wifi)
            setPositiveButton(cn.jzvd.R.string.tips_not_wifi_confirm) { _, _ ->
                WIFI_TIP_DIALOG_SHOWED = true
                if (state == STATE_PAUSE) startButton.performClick() else startVideo()
            }
            setNegativeButton(cn.jzvd.R.string.tips_not_wifi_cancel) { _, _ ->
                releaseAllVideos()
                clearFloatScreen()
            }
        }
    }

    // 原來是 300 period 我改成了 100 爲了計時準確
    override fun startProgressTimer() {
        Log.i(TAG, "startProgressTimer: " + " [" + this.hashCode() + "] ")
        cancelProgressTimer()
        UPDATE_PROGRESS_TIMER = Timer()
        mProgressTimerTask = ProgressTimerTask()
        UPDATE_PROGRESS_TIMER.schedule(mProgressTimerTask, 0, 100)
    }

    override fun onProgress(progress: Int, position: Long, duration: Long) {
        val displayedPosition = playerGestureController?.displayPosition(position) ?: position
        val displayedProgress = if (duration > 0) {
            (displayedPosition.toDouble() * 100 / duration).toInt().coerceIn(0, 100)
        } else progress
        super.onProgress(displayedProgress, displayedPosition, duration)
        if (screen == SCREEN_FULLSCREEN) hKeyframe?.let {
            var match = false
            for ((index, kf) in it.keyframes.withIndex()) {
                val interval = kf.position - position
                if (interval in 0L..<userDefWhenCountdownRemind) {
                    val timeLong = interval / 1_000L
                    val spannable = spannable {
                        if (userDefShowCommentWhenCountdown) {
                            "#${index + 1}".span {
                                relativeSize(proportion = 0.7F)
                            }
                            if (!kf.prompt.isNullOrBlank()) {
                                " ${kf.prompt}".span {
                                    relativeSize(proportion = 0.7F)
                                }
                            }
                            newline()
                        }
                        val time = if (timeLong >= 1) {
                            (timeLong + 1).toString()
                        } else {
                            val timeFloat = interval / 1_000F
                            "%.1f".format(timeFloat)
                        }
                        time.span {
                            style(Typeface.BOLD)
                        }
                    }
                    tvTimer.text = spannable
                    match = true
                    break
                }
            }
            tvTimer.isInvisible = !match
        } ?: run { tvTimer.isInvisible = true }
    }

    private fun changeUiToPreparingPlayingClear() {
        when (screen) {
            SCREEN_NORMAL, SCREEN_FULLSCREEN -> {
                setAllControlsVisiblity(
                    INVISIBLE, INVISIBLE, INVISIBLE,
                    VISIBLE, INVISIBLE, INVISIBLE, INVISIBLE
                )
            }
        }
    }

    private fun changeUiToPreparingPlayingShow() {
        when (screen) {
            SCREEN_NORMAL, SCREEN_FULLSCREEN -> {
                setAllControlsVisiblity(
                    VISIBLE, VISIBLE, INVISIBLE,
                    VISIBLE, INVISIBLE, VISIBLE, INVISIBLE
                )
            }
        }
    }

    // #issue-14: 之前用 XPopup 三键模式下会有 bug，无法呼出，所以换成这个
    @SuppressLint("InflateParams")
    fun clickSpeed() {
        onCLickUiToggleToClear()
        val inflater = LayoutInflater.from(context).inflate(R.layout.jz_layout_speed, null)
        val rv = inflater.findViewById<RecyclerView>(R.id.rv_video_speed)
        val popup = PopupWindow(
            inflater, JZUtils.dip2px(jzvdContext, 240f),
            LayoutParams.MATCH_PARENT, true
        ).apply {
            contentView = inflater
            animationStyle = cn.jzvd.R.style.pop_animation
        }
        controlsPopup?.dismiss()
        controlsPopup = popup
        rv.layoutManager = LinearLayoutManager(context)
        rv.adapter = VideoSpeedAdapter(currentSpeedIndex).apply {
            setOnItemClickListener { _, _, position ->
                currentSpeedIndex = position
                popup.dismiss()
            }
        }
        popup.showAtLocation(textureViewContainer, Gravity.END, 0, 0)
    }

    @SuppressLint("InflateParams")
    fun clickHKeyframe(v: View) {
        onCLickUiToggleToClear()
        val inflater = LayoutInflater.from(context).inflate(R.layout.jz_layout_speed, null)
        val rv = inflater.findViewById<RecyclerView>(R.id.rv_video_speed)
        val popup = PopupWindow(
            inflater, JZUtils.dip2px(jzvdContext, 240f),
            LayoutParams.MATCH_PARENT, true
        ).apply {
            contentView = inflater
            animationStyle = cn.jzvd.R.style.pop_animation
        }
        controlsPopup?.dismiss()
        controlsPopup = popup
        rv.layoutManager = LinearLayoutManager(v.context)
        val adapter = hKeyframeAdapter
        rv.adapter = adapter
        adapter.setStateViewLayout(
            inflate(v.context, R.layout.layout_empty_view, null),
            this@HJzvdStd.context.getString(R.string.here_is_empty) + "\n"
                    + this@HJzvdStd.context.getString(R.string.long_press_to_add_h_keyframe)
        )
        popup.showAtLocation(textureViewContainer, Gravity.END, 0, 0)
    }

    fun updatePlaybackPlaylist(playlist: PlaybackPlaylist) {
        if (playbackPlaylist != playlist) dismissPlaybackPlaylist()
        playbackPlaylist = playlist
        tvPlaybackPlaylist.isVisible = screen == SCREEN_FULLSCREEN && playlist.videos.isNotEmpty()
    }

    fun dismissPlaybackPlaylist() {
        playlistPopup?.dismiss()
        playlistPopup = null
    }

    @SuppressLint("InflateParams")
    fun clickPlaybackPlaylist() {
        val playlist = playbackPlaylist ?: return
        if (screen != SCREEN_FULLSCREEN || playlist.videos.isEmpty() || !isAttachedToWindow) return
        playerGestureController?.cancel()
        cancelDismissControlViewTimer()
        onCLickUiToggleToClear()
        controlsPopup?.dismiss()
        dismissPlaybackPlaylist()
        val sourceCode = videoCode
        val content = LayoutInflater.from(context).inflate(R.layout.jz_layout_playlist, null)
        content.findViewById<TextView>(R.id.playlist_title).text = playlist.title ?: context.getString(R.string.play_list)
        content.findViewById<SwitchCompat>(R.id.auto_play_next).apply {
            isChecked = Preferences.autoPlayNext
            setOnCheckedChangeListener { _, enabled -> Preferences.autoPlayNext = enabled }
        }
        val popup = PopupWindow(
            content, minOf(JZUtils.dip2px(context, 360f), (width * 0.5f).toInt()),
            LayoutParams.MATCH_PARENT, true,
        ).apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            isOutsideTouchable = true
            animationStyle = cn.jzvd.R.style.pop_animation
            setOnDismissListener {
                if (playlistPopup === this) playlistPopup = null
                if (state == STATE_PLAYING) startDismissControlViewTimer()
            }
        }
        playlistPopup = popup
        content.findViewById<View>(R.id.close_playlist).setOnClickListener { popup.dismiss() }
        content.findViewById<RecyclerView>(R.id.rv_playback_playlist).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = PlaybackPlaylistAdapter(playlist.videos, sourceCode) { selectedCode ->
                popup.dismiss()
                if (videoCode == sourceCode && selectedCode != sourceCode) {
                    onPlaylistVideoSelected?.invoke(selectedCode)
                }
            }
            val selected = playlist.videos.indexOfFirst { it.videoCode == sourceCode }
            if (selected >= 0) scrollToPosition(selected)
        }
        popup.showAtLocation(textureViewContainer, Gravity.END, 0, 0)
    }

    // Kernels validate speed and preserve pause state inside their owning-thread command.
    private fun setSpeedInternal(speed: Float) {
        mediaInterface?.setSpeed(speed)
    }

}
