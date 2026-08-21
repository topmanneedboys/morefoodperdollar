package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemMatcherTest {
    @Test
    fun exactSameNameAndSizeCandidateIsSelected() {
        val candidates = listOf(
            candidate(name = "Large Eggs", price = 5.49, count = 12.0, fingerprint = "eggs-12"),
            candidate(name = "Large Eggs", price = 11.65, count = 30.0, fingerprint = "eggs-30")
        )

        val decision = ItemMatcher.choose(locator(), candidates)

        assertEquals(1, decision.candidateIndex)
        assertFalse(decision.ambiguous)
        assertTrue(decision.confidence >= .82)
    }

    @Test
    fun wrongOrChangedCardIsNeverSelected() {
        val wrongFingerprint = candidate(fingerprint = "different-card")
        val changedPrice = candidate(price = 12.65)
        val changedSize = candidate(count = 12.0)

        assertNull(ItemMatcher.choose(locator(), listOf(wrongFingerprint)).candidateIndex)
        assertNull(ItemMatcher.choose(locator(), listOf(changedPrice)).candidateIndex)
        assertNull(ItemMatcher.choose(locator(), listOf(changedSize)).candidateIndex)
    }

    @Test
    fun ambiguousDuplicateCardsAreNotSelected() {
        val decision = ItemMatcher.choose(locator(), listOf(candidate(), candidate()))

        assertNull(decision.candidateIndex)
        assertTrue(decision.ambiguous)
    }

    @Test
    fun staleSearchSessionCannotOpenAResult() {
        val locator = locator()
        val current = context(session = "new-session")

        assertFalse(ItemMatcher.sessionIsCurrent(locator, current, PACKAGE))
        assertFalse(ItemMatcher.sessionIsCurrent(locator, context(), "another.package"))
        assertTrue(ItemMatcher.sessionIsCurrent(locator, context(), PACKAGE))
    }

    @Test
    fun offScreenResultCanBeReacquiredOnlyAfterExactCardAppears() {
        assertNull(ItemMatcher.choose(locator(), emptyList()).candidateIndex)
        assertEquals(0, ItemMatcher.choose(locator(), listOf(candidate())).candidateIndex)
    }

    private fun locator() = ItemLocator(
        packageName = PACKAGE,
        searchSessionId = "session",
        windowId = 3,
        canonicalName = "large eggs",
        currentPrice = 11.65,
        memberPrice = 9.81,
        quantityKind = Quantity.Kind.COUNT,
        quantityAmount = 30.0,
        quantityDisplay = "30 count",
        viewId = "product_card",
        contentDescription = null,
        originalBounds = ScreenBounds(0, 100, 1_000, 500),
        cardFingerprint = "eggs-30"
    )

    private fun candidate(
        name: String = "Large Eggs",
        price: Double = 11.65,
        memberPrice: Double? = 9.81,
        count: Double = 30.0,
        fingerprint: String = "eggs-30"
    ) = ItemMatchCandidate(
        name = name,
        currentPrice = price,
        memberPrice = memberPrice,
        quantityKind = Quantity.Kind.COUNT,
        quantityAmount = count,
        viewId = "product_card",
        cardFingerprint = fingerprint,
        clickPath = NodePath(listOf(2, 1))
    )

    private fun context(session: String = "session") = SearchContext(
        platform = "Uber Eats",
        packageName = PACKAGE,
        storeIdentity = "Example Market",
        query = "eggs",
        queryFingerprint = SearchContextDetector.fingerprint("eggs"),
        pageFingerprint = SearchContextDetector.fingerprint("search"),
        sessionId = session,
        startedAtMillis = 1L
    )

    companion object {
        private const val PACKAGE = "com.ubercab.eats"
    }
}
