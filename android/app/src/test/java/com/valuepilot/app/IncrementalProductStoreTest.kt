package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncrementalProductStoreTest {
    @Test
    fun bananasAreRemovedWhenEggsSessionBegins() {
        var now = 10_000L
        val sessions = SearchSessionManager { ++now }
        val store = IncrementalProductStore()

        val bananasContext = sessions.observeExplicitQuery(PACKAGE, "bananas").context
        store.beginContext(bananasContext)
        apply(store, bananasContext, card("banana", "Organic Bananas\n1 kg\n\$3.99"))
        assertTrue(store.snapshot().any { it.name.contains("Banana") })

        val eggsContext = sessions.observeExplicitQuery(PACKAGE, "eggs").context
        assertTrue(store.beginContext(eggsContext))
        apply(store, eggsContext, card("eggs", "Large Eggs\n30 ct\n\$11.65"))

        assertEquals(1, store.size())
        assertTrue(store.snapshot().all { it.name.contains("Egg") })
        assertFalse(store.snapshot().any { it.name.contains("Banana") })
    }

    @Test
    fun unchangedCardsAreIgnoredWithoutReparsing() {
        val context = context("session-one", "eggs")
        val store = IncrementalProductStore()
        val card = card("eggs-30", "Large Eggs\n30 ct\n\$11.65")
        store.beginContext(context)
        val firstReservation = store.reserveChanged(listOf(card))
        assertEquals(1, firstReservation.size)
        store.apply(firstReservation.map { parsed(it, context) })

        assertTrue(store.reserveChanged(listOf(card)).isEmpty())
        assertEquals(1L, store.stats().ignoredUnchangedCards)
        assertEquals(1, store.size())
    }

    @Test
    fun changedCardReplacesOnlyItsPreviousVersion() {
        val context = context("session-two", "eggs")
        val store = IncrementalProductStore()
        val original = card("eggs-30", "Large Eggs\n30 ct\n\$11.65")
        store.beginContext(context)
        apply(store, context, original)

        val changed = original.copy(
            contentFingerprint = StableIds.text("changed"),
            rawText = "Large Eggs\n30 ct\n\$10.99"
        )
        val result = apply(store, context, changed)

        assertTrue(result.changed)
        assertEquals(1, store.size())
        assertEquals(10.99, store.snapshot().single().offer.currentPrice, .001)
    }

    private fun apply(store: IncrementalProductStore, context: SearchContext, card: ProductCardSnapshot): StoreApplyResult {
        val reserved = store.reserveChanged(listOf(card))
        return store.apply(reserved.map { parsed(it, context) })
    }

    private fun parsed(card: ProductCardSnapshot, context: SearchContext): ParsedCard {
        val base = ValueEngine.analyze(card.rawText, PACKAGE)
        val item = base?.let { parsedItem ->
            parsedItem.copy(
                searchSessionId = context.sessionId,
                cardFingerprint = card.cardFingerprint,
                locator = card.toLocator(parsedItem, context)
            )
        }
        return ParsedCard(card.cardFingerprint, card.contentFingerprint, item)
    }

    private fun card(id: String, text: String) = ProductCardSnapshot(
        cardFingerprint = id,
        contentFingerprint = StableIds.text(text),
        rawText = text,
        locatorSeed = LocatorSeed(1, "product_card", null, ScreenBounds(0, 100, 1_000, 500), NodePath(listOf(1)), NodePath(listOf(1, 0))),
        capturedAtMillis = 1L
    )

    private fun context(session: String, query: String) = SearchContext(
        platform = "Uber Eats",
        packageName = PACKAGE,
        storeIdentity = "Example Market",
        query = query,
        queryFingerprint = SearchContextDetector.fingerprint(query),
        pageFingerprint = SearchContextDetector.fingerprint("$PACKAGE|Example Market|$query|search"),
        sessionId = session,
        startedAtMillis = 1L
    )

    companion object {
        private const val PACKAGE = "com.ubercab.eats"
    }
}
