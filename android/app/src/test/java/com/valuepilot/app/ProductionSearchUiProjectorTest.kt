package com.valuepilot.app

import com.valuepilot.core.AvailabilityState
import com.valuepilot.core.EvidenceBackedUnitValueBlockReason
import com.valuepilot.core.EvidenceFreshness
import com.valuepilot.core.Money
import com.valuepilot.core.ProductionBestValueBlockedPresentationItem
import com.valuepilot.core.ProductionBestValueComparisonKey
import com.valuepilot.core.ProductionBestValuePresentationEvidenceLink
import com.valuepilot.core.ProductionBestValuePresentationGroup
import com.valuepilot.core.ProductionBestValuePresentationItem
import com.valuepilot.core.ProductionBestValuePresentationSnapshot
import com.valuepilot.core.ProductionCurrentPriceEligibilityBlocker
import com.valuepilot.core.ProductionProductEvidenceKey
import com.valuepilot.core.ProductionProductKeyScope
import com.valuepilot.core.ProductionUnitValueEligibilityBlocker
import com.valuepilot.core.QuantityNormalization
import com.valuepilot.core.RateUnit
import com.valuepilot.core.UnitRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionSearchUiProjectorTest {

    @Test
    fun `projector preserves exact presentation without leaking scope urls or diagnostics`() {
        val best = item("best", rank = 1, order = 1, rateMicros = 80_000L, bestValue = true)
        val second = item("second", rank = 2, order = 2, rateMicros = 90_000L)
        val mass =
            item(
                candidateId = "mass",
                rank = 1,
                order = 1,
                rateMicros = 10_000_000L,
                rateUnit = RateUnit.KILOGRAM,
                quantity = QuantityNormalization.grams(500)
            )

        val snapshot =
            ProductionBestValuePresentationSnapshot(
                evaluatedAtEpochMillis = 2_000L,
                groups =
                    listOf(
                        ProductionBestValuePresentationGroup(
                            key = ProductionBestValueComparisonKey("CAD", RateUnit.ITEM),
                            meaningfulComparison = true,
                            items = listOf(best, second)
                        ),
                        ProductionBestValuePresentationGroup(
                            key = ProductionBestValueComparisonKey("CAD", RateUnit.KILOGRAM),
                            meaningfulComparison = false,
                            items = listOf(mass)
                        )
                    ),
                blockedItems = emptyList()
            )

        val projected = ProductionSearchUiProjector.project(snapshot)

        assertEquals(2_000L, projected.state.evaluatedAtEpochMillis)
        assertEquals(2, projected.state.groups.size)

        val itemGroup = projected.state.groups[0]
        assertEquals("CAD · Price per item", itemGroup.title)
        assertTrue(itemGroup.meaningfulComparison)
        assertEquals(listOf("best", "second"), itemGroup.rows.map { it.candidateId })
        assertEquals(listOf(1, 2), itemGroup.rows.map { it.valueRank })
        assertTrue(itemGroup.rows.first().bestValue)
        assertEquals("8.00 CAD", itemGroup.rows.first().priceText)
        assertEquals("Reference 10.00 CAD", itemGroup.rows.first().referencePriceText)
        assertEquals("100 items", itemGroup.rows.first().quantityText)
        assertEquals("0.08 CAD/item", itemGroup.rows.first().unitRateText)
        assertEquals("Offer country: CA", itemGroup.rows.first().offerScopeText)
        assertEquals("Provider best · Source best", itemGroup.rows.first().sourceSummary)
        assertEquals("In stock", itemGroup.rows.first().availabilityText)
        assertEquals("Fresh price evidence", itemGroup.rows.first().freshnessText)

        val uiReadyText =
            itemGroup.rows.first().let { row ->
                listOfNotNull(
                    row.name,
                    row.priceText,
                    row.referencePriceText,
                    row.quantityText,
                    row.unitRateText,
                    row.offerScopeText,
                    row.sourceSummary,
                    row.availabilityText,
                    row.freshnessText
                ).joinToString("|")
            }
        assertFalse(uiReadyText.contains("merchant-best"))
        assertFalse(uiReadyText.contains("location-best"))
        assertFalse(uiReadyText.contains("ONLINE"))
        assertFalse(uiReadyText.contains("example.test"))

        val uiFieldNames = ProductionSearchRowUiState::class.java.declaredFields.map { it.name }.toSet()
        assertFalse("productUrl" in uiFieldNames)
        assertFalse("imageUrl" in uiFieldNames)
        assertFalse("merchantKey" in uiFieldNames)
        assertFalse("locationKey" in uiFieldNames)
        assertFalse("commerceChannelKey" in uiFieldNames)

        val exactBest = projected.rankedByCandidateId.getValue("best")
        assertSame(best, exactBest)
        assertEquals("merchant-best", exactBest.merchantKey)
        assertEquals("location-best", exactBest.locationKey)
        assertEquals("ONLINE", exactBest.commerceChannelKey)
        assertEquals("https://example.test/product/best", exactBest.productUrl)
        assertEquals("https://example.test/image/best.jpg", exactBest.imageUrl)

        val massGroup = projected.state.groups[1]
        assertEquals("CAD · Price per kilogram", massGroup.title)
        assertFalse(massGroup.meaningfulComparison)
        assertFalse(massGroup.rows.single().bestValue)
        assertEquals("500 g", massGroup.rows.single().quantityText)
        assertEquals("10 CAD/kg", massGroup.rows.single().unitRateText)

        assertSame(mass, projected.rankedByCandidateId.getValue("mass"))
        assertTrue(projected.blockedByCandidateId.isEmpty())
    }

    @Test
    fun `exact ties remain co best and candidate id does not change value rank`() {
        val alpha = item("alpha", rank = 1, order = 1, rateMicros = 80_000L, bestValue = true)
        val zeta = item("zeta", rank = 1, order = 2, rateMicros = 80_000L, bestValue = true)
        val snapshot =
            ProductionBestValuePresentationSnapshot(
                evaluatedAtEpochMillis = 2_000L,
                groups =
                    listOf(
                        ProductionBestValuePresentationGroup(
                            key = ProductionBestValueComparisonKey("CAD", RateUnit.ITEM),
                            meaningfulComparison = true,
                            items = listOf(alpha, zeta)
                        )
                    ),
                blockedItems = emptyList()
            )

        val rows = ProductionSearchUiProjector.project(snapshot).state.groups.single().rows

        assertEquals(listOf(1, 1), rows.map { it.valueRank })
        assertEquals(listOf(true, true), rows.map { it.bestValue })
    }

    @Test
    fun `blocked candidate exposes neutral notice while exact diagnostics remain in lookup`() {
        val blocked =
            ProductionBestValueBlockedPresentationItem(
                candidateId = "blocked",
                unitValueBlockers = setOf(ProductionUnitValueEligibilityBlocker.PRICE_STAGE_BLOCKED),
                priceBlockers = setOf(ProductionCurrentPriceEligibilityBlocker.CANDIDATE_NOT_ACCEPTANCE_RANKABLE),
                unitValuePolicyBlockReasons = setOf(EvidenceBackedUnitValueBlockReason.WEAK_QUANTITY_AUTHORITY)
            )
        val snapshot =
            ProductionBestValuePresentationSnapshot(
                evaluatedAtEpochMillis = 2_000L,
                groups = emptyList(),
                blockedItems = listOf(blocked)
            )

        val projected = ProductionSearchUiProjector.project(snapshot)

        assertTrue(projected.state.groups.isEmpty())
        val row = projected.state.blocked.single()
        assertEquals("blocked", row.candidateId)
        assertEquals("Reference only — not eligible for Best Value", row.notice)
        val blockedUiFieldNames = ProductionSearchBlockedUiState::class.java.declaredFields.map { it.name }.toSet()
        assertFalse("reasonCodes" in blockedUiFieldNames)
        assertFalse(row.notice.contains("PRICE_STAGE_BLOCKED"))
        assertFalse(row.notice.contains("CANDIDATE_NOT_ACCEPTANCE_RANKABLE"))
        assertFalse(row.notice.contains("WEAK_QUANTITY_AUTHORITY"))

        val exactBlocked = projected.blockedByCandidateId.getValue("blocked")
        assertSame(blocked, exactBlocked)
        assertTrue(ProductionUnitValueEligibilityBlocker.PRICE_STAGE_BLOCKED in exactBlocked.unitValueBlockers)
        assertTrue(
            ProductionCurrentPriceEligibilityBlocker.CANDIDATE_NOT_ACCEPTANCE_RANKABLE in
                exactBlocked.priceBlockers
        )
        assertTrue(
            EvidenceBackedUnitValueBlockReason.WEAK_QUANTITY_AUTHORITY in
                exactBlocked.unitValuePolicyBlockReasons
        )
        assertTrue(projected.rankedByCandidateId.isEmpty())
    }

    @Test
    fun `formatting preserves integers beyond double exact range`() {
        val money = Money(9_007_199_254_740_993L, "CAD")
        val rate = UnitRate("CAD", 9_007_199_254_740_993L, RateUnit.ITEM)

        assertEquals("90071992547409.93 CAD", ProductionSearchUiProjector.formatMoney(money))
        assertEquals("9007199254.740993 CAD/item", ProductionSearchUiProjector.formatRate(rate))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate ranked candidate id across comparison groups is rejected`() {
        val itemA = item("duplicate", rank = 1, order = 1, rateMicros = 80_000L)
        val itemB =
            item(
                candidateId = "duplicate",
                rank = 1,
                order = 1,
                rateMicros = 10_000_000L,
                rateUnit = RateUnit.KILOGRAM,
                quantity = QuantityNormalization.grams(500)
            )
        val snapshot =
            ProductionBestValuePresentationSnapshot(
                evaluatedAtEpochMillis = 2_000L,
                groups =
                    listOf(
                        ProductionBestValuePresentationGroup(
                            key = ProductionBestValueComparisonKey("CAD", RateUnit.ITEM),
                            meaningfulComparison = false,
                            items = listOf(itemA)
                        ),
                        ProductionBestValuePresentationGroup(
                            key = ProductionBestValueComparisonKey("CAD", RateUnit.KILOGRAM),
                            meaningfulComparison = false,
                            items = listOf(itemB)
                        )
                    ),
                blockedItems = emptyList()
            )

        ProductionSearchUiProjector.project(snapshot)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `candidate cannot be both ranked and blocked`() {
        val ranked = item("same", rank = 1, order = 1, rateMicros = 80_000L)
        val blocked =
            ProductionBestValueBlockedPresentationItem(
                candidateId = "same",
                unitValueBlockers = setOf(ProductionUnitValueEligibilityBlocker.PRICE_STAGE_BLOCKED),
                priceBlockers = emptySet(),
                unitValuePolicyBlockReasons = emptySet()
            )
        val snapshot =
            ProductionBestValuePresentationSnapshot(
                evaluatedAtEpochMillis = 2_000L,
                groups =
                    listOf(
                        ProductionBestValuePresentationGroup(
                            key = ProductionBestValueComparisonKey("CAD", RateUnit.ITEM),
                            meaningfulComparison = false,
                            items = listOf(ranked)
                        )
                    ),
                blockedItems = listOf(blocked)
            )

        ProductionSearchUiProjector.project(snapshot)
    }

    private fun item(
        candidateId: String,
        rank: Int,
        order: Int,
        rateMicros: Long,
        bestValue: Boolean = false,
        rateUnit: RateUnit = RateUnit.ITEM,
        quantity: com.valuepilot.core.NormalizedQuantity = QuantityNormalization.count(100)
    ): ProductionBestValuePresentationItem =
        ProductionBestValuePresentationItem(
            candidateId = candidateId,
            productKey =
                ProductionProductEvidenceKey(
                    value = "gtin:0036000291452-$candidateId",
                    scope = ProductionProductKeyScope.CROSS_SOURCE_GTIN
                ),
            productName = "Product $candidateId",
            providerDisplayName = "Provider $candidateId",
            sourceDisplayName = "Source $candidateId",
            merchantKey = "merchant-$candidateId",
            locationKey = "location-$candidateId",
            commerceChannelKey = "ONLINE",
            offerCountryCode = "CA",
            currentPrice = Money(800L, "CAD"),
            referencePrice = Money(1_000L, "CAD"),
            quantity = quantity,
            unitRate = UnitRate("CAD", rateMicros, rateUnit),
            availabilityState = AvailabilityState.IN_STOCK,
            currentFreshness = EvidenceFreshness.FRESH,
            priceObservedAtEpochMillis = 1_000L,
            valueRank = rank,
            deterministicOrder = order,
            bestValue = bestValue,
            productUrl = "https://example.test/product/$candidateId",
            imageUrl = "https://example.test/image/$candidateId.jpg",
            evidenceLink =
                ProductionBestValuePresentationEvidenceLink(
                    priceProviderId = com.valuepilot.core.EvidenceProviderId("provider-$candidateId"),
                    priceSourceId = com.valuepilot.core.ShoppingSourceId("source-$candidateId"),
                    priceDatasetNamespaceId = "dataset-$candidateId",
                    priceSnapshotId = "snapshot-$candidateId",
                    priceClaimId = "price-claim-$candidateId",
                    quantityDatasetNamespaceId = "quantity-$candidateId",
                    quantityClaimId = "quantity-claim-$candidateId",
                    quantityEvidenceId = "quantity-evidence-$candidateId",
                    lifecycleRevision = 1L,
                    dispositionRevision = 1L
                )
        )
}
