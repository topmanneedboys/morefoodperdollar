package com.valuepilot.app

import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PracticalShoppingSavedObservedPriceLaunchPresentationTest {

    @Test
    fun `ready accepted Saved content with visible product and store offers typed navigation`() {
        val state =
            PracticalShoppingSavedObservedPriceLaunchUiProjector.project(
                lifecycle(
                    status = PracticalShoppingSavedLifecycleStatus.READY,
                    projection = projection(productCount = 1, storeCount = 1)
                )
            )

        assertEquals(
            PracticalShoppingSavedObservedPriceLaunchAction.OpenObservedPriceSavedSelection,
            state.action
        )
        assertEquals("Confirm an observed price", state.actionLabel)
    }

    @Test
    fun `accepted degraded Saved content with visible choices still offers navigation`() {
        val state =
            PracticalShoppingSavedObservedPriceLaunchUiProjector.project(
                lifecycle(
                    status = PracticalShoppingSavedLifecycleStatus.DEGRADED,
                    projection = projection(productCount = 1, storeCount = 1),
                    displayCleanupDegraded = true
                )
            )

        assertEquals(
            PracticalShoppingSavedObservedPriceLaunchAction.OpenObservedPriceSavedSelection,
            state.action
        )
    }

    @Test
    fun `missing visible product or store keeps launcher unavailable`() {
        val missingProduct =
            PracticalShoppingSavedObservedPriceLaunchUiProjector.project(
                lifecycle(
                    status = PracticalShoppingSavedLifecycleStatus.READY,
                    projection = projection(productCount = 0, storeCount = 1)
                )
            )
        val missingStore =
            PracticalShoppingSavedObservedPriceLaunchUiProjector.project(
                lifecycle(
                    status = PracticalShoppingSavedLifecycleStatus.READY,
                    projection = projection(productCount = 1, storeCount = 0)
                )
            )

        assertNull(missingProduct.action)
        assertNull(missingProduct.actionLabel)
        assertNull(missingStore.action)
        assertNull(missingStore.actionLabel)
    }

    @Test
    fun `unresolved technical identities do not count as consumer visible launch choices`() {
        val unresolvedProductOnly =
            PracticalShoppingSavedObservedPriceLaunchUiProjector.project(
                lifecycle(
                    status = PracticalShoppingSavedLifecycleStatus.DEGRADED,
                    projection =
                        projection(
                            productCount = 0,
                            storeCount = 1,
                            unresolvedProductCount = 1
                        ),
                    displayMetadataDegraded = true
                )
            )
        val unresolvedStoreOnly =
            PracticalShoppingSavedObservedPriceLaunchUiProjector.project(
                lifecycle(
                    status = PracticalShoppingSavedLifecycleStatus.DEGRADED,
                    projection =
                        projection(
                            productCount = 1,
                            storeCount = 0,
                            unresolvedStoreCount = 1
                        ),
                    displayMetadataDegraded = true
                )
            )

        assertNull(unresolvedProductOnly.action)
        assertNull(unresolvedStoreOnly.action)
    }

    @Test
    fun `busy and error lifecycle states never expose observed price navigation`() {
        val retained = projection(productCount = 1, storeCount = 1)
        val states =
            listOf(
                lifecycle(
                    status = PracticalShoppingSavedLifecycleStatus.LOADING,
                    projection = retained,
                    activeRequestId = 2L
                ),
                lifecycle(
                    status = PracticalShoppingSavedLifecycleStatus.MUTATING,
                    projection = retained,
                    activeRequestId = 3L,
                    pendingAction = PracticalShoppingSavedExactPreferenceUiAction.ClearAll
                ),
                lifecycle(
                    status = PracticalShoppingSavedLifecycleStatus.ERROR,
                    projection = retained,
                    failure = PracticalShoppingSavedLifecycleFailure.LOAD_FAILED
                )
            ).map(PracticalShoppingSavedObservedPriceLaunchUiProjector::project)

        assertTrue(states.all { it.action == null && it.actionLabel == null })
    }

    @Test
    fun `launcher projection owns navigation readiness only`() {
        val source = source("PracticalShoppingSavedObservedPriceLaunchPresentation.kt").readText()

        assertTrue(source.contains("OpenObservedPriceSavedSelection"))
        assertTrue(source.contains("projection.state.productRows.isNotEmpty()"))
        assertTrue(source.contains("projection.state.storeRows.isNotEmpty()"))
        listOf(
            "UserObservedPriceSavedPrefillGate",
            "UserObservedPriceSavedPrefillHandoffGate",
            "UserObservedPriceSavedSelectionRouteSession",
            "SourceProductIdentity",
            "rawGtin",
            "PracticalShoppingSavedAndroidSession",
            "PracticalShoppingSavedExactPreferenceLocalStore",
            "PracticalShoppingSavedDisplayMetadataLocalStore",
            "UserObservedPriceConfirmationDraft",
            "UserObservedPriceConfirmationTransaction",
            "UserObservedPriceConfirmationExecution",
            "UserObservedPriceConfirmationAndroidSession",
            "UserProvidedPriceProof",
            "UserConfirmedObservedPrice",
            "UserProofBackedObservedPrice",
            "UserObservedPriceUnitValue",
            "ProductPackageQuantity",
            "EvidenceFreshness",
            "ProductionCurrentPrice",
            "Money",
            "ByteArray",
            "System.currentTimeMillis",
            "UUID",
            "AppShell",
            "MainActivity",
            "Intent",
            "startActivity",
            "android.",
            "java.net",
            "java.io"
        ).forEach { forbidden ->
            assertFalse(
                "Observed-price Saved launcher presentation must not own $forbidden",
                source.contains(forbidden)
            )
        }
    }

    private fun lifecycle(
        status: PracticalShoppingSavedLifecycleStatus,
        projection: PracticalShoppingSavedExactPreferenceUiProjection? = null,
        activeRequestId: Long? = null,
        pendingAction: PracticalShoppingSavedExactPreferenceUiAction? = null,
        failure: PracticalShoppingSavedLifecycleFailure? = null,
        displayMetadataDegraded: Boolean = false,
        displayCleanupDegraded: Boolean = false
    ): PracticalShoppingSavedLifecycleState =
        PracticalShoppingSavedLifecycleState(
            status = status,
            projection = projection,
            activeRequestId = activeRequestId,
            nextRequestId = 5L,
            pendingAction = pendingAction,
            failure = failure,
            displayMetadataDegraded = displayMetadataDegraded,
            displayCleanupDegraded = displayCleanupDegraded
        )

    private fun projection(
        productCount: Int,
        storeCount: Int,
        unresolvedProductCount: Int = 0,
        unresolvedStoreCount: Int = 0
    ): PracticalShoppingSavedExactPreferenceUiProjection {
        val productRows =
            (0 until productCount).map { index ->
                val key = ShoppingItemKey("visible-product-$index-123456")
                PracticalShoppingSavedProductUiRow(
                    title = "Saved Product ${index + 1}",
                    supportingText = "Exact product choice",
                    action = PracticalShoppingSavedExactPreferenceUiAction.DeleteProduct(key)
                )
            }
        val storeRows =
            (0 until storeCount).map { index ->
                val key = ShoppingStoreKey("visible-store-$index-123456")
                PracticalShoppingSavedStoreUiRow(
                    title = "Saved Store ${index + 1}",
                    supportingText = "Exact store choice",
                    action = PracticalShoppingSavedExactPreferenceUiAction.DeleteStore(key)
                )
            }
        val unresolvedProducts =
            (0 until unresolvedProductCount).map { index ->
                ShoppingItemKey("unresolved-product-$index-123456")
            }
        val unresolvedStores =
            (0 until unresolvedStoreCount).map { index ->
                ShoppingStoreKey("unresolved-store-$index-123456")
            }
        val empty =
            productRows.isEmpty() &&
                storeRows.isEmpty() &&
                unresolvedProducts.isEmpty() &&
                unresolvedStores.isEmpty()

        return PracticalShoppingSavedExactPreferenceUiProjection(
            state =
                PracticalShoppingSavedExactPreferenceUiState(
                    headline = "Saved choices",
                    productRows = productRows,
                    storeRows = storeRows,
                    unresolvedDisplayNameCount =
                        unresolvedProducts.size + unresolvedStores.size,
                    notice =
                        if (unresolvedProducts.isEmpty() && unresolvedStores.isEmpty()) null
                        else "Some saved choices need a display name.",
                    emptyMessage = if (empty) "No saved choices yet." else null,
                    clearAllAction =
                        if (empty) null
                        else PracticalShoppingSavedExactPreferenceUiAction.ClearAll
                ),
            unresolvedProductKeys = unresolvedProducts,
            unresolvedStoreKeys = unresolvedStores
        )
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
