package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchContextTest {
    private val packageName = "com.ubercab.eats"

    @Test
    fun bananasSessionSwitchingToEggsCreatesANewSession() {
        var now = 1_000L
        val sessions = SearchSessionManager { ++now }
        val bananas = sessions.observeExplicitQuery(packageName, "bananas")
        val eggs = sessions.observeExplicitQuery(packageName, "eggs")

        assertTrue(bananas.changed)
        assertTrue(eggs.changed)
        assertEquals("query changed", eggs.reason)
        assertNotEquals(bananas.context.sessionId, eggs.context.sessionId)
        assertEquals("eggs", eggs.context.query)
    }

    @Test
    fun missingPageSignalDoesNotResetAnExistingSearch() {
        val sessions = SearchSessionManager { 2_000L }
        val first = sessions.observe(observation(query = "eggs", page = "search"))
        val second = sessions.observe(observation(query = "eggs", page = null))

        assertFalse(second.changed)
        assertEquals(first.context.sessionId, second.context.sessionId)
        assertEquals(first.context.pageFingerprint, second.context.pageFingerprint)
    }

    @Test
    fun changingStoreInvalidatesTheSession() {
        var now = 3_000L
        val sessions = SearchSessionManager { ++now }
        val first = sessions.observe(observation(query = "eggs", store = "Market A", page = "search"))
        val second = sessions.observe(observation(query = "eggs", store = "Market B", page = "search"))

        assertTrue(second.changed)
        assertEquals("store changed", second.reason)
        assertNotEquals(first.context.sessionId, second.context.sessionId)
    }

    @Test
    fun clearingSearchInvalidatesTheQuerySession() {
        val sessions = SearchSessionManager { 4_000L }
        sessions.observeExplicitQuery(packageName, "eggs")
        val cleared = sessions.observeExplicitQuery(packageName, null)

        assertTrue(cleared.changed)
        assertEquals("query changed", cleared.reason)
        assertNull(cleared.context.query)
    }

    @Test
    fun deepProductHeadingCannotMasqueradeAsStoreOrPageContext() {
        val detected = SearchContextDetector.detect(
            packageName,
            listOf(
                ContextSignal(
                    text = "eggs",
                    viewId = "com.ubercab.eats:id/search_query",
                    className = "android.widget.EditText",
                    editable = true,
                    focused = true,
                    depth = 3
                ),
                ContextSignal(text = "Bananas", heading = true, depth = 8)
            ),
            observedAtMillis = 5_000L
        )

        assertEquals("eggs", detected.query)
        assertNull(detected.storeIdentity)
        assertEquals("search", detected.pageHint)
    }

    @Test
    fun newestTextChangeWinsAndVisibleResultCountIsNotPartOfQuery() {
        assertEquals("eggs", SearchContextDetector.queryFromEvent(listOf("bananas", "eggs"), "Search for items"))
        assertEquals("eggs", SearchContextDetector.normalizeQuery("Search results for eggs · 62 results"))
    }

    private fun observation(
        query: String?,
        store: String? = "Example Market",
        page: String?
    ) = ContextObservation(
        packageName = packageName,
        platform = "Uber Eats",
        query = query,
        storeIdentity = store,
        pageHint = page,
        observedAtMillis = 1L
    )
}
