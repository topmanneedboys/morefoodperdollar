package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareHerePhotoRequestPolicyTest {

    @Test
    fun `begin advances generation and marks photo work active`() {
        val started =
            CompareHerePhotoRequestPolicy.begin(
                CompareHerePhotoRequestState.initial()
            )

        assertEquals(1L, started.requestId)
        assertTrue(started.inFlight)
        assertTrue(
            CompareHerePhotoRequestPolicy.accepts(
                callbackRequestId = started.requestId,
                current = started,
                closed = false
            )
        )
    }

    @Test
    fun `invalidation rejects an old callback and returns to idle`() {
        val started =
            CompareHerePhotoRequestPolicy.begin(
                CompareHerePhotoRequestState.initial()
            )
        val invalidated = CompareHerePhotoRequestPolicy.invalidate(started)

        assertEquals(2L, invalidated.requestId)
        assertFalse(invalidated.inFlight)
        assertFalse(
            CompareHerePhotoRequestPolicy.accepts(
                callbackRequestId = started.requestId,
                current = invalidated,
                closed = false
            )
        )
    }

    @Test
    fun `closed routes reject even a matching active callback`() {
        val started =
            CompareHerePhotoRequestPolicy.begin(
                CompareHerePhotoRequestState.initial()
            )

        assertFalse(
            CompareHerePhotoRequestPolicy.accepts(
                callbackRequestId = started.requestId,
                current = started,
                closed = true
            )
        )
    }

    @Test
    fun `generation wraps to positive one instead of overflowing`() {
        val previous =
            CompareHerePhotoRequestState(
                requestId = Long.MAX_VALUE,
                inFlight = false
            )

        val next = CompareHerePhotoRequestPolicy.begin(previous)

        assertEquals(1L, next.requestId)
        assertTrue(next.inFlight)
    }
}
