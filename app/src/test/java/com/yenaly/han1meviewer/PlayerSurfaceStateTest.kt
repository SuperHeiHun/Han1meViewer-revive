package com.yenaly.han1meviewer

import com.yenaly.han1meviewer.ui.view.video.PlayerSurfaceState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure ownership checks: no media, network or device operations. Not run automatically. */
class PlayerSurfaceStateTest {
    @Test fun firstAvailabilityUsesTheActualCallbackSurface() {
        val state = PlayerSurfaceState<Any>()
        val incoming = Any()
        assertSame(incoming, state.available(incoming))
        assertSame(incoming, state.surface)
    }

    @Test fun differentKernelsCannotBorrowEachOthersOutput() {
        val first = PlayerSurfaceState<Any>()
        val second = PlayerSurfaceState<Any>()
        val old = Any()
        val current = Any()
        first.available(old)
        assertSame(current, second.available(current))
        assertSame(old, first.surface)
    }

    @Test fun fullscreenReparentingRetainsOnlyThisKernelsSurface() {
        val state = PlayerSurfaceState<Any>()
        val retained = Any()
        state.available(retained)
        assertFalse(state.destroyed(retained))
        assertSame(retained, state.available(Any()))
        assertNull(state.clear())
    }

    @Test fun releasedSessionDoesNotReuseThePreviousOutput() {
        val state = PlayerSurfaceState<Any>()
        state.available(Any())
        state.clear()
        val current = Any()
        assertSame(current, state.available(current))
    }

    @Test fun detachedSurfaceIsReturnedForDisposalExactlyOnce() {
        val state = PlayerSurfaceState<Any>()
        val surface = Any()
        state.available(surface)
        assertFalse(state.destroyed(surface))
        assertSame(surface, state.clear())
        assertNull(state.clear())
        assertNull(state.surface)
    }

    @Test fun attachedSurfaceIsReturnedToTheViewForDisposal() {
        val state = PlayerSurfaceState<Any>()
        val surface = Any()
        state.available(surface)
        assertNull(state.clear())
        assertTrue(state.destroyed(surface))
    }

    @Test fun obsoleteDestructionCannotDetachTheCurrentOutput() {
        val state = PlayerSurfaceState<Any>()
        val old = Any()
        val current = Any()
        state.available(old)
        state.clear()
        state.available(current)
        assertTrue(state.destroyed(old))
        assertSame(current, state.surface)
        assertNull(state.clear())
    }

    @Test fun unusedCallbackSurfaceIsNotRetainedDuringReparenting() {
        val state = PlayerSurfaceState<Any>()
        val retained = Any()
        val unused = Any()
        state.available(retained)
        state.destroyed(retained)
        assertSame(retained, state.available(unused))
        assertTrue(state.destroyed(unused))
        assertSame(retained, state.surface)
        assertNull(state.clear())
    }
}
