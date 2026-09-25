package com.valuepilot.app

import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserObservedPriceSavedConfirmationDraftIdentityPrefillAdapterTest {

    @Test
    fun `accepted saved prefill maps only exact confirmation draft identity context`() {
        val storeScope = exactStoreScope()
        val attempt =
            UserObservedPriceSavedPrefillHandoffAttempt(
                prefill =
                    UserObservedPriceSavedPrefill(
                        itemKey = ShoppingItemKey("milk"),
                        storeKey = ShoppingStoreKey("north"),
                        rawGtin = "036000291452",
                        productName = "Whole Milk 2%",
                        storeScope = storeScope,
                        storeDisplayName = "North Market"
                    )
            )

        val adapted =
            requireNotNull(
                UserObservedPriceSavedConfirmationDraftIdentityPrefillAdapter.adapt(attempt)
            )

        assertEquals("036000291452", adapted.rawGtin)
        assertEquals("Whole Milk 2%", adapted.productName)
        assertSame(storeScope, adapted.storeScope)
    }

    @Test
    fun `selection readiness rejection fails closed without draft identity context`() {
        val attempt =
            UserObservedPriceSavedPrefillHandoffAttempt(
                prefill = null,
                issue = UserObservedPriceSavedPrefillHandoffIssue.SELECTION_NOT_READY
            )

        assertNull(UserObservedPriceSavedConfirmationDraftIdentityPrefillAdapter.adapt(attempt))
    }

    @Test
    fun `downstream saved prefill rejection fails closed without draft identity context`() {
        val attempt =
            UserObservedPriceSavedPrefillHandoffAttempt(
                prefill = null,
                prefillIssue = UserObservedPriceSavedPrefillIssue.PRODUCT_GTIN_INVALID
            )

        assertNull(UserObservedPriceSavedConfirmationDraftIdentityPrefillAdapter.adapt(attempt))
    }

    @Test
    fun `adapter owns no route draft proof submission ranking persistence Android or network authority`() {
        val source = source("UserObservedPriceSavedConfirmationDraftIdentityPrefill.kt").readText()

        listOf(
            "UserObservedPriceConfirmationDraftRouteSession",
            "UserObservedPriceConfirmationDraft.start",
            "UserObservedPriceConfirmationTransaction",
            "UserObservedPriceConfirmationAndroidSession",
            "UserObservedPriceConfirmationDraftSubmissionHandoff",
            "UserProvidedPriceProofArtifact",
            "ByteArray",
            "Money(",
            "System.currentTimeMillis",
            "UUID",
            "ShoppingEvidence(",
            "EvidenceClaim(",
            "CURRENT_PRICE",
            "AppShell",
            "MainActivity",
            "android.",
            "java.net",
            "submit(",
            "onProductChanged(",
            "onStoreScopeChanged("
        ).forEach { forbidden ->
            assertFalse("Draft identity adapter must not own $forbidden", source.contains(forbidden))
        }

        assertTrue(source.contains("val prefill = attempt.prefill ?: return null"))
        assertTrue(source.contains("rawGtin = prefill.rawGtin"))
        assertTrue(source.contains("productName = prefill.productName"))
        assertTrue(source.contains("storeScope = prefill.storeScope"))
        assertFalse(source.contains("itemKey = prefill.itemKey"))
        assertFalse(source.contains("storeKey = prefill.storeKey"))
        assertFalse(source.contains("storeDisplayName = prefill.storeDisplayName"))
    }

    private fun exactStoreScope(): PracticalShoppingStoreIdentityScope =
        PracticalShoppingStoreIdentityScope(
            merchantKey = "merchant-north",
            locationKey = "location-north",
            commerceChannelKey = "PHYSICAL_STORE"
        )

    private fun source(fileName: String): File {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate = File(directory, "app/src/main/java/com/valuepilot/app/$fileName")
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate $fileName")
    }
}
