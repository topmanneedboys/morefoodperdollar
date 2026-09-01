package com.valuepilot.app

import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserObservedPriceSavedPrefillHandoffPresentationTest {

    @Test
    fun acceptedHandoffProjectsOnlyVerifiedConsumerDisplayNames() {
        val rawGtin = "036000291452"
        val itemKey = ShoppingItemKey("internal-item-key")
        val storeKey = ShoppingStoreKey("internal-store-key")
        val scope =
            PracticalShoppingStoreIdentityScope(
                merchantKey = "internal-merchant-key",
                locationKey = "internal-location-key",
                commerceChannelKey = "PHYSICAL_STORE"
            )
        val attempt =
            UserObservedPriceSavedPrefillHandoffAttempt(
                prefill =
                    UserObservedPriceSavedPrefill(
                        itemKey = itemKey,
                        storeKey = storeKey,
                        rawGtin = rawGtin,
                        productName = "Whole Milk",
                        storeScope = scope,
                        storeDisplayName = "North Market"
                    )
            )

        val state = UserObservedPriceSavedPrefillHandoffUiProjector.project(attempt)

        assertEquals(
            UserObservedPriceSavedPrefillHandoffUiStatus.IDENTITY_PREFILL_READY,
            state.status
        )
        assertEquals("Whole Milk", state.productName)
        assertEquals("North Market", state.storeDisplayName)
        assertTrue(state.message.contains("identity details", ignoreCase = true))
        assertTrue(state.message.contains("later", ignoreCase = true))
        val rendered = state.toString()
        assertFalse(rendered.contains(rawGtin))
        assertFalse(rendered.contains(itemKey.value))
        assertFalse(rendered.contains(storeKey.value))
        assertFalse(rendered.contains(scope.merchantKey))
        assertFalse(rendered.contains(requireNotNull(scope.locationKey)))
        assertFalse(rendered.contains(requireNotNull(scope.commerceChannelKey)))
    }

    @Test
    fun selectionNotReadyProjectsConsumerSafeBlockedState() {
        val state =
            UserObservedPriceSavedPrefillHandoffUiProjector.project(
                UserObservedPriceSavedPrefillHandoffAttempt(
                    prefill = null,
                    issue = UserObservedPriceSavedPrefillHandoffIssue.SELECTION_NOT_READY
                )
            )

        assertEquals(UserObservedPriceSavedPrefillHandoffUiStatus.BLOCKED, state.status)
        assertNull(state.productName)
        assertNull(state.storeDisplayName)
        assertTrue(state.message.contains("Choose one saved product", ignoreCase = true))
        assertFalse(state.message.contains("key", ignoreCase = true))
    }

    @Test
    fun everyPrefillBlockerHasDeterministicConsumerSafeCopy() {
        val expectedMessages =
            mapOf(
                UserObservedPriceSavedPrefillIssue.PRODUCT_NOT_SAVED to
                    "That saved product is no longer available. Choose a saved product again.",
                UserObservedPriceSavedPrefillIssue.STORE_NOT_SAVED to
                    "That saved store is no longer available. Choose a saved store again.",
                UserObservedPriceSavedPrefillIssue.PRODUCT_GTIN_UNAVAILABLE to
                    "This saved product cannot be safely identified for observed-price confirmation yet.",
                UserObservedPriceSavedPrefillIssue.PRODUCT_GTIN_INVALID to
                    "This saved product identifier cannot be verified for observed-price confirmation. Choose another saved product.",
                UserObservedPriceSavedPrefillIssue.PRODUCT_DISPLAY_NAME_UNAVAILABLE to
                    "The selected saved product needs a current display name before it can be checked.",
                UserObservedPriceSavedPrefillIssue.STORE_DISPLAY_NAME_UNAVAILABLE to
                    "The selected saved store needs a current display name before it can be checked."
            )

        assertEquals(UserObservedPriceSavedPrefillIssue.values().toSet(), expectedMessages.keys)

        expectedMessages.forEach { (issue, expectedMessage) ->
            val state =
                UserObservedPriceSavedPrefillHandoffUiProjector.project(
                    UserObservedPriceSavedPrefillHandoffAttempt(
                        prefill = null,
                        prefillIssue = issue
                    )
                )

            assertEquals(UserObservedPriceSavedPrefillHandoffUiStatus.BLOCKED, state.status)
            assertEquals("Saved pair needs attention", state.headline)
            assertEquals(expectedMessage, state.message)
            assertNull(state.productName)
            assertNull(state.storeDisplayName)
        }
    }

    @Test
    fun presenterForwardsExactlyThePureProjectedImmutableState() {
        val attempt =
            UserObservedPriceSavedPrefillHandoffAttempt(
                prefill = null,
                prefillIssue = UserObservedPriceSavedPrefillIssue.PRODUCT_GTIN_UNAVAILABLE
            )
        val expected = UserObservedPriceSavedPrefillHandoffUiProjector.project(attempt)
        var rendered: UserObservedPriceSavedPrefillHandoffUiState? = null
        val presenter =
            UserObservedPriceSavedPrefillHandoffSurfacePresenter { state -> rendered = state }

        presenter.render(attempt)

        assertEquals(expected, rendered)
    }

    @Test
    fun presentationOwnsNoIdentityLeakExecutionDraftProofPricePersistenceOrNavigationAuthority() {
        val source = source("UserObservedPriceSavedPrefillHandoffPresentation.kt").readText()

        assertTrue(source.contains("prefill.productName"))
        assertTrue(source.contains("prefill.storeDisplayName"))
        listOf(
            "rawGtin",
            "itemKey",
            "storeKey",
            "storeScope",
            "merchantKey",
            "locationKey",
            "commerceChannelKey",
            "UserObservedPriceSavedPrefillGate.request(",
            "UserObservedPriceSavedPrefillHandoffGate.request(",
            "UserObservedPriceConfirmationDraft",
            "UserObservedPriceConfirmationTransaction",
            "UserProvidedPriceProofArtifact",
            "GtinValidation",
            "ByteArray",
            "Money",
            "System.currentTimeMillis",
            "UUID",
            "AppShellIntent",
            "MainActivity",
            "EvidenceBackedUnitValuePolicy",
            "CURRENT_PRICE",
            "SharedPreferences",
            "android.",
            "java.io",
            "java.net"
        ).forEach { forbidden ->
            assertFalse("Handoff presentation must not own or expose $forbidden", source.contains(forbidden))
        }
    }

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
