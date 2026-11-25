package com.yenaly.han1meviewer.ui.view.video

import android.content.pm.ActivityInfo
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.Surface
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
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

    private var outputSurface: Surface? = null

    @Volatile
    private var session: Any? = null

    private var callback: Runnable? = null

    @OptIn(UnstableApi::class)
    override fun prepare() {
        Log.e(TAG, "prepare")
        val context = jzvd.context
        val source = jzvd.jzDataSource
        val currUrl = source.currentUrl.toString()
        val initialPosition = jzvd.seekToInAdvance.coerceAtLeast(0L)
        val surfaceTexture = jzvd.textureView?.surfaceTexture

        release()
        SAVED_SURFACE = surfaceTexture
        val token = Any()
        session = token
        isPlayingCache = true
        currentPositionCache = 0L
        durationCache = 0L
        mMediaHandlerThread = HandlerThread("JZVD")
        mMediaHandlerThread.start()
        val worker = Handler(mMediaHandlerThread.looper)
        mMediaHandler = worker
        handler = Handler(Looper.getMainLooper())
        worker.post {
            if (session !== token) return@post
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
            val player = ExoPlayer.Builder(context, renderersFactory)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .setBandwidthMeter(bandwidthMeter)
                .build()
            val videoSurface = surfaceTexture?.let(::Surface)
            val attached = synchronized(this) {
                if (session === token) {
                    _exoPlayer = player
                    outputSurface = videoSurface
                    true
                } else false
            }
            if (!attached) {
                player.release()
                videoSurface?.release()
                return@post
            }
            // Produces DataSource instances through which media data is loaded.
            val dataSourceFactory = DefaultDataSource.Factory(
                context,
                DefaultHttpDataSource.Factory()
                    .setDefaultRequestProperties(source.headerMap)
            )

            val videoSource = if (currUrl.contains(".m3u8")) {
                HlsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(currUrl))
            } else {
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(currUrl))
            }

            Log.e(TAG, "URL Link = $currUrl")

            player.addListener(this)

            val isLoop = source.looping
            if (isLoop) {
                player.repeatMode = Player.REPEAT_MODE_ONE
            } else {
                player.repeatMode = Player.REPEAT_MODE_OFF
            }
            if (initialPosition > 0L) player.setMediaSource(videoSource, initialPosition)
            else player.setMediaSource(videoSource)
            player.prepare()
            player.playWhenReady = isPlayingCache
            synchronized(this) {
                if (session === token) callback = OnBufferingUpdate(player, worker)
            }
            videoSurface?.let { player.setVideoSurface(it) }
            startSnapshot(player, token, worker)
        }
    }

    /**
     * 在 JZVD 线程定期刷新状态快照，供主线程同步读取。
     */
    private fun startSnapshot(player: ExoPlayer, token: Any, worker: Handler) {
        val runnable = object : Runnable {
            override fun run() {
                if (session !== token || !updateSnapshot(player)) return
                worker.postDelayed(this, 200)
            }
        }
        worker.post(runnable)
    }

    private fun updateSnapshot(player: ExoPlayer): Boolean = synchronized(this) {
        if (player !== _exoPlayer) false else {
            currentPositionCache = player.currentPosition.coerceAtLeast(0L)
            durationCache = player.duration.coerceAtLeast(0L)
            true
        }
    }

    private fun postPlayerCommand(action: (ExoPlayer) -> Unit) {
        val token = session ?: return
        mMediaHandler?.post {
            val player = _exoPlayer
            if (session === token && player != null) action(player)
        }
    }

    private fun postToUi(player: ExoPlayer, action: () -> Unit) {
        handler?.post {
            if (_exoPlayer === player && jzvd.mediaInterface === this) action()
        }
    }

    override fun start() {
        isPlayingCache = true
        postPlayerCommand { it.playWhenReady = true }
    }

    override fun pause() {
        isPlayingCache = false
        postPlayerCommand { it.playWhenReady = false }
    }

    override fun isPlaying(): Boolean {
        return isPlayingCache
    }

    override fun seekTo(time: Long) {
        postPlayerCommand { it.seekTo(time.coerceAtLeast(0L)) }
    }

    override fun release() {
        val worker = mMediaHandler
        val thread = mMediaHandlerThread
        mMediaHandler = null
        mMediaHandlerThread = null
        val (player, surface) = synchronized(this) {
            session = null
            (_exoPlayer to outputSurface).also {
                _exoPlayer = null
                outputSurface = null
            }
        }
        isPlayingCache = false
        currentPositionCache = 0L
        durationCache = 0L
        if (worker != null || player != null) SAVED_SURFACE = null
        callback = null
        worker?.removeCallbacksAndMessages(null)
        worker?.post {
            player?.release() // Release on the owning thread, never on the UI thread.
            surface?.release()
            thread?.quit()
        }
    }

    override fun getCurrentPosition(): Long {
        return currentPositionCache
    }

    override fun getDuration(): Long {
        return durationCache
    }

    override fun setVolume(leftVolume: Float, rightVolume: Float) {
        postPlayerCommand { it.volume = (leftVolume + rightVolume) / 2 }
    }

    override fun setSpeed(speed: Float) {
        if (!speed.isFinite() || speed <= 0f) return
        postPlayerCommand {
            try {
                it.playbackParameters = PlaybackParameters(speed, 1.0F)
            } catch (error: IllegalArgumentException) {
                Log.w(TAG, "Unsupported playback speed: $speed", error)
            } catch (error: IllegalStateException) {
                Log.w(TAG, "Cannot change speed in the current media state", error)
            }
        }
    }

    // onEvents supplies the emitting player. Late events from a released session cannot
    // change a new video's state, and READY/BUFFERING must honor the latest pause request.
    override fun onEvents(player: Player, events: Player.Events) {
        val current = _exoPlayer ?: return
        if (player !== current || !updateSnapshot(current)) return
        if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) || events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)) {
            val playbackState = current.playbackState
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    postToUi(current) { if (isPlayingCache) jzvd.onStatePreparingPlaying() }
                    callback?.let { cb -> mMediaHandler?.removeCallbacks(cb) }
                    callback?.let { cb -> mMediaHandler?.post(cb) }
                }

                Player.STATE_READY -> postToUi(current) {
                    if (isPlayingCache) {
                        jzvd.seekToInAdvance = 0L
                        jzvd.onStatePlaying()
                    } else if (jzvd.state != Jzvd.STATE_PAUSE) jzvd.onStatePause()
                }

                Player.STATE_ENDED -> postToUi(current) {
                    if (isPlayingCache) {
                        isPlayingCache = false
                        jzvd.onCompletion()
                    } else if (jzvd.state != Jzvd.STATE_PAUSE) jzvd.onStatePause()
                }

                else -> {
                    Log.e(TAG, "onPlaybackStateChanged: $playbackState")
                }
            }
        }
        if (events.contains(Player.EVENT_VIDEO_SIZE_CHANGED)) {
            val size = current.videoSize
            val width = (size.width * size.pixelWidthHeightRatio).toInt()
            val height = size.height
            postToUi(current) {
                jzvd.onVideoSizeChanged(width, height)
                if (width > 0 && height > 0) Jzvd.FULLSCREEN_ORIENTATION = if (width > height) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
        if (events.contains(Player.EVENT_PLAYER_ERROR) && current.playerError != null) {
            Log.e(TAG, "onPlayerError: ${current.playerError}")
            postToUi(current) {
                isPlayingCache = false
                jzvd.onError(1000, 1000)
            }
        }
        if (events.contains(Player.EVENT_POSITION_DISCONTINUITY)) {
            postToUi(current) { jzvd.onSeekComplete() }
        }
    }

    override fun setSurface(surface: Surface?) {
        postPlayerCommand { it.setVideoSurface(surface) }
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


    private inner class OnBufferingUpdate(private val player: ExoPlayer, private val worker: Handler) : Runnable {
        override fun run() {
            if (player !== _exoPlayer) return
            val per = player.bufferedPercentage
            postToUi(player) { jzvd.setBufferProgress(per) }
            if (per < 100) {
                worker.postDelayed(this, 300)
            } else {
                worker.removeCallbacks(this)
            }
        }
    }
}

class SystemMediaKernel(jzvd: Jzvd) : JZMediaSystem(jzvd), HMediaKernel {
    override fun prepare() {
        val uri = jzvd.jzDataSource.currentUrl.toString().toUri()
        if (uri.scheme != "content") {
            super.prepare()
            return
        }
        // The upstream String data-source overload cannot resolve our SAF grant.
        // Only local content uses this branch; online playback keeps the upstream path.
        val texture = SAVED_SURFACE ?: jzvd.textureView?.surfaceTexture
        release()
        SAVED_SURFACE = texture
        mMediaHandlerThread = HandlerThread("JZVD").also { it.start() }
        mMediaHandler = Handler(mMediaHandlerThread.looper)
        handler = Handler(Looper.getMainLooper())
        mMediaHandler.post {
            try {
                val player = MediaPlayer()
                mediaPlayer = player
                player.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
                player.isLooping = jzvd.jzDataSource.looping
                player.setScreenOnWhilePlaying(true)
                player.setOnPreparedListener(this)
                player.setOnCompletionListener(this)
                player.setOnBufferingUpdateListener(this)
                player.setOnSeekCompleteListener(this)
                player.setOnErrorListener(this)
                player.setOnInfoListener(this)
                player.setOnVideoSizeChangedListener(this)
                player.setDataSource(jzvd.context, uri)
                texture?.let { Surface(it).also { surface -> player.setSurface(surface); surface.release() } }
                player.prepareAsync()
            } catch (e: Exception) {
                Log.e("SystemMediaKernel", "Cannot open downloaded content", e)
                handler.post {
                    if (jzvd.mediaInterface === this) jzvd.onError(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0)
                }
            }
        }
    }

    private fun postPlayerCommand(action: (MediaPlayer) -> Unit) {
        val player = mediaPlayer ?: return
        mMediaHandler?.post {
            if (player !== mediaPlayer) return@post
            try {
                action(player)
            } catch (error: IllegalStateException) {
                Log.w("SystemMediaKernel", "Media state changed before the command ran", error)
            }
        }
    }

    private fun postToUi(player: MediaPlayer?, action: () -> Unit) {
        if (player == null) return
        handler?.post {
            if (player === mediaPlayer && jzvd.mediaInterface === this) action()
        }
    }

    // Nonzero PlaybackParams may start MediaPlayer. Keep its prior state on the
    // owning thread, including a restoration queued after a pause request.
    override fun setSpeed(speed: Float) {
        if (!speed.isFinite() || speed <= 0f) return
        postPlayerCommand { player ->
            val wasPlaying = player.isPlaying
            try {
                try {
                    player.playbackParams = player.playbackParams.setSpeed(speed)
                } catch (_: IllegalArgumentException) {
                    player.playbackParams = PlaybackParams().allowDefaults().setSpeed(speed)
                }
            } catch (error: IllegalArgumentException) {
                Log.w("SystemMediaKernel", "Unsupported playback speed: $speed", error)
            } catch (error: UnsupportedOperationException) {
                Log.w("SystemMediaKernel", "Playback speed is unavailable", error)
            } finally {
                if (!wasPlaying) player.pause()
            }
        }
    }

    override fun onPrepared(player: MediaPlayer?) = postToUi(player) { jzvd.onPrepared() }

    override fun onCompletion(player: MediaPlayer?) = postToUi(player) {
        if (jzvd.state != Jzvd.STATE_PAUSE) jzvd.onCompletion()
    }

    override fun onBufferingUpdate(player: MediaPlayer?, percent: Int) =
        postToUi(player) { jzvd.setBufferProgress(percent) }

    override fun onSeekComplete(player: MediaPlayer?) = postToUi(player) { jzvd.onSeekComplete() }

    override fun onError(player: MediaPlayer?, what: Int, extra: Int): Boolean {
        postToUi(player) { jzvd.onError(what, extra) }
        return true
    }

    override fun onInfo(player: MediaPlayer?, what: Int, extra: Int): Boolean {
        postToUi(player) {
            when (what) {
                MediaPlayer.MEDIA_INFO_BUFFERING_START -> {
                    if (jzvd.state == Jzvd.STATE_PLAYING && isPlaying()) jzvd.onStatePreparingPlaying()
                }
                MediaPlayer.MEDIA_INFO_BUFFERING_END, MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> {
                    if (jzvd.state in setOf(Jzvd.STATE_PREPARED, Jzvd.STATE_PREPARING, Jzvd.STATE_PREPARING_PLAYING) &&
                        isPlaying()
                    ) jzvd.onStatePlaying()
                }
                else -> jzvd.onInfo(what, extra)
            }
        }
        return true
    }

    override fun onVideoSizeChanged(player: MediaPlayer?, width: Int, height: Int) = postToUi(player) {
        jzvd.onVideoSizeChanged(width, height)
        if (width > 0 && height > 0) Jzvd.FULLSCREEN_ORIENTATION = if (width > height) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    override fun start() = postPlayerCommand { it.start() }

    override fun pause() = postPlayerCommand { it.pause() }

    override fun seekTo(time: Long) = postPlayerCommand { it.seekTo(time.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()) }

    override fun isPlaying(): Boolean {
        return try {
            mediaPlayer?.isPlaying == true
        } catch (_: IllegalStateException) {
            false
        }
    }
}
