package com.valuepilot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceIsolatedEvidenceIndexTest {

    @Test
    fun independentDatasetsStaySeparateWhileProductLookupCanSeeBoth() {
        val index = SourceIsolatedEvidenceIndex()
        val openPrices = openPricesNamespace()
        val off = openFoodFactsNamespace()

        assertEquals(
            EvidenceIndexInsertResult.ADDED,
            index.insert(openPrices, observedPrice("price-1"))
        )
        assertEquals(
            EvidenceIndexInsertResult.ADDED,
            index.insert(off, packageQuantity("quantity-1"))
        )

        assertEquals(1, index.claimsInNamespace(openPrices.id).size)
        assertEquals(1, index.claimsInNamespace(off.id).size)

        val combined = index.claimsForProduct(PRODUCT_KEY)
        assertEquals(2, combined.size)
        assertEquals(
            listOf("open-food-facts", "open-prices"),
            combined.map { it.namespace.id }
        )
        assertEquals(
            setOf(EvidenceClaimDomain.OBSERVED_PRICE, EvidenceClaimDomain.PACKAGE_QUANTITY),
            combined.map { it.claim.domain }.toSet()
        )
    }

    @Test
    fun sameClaimIdMayExistInDifferentNamespacesWithoutCrossSourceOverwrite() {
        val index = SourceIsolatedEvidenceIndex()
        val sameIdPrice = observedPrice("shared-id")
        val sameIdQuantity = packageQuantity("shared-id")

        assertEquals(EvidenceIndexInsertResult.ADDED, index.insert(openPricesNamespace(), sameIdPrice))
        assertEquals(EvidenceIndexInsertResult.ADDED, index.insert(openFoodFactsNamespace(), sameIdQuantity))
        assertEquals(2, index.size())
    }

    @Test
    fun sameNamespaceClaimIdCollisionIsRejectedAndOriginalIsPreserved() {
        val index = SourceIsolatedEvidenceIndex()
        val namespace = openPricesNamespace()
        val original = observedPrice("price-1")
        val collision = original.copy(
            valueFingerprint = EvidenceFingerprints.money(Money.parse("5.99", "CAD"))
        )

        assertEquals(EvidenceIndexInsertResult.ADDED, index.insert(namespace, original))
        assertEquals(EvidenceIndexInsertResult.DUPLICATE, index.insert(namespace, original))
        assertEquals(
            EvidenceIndexInsertResult.REJECTED_CLAIM_ID_COLLISION,
            index.insert(namespace, collision)
        )

        assertEquals(listOf(original), index.claimsInNamespace(namespace.id))
    }

    @Test
    fun productLookupCanBeBoundedToOneFactDomain() {
        val index = SourceIsolatedEvidenceIndex()
        index.insert(openPricesNamespace(), observedPrice("price-1"))
        index.insert(openFoodFactsNamespace(), packageQuantity("quantity-1"))

        val prices = index.claimsForProduct(
            productKey = PRODUCT_KEY,
            domain = EvidenceClaimDomain.OBSERVED_PRICE
        )

        assertEquals(1, prices.size)
        assertEquals("open-prices", prices.single().namespace.id)
    }

    @Test
    fun indexCanResolveProductFactsWithoutFlatteningProviderNamespaces() {
        val index = SourceIsolatedEvidenceIndex()
        index.insert(openPricesNamespace(), observedPrice("price-1"))
        index.insert(openFoodFactsNamespace(), packageQuantity("quantity-1"))

        val resolutions = index.resolveFactsForProduct(PRODUCT_KEY)

        assertEquals(2, resolutions.size)
        assertTrue(resolutions.all { it.status == EvidenceFactResolutionStatus.RESOLVED })
        assertEquals(
            setOf(EvidenceClaimDomain.OBSERVED_PRICE, EvidenceClaimDomain.PACKAGE_QUANTITY),
            resolutions.map { it.key.domain }.toSet()
        )
        assertEquals(
            setOf("open-prices", "open-food-facts"),
            resolutions.flatMap { result ->
                result.supportingClaims.map { it.namespace.id }
            }.toSet()
        )
    }

    @Test
    fun removingOneDatasetDoesNotTouchAnotherDataset() {
        val index = SourceIsolatedEvidenceIndex()
        index.insert(openPricesNamespace(), observedPrice("price-1"))
        index.insert(openFoodFactsNamespace(), packageQuantity("quantity-1"))

        assertEquals(1, index.removeNamespace("open-food-facts"))
        assertTrue(index.claimsInNamespace("open-food-facts").isEmpty())
        assertEquals(1, index.size())
        assertEquals(
            listOf("open-prices"),
            index.registeredNamespaces().map { it.id }
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun namespaceMetadataCannotSilentlyChangeForExistingId() {
        val index = SourceIsolatedEvidenceIndex()
        index.register(openFoodFactsNamespace())
        index.register(
            openFoodFactsNamespace().copy(
                storageBoundary = EvidenceStorageBoundary.PROPRIETARY_RESTRICTED
            )
        )
    }

    private fun observedPrice(id: String) = EvidenceClaim(
        claimId = id,
        domain = EvidenceClaimDomain.OBSERVED_PRICE,
        valueFingerprint = EvidenceFingerprints.money(Money.parse("4.99", "CAD")),
        authority = EvidenceAuthorityClass.PROOF_BACKED_DIRECT_OBSERVATION,
        scope = EvidenceClaimScope(
            productKey = PRODUCT_KEY,
            locationKey = "open-prices-location-1",
            commerceChannelKey = "PHYSICAL_STORE",
            currencyCode = "CAD"
        ),
        observedAtEpochMillis = 1_800_000_000_000L
    )

    private fun packageQuantity(id: String) = EvidenceClaim(
        claimId = id,
        domain = EvidenceClaimDomain.PACKAGE_QUANTITY,
        valueFingerprint = EvidenceFingerprints.quantity(
            QuantityNormalization.grams(1000)
        ),
        authority = EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA,
        scope = EvidenceClaimScope(productKey = PRODUCT_KEY),
        observedAtEpochMillis = 1_799_000_000_000L
    )

    private fun openPricesNamespace() = EvidenceDatasetNamespace(
        id = "open-prices",
        displayName = "Open Prices",
        licenseId = "ODbL-1.0",
        storageBoundary = EvidenceStorageBoundary.OPEN_SHARE_ALIKE
    )

    private fun openFoodFactsNamespace() = EvidenceDatasetNamespace(
        id = "open-food-facts",
        displayName = "Open Food Facts",
        licenseId = "ODbL-1.0",
        storageBoundary = EvidenceStorageBoundary.OPEN_SHARE_ALIKE
    )

    companion object {
        private const val PRODUCT_KEY = "gtin:036000291452"
    }
}
