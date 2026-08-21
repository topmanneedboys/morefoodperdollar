package com.valuepilot.app

import java.util.Locale
import kotlin.system.measureNanoTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class PerformanceFixtureTest {
    companion object {
        private const val PACKAGE = "com.walmart.android"

        @JvmStatic
        @BeforeClass
        fun loadLocalModel() = TestModelLoader.load()
    }

    @Test
    fun productCollectionsFrom20Through500StayWithinBackgroundBudgets() {
        val sizes = listOf(20, 60, 100, 160, 250, 500)
        for (size in sizes) {
            val context = context(size)
            val cards = (0 until size).map(::card)
            val store = IncrementalProductStore(maxItems = 1_000)
            store.beginContext(context)

            val reserved = store.reserveChanged(cards)
            lateinit var parsed: List<ParsedCard>
            val parseMillis = measureNanoTime {
                parsed = reserved.map { snapshot ->
                    val base = ValueEngine.analyze(snapshot.rawText, PACKAGE)
                    ParsedCard(
                        snapshot.cardFingerprint,
                        snapshot.contentFingerprint,
                        base?.let { parsedItem ->
                            parsedItem.copy(
                                searchSessionId = context.sessionId,
                                cardFingerprint = snapshot.cardFingerprint,
                                locator = snapshot.toLocator(parsedItem, context)
                            )
                        }
                    )
                }
            }.millis()
            val applyMillis = measureNanoTime { store.apply(parsed) }.millis()
            lateinit var ranked: List<RankedItem>
            val rankMillis = measureNanoTime {
                ranked = ValueEngine.rank(
                    ValueEngine.filterItems(store.snapshot(), foodOnly = true, query = "eggs"),
                    RankMode.UNIT
                )
            }.millis()
            val unchangedMillis = measureNanoTime { store.reserveChanged(cards) }.millis()

            println(
                "VALUEPIL_PERF products=$size parse_ms=${format(parseMillis)} " +
                    "apply_ms=${format(applyMillis)} rank_ms=${format(rankMillis)} unchanged_ms=${format(unchangedMillis)}"
            )
            assertEquals(size, store.size())
            assertEquals(size, ranked.size)
            assertTrue("$size-product parse exceeded background budget: $parseMillis ms", parseMillis < 8_000.0)
            assertTrue("$size-product apply exceeded budget: $applyMillis ms", applyMillis < 1_500.0)
            assertTrue("$size-product rank exceeded budget: $rankMillis ms", rankMillis < 1_500.0)
            assertTrue("$size unchanged cards took too long: $unchangedMillis ms", unchangedMillis < 500.0)
        }
    }

    private fun card(index: Int): ProductCardSnapshot {
        val count = 6 + index % 48
        val price = 2.99 + index * .03
        val text = "Farm Eggs Variety $index\n$count ct\n${'$'}${String.format(Locale.US, "%.2f", price)}\nIn stock"
        return ProductCardSnapshot(
            cardFingerprint = "eggs-$index-$count",
            contentFingerprint = StableIds.text(text),
            rawText = text,
            locatorSeed = LocatorSeed(
                windowId = 1,
                viewId = "product_card",
                contentDescription = null,
                bounds = ScreenBounds(0, 100, 1_000, 500),
                cardPath = NodePath(listOf(index % 12)),
                clickPath = NodePath(listOf(index % 12, 0))
            ),
            capturedAtMillis = 1L
        )
    }

    private fun context(size: Int) = SearchContext(
        platform = "Walmart",
        packageName = PACKAGE,
        storeIdentity = "Example Walmart",
        query = "eggs",
        queryFingerprint = SearchContextDetector.fingerprint("eggs"),
        pageFingerprint = SearchContextDetector.fingerprint("$PACKAGE|eggs|search"),
        sessionId = "performance-$size",
        startedAtMillis = 1L
    )

    private fun Long.millis(): Double = this / 1_000_000.0
    private fun format(value: Double): String = String.format(Locale.US, "%.3f", value)
}
