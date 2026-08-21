package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class IncrementalProductStoreTest {
    @Test
    fun bananasAreRemovedWhenEggsSessionBegins() {
        val sessions = SearchSessionManager()
        val store = IncrementalProductStore()

        val bananasContext = sessions.observeExplicitQuery(PACKAGE, "bananas", 1_000L).context
        store.beginContext(bananasContext)
        apply(store, bananasContext, card("banana", "Organic Bananas\n1 kg\n\$3.99"))
        assertTrue(store.snapshot().any { it.name.contains("Banana") })

        val eggsContext = sessions.observeExplicitQuery(PACKAGE, "eggs", 2_000L).context
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

    @Test
    fun identityIsLocaleIndependentAndDistinguishesQuantityAndOffer() {
        val originalLocale = Locale.getDefault()
        try {
            val eggs = ValueEngine.analyze("Large Eggs\n30 ct\n\$11.65", PACKAGE)!!
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val turkishKey = IncrementalProductStore.itemIdentity(eggs)
            Locale.setDefault(Locale.US)
            val englishKey = IncrementalProductStore.itemIdentity(eggs)
            assertEquals(englishKey, turkishKey)

            val alternateDisplay = ValueEngine.analyze("large   eggs\n30 count\n\$11.65", PACKAGE)!!
            assertEquals(englishKey, IncrementalProductStore.itemIdentity(alternateDisplay))

            val smaller = ValueEngine.analyze("Large Eggs\n12 ct\n\$11.65", PACKAGE)!!
            val differentOffer = ValueEngine.analyze("Large Eggs\n30 ct\n\$12.00", PACKAGE)!!
            assertFalse(englishKey == IncrementalProductStore.itemIdentity(smaller))
            assertFalse(englishKey == IncrementalProductStore.itemIdentity(differentOffer))
        } finally {
            Locale.setDefault(originalLocale)
        }
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
