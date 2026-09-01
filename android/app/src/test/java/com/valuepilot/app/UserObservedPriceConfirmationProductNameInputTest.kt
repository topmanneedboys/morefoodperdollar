package com.valuepilot.app

import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserObservedPriceConfirmationProductNameInputTest {

    @Test
    fun `text adapter trims transport whitespace and emits only typed product name action`() {
        val result =
            UserObservedPriceConfirmationProductNameTextInputAdapter.adapt(
                UserObservedPriceConfirmationProductNameTextInput("  Whole Milk 2%  ")
            )

        assertEquals(
            UserObservedPriceConfirmationProductNameTextInputResult.Success(
                UserObservedPriceConfirmationProductNameUiAction.SetProductName("Whole Milk 2%")
            ),
            result
        )
    }

    @Test
    fun `text adapter rejects blank but leaves semantic product name validation downstream`() {
        assertEquals(
            UserObservedPriceConfirmationProductNameTextInputResult.Failure(
                UserObservedPriceConfirmationProductNameTextInputFailure.BLANK
            ),
            UserObservedPriceConfirmationProductNameTextInputAdapter.adapt(
                UserObservedPriceConfirmationProductNameTextInput("   ")
            )
        )

        assertEquals(
            UserObservedPriceConfirmationProductNameTextInputResult.Success(
                UserObservedPriceConfirmationProductNameUiAction.SetProductName("X")
            ),
            UserObservedPriceConfirmationProductNameTextInputAdapter.adapt(
                UserObservedPriceConfirmationProductNameTextInput("X")
            )
        )
    }

    @Test
    fun `visible Saved confirmation route may complete missing product name without replacing identity`() {
        val finalizations = mutableListOf<UserObservedPriceConfirmationDraftFinalization>()
        val coordinator = coordinator(finalizations)

        coordinator.onAttempt(acceptedAttempt(productName = null))
        coordinator.onRouteVisibilityChanged(true)

        val before = finalizations.last()
        assertTrue(UserObservedPriceConfirmationDraftMissingField.PRODUCT_NAME in before.missingFields)
        assertFalse(UserObservedPriceConfirmationDraftMissingField.GTIN in before.missingFields)
        assertFalse(UserObservedPriceConfirmationDraftMissingField.STORE_SCOPE in before.missingFields)

        coordinator.onProductNameAction(
            UserObservedPriceConfirmationProductNameUiAction.SetProductName("Whole Milk 2%")
        )

        val after = finalizations.last()
        assertFalse(UserObservedPriceConfirmationDraftMissingField.PRODUCT_NAME in after.missingFields)
        assertFalse(UserObservedPriceConfirmationDraftMissingField.GTIN in after.missingFields)
        assertFalse(UserObservedPriceConfirmationDraftMissingField.STORE_SCOPE in after.missingFields)
        assertTrue(UserObservedPriceConfirmationDraftMissingField.PRICE in after.missingFields)
        assertTrue(UserObservedPriceConfirmationDraftMissingField.ARTIFACT_ID in after.missingFields)
        assertTrue(UserObservedPriceConfirmationDraftMissingField.OBSERVED_AT in after.missingFields)
        assertTrue(UserObservedPriceConfirmationDraftMissingField.CONFIRMATION_ID in after.missingFields)
    }

    @Test
    fun `product name completion is ignored before route visibility and cannot overwrite prefilled name`() {
        val missingNameFinalizations = mutableListOf<UserObservedPriceConfirmationDraftFinalization>()
        val missingNameCoordinator = coordinator(missingNameFinalizations)

        missingNameCoordinator.onAttempt(acceptedAttempt(productName = null))
        missingNameCoordinator.onProductNameAction(
            UserObservedPriceConfirmationProductNameUiAction.SetProductName("Too Early")
        )
        missingNameCoordinator.onRouteVisibilityChanged(true)

        assertTrue(
            UserObservedPriceConfirmationDraftMissingField.PRODUCT_NAME in
                missingNameFinalizations.last().missingFields
        )

        val prefilledFinalizations = mutableListOf<UserObservedPriceConfirmationDraftFinalization>()
        val prefilledCoordinator = coordinator(prefilledFinalizations)

        prefilledCoordinator.onAttempt(acceptedAttempt(productName = "Original Milk"))
        prefilledCoordinator.onRouteVisibilityChanged(true)
        val publicationCount = prefilledFinalizations.size

        prefilledCoordinator.onProductNameAction(
            UserObservedPriceConfirmationProductNameUiAction.SetProductName("Replacement Milk")
        )

        assertEquals(publicationCount, prefilledFinalizations.size)
        assertFalse(
            UserObservedPriceConfirmationDraftMissingField.PRODUCT_NAME in
                prefilledFinalizations.last().missingFields
        )
    }

    @Test
    fun `product name input boundary owns no proof price identifier clock evidence or network authority`() {
        val source = source().readText()

        listOf(
            "Money(",
            "PracticalShoppingStoreIdentityScope(",
            "UserProvidedPriceProof",
            "artifactId",
            "observationId",
            "confirmationId",
            "System.currentTimeMillis",
            "UUID",
            "MessageDigest",
            "ShoppingEvidence(",
            "EvidenceClaim(",
            "CURRENT_PRICE",
            "ProductionBestValue",
            "ProviderProductionAuthorization",
            "android.",
            "java.net"
        ).forEach { forbidden ->
            assertFalse("Product-name input boundary must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun coordinator(
        finalizations: MutableList<UserObservedPriceConfirmationDraftFinalization>
    ): UserObservedPriceSavedConfirmationDraftRouteCoordinator =
        UserObservedPriceSavedConfirmationDraftRouteCoordinator(
            routeOpenObserver = UserObservedPriceConfirmationDraftRouteOpenObserver { },
            sessionFactory = {
                UserObservedPriceConfirmationDraftRouteSession(
                    observer =
                        UserObservedPriceConfirmationDraftObserver { finalization ->
                            finalizations += finalization
                        }
                )
            }
        )

    private fun acceptedAttempt(
        productName: String?
    ): UserObservedPriceSavedPrefillHandoffAttempt =
        UserObservedPriceSavedPrefillHandoffAttempt(
            prefill =
                UserObservedPriceSavedPrefill(
                    itemKey = ShoppingItemKey("milk"),
                    storeKey = ShoppingStoreKey("north"),
                    rawGtin = "036000291452",
                    productName = productName,
                    storeScope =
                        PracticalShoppingStoreIdentityScope(
                            merchantKey = "merchant-north",
                            locationKey = "location-north",
                            commerceChannelKey = "PHYSICAL_STORE"
                        ),
                    storeDisplayName = "North Market"
                )
        )

    private fun source(): File {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate =
                File(
                    directory,
                    "app/src/main/java/com/valuepilot/app/UserObservedPriceConfirmationProductNameInput.kt"
                )
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate product-name input source")
    }
}