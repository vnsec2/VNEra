package app.myspoti.extensions

import org.junit.Assert.*
import org.junit.Test

class QueueLookaheadTest {
    @Test fun keepsFirstFiveInOrderIncludingDuplicateRecordings() {
        val queue = listOf("a", "a", "b", "c", "d", "e")
        assertEquals(listOf("a", "a", "b", "c", "d"), QueueLookahead.next(queue))
    }
}
