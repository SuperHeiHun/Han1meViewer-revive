package com.yenaly.han1meviewer.ui.view.video

import android.content.pm.ActivityInfo
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.Surface
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import cn.jzvd.JZMediaInterface
import cn.jzvd.JZMediaSystem
import cn.jzvd.Jzvd
import kotlin.math.absoluteValue


/**
 * @project Han1meViewer
 * @author Yenaly Liew
 * @time 2024/04/21 021 16:57
 */
sealed interface HMediaKernel {
    enum class Type(val clazz: Class<out JZMediaInterface>) {
        MediaPlayer(SystemMediaKernel::class.java),
        ExoPlayer(ExoMediaKernel::class.java);

        companion object {
            fun fromString(name: String): Type {
                return when (name) {
                    MediaPlayer.name -> MediaPlayer
                    ExoPlayer.name -> ExoPlayer
                    else -> ExoPlayer
                }
            }
        }
    }
}

class ExoMediaKernel(jzvd: Jzvd) : JZMediaInterface(jzvd), Player.Listener, HMediaKernel {
    companion object {
        const val TAG = "ExoMediaKernel"
    }

    @Volatile
    private var _exoPlayer: ExoPlayer? = null

    // ExoPlayer 只能在创建它的 JZVD 线程访问。JZVD 的进度条、速度设置等
    // 会从主线程同步读取这些状态，因此用快照缓存避免跨线程访问。
    @Volatile
    private var isPlayingCache = false

    @Volatile
    private var currentPositionCache = 0L

    @Volatile
    private var durationCache = 0L

    private var snapshotRunnable: Runnable? = null

    /**
     * 尽量少用，用了之后容易出bug
     */
    private val exoPlayer get() = _exoPlayer!!

    private var callback: Runnable? = null
    private var prevSeek = 0L

    @OptIn(UnstableApi::class)
    override fun prepare() {
        Log.e(TAG, "prepare")
        val context = jzvd.context

        release()
        // 新播放器创建完成前先置空，避免旧的 media handler 访问到跨线程播放器
        _exoPlayer = null
        isPlayingCache = false
        currentPositionCache = 0L
        durationCache = 0L
        mMediaHandlerThread = HandlerThread("JZVD")
        mMediaHandlerThread.start()
        mMediaHandler = Handler(mMediaHandlerThread.looper)
        handler = Handler(Looper.getMainLooper())
        mMediaHandler?.post {
            val videoTrackSelectionFactory = AdaptiveTrackSelection.Factory()
            val trackSelector = DefaultTrackSelector(context, videoTrackSelectionFactory)

            val loadControl: LoadControl = DefaultLoadControl.Builder()
                // .setBufferDurationsMs(360000, 600000, 1000, 5000)
                // .setPrioritizeTimeOverSizeThresholds(false)
                // .setTargetBufferBytes(C.LENGTH_UNSET)
                .build()


            val bandwidthMeter = DefaultBandwidthMeter.Builder(context).build()
            // 2. Create the player
            val renderersFactory = DefaultRenderersFactory(context)
            _exoPlayer = ExoPlayer.Builder(context, renderersFactory)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .setBandwidthMeter(bandwidthMeter)
                .build()
            // Produces DataSource instances through which media data is loaded.
            val dataSourceFactory = DefaultDataSource.Factory(
                context,
                DefaultHttpDataSource.Factory()
                    .setDefaultRequestProperties(jzvd.jzDataSource.headerMap)
            )

            val currUrl = jzvd.jzDataSource.currentUrl.toString()
            val videoSource = if (currUrl.contains(".m3u8")) {
                HlsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(currUrl))
            } else {
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(currUrl))
            }

            Log.e(TAG, "URL Link = $currUrl")

            exoPlayer.addListener(this)

            val isLoop = jzvd.jzDataSource.looping
            if (isLoop) {
                exoPlayer.repeatMode = Player.REPEAT_MODE_ONE
            } else {
                exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
            }
            currentPositionCache = 0L
            durationCache = 0L
            exoPlayer.setMediaSource(videoSource)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            isPlayingCache = true
            callback = OnBufferingUpdate(exoPlayer)

