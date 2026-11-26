package app.myspoti.wear

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class CompanionTransportTest {
    @Test fun layerSuccessDoesNotContactLan() = runBlocking {
        assertEquals("layer", companionFallback({ "layer" }, { error("LAN must not be contacted") }))
    }

    @Test fun layerTimeoutAllowsLanFallback() = runBlocking {
        assertEquals("lan", companionFallback({ withTimeout(0L) { "layer" } }, { "lan" }))
    }

    @Test fun cancellationDoesNotContactLan() = runBlocking {
        val cancelled = CancellationException("caller stopped")
        var contactedLan = false
        try {
            companionFallback({ throw cancelled }, { contactedLan = true })
            fail("expected cancellation")
        } catch (e: CancellationException) {
            assertSame(cancelled, e)
        }
        assertEquals(false, contactedLan)
    }

    @Test fun parentTimeoutDoesNotContactLan() = runBlocking {
        var contactedLan = false
        try {
            withTimeout(100L) {
                companionFallback({ kotlinx.coroutines.awaitCancellation() }, { contactedLan = true })
            }
            fail("expected timeout")
        } catch (_: CancellationException) {
            assertEquals(false, contactedLan)
        }
    }
}
