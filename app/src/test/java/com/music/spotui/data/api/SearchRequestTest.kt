package com.music.spotui.data.api

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class SearchRequestTest {
    @Test fun cancelledQueryIsNotASearchError() {
        val cancelled = CancellationException("query replaced")
        try {
            searchRequest<Unit> { throw cancelled }
            fail("Cancellation was swallowed")
        } catch (actual: CancellationException) { assertSame(cancelled, actual) }
    }
    @Test fun networkFailureIsReported() {
        val offline = IOException("offline")
        assertSame(offline, searchRequest<Unit> { throw offline }.exceptionOrNull())
    }
}
