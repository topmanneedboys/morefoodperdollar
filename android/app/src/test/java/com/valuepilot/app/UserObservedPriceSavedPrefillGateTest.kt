package com.valuepilot.app

import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.SourceProductIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserObservedPriceSavedPrefillGateTest {

    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val north = ShoppingStoreKey("north")
    private val west = ShoppingStoreKey("west")
    private val northScope =
        PracticalShoppingStoreIdentityScope(
            merchantKey = "merchant-north",
            locationKey = "location-north",
            commerceChannelKey = "PHYSICAL_STORE"
        )
    private val westScope =
        PracticalShoppingStoreIdentityScope(
            merchantKey = "merchant-west",
            locationKey = "location-west",
            commerceChannelKey = "PHYSICAL_STORE"
        )

    @Test
    fun `explicit saved pair yields only exact gtin display labels and store scope`() {
        val snapshot = snapshot()

        val attempt = UserObservedPriceSavedPrefillGate.request(milk, north, snapshot)

        assertTrue(attempt.accepted)
        assertNull(attempt.issue)
        assertEquals(milk, attempt.prefill?.itemKey)
        assertEquals(north, attempt.prefill?.storeKey)
        assertEquals("036000291452", attempt.prefill?.rawGtin)
        assertEquals("Whole Milk", attempt.prefill?.productName)
        assertEquals(northScope, attempt.prefill?.storeScope)
        assertEquals("North Market", attempt.prefill?.storeDisplayName)
    }

    @Test
    fun `saved gtin representation is preserved instead of silently canonicalized`() {
        val attempt = UserObservedPriceSavedPrefillGate.request(milk, north, snapshot())

        assertEquals("036000291452", attempt.prefill?.rawGtin)
    }

    @Test
    fun `unsaved product fails closed`() {
        val snapshot =
            snapshot(
                products = listOf(product(eggs, SourceProductIdentity(gtin = "4006381333931"))),
                metadata =
                    metadata(
                        productNames = mapOf(eggs to "Large Eggs")
                    )
            )

        val attempt = UserObservedPriceSavedPrefillGate.request(milk, north, snapshot)

        assertFalse(attempt.accepted)
        assertNull(attempt.prefill)
        assertEquals(UserObservedPriceSavedPrefillIssue.PRODUCT_NOT_SAVED, attempt.issue)
    }

    @Test
    fun `unsaved store fails closed`() {
        val snapshot =
            snapshot(
                stores = listOf(store(west, westScope)),
                metadata =
                    metadata(
                        storeNames = mapOf(west to "West Market")
                    )
            )

        val attempt = UserObservedPriceSavedPrefillGate.request(milk, north, snapshot)

        assertFalse(attempt.accepted)
        assertEquals(UserObservedPriceSavedPrefillIssue.STORE_NOT_SAVED, attempt.issue)
    }

    @Test
    fun `provider item identity is never converted into a gtin`() {
        val snapshot =
            snapshot(
                products =
                    listOf(
                        product(
                            milk,
                            SourceProductIdentity(providerItemId = "provider-item-1")
                        ),
                        product(eggs, SourceProductIdentity(gtin = "4006381333931"))
                    )
            )

        val attempt = UserObservedPriceSavedPrefillGate.request(milk, north, snapshot)

        assertFalse(attempt.accepted)
        assertEquals(UserObservedPriceSavedPrefillIssue.PRODUCT_GTIN_UNAVAILABLE, attempt.issue)
    }

    @Test
    fun `invalid gtin is not repaired or replaced by provider item fallback`() {
        val snapshot =
            snapshot(
                products =
                    listOf(
                        product(
                            milk,
                            SourceProductIdentity(
                                providerItemId = "provider-item-1",
                                gtin = "036000291453"
                            )
                        ),
                        product(eggs, SourceProductIdentity(gtin = "4006381333931"))
                    )
            )

        val attempt = UserObservedPriceSavedPrefillGate.request(milk, north, snapshot)

        assertFalse(attempt.accepted)
        assertEquals(UserObservedPriceSavedPrefillIssue.PRODUCT_GTIN_INVALID, attempt.issue)
    }

    @Test
    fun `unsafe selected product display name remains unavailable`() {
        val snapshot =
            snapshot(
                metadata =
                    metadata(
                        productNames = mapOf(milk to "036000291452", eggs to "Large Eggs")
                    )
            )

        val attempt = UserObservedPriceSavedPrefillGate.request(milk, north, snapshot)

        assertFalse(attempt.accepted)
        assertEquals(
            UserObservedPriceSavedPrefillIssue.PRODUCT_DISPLAY_NAME_UNAVAILABLE,
            attempt.issue
        )
    }

    @Test
    fun `unsafe selected store display name remains unavailable`() {
        val snapshot =
            snapshot(
                metadata =
                    metadata(
                        storeNames = mapOf(north to "merchant-north", west to "West Market")
                    )
            )

        val attempt = UserObservedPriceSavedPrefillGate.request(milk, north, snapshot)

        assertFalse(attempt.accepted)
        assertEquals(
            UserObservedPriceSavedPrefillIssue.STORE_DISPLAY_NAME_UNAVAILABLE,
            attempt.issue
        )
    }

    @Test
    fun `unresolved unselected saved choices do not block selected pair`() {
        val snapshot =
            snapshot(
                metadata =
                    PracticalShoppingSavedExactPreferenceDisplayMetadata(
                        productDisplayNames = mapOf(milk to "Whole Milk"),
                        storeDisplayNames = mapOf(north to "North Market")
                    )
            )

        val attempt = UserObservedPriceSavedPrefillGate.request(milk, north, snapshot)

        assertTrue(attempt.accepted)
        assertEquals("Whole Milk", attempt.prefill?.productName)
        assertEquals(northScope, attempt.prefill?.storeScope)
    }

    @Test
    fun `prefill gate owns no proof price time evidence quantity ranking or navigation authority`() {
        val source = source("UserObservedPriceSavedPrefillGate.kt").readText()

        assertTrue(source.contains("PracticalShoppingSavedExactPreferenceUiProjector"))
        assertTrue(source.contains("GtinValidation.isValid"))
        listOf(
            "UserObservedPriceConfirmationDraft",
            "UserObservedPriceConfirmationTransaction",
            "UserConfirmedObservedPrice",
            "UserProvidedPriceProof",
            "UserObservedPriceUnitValue",
            "UserProofBackedObservedPrice",
            "ProductPackageQuantity",
            "EvidenceFreshness",
            "ProductionCurrentPrice",
            "Money",
            "ByteArray",
            "System.currentTimeMillis",
            "UUID",
            "AppShell",
            "MainActivity",
            "android.",
            "java.net"
        ).forEach { forbidden ->
            assertFalse("Saved prefill gate must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun snapshot(
        products: List<PracticalShoppingSavedExactProductPreference> =
            listOf(
                product(milk, SourceProductIdentity(gtin = "036000291452")),
                product(eggs, SourceProductIdentity(gtin = "4006381333931"))
            ),
        stores: List<PracticalShoppingSavedExactStorePreference> =
            listOf(store(north, northScope), store(west, westScope)),
        metadata: PracticalShoppingSavedExactPreferenceDisplayMetadata = metadata()
    ): PracticalShoppingSavedValidatedSnapshot =
        PracticalShoppingSavedValidatedSnapshot(
            exactState =
                PracticalShoppingSavedExactPreferenceState(
                    productPreferences = products,
                    storePreferences = stores
                ),
            displayMetadata = metadata
        )

    private fun product(
        itemKey: ShoppingItemKey,
        identity: SourceProductIdentity
    ): PracticalShoppingSavedExactProductPreference =
        PracticalShoppingSavedExactProductPreference(
            itemKey = itemKey,
            providerId = EvidenceProviderId("test-provider"),
            sourceIdentity = identity
        )

    private fun store(
        storeKey: ShoppingStoreKey,
        scope: PracticalShoppingStoreIdentityScope
    ): PracticalShoppingSavedExactStorePreference =
        PracticalShoppingSavedExactStorePreference(
            storeKey = storeKey,
            scope = scope
        )

    private fun metadata(
        productNames: Map<ShoppingItemKey, String> =
            mapOf(milk to "Whole Milk", eggs to "Large Eggs"),
        storeNames: Map<ShoppingStoreKey, String> =
            mapOf(north to "North Market", west to "West Market")
    ): PracticalShoppingSavedExactPreferenceDisplayMetadata =
        PracticalShoppingSavedExactPreferenceDisplayMetadata(
            productDisplayNames = productNames,
            storeDisplayNames = storeNames
        )

    private fun source(name: String): File {
        val workingDirectory =
            requireNotNull(System.getProperty("user.dir")) { "Missing user.dir for source boundary test" }
        return File(workingDirectory, "src/main/java/com/valuepilot/app/$name").also {
            assertTrue("Missing source $name at ${it.absolutePath}", it.isFile)
        }
    }
}
