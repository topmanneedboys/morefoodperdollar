package com.valuepilot.app

import com.valuepilot.core.EvidenceStorageBoundary
import com.valuepilot.core.ImportedOfferCountryBasis
import com.valuepilot.core.ProductionActivationDecisionStatus
import com.valuepilot.core.ProductionActivationProfiles
import com.valuepilot.core.ProductionAuthorizationEvaluator
import com.valuepilot.core.ProductionAuthorizationGate
import com.valuepilot.core.ProductionAuthorizationState
import com.valuepilot.core.ProductionDatasetDispositionState
import com.valuepilot.core.ProviderDatasetCountryMatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class JamiesonProductCatalogProductionContractTest {

    @Test
    fun `written terms stay scoped to one proprietary jamieson dataset`() {
        val contract = JamiesonProductCatalogProductionContract

        assertEquals("rakuten-advertising", contract.providerId.value)
        assertEquals(
            "rakuten.jamieson-product-catalog",
            contract.datasetNamespace.id
        )
        assertEquals(
            EvidenceStorageBoundary.PROPRIETARY_RESTRICTED,
            contract.datasetNamespace.storageBoundary
        )
        assertEquals("CA", contract.EXPECTED_COUNTRY_CODE)
        assertEquals("CAD", contract.EXPECTED_CURRENCY_CODE)
        assertEquals(60L, contract.TERMINATION_DELETION_DAYS)
        assertEquals(
            setOf(
                JamiesonProductCatalogApprovedUse.MOBILE_DISPLAY,
                JamiesonProductCatalogApprovedUse.SEARCH_AND_COMPARISON,
                JamiesonProductCatalogApprovedUse.CACHE,
                JamiesonProductCatalogApprovedUse.INDEX,
                JamiesonProductCatalogApprovedUse.PRODUCT_OR_AFFILIATE_LINKS
            ),
            contract.approvedUses
        )
    }

    @Test
    fun `partner rights clear rights gates but factual current-price gates remain unknown`() {
        val assessment =
            JamiesonProductCatalogProductionContract.partnerAuthorizationAssessment()
        val decision =
            ProductionAuthorizationEvaluator.evaluate(
                assessment = assessment,
                profile = ProductionActivationProfiles.CONSUMER_MOBILE_CATALOG
            )

        assertFalse(decision.authorized)
        assertEquals(ProductionActivationDecisionStatus.BLOCKED, decision.status)
        assertTrue(decision.missingGates.isEmpty())
        assertTrue(decision.pendingGates.isEmpty())
        assertTrue(decision.deniedGates.isEmpty())
        assertEquals(
            setOf(
                ProductionAuthorizationGate.PRICE_SEMANTICS_VALIDATED,
                ProductionAuthorizationGate.DATASET_RECENCY_POLICY_DEFINED,
                ProductionAuthorizationGate.OFFER_FRESHNESS_POLICY_DEFINED
            ),
            decision.unknownGates
        )

        setOf(
            ProductionAuthorizationGate.DATA_ACCESS_AUTHORIZED,
            ProductionAuthorizationGate.CONSUMER_DISPLAY_AUTHORIZED,
            ProductionAuthorizationGate.CACHE_AUTHORIZED,
            ProductionAuthorizationGate.INDEX_AUTHORIZED,
            ProductionAuthorizationGate.MOBILE_APP_AUTHORIZED,
            ProductionAuthorizationGate.RETENTION_DELETION_POLICY_DEFINED,
            ProductionAuthorizationGate.OFFER_GEOGRAPHY_VALIDATED
        ).forEach { gate ->
            assertTrue("Expected satisfied gate $gate", gate in decision.satisfiedGates)
        }
    }

    @Test
    fun `network link profile keeps link rights but blocks unresolved installed-network and privacy gates`() {
        val assessment =
            JamiesonProductCatalogProductionContract.partnerAuthorizationAssessment()
        val decision =
            ProductionAuthorizationEvaluator.evaluate(
                assessment = assessment,
                profile =
                    ProductionActivationProfiles
                        .CONSUMER_MOBILE_CATALOG_WITH_NETWORK_LINKS
            )

        assertFalse(decision.authorized)
        assertTrue(
            ProductionAuthorizationGate.AFFILIATE_LINK_USE_AUTHORIZED in
                decision.satisfiedGates
        )
        assertTrue(
            ProductionAuthorizationGate.ADVERTISER_DISTRIBUTION_APPROVED in
                decision.satisfiedGates
        )
        assertEquals(
            setOf(
                ProductionAuthorizationGate.INSTALLED_SOFTWARE_NETWORK_APPROVED,
                ProductionAuthorizationGate.TRACKING_PRIVACY_READY
            ),
            decision.pendingGates
        )
        assertEquals(
            setOf(
                ProductionAuthorizationGate.PRICE_SEMANTICS_VALIDATED,
                ProductionAuthorizationGate.DATASET_RECENCY_POLICY_DEFINED,
                ProductionAuthorizationGate.OFFER_FRESHNESS_POLICY_DEFINED
            ),
            decision.unknownGates
        )
    }

    @Test
    fun `canadian geography is documented market evidence and never inferred from cad`() {
        val geography = JamiesonProductCatalogProductionContract.documentedGeography()
        val canada =
            JamiesonProductCatalogProductionContract.geographyAssessment("CA")
        val unitedStates =
            JamiesonProductCatalogProductionContract.geographyAssessment("US")

        assertEquals("CA", geography.countryCode)
        assertEquals(
            ImportedOfferCountryBasis.DOCUMENTED_DATASET_MARKET,
            geography.basis
        )
        assertEquals(ProviderDatasetCountryMatchStatus.MATCH, canada.status)
        assertEquals(
            ProductionAuthorizationState.SATISFIED,
            canada.toProductionGateAssessment().state
        )
        assertEquals(ProviderDatasetCountryMatchStatus.MISMATCH, unitedStates.status)
        assertEquals(
            ProductionAuthorizationState.DENIED,
            unitedStates.toProductionGateAssessment().state
        )
    }

    @Test
    fun `declared feed currency accepts only canonical cad`() {
        val contract = JamiesonProductCatalogProductionContract

        assertTrue(contract.matchesDeclaredFeedCurrency("CAD"))
        assertFalse(contract.matchesDeclaredFeedCurrency("USD"))
        assertFalse(contract.matchesDeclaredFeedCurrency("cad"))
        assertFalse(contract.matchesDeclaredFeedCurrency(null))
    }

    @Test
    fun `partnership termination stops production use immediately and requires withdrawal`() {
        val terminatedAt = 1_700_000_000_000L
        val decision =
            JamiesonProductCatalogProductionContract.evaluateTermination(
                partnershipTerminationAtEpochMillis = terminatedAt,
                evaluatedAtEpochMillis = terminatedAt
            )

        assertFalse(decision.productionUseAllowed)
        assertEquals(
            ProductionDatasetDispositionState.WITHDRAWAL_REQUIRED,
            decision.requiredNamespaceDisposition
        )
        assertEquals(
            terminatedAt + 5_184_000_000L,
            decision.deletionDeadlineEpochMillis
        )
        assertFalse(decision.deletionOverdue)
    }

    @Test
    fun `sixty day deadline is exact and overdue begins only after it`() {
        val terminatedAt = 1_700_000_000_000L
        val deadline = terminatedAt + 5_184_000_000L

        val atDeadline =
            JamiesonProductCatalogProductionContract.evaluateTermination(
                partnershipTerminationAtEpochMillis = terminatedAt,
                evaluatedAtEpochMillis = deadline
            )
        val afterDeadline =
            JamiesonProductCatalogProductionContract.evaluateTermination(
                partnershipTerminationAtEpochMillis = terminatedAt,
                evaluatedAtEpochMillis = deadline + 1L
            )
        val active =
            JamiesonProductCatalogProductionContract.evaluateTermination(
                partnershipTerminationAtEpochMillis = null,
                evaluatedAtEpochMillis = terminatedAt
            )

        assertFalse(atDeadline.deletionOverdue)
        assertTrue(afterDeadline.deletionOverdue)
        assertTrue(active.productionUseAllowed)
        assertEquals(
            ProductionDatasetDispositionState.RETAINED,
            active.requiredNamespaceDisposition
        )
        assertNull(active.deletionDeadlineEpochMillis)
    }

    @Test
    fun `future dated termination input fails closed`() {
        try {
            JamiesonProductCatalogProductionContract.evaluateTermination(
                partnershipTerminationAtEpochMillis = 2_000L,
                evaluatedAtEpochMillis = 1_999L
            )
            fail("Expected future termination rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("future-dated"))
        }
    }

    @Test
    fun `partner contract contains no feed acquisition current price or runtime authority`() {
        val source = source("JamiesonProductCatalogProductionContract.kt").readText()

        listOf(
            "System.currentTimeMillis",
            "java.net",
            "okhttp",
            "retrofit",
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "WorkManager",
            "Notification",
            "MainActivity",
            "CURRENT_PRICE",
            "Offer(",
            "ftpUsername",
            "ftpPassword",
            "sftpUsername",
            "sftpPassword"
        ).forEach { forbidden ->
            assertFalse("Partner contract must not contain $forbidden", source.contains(forbidden))
        }

        assertTrue(source.contains("PRICE_SEMANTICS_VALIDATED"))
        assertTrue(source.contains("ProductionAuthorizationState.UNKNOWN"))
        assertTrue(source.contains("WITHDRAWAL_REQUIRED"))
        assertTrue(source.contains("DOCUMENTED_DATASET_MARKET"))
    }

    private fun source(name: String): File {
        val root = File(System.getProperty("user.dir"))
        return File(root, "src/main/java/com/valuepilot/app/$name").also {
            assertTrue("Missing source $name at ${it.absolutePath}", it.isFile)
        }
    }
}
