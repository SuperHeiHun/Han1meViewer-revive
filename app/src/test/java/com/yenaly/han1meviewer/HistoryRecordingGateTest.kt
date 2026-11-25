package com.yenaly.han1meviewer

import com.yenaly.han1meviewer.logic.history.HistoryRecordingGate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class HistoryRecordingGateTest {
    @Test fun normalRequestsCanRecord() = runBlocking {
        val gate = HistoryRecordingGate { false }
        var inserted = 0
        gate.record(gate.ticket()) { inserted++ }
        assertEquals(1, inserted)
    }

    @Test fun privateRequestsNeverRecordEvenAfterDisablingPrivacy() = runBlocking {
        var enabled = true
        val gate = HistoryRecordingGate { enabled }
        val ticket = gate.ticket()
        gate.change { enabled = false }
        gate.record(ticket) { fail("Private request must not record") }
    }

    @Test fun enablingPrivacyInvalidatesPendingNormalRequests() = runBlocking {
        var enabled = false
        val gate = HistoryRecordingGate { enabled }
        val ticket = gate.ticket()
        gate.change { enabled = true }
        gate.record(ticket) { fail("Stale request must not record") }
    }

    @Test fun switchingOnAndOffStillInvalidatesOldRequests() = runBlocking {
        var enabled = false
        val gate = HistoryRecordingGate { enabled }
        val old = gate.ticket()
        gate.change { enabled = true }
        gate.change { enabled = false }
        gate.record(old) { fail("Request spanning a switch must not record") }
        var inserted = false
        gate.record(gate.ticket()) { inserted = true }
        assertTrue(inserted)
    }

    @Test fun privacyChangesSerializeWithShortDatabaseWrites() = runBlocking {
        var enabled = false
        val gate = HistoryRecordingGate { enabled }
        val release = CompletableDeferred<Unit>()
        val write = async(start = CoroutineStart.UNDISPATCHED) { gate.record(gate.ticket()) { release.await() } }
        val toggle = async { gate.change { enabled = true } }
        yield()
        assertFalse(toggle.isCompleted)
        release.complete(Unit)
        write.await()
        toggle.await()
        assertTrue(enabled)
        assertNull(gate.ticket())
    }

    @Test fun failedPersistenceDoesNotInvalidateAnOtherwiseValidTicket() = runBlocking {
        val gate = HistoryRecordingGate { false }
        val ticket = gate.ticket()
        try { gate.change { throw IllegalStateException() } } catch (_: IllegalStateException) { }
        var inserted = false
        gate.record(ticket) { inserted = true }
        assertTrue(inserted)
    }
}
