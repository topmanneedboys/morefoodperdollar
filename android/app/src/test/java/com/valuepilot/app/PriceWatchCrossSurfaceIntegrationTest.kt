package com.valuepilot.app

import com.valuepilot.core.ConfirmedPurchase
import com.valuepilot.core.ConfirmedPurchaseIdentityOrigin
import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.SourceProductIdentity
import com.valuepilot.core.PriceWatch
import com.valuepilot.core.PriceWatchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceWatchCrossSurfaceIntegrationTest {

    @Test
    fun `one exact watch is visible through search home basket saved and purchase history`() {
        val gtin = "036000291452"
        val key = "gtin:0036000291452"
        val watch = PriceWatch(key, "Coffee", packageQuantityLabel = "340 g")

        val searchMatch =
            PracticalShoppingSearchIdentityPresentation.Match(
                displayName = "Coffee",
                brand = "Example",
                matchLabel = "exact GTIN",
                packageQuantityLabel = "340 g",
                identityKey = key
            )
        assertEquals(key, searchMatch.identityKey)

        val privateIdentity =
            PracticalShoppingPrivateIdentity(
                identityKey = key,
                displayName = "Coffee",
                brand = "Example",
                sourceLabel = "Search identity",
                packageQuantityLabel = "340 g"
            )
        val home =
            PracticalShoppingHomeRenderer.render(
                source = LocalSamplePracticalShoppingDemo.initialModel().ui,
                requestDetails = null,
                privateIdentities = listOf(privateIdentity),
                watchedProductIdentityKeys = setOf(key)
            )
        assertTrue(home.privateIdentityItems.single().watched)
        val basket = PracticalShoppingBasketRenderer.render(home)
        assertTrue(basket.privateIdentityItems.single().watched)

        val preference =
            PracticalShoppingSavedExactProductPreference(
                itemKey = ShoppingItemKey("coffee-request"),
                providerId = EvidenceProviderId("openfoodfacts"),
                sourceIdentity = SourceProductIdentity(gtin = gtin)
            )
        val savedState =
            requireNotNull(
                PracticalShoppingSavedExactPreferenceStateManager
                    .load(
                        PracticalShoppingSavedExactPreferenceDocument(
                            schemaVersion = PracticalShoppingSavedExactPreferenceStateManager.currentSchemaVersion,
                            productPreferences = listOf(preference),
                            storePreferences = emptyList()
                        )
                    ).state
            )
        val savedProjection =
            PracticalShoppingSavedExactPreferenceUiProjector.project(
                savedState,
                PracticalShoppingSavedExactPreferenceDisplayMetadata(
                    productDisplayNames = mapOf(preference.itemKey to "Coffee")
                )
            )
        val savedSurface =
            PracticalShoppingSavedSurfaceProjector.project(
                lifecycle =
                    PracticalShoppingSavedLifecycleState(
                        status = PracticalShoppingSavedLifecycleStatus.READY,
                        projection = savedProjection,
                        activeRequestId = null,
                        nextRequestId = 1L,
                        pendingAction = null,
                        failure = null,
                        displayMetadataDegraded = false,
                        displayCleanupDegraded = false
                    ),
                watchedProductIdentityKeys = setOf(key)
            )
        assertEquals(key, savedSurface.productRows.single().watchAction?.productIdentityKey)
        assertTrue(savedSurface.productRows.single().watchAction?.watched == true)

        val purchase =
            ConfirmedPurchase(
                purchaseId = "purchase-1",
                productIdentityKey = key,
                identityOrigin = ConfirmedPurchaseIdentityOrigin.SEARCH_IDENTITY,
                displayName = "Coffee",
                packageQuantityLabel = "340 g"
            )
        val history =
            ConfirmedPurchaseHistoryProjector.project(
                state = ConfirmedPurchaseHistoryState(listOf(purchase)),
                unavailable = false,
                watchedProductIdentityKeys = setOf(key)
            )
        assertEquals(key, history.rows.single().priceWatchAction?.productIdentityKey)
        assertTrue(history.rows.single().priceWatchAction?.watched == true)

        val watchSurface =
            PriceWatchProjector.project(
                state = PriceWatchState(watches = listOf(watch)),
                purchases = listOf(purchase),
                unavailable = false
            )
        assertEquals(key, watchSurface.rows.single().productIdentityKey)
        assertEquals(1, watchSurface.rows.single().purchaseHistory.size)
    }
}
