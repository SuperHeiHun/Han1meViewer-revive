package com.yenaly.han1meviewer.ui.view.video

import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.getSystemService
import cn.jzvd.JZUtils
import kotlin.math.abs
import kotlin.math.roundToInt

internal sealed interface PlayerGestureFeedback {
    data class Seek(val target: Long, val duration: Long, val offset: Long, val discrete: Boolean) : PlayerGestureFeedback
    data class Volume(val percent: Int) : PlayerGestureFeedback
    data class Brightness(val percent: Int) : PlayerGestureFeedback
    data class Speed(val speed: Float) : PlayerGestureFeedback
}

/** Owns complete surface touch sequences; JZVD must not process these events a second time. */
internal class PlayerGestureController(
    private val surface: View,
    private val configProvider: () -> PlayerGestureConfig,
    private val host: Host,
) {
    interface Host {
        val mediaIdentity: Any?
        val position: Long
        val duration: Long
        val normalSpeed: Float
        val canSeek: Boolean
        val canLongPress: Boolean
        val canAdjustLevels: Boolean
        val canEnterFullscreen: Boolean
        fun toggleControls()
        fun togglePlayback()
        fun enterFullscreen()
        fun seekTo(position: Long)
        fun setTemporarySpeed(speed: Float)
        fun beginInteraction()
        fun endInteraction()
        fun showFeedback(feedback: PlayerGestureFeedback)
        fun finishFeedback(immediate: Boolean)
    }

    private data class Tap(val x: Float, val y: Float, val time: Long, val media: Any?)

    private val handler = Handler(Looper.getMainLooper())
    private val viewConfig = ViewConfiguration.get(surface.context)
    private val density = surface.resources.displayMetrics.density
    private val session = PlayerGestureSession(
        viewConfig.scaledTouchSlop.toFloat(),
        maxOf(48f * density, viewConfig.scaledTouchSlop * 2f),
    )
    private val pendingSeek = PlayerPendingSeek()
    private val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()
    private val doubleTapSlop = viewConfig.scaledDoubleTapSlop.toFloat()
    private val audioManager = surface.context.getSystemService<AudioManager>()
    private var config = PlayerGestureConfig()
    private var pointerId = MotionEvent.INVALID_POINTER_ID
    private var media: Any? = null
    private var firstTap: Tap? = null
    private var secondTapCandidate = false
    private var chainTap: Tap? = null
    private var chainAction = PlayerDoubleTapAction.NONE
    private var chainOffset = 0L
    private var startPosition = 0L
    private var previewPosition: Long? = null
    private var startLevel = 0f
    private var levelStarted = false
    private var originalSpeed: Float? = null
    private var longPressX = 0f
    private var lastX = 0f
    private var lastTemporarySpeed: Float? = null

    val isTemporarySpeedActive: Boolean get() = originalSpeed != null

    private val singleTap = Runnable {
        val tap = firstTap
        firstTap = null
        if (tap != null && tap.media === host.mediaIdentity && pointerId == MotionEvent.INVALID_POINTER_ID) {
            host.toggleControls()
        }
    }

    private val longPress = Runnable {
        if (pointerId == MotionEvent.INVALID_POINTER_ID || media !== host.mediaIdentity ||
            !host.canLongPress || config.longPressMode == PlayerLongPressMode.NONE
        ) return@Runnable
        if (!session.longPress()) return@Runnable
        clearTaps()
        originalSpeed = host.normalSpeed
        longPressX = lastX
        applyTemporarySpeed(PlayerGestureMath.temporarySpeed(host.normalSpeed, config))
        surface.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun onTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> onDown(event)
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointerId)
                if (event.pointerCount != 1 || index < 0 || media !== host.mediaIdentity) {
                    cancel()
                } else move(event.getX(index), event.getY(index))
            }
            MotionEvent.ACTION_UP -> onUp(event)
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> cancel()
        }
        return true
    }

    private fun onDown(event: MotionEvent) {
        if (pointerId != MotionEvent.INVALID_POINTER_ID) cancel()
        if (media !== host.mediaIdentity) cancel()
        media = host.mediaIdentity
        config = configProvider()
        pointerId = event.getPointerId(0)
        lastX = event.x
        surface.parent?.requestDisallowInterceptTouchEvent(true)
        previewPosition = null
        levelStarted = false
        startPosition = pendingSeek.position(host.position, SystemClock.uptimeMillis())
        val tap = firstTap
        secondTapCandidate = tap != null && isNearby(tap, event.x, event.y, event.eventTime)
        handler.removeCallbacks(singleTap)
        if (tap != null && !secondTapCandidate) {
            firstTap = null
            if (tap.media === media) host.toggleControls()
        }
        session.down(
            event.x, event.y, surface.width, config,
            host.canSeek, host.canAdjustLevels, host.canEnterFullscreen,
        )
        host.beginInteraction()
        if (config.longPressMode != PlayerLongPressMode.NONE) {
            handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
        }
    }

    private fun move(x: Float, y: Float) {
        if (!x.isFinite() || !y.isFinite()) {
            cancel()
            return
        }
        lastX = x
        val state = session.move(x, y)
        if (session.hasMoved) {
            handler.removeCallbacks(longPress)
            clearTaps()
        }
        when (state) {
            PlayerGestureSession.State.SEEK -> {
                if (!host.canSeek) {
                    cancel()
                    return
                }
                previewPosition = PlayerGestureMath.seekPosition(
                    startPosition, x - session.downX, surface.width, host.duration, config.seekSensitivity,
                )
                previewPosition?.let {
                    host.showFeedback(PlayerGestureFeedback.Seek(it, host.duration, it - startPosition, false))
                }
            }
            PlayerGestureSession.State.BRIGHTNESS -> adjustBrightness(y)
            PlayerGestureSession.State.VOLUME -> adjustVolume(y)
            PlayerGestureSession.State.LONG_PRESS -> {
                if (!host.canLongPress) {
                    cancel(clearPendingSeek = false)
                    return
                }
                if (config.longPressMode == PlayerLongPressMode.VARIABLE) {
                    applyTemporarySpeed(PlayerGestureMath.variableSpeed(config.temporarySpeed, x - longPressX, density))
                }
            }
            else -> Unit
        }
    }

    private fun onUp(event: MotionEvent) {
        if (pointerId == MotionEvent.INVALID_POINTER_ID) return
        if (event.getPointerId(0) != pointerId || media !== host.mediaIdentity) {
            cancel()
            return
        }
        move(event.x, event.y)
        if (pointerId == MotionEvent.INVALID_POINTER_ID) return
        handler.removeCallbacks(longPress)
        val state = session.state
        val enterFullscreen = session.entersFullscreen(event.x, event.y)
        val target = previewPosition
        val isTap = state == PlayerGestureSession.State.PENDING && !session.hasMoved
        pointerId = MotionEvent.INVALID_POINTER_ID
        session.reset()
        restoreSpeed()
        surface.parent?.requestDisallowInterceptTouchEvent(false)
        host.endInteraction()
        when {
            enterFullscreen && host.canEnterFullscreen -> {
                clearTaps()
                host.finishFeedback(true)
                host.enterFullscreen()
            }
            state == PlayerGestureSession.State.SEEK && target != null && host.canSeek -> {
                requestSeek(target)
                host.finishFeedback(false)
            }
            isTap -> handleTap(event.x, event.y, event.eventTime)
            else -> host.finishFeedback(state == PlayerGestureSession.State.LONG_PRESS)
        }
    }

    private fun handleTap(x: Float, y: Float, time: Long) {
        val action = PlayerGestureMath.doubleTap(config.doubleTapMode, x, surface.width)
        val chain = chainTap
        if (chain != null && isNearby(chain, x, y, time) && action == chainAction) {
            seekStep(action, x, y, time)
            return
        }
        chainTap = null
        chainOffset = 0L
        val first = firstTap
        if (secondTapCandidate && first != null &&
            PlayerGestureMath.doubleTap(config.doubleTapMode, first.x, surface.width) == action
        ) {
            firstTap = null
            secondTapCandidate = false
            when (action) {
                PlayerDoubleTapAction.PLAY_PAUSE -> host.togglePlayback()
                PlayerDoubleTapAction.BACKWARD, PlayerDoubleTapAction.FORWARD -> seekStep(action, x, y, time)
                PlayerDoubleTapAction.NONE -> Unit
            }
        } else if (config.doubleTapMode == PlayerDoubleTapMode.NONE) {
            host.toggleControls()
        } else {
            firstTap = Tap(x, y, time, media)
            handler.postDelayed(singleTap, doubleTapTimeout)
        }
    }

    private fun seekStep(action: PlayerDoubleTapAction, x: Float, y: Float, time: Long) {
        if (!host.canSeek) return
        val start = pendingSeek.position(host.position, SystemClock.uptimeMillis())
        val direction = if (action == PlayerDoubleTapAction.BACKWARD) -1L else 1L
        val target = PlayerGestureMath.seekBy(start, direction * config.seekStepSeconds * 1_000L, host.duration) ?: return
        if (action != chainAction) chainOffset = 0L
        chainOffset += target - start
        chainTap = Tap(x, y, time, media)
        chainAction = action
        firstTap = null
        if (target != start) requestSeek(target)
        host.showFeedback(PlayerGestureFeedback.Seek(target, host.duration, chainOffset, true))
        host.finishFeedback(false)
    }

    private fun isNearby(tap: Tap, x: Float, y: Float, time: Long): Boolean =
        tap.media === media && time - tap.time in 0..doubleTapTimeout &&
                abs(x - tap.x) <= doubleTapSlop && abs(y - tap.y) <= doubleTapSlop

    private fun requestSeek(target: Long) {
        pendingSeek.request(target, SystemClock.uptimeMillis())
        host.seekTo(target)
    }

    fun displayPosition(actual: Long): Long = pendingSeek.position(actual, SystemClock.uptimeMillis())

    private fun adjustBrightness(y: Float) {
        val window = runCatching { JZUtils.getWindow(surface.context) }.getOrNull() ?: return
        if (!levelStarted) {
            val brightness = window.attributes.screenBrightness
            startLevel = if (brightness >= 0f) brightness else runCatching {
                Settings.System.getInt(surface.context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
            }.getOrDefault(0.5f)
            levelStarted = true
        }
        val value = PlayerGestureMath.level(startLevel, y - session.downY, surface.height, config.brightnessSensitivity)
            .coerceAtLeast(0.01f)
        val attributes = window.attributes
        attributes.screenBrightness = value
        window.attributes = attributes
        host.showFeedback(PlayerGestureFeedback.Brightness((value * 100).roundToInt()))
    }

    private fun adjustVolume(y: Float) {
        val manager = audioManager ?: return
        val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0 || manager.isVolumeFixed) return
        if (!levelStarted) {
            startLevel = manager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
            levelStarted = true
        }
        val fraction = PlayerGestureMath.level(startLevel, y - session.downY, surface.height, config.volumeSensitivity)
        val level = (fraction * max).roundToInt().coerceIn(0, max)
        runCatching { manager.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0) }
        val actual = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        host.showFeedback(PlayerGestureFeedback.Volume((actual * 100f / max).roundToInt()))
    }

    private fun applyTemporarySpeed(speed: Float) {
        if (!speed.isFinite() || speed <= 0f || speed == lastTemporarySpeed) return
        host.setTemporarySpeed(speed)
        lastTemporarySpeed = speed
        host.showFeedback(PlayerGestureFeedback.Speed(speed))
    }

    private fun restoreSpeed() {
        val speed = originalSpeed
        originalSpeed = null
        lastTemporarySpeed = null
        if (speed != null && media === host.mediaIdentity) host.setTemporarySpeed(speed)
    }

    private fun clearTaps() {
        handler.removeCallbacks(singleTap)
        firstTap = null
        secondTapCandidate = false
        chainTap = null
        chainAction = PlayerDoubleTapAction.NONE
        chainOffset = 0L
    }

    fun onPlaybackPaused() {
        if (session.state == PlayerGestureSession.State.LONG_PRESS) cancel(clearPendingSeek = false)
    }

    fun cancel(clearPendingSeek: Boolean = true) {
        val wasActive = pointerId != MotionEvent.INVALID_POINTER_ID
        handler.removeCallbacks(longPress)
        clearTaps()
        restoreSpeed()
        pointerId = MotionEvent.INVALID_POINTER_ID
        previewPosition = null
        session.reset()
        if (clearPendingSeek) pendingSeek.clear()
        surface.parent?.requestDisallowInterceptTouchEvent(false)
        host.finishFeedback(true)
        if (wasActive) host.endInteraction()
    }
}
