package com.valuepilot.app

import com.valuepilot.core.EvidenceFreshnessPolicy
import com.valuepilot.core.ImportedDiscountRelationship
import com.valuepilot.core.ProductionActivationProfiles
import com.valuepilot.core.ProductionAuthorizationGate
import com.valuepilot.core.ProductionOfferCandidateBlocker
import com.valuepilot.core.ProductionOfferCandidateEvaluator
import com.valuepilot.core.ProductionPriceRelationshipRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class JamiesonRakutenPublishedPriceFieldRolesTest {

    @Test
    fun `documented sale price resolves as current discounted field with retail reference`() {
        val staged = staged(row(sale = "14.99", retail = "19.99"))
        val result = JamiesonRakutenPublishedPriceFieldRoleResolver.resolve(staged)
        val resolved = result as JamiesonRakutenPublishedPriceRoleResolution.Resolved

        assertEquals("Sale Price", resolved.roles.currentPriceFieldName)
        assertEquals("Retail Price", resolved.roles.referencePriceFieldName)
        assertEquals(
            ProductionPriceRelationshipRule.CURRENT_MUST_NOT_EXCEED_REFERENCE,
            resolved.roles.relationshipRule
        )
        assertEquals(
            ImportedDiscountRelationship.DISCOUNTED_BELOW_REFERENCE,
            staged.priceAssessment.relationshipAssessment?.relationship
        )
    }

    @Test
    fun `absent optional sale price resolves retail only when no separate discount fields exist`() {
        val staged = staged(row(sale = "", retail = "19.99"))
        val result = JamiesonRakutenPublishedPriceFieldRoleResolver.resolve(staged)
        val resolved = result as JamiesonRakutenPublishedPriceRoleResolution.Resolved

        assertEquals("Retail Price", resolved.roles.currentPriceFieldName)
        assertNull(resolved.roles.referencePriceFieldName)
        assertEquals(ProductionPriceRelationshipRule.NONE, resolved.roles.relationshipRule)
    }

    @Test
    fun `sale absent with discount or discount type fails closed instead of ignoring adjustment`() {
        listOf(
            row(sale = "", retail = "19.99", discount = "5.00"),
            row(sale = "", retail = "19.99", discountType = "amount"),
            row(
                sale = "",
                retail = "19.99",
                discount = "25",
                discountType = "percentage"
            )
        ).forEach { sourceRow ->
            val staged = staged(sourceRow)
            val result = JamiesonRakutenPublishedPriceFieldRoleResolver.resolve(staged)
            val blocked = result as JamiesonRakutenPublishedPriceRoleResolution.Blocked

            assertEquals(
                JamiesonRakutenPublishedPriceRoleBlocker
                    .DISCOUNT_FIELDS_REQUIRE_SEPARATE_RESOLUTION,
                blocked.blocker
            )
        }
    }

    @Test
    fun `explicit sale price remains authoritative field even when discount metadata is also present`() {
        val staged =
            staged(
                row(
                    sale = "14.99",
                    retail = "19.99",
                    discount = "5.00",
                    discountType = "amount"
                )
            )
        val result = JamiesonRakutenPublishedPriceFieldRoleResolver.resolve(staged)
        val resolved = result as JamiesonRakutenPublishedPriceRoleResolution.Resolved

        assertEquals("Sale Price", resolved.roles.currentPriceFieldName)
        assertEquals("Retail Price", resolved.roles.referencePriceFieldName)
    }

    @Test
    fun `equal sale and retail keeps documented sale role without inventing a discount`() {
        val staged = staged(row(sale = "19.99", retail = "19.99"))
        val result = JamiesonRakutenPublishedPriceFieldRoleResolver.resolve(staged)
        val resolved = result as JamiesonRakutenPublishedPriceRoleResolution.Resolved

        assertEquals("Sale Price", resolved.roles.currentPriceFieldName)
        assertEquals("Retail Price", resolved.roles.referencePriceFieldName)
        assertEquals(
            ImportedDiscountRelationship.EQUAL,
            staged.priceAssessment.relationshipAssessment?.relationship
        )
    }

    @Test
    fun `sale above retail fails closed instead of reversing documented roles`() {
        val staged = staged(row(sale = "24.99", retail = "19.99"))
        val result = JamiesonRakutenPublishedPriceFieldRoleResolver.resolve(staged)
        val blocked = result as JamiesonRakutenPublishedPriceRoleResolution.Blocked

        assertEquals(
            JamiesonRakutenPublishedPriceRoleBlocker.DISCOUNTED_PRICE_ABOVE_RETAIL_REFERENCE,
            blocked.blocker
        )
        assertEquals(
            ImportedDiscountRelationship.DISCOUNTED_ABOVE_REFERENCE_CONFLICT,
            staged.priceAssessment.relationshipAssessment?.relationship
        )
    }

    @Test
    fun `resolved price roles still cannot bypass unresolved recency or per offer freshness`() {
        val staged = staged(row(sale = "14.99", retail = "19.99"))
        val resolution =
            JamiesonRakutenPublishedPriceFieldRoleResolver.resolve(staged)
                as JamiesonRakutenPublishedPriceRoleResolution.Resolved

        val result =
            ProductionOfferCandidateEvaluator.evaluate(
                record = staged.importRecord,
                priceRoles = resolution.roles,
                authorizationAssessment =
                    JamiesonProductCatalogProductionContract.partnerAuthorizationAssessment(),
                activationProfile = ProductionActivationProfiles.CONSUMER_MOBILE_CATALOG,
                geography = JamiesonProductCatalogProductionContract.documentedGeography(),
                targetCountryCode = "CA",
                evaluatedAtEpochMillis = 1_788_000_000_000L,
                offerFreshnessPolicy =
                    EvidenceFreshnessPolicy(
                        freshForMillis = 86_400_000L,
                        staleAfterMillis = 604_800_000L,
                        futureToleranceMillis = 60_000L
                    )
            )

        assertFalse(result.accepted)
        assertNull(result.candidate)
        assertTrue(ProductionOfferCandidateBlocker.PRODUCTION_AUTHORIZATION_BLOCKED in result.blockers)
        assertTrue(ProductionOfferCandidateBlocker.OFFER_TIMESTAMP_MISSING in result.blockers)
        assertEquals(
            setOf(
                ProductionAuthorizationGate.DATASET_RECENCY_POLICY_DEFINED,
                ProductionAuthorizationGate.OFFER_FRESHNESS_POLICY_DEFINED
            ),
            result.authorizationDecision?.unknownGates
        )
        val satisfiedGates = result.authorizationDecision?.satisfiedGates ?: emptySet()
        assertTrue(ProductionAuthorizationGate.PRICE_SEMANTICS_VALIDATED in satisfiedGates)
    }

    @Test
    fun `price role resolver owns no freshness availability discount arithmetic offer ranking network clock or persistence authority`() {
        val source = source("JamiesonRakutenPublishedPriceFieldRoles.kt").readText()

        listOf(
            "ProductionPriceFieldRoles(",
            "CURRENT_MUST_NOT_EXCEED_REFERENCE",
            "SALE_PRICE_FIELD_NAME",
            "RETAIL_PRICE_FIELD_NAME",
            "DISCOUNT_FIELDS_REQUIRE_SEPARATE_RESOLUTION"
        ).forEach { required ->
            assertTrue("missing price-role boundary: $required", source.contains(required))
        }

        listOf(
            "priceObservedAtEpochMillis",
            "datasetGeneratedAtEpochMillis",
            "EvidenceFreshness",
            "AvailabilityEvidence(",
            "Money.parse(",
            "discount.toDouble",
            "Offer(",
            "ShoppingEvidence(",
            "ProductionOfferCandidateEvaluator",
            "System.currentTimeMillis",
            "Instant.now",
            "HttpURLConnection",
            "OkHttp",
            "Retrofit",
            "android.permission.INTERNET",
            "SharedPreferences",
            "AtomicFile",
            "WorkManager",
            "Notification",
            "MainActivity",
            "StapleWatch"
        ).forEach { forbidden ->
            assertFalse("unexpected authority or side effect: $forbidden", source.contains(forbidden))
        }
    }

    private fun staged(
        row: JamiesonRakutenPublishedCatalogRow
    ): JamiesonRakutenPublishedCatalogStagedRecord {
        val result = JamiesonRakutenPublishedCatalogStagingAdapter.stage(row)
        assertTrue(result is JamiesonRakutenPublishedCatalogStagingResult.Staged)
        return (result as JamiesonRakutenPublishedCatalogStagingResult.Staged).value
    }

    private fun row(
        sale: String,
        retail: String,
        discount: String = "",
        discountType: String = ""
    ): JamiesonRakutenPublishedCatalogRow =
        JamiesonRakutenPublishedCatalogRow.decode(
            MutableList(JamiesonRakutenPublishedCatalogRow.PRIMARY_FIELD_COUNT) { "" }
                .also { fields ->
                    fields[JamiesonRakutenPublishedCatalogField.PRODUCT_ID.index] = "product-1"
                    fields[JamiesonRakutenPublishedCatalogField.PRODUCT_NAME.index] = "Jamieson Vitamin D3"
                    fields[JamiesonRakutenPublishedCatalogField.SKU_NUMBER.index] = "JAM-D3-100"
                    fields[JamiesonRakutenPublishedCatalogField.PRIMARY_CATEGORY.index] = "Health"
                    fields[JamiesonRakutenPublishedCatalogField.PRODUCT_URL.index] = "https://example.invalid/product"
                    fields[JamiesonRakutenPublishedCatalogField.PRODUCT_IMAGE_URL.index] = "https://example.invalid/image.jpg"
                    fields[JamiesonRakutenPublishedCatalogField.DISCOUNT.index] = discount
                    fields[JamiesonRakutenPublishedCatalogField.DISCOUNT_TYPE.index] = discountType
                    fields[JamiesonRakutenPublishedCatalogField.SALE_PRICE.index] = sale
                    fields[JamiesonRakutenPublishedCatalogField.RETAIL_PRICE.index] = retail
                    fields[JamiesonRakutenPublishedCatalogField.UNIVERSAL_PRODUCT_CODE.index] = "4006381333931"
                    fields[JamiesonRakutenPublishedCatalogField.CURRENCY.index] = "CAD"
                }
        )

    private fun source(fileName: String): File =
        sequenceOf(
            File("src/main/java/com/valuepilot/app/$fileName"),
            File("android/app/src/main/java/com/valuepilot/app/$fileName")
        ).firstOrNull(File::isFile)
            ?: error("Source file not found: $fileName")
}
