package com.yenaly.han1meviewer.ui.view.video

import kotlin.math.abs
import kotlin.math.roundToInt

enum class PlayerDoubleTapMode(val value: String) {
    PLAY_PAUSE("play_pause"), SEEK("seek"), BOTH("both"), NONE("none");

    companion object {
        fun fromValue(value: String?) = entries.firstOrNull { it.value == value } ?: PLAY_PAUSE
    }
}

enum class PlayerLongPressMode(val value: String) {
    LEGACY("legacy"), FIXED("fixed"), VARIABLE("variable"), NONE("none");

    companion object {
        fun fromValue(value: String?) = entries.firstOrNull { it.value == value } ?: LEGACY
    }
}

internal data class PlayerGestureConfig(
    val doubleTapMode: PlayerDoubleTapMode = PlayerDoubleTapMode.PLAY_PAUSE,
    val seekStepSeconds: Int = 10,
    val longPressMode: PlayerLongPressMode = PlayerLongPressMode.LEGACY,
    val temporarySpeed: Float = 2f,
    val legacySpeedMultiplier: Float = 2.5f,
    val seekEnabled: Boolean = true,
    val brightnessEnabled: Boolean = true,
    val volumeEnabled: Boolean = true,
    val seekSensitivity: Int = 5,
    val brightnessSensitivity: Int = 5,
    val volumeSensitivity: Int = 5,
)

internal enum class PlayerDoubleTapAction { NONE, PLAY_PAUSE, BACKWARD, FORWARD }

/** Pure calculations shared by the touch controller and its regression tests. */
internal object PlayerGestureMath {
    fun doubleTap(mode: PlayerDoubleTapMode, x: Float, width: Int): PlayerDoubleTapAction {
        if (width <= 0 || !x.isFinite()) return PlayerDoubleTapAction.NONE
        return when (mode) {
            PlayerDoubleTapMode.NONE -> PlayerDoubleTapAction.NONE
            PlayerDoubleTapMode.PLAY_PAUSE -> PlayerDoubleTapAction.PLAY_PAUSE
            PlayerDoubleTapMode.SEEK -> if (x < width / 2f) {
                PlayerDoubleTapAction.BACKWARD
            } else PlayerDoubleTapAction.FORWARD
            PlayerDoubleTapMode.BOTH -> when {
                x < width * 0.35f -> PlayerDoubleTapAction.BACKWARD
                x > width * 0.65f -> PlayerDoubleTapAction.FORWARD
                else -> PlayerDoubleTapAction.PLAY_PAUSE
            }
        }
    }

    fun sensitivity(value: Int): Int = when (value.coerceIn(1, 9)) {
        6 -> 7
        7 -> 10
        8 -> 20
        9 -> 40
        else -> value.coerceIn(1, 9)
    }

    fun seekPosition(start: Long, dx: Float, width: Int, duration: Long, sensitivity: Int): Long? {
        if (duration <= 0 || width <= 0 || !dx.isFinite()) return null
        return (start.toDouble() + dx.toDouble() * duration / (width.toDouble() * PlayerGestureMath.sensitivity(sensitivity)))
            .coerceIn(0.0, duration.toDouble()).toLong()
    }

    fun seekBy(start: Long, offset: Long, duration: Long): Long? {
        if (duration <= 0) return null
        val boundedStart = start.coerceIn(0L, duration)
        return boundedStart + if (offset >= 0) {
            offset.coerceAtMost(duration - boundedStart)
        } else offset.coerceAtLeast(-boundedStart)
    }

    fun level(start: Float, dy: Float, height: Int, sensitivity: Int): Float {
        if (height <= 0 || !dy.isFinite()) return start.coerceIn(0f, 1f)
        return (start - dy * 3f * 5f / (height * sensitivity.coerceIn(1, 9)))
            .coerceIn(0f, 1f)
    }

    fun temporarySpeed(normalSpeed: Float, config: PlayerGestureConfig): Float =
        if (config.longPressMode == PlayerLongPressMode.LEGACY) {
            normalSpeed * config.legacySpeedMultiplier
        } else config.temporarySpeed.coerceIn(0.5f, 4f)

    fun variableSpeed(base: Float, dx: Float, density: Float): Float {
        if (!dx.isFinite() || !density.isFinite() || density <= 0f) return base.coerceIn(0.5f, 4f)
        val speed = (base + dx / (48f * density) * 0.1f).coerceIn(0.5f, 4f)
        return (speed * 10f).roundToInt() / 10f
    }
}

internal class PlayerGestureSession(touchSlop: Float, minimumFullscreenDistance: Float) {
    enum class State { IDLE, PENDING, SEEK, BRIGHTNESS, VOLUME, FULLSCREEN, LONG_PRESS, IGNORED }

    private val fullscreen = CenterFullscreenGesture(touchSlop, minimumFullscreenDistance)
    private val slop = touchSlop
    private var config = PlayerGestureConfig()
    private var canSeek = false
    private var canAdjustLevels = false
    private var width = 0
    var state = State.IDLE
        private set
    var downX = 0f
        private set
    var downY = 0f
        private set
    var hasMoved = false
        private set

    fun down(
        x: Float, y: Float, width: Int, config: PlayerGestureConfig,
        canSeek: Boolean, canAdjustLevels: Boolean, canEnterFullscreen: Boolean,
        canEnterPortraitFullscreen: Boolean = false,
    ) {
        this.downX = x
        this.downY = y
        this.width = width
        this.config = config
        this.canSeek = canSeek
        this.canAdjustLevels = canAdjustLevels
        hasMoved = false
        state = if (width > 0 && x.isFinite() && y.isFinite()) State.PENDING else State.IGNORED
        fullscreen.onDown(x, y, width, canEnterFullscreen, allowDownward = canEnterPortraitFullscreen)
    }

    fun move(x: Float, y: Float): State {
        if (state != State.PENDING) return state
        val dx = x - downX
        val dy = y - downY
        if (maxOf(abs(dx), abs(dy)) <= slop) return state
        hasMoved = true
        if (abs(dx) <= abs(dy) * 1.5f && abs(dy) <= abs(dx) * 1.5f) return state
        if (fullscreen.onMove(x, y)) {
            if (fullscreen.isTracking) state = State.FULLSCREEN
            return state
        }
        state = when {
            abs(dx) > abs(dy) * 1.5f ->
                if (canSeek && config.seekEnabled) State.SEEK else State.IGNORED
            abs(dy) > abs(dx) * 1.5f -> when {
                !canAdjustLevels -> State.IGNORED
                downX < width / 2f -> if (config.brightnessEnabled) State.BRIGHTNESS else State.IGNORED
                else -> if (config.volumeEnabled) State.VOLUME else State.IGNORED
            }
            else -> State.PENDING
        }
        return state
    }

    fun longPress(): Boolean {
        if (state != State.PENDING || hasMoved) return false
        fullscreen.cancel()
        state = State.LONG_PRESS
        return true
    }

    val isPortraitFullscreen: Boolean get() = state == State.FULLSCREEN && fullscreen.isDownward

    fun entersFullscreen(x: Float, y: Float): Boolean =
        state == State.FULLSCREEN && fullscreen.onUp(x, y)

    fun reset() {
        state = State.IDLE
        fullscreen.cancel()
    }
}

/** Retains a requested target while the asynchronous media position still describes the old frame. */
internal class PlayerPendingSeek {
    private var target: Long? = null
    private var requestedAt = 0L

    fun request(position: Long, now: Long) {
        target = position
        requestedAt = now
    }

    fun position(actual: Long, now: Long): Long {
        val pending = target ?: return actual
        if (now - requestedAt >= 5_000L || abs(actual.toDouble() - pending) <= 1_000.0) {
            clear()
            return actual
        }
        return pending
    }

    fun clear() {
        target = null
    }
}
