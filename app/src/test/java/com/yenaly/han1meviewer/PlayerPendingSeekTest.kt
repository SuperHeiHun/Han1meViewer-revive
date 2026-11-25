package com.yenaly.han1meviewer

import com.yenaly.han1meviewer.ui.view.video.PlayerPendingSeek
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerPendingSeekTest {
    private val pending = PlayerPendingSeek()

    @Test fun successiveRequestsAccumulateFromThePendingTargetNotTheOldSnapshot() {
        pending.request(52_000L, 100)
        assertEquals(52_000L, pending.position(42_000L, 150))
        pending.request(pending.position(42_000L, 180) + 10_000L, 180)
        assertEquals(62_000L, pending.position(42_000L, 200))
    }

    @Test fun confirmedPositionReleasesThePendingTarget() {
        pending.request(52_000L, 100)
        assertEquals(52_100L, pending.position(52_100L, 200))
        assertEquals(52_500L, pending.position(52_500L, 300))
    }

    @Test fun failedSeekEventuallyFallsBackToTheActualPosition() {
        pending.request(52_000L, 100)
        assertEquals(52_000L, pending.position(42_000L, 5_099))
        assertEquals(42_000L, pending.position(42_000L, 5_100))
    }

    @Test fun aNewRequestResetsTheConfirmationTimeout() {
        pending.request(52_000L, 100)
        pending.request(62_000L, 4_900)
        assertEquals(62_000L, pending.position(42_000L, 5_100))
    }

    @Test fun cancelOrMediaChangeCanClearTheOldTarget() {
        pending.request(52_000L, 100)
        pending.clear()
        assertEquals(500L, pending.position(500L, 200))
    }
}
