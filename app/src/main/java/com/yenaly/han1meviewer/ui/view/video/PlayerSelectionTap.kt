package com.yenaly.han1meviewer.ui.view.video

/** Tap-only selection state, independent of Android's long-press forwarding listener. */
internal class PlayerSelectionTap(private val touchSlop: Float) {
    var eligible = false
        private set
    private var x = 0f
    private var y = 0f

    fun down(x: Float, y: Float) {
        this.x = x
        this.y = y
        eligible = x.isFinite() && y.isFinite()
    }

    fun move(x: Float, y: Float) {
        if (!x.isFinite() || !y.isFinite() ||
            (x - this.x) * (x - this.x) + (y - this.y) * (y - this.y) > touchSlop * touchSlop) cancel()
    }

    fun up(x: Float, y: Float, heldMillis: Long, longPressMillis: Long): Boolean {
        move(x, y)
        val click = eligible && heldMillis in 0L until longPressMillis
        cancel()
        return click
    }

    fun cancel() { eligible = false }
}