            val surfaceTexture = jzvd.textureView?.surfaceTexture
            surfaceTexture?.let { exoPlayer.setVideoSurface(Surface(it)) }
            startSnapshot()
        }
    }

    /**
     * 在 JZVD 线程定期刷新状态快照，供主线程同步读取。
     */
    private fun startSnapshot() {
        snapshotRunnable?.let { mMediaHandler?.removeCallbacks(it) }
        val runnable = object : Runnable {
            override fun run() {
                val player = _exoPlayer ?: return
                if (player !== _exoPlayer) return
                currentPositionCache = player.currentPosition.coerceAtLeast(0L)
                durationCache = player.duration.coerceAtLeast(0L)
                isPlayingCache = player.playWhenReady
                mMediaHandler?.postDelayed(this, 200)
            }
        }
        snapshotRunnable = runnable
        mMediaHandler?.post(runnable)
    }

    override fun start() {
        isPlayingCache = true
        mMediaHandler?.post {
            _exoPlayer?.playWhenReady = true
        }
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        val realWidth = videoSize.width * videoSize.pixelWidthHeightRatio
        val realHeight = videoSize.height
        handler.post {
            jzvd.onVideoSizeChanged(realWidth.toInt(), realHeight)
        }
        val ratio = realWidth / realHeight // > 1 橫屏， < 1 竖屏
        if (ratio > 1) {
            Jzvd.FULLSCREEN_ORIENTATION = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            Jzvd.FULLSCREEN_ORIENTATION = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    override fun onRenderedFirstFrame() {
        Log.e(TAG, "onRenderedFirstFrame")
    }

    override fun pause() {
        isPlayingCache = false
        mMediaHandler?.post {
            _exoPlayer?.playWhenReady = false
        }
    }

    override fun isPlaying(): Boolean {
        return isPlayingCache
    }

    override fun seekTo(time: Long) {
        mMediaHandler?.post {
            if (time != prevSeek) {
                _exoPlayer?.let { exoPlayer ->
                    if (time >= exoPlayer.bufferedPosition) {
                        handler?.post { jzvd.onStatePreparingPlaying() }
                    }
                    exoPlayer.seekTo(time)
                    prevSeek = time
                    jzvd.seekToInAdvance = time
                }
            }
        }
    }

    override fun release() {
        if (mMediaHandler != null && mMediaHandlerThread != null && _exoPlayer != null) { //不知道有没有妖孽
            val tmpHandlerThread = mMediaHandlerThread
            val tmpMediaPlayer = exoPlayer
            SAVED_SURFACE = null
            snapshotRunnable?.let { mMediaHandler?.removeCallbacks(it) }
            callback?.let { mMediaHandler?.removeCallbacks(it) }
            mMediaHandler?.post {
                tmpMediaPlayer.release() //release就不能放到主线程里，界面会卡顿
                tmpHandlerThread.quit()
                // 避免旧播放器的延迟释放把刚创建的新播放器置空
                if (_exoPlayer === tmpMediaPlayer) {
                    _exoPlayer = null
                    currentPositionCache = 0L
                    durationCache = 0L
                    isPlayingCache = false
                }
            }
        }
    }

    override fun getCurrentPosition(): Long {
        return currentPositionCache
    }

    override fun getDuration(): Long {
        return durationCache
    }

    override fun setVolume(leftVolume: Float, rightVolume: Float) {
        mMediaHandler?.post {
            _exoPlayer?.volume = (leftVolume + rightVolume) / 2
        }
    }

    override fun setSpeed(speed: Float) {
        mMediaHandler?.post {
            val playbackParams = PlaybackParameters(speed, 1.0F)
            _exoPlayer?.playbackParameters = playbackParams
        }
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        Log.e(TAG, "onTimelineChanged")
    }

    override fun onIsLoadingChanged(isLoading: Boolean) {
        Log.e(TAG, "onIsLoadingChanged")
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        isPlayingCache = playWhenReady
        if (playWhenReady && _exoPlayer?.playbackState == Player.STATE_READY) {
            handler?.post {
                jzvd.onStatePlaying()
            }
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        handler?.post {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    jzvd.onStatePreparingPlaying()
                    callback?.let { cb -> mMediaHandler?.post(cb) }
                }

                Player.STATE_READY -> {
                    jzvd.onStatePlaying()
                }

                Player.STATE_ENDED -> {
                    isPlayingCache = false
                    jzvd.onCompletion()
                }

                else -> {
                    Log.e(TAG, "onPlaybackStateChanged: $playbackState")
                }
            }
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        Log.e(TAG, "onPlayerError: $error")
        handler?.post { jzvd.onError(1000, 1000) }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            handler?.post { jzvd.onSeekComplete() }
        }
    }

    override fun setSurface(surface: Surface?) {
        mMediaHandler?.post {
            _exoPlayer?.setVideoSurface(surface)
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        if (SAVED_SURFACE == null) {
            SAVED_SURFACE = surface
            prepare()
        } else {
            jzvd.textureView.setSurfaceTexture(SAVED_SURFACE)
        }
    }

    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) = Unit

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = false

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit


    private inner class OnBufferingUpdate(private val player: ExoPlayer) : Runnable {
        override fun run() {
            if (player !== _exoPlayer) return
            val per = player.bufferedPercentage
            handler.post {
                jzvd.setBufferProgress(per)
            }
            if (per < 100) {
                mMediaHandler?.postDelayed(this, 300)
            } else {
                mMediaHandler?.removeCallbacks(this)
            }
        }
    }
}

class SystemMediaKernel(jzvd: Jzvd) : JZMediaSystem(jzvd), HMediaKernel {
    // #issue-26: 有的手機長按快進會報錯，合理懷疑是不是因爲沒有加 post
    // #issue-28: 有的平板长按快进也会报错，结果是 IllegalArgumentException，很奇怪，两次 try-catch 处理试试。
    override fun setSpeed(speed: Float) {
        mMediaHandler?.post {
            try {
                val pp = mediaPlayer.playbackParams
                pp.speed = speed.absoluteValue
                mediaPlayer.playbackParams = pp
            } catch (_: IllegalArgumentException) {
                try {
                    val opp = PlaybackParams().setSpeed(speed.absoluteValue)
                    mediaPlayer.playbackParams = opp
                } catch (e: IllegalArgumentException) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onVideoSizeChanged(mediaPlayer: MediaPlayer?, width: Int, height: Int) {
        super.onVideoSizeChanged(mediaPlayer, width, height)
        val ratio = width.toFloat() / height // > 1 橫屏， < 1 竖屏
        if (ratio > 1) {
            Jzvd.FULLSCREEN_ORIENTATION = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            Jzvd.FULLSCREEN_ORIENTATION = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    // #issue-139: 部分机型暂停报错，没判空导致的
    override fun pause() {
        mMediaHandler?.post {
            mediaPlayer?.pause()
        }
    }

    // #issue-crashlytics-c8636c4bb0b8516675cbeb9e8776bf0b:
    // 有些机器到这里可能会报空指针异常，所以加了个判断，但是不知道为什么会报空指针异常
    override fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying == true
    }
}