package com.valuepilot.app

import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PracticalShoppingSavedStapleLaunchPresentationTest {

    @Test
    fun readyAcceptedSavedContentWithTwoProductsAndStoreOffersTypedSetupNavigation() {
        val state =
            PracticalShoppingSavedStapleLaunchUiProjector.project(
                lifecycle(
                    status = PracticalShoppingSavedLifecycleStatus.READY,
                    projection = projection(productCount = 2, storeCount = 1)
                )
            )

        assertEquals(
            PracticalShoppingSavedStapleLaunchAction.OpenStapleWatchSetup,
            state.action
        )
        assertEquals("Watch My Staples", state.title)
        assertEquals(
            "Choose recurring saved items and a usual store to check whether a future switch is worth the trip.",
            state.supportingText
        )
        assertNull(state.notice)
        assertEquals("Choose staples to watch", state.actionLabel)
    }

    @Test
    fun fewerThanTwoSelectableProductsRemainsUnavailable() {
        val oneProduct =
            PracticalShoppingSavedStapleLaunchUiProjector.project(
                lifecycle(
                    status = PracticalShoppingSavedLifecycleStatus.READY,
                    projection = projection(productCount = 1, storeCount = 1)
                )
            )
        val missingDisplayName =
            PracticalShoppingSavedStapleLaunchUiProjector.project(
                lifecycle(
                    status = PracticalShoppingSavedLifecycleStatus.DEGRADED,
                    projection =
                        projection(
                            productCount = 1,
                            storeCount = 1,
                            unresolvedProductCount = 1
                        ),
                    displayMetadataDegraded = true
                )
            )

        assertNull(oneProduct.action)
        assertEquals("Watch My Staples", oneProduct.title)
        assertNull(oneProduct.supportingText)
        assertEquals(
            "Save one more named product to set up Watch My Staples.",
            oneProduct.notice
        )
        assertNull(oneProduct.actionLabel)
        assertNull(missingDisplayName.action)
        assertEquals("Watch My Staples", missingDisplayName.title)
        assertNull(missingDisplayName.supportingText)
        assertEquals(
            "Save one more named product to set up Watch My Staples.",
            missingDisplayName.notice
        )
        assertNull(missingDisplayName.actionLabel)
    }

    @Test
    fun missingSelectableStoreRemainsUnavailable() {
        val state =
            PracticalShoppingSavedStapleLaunchUiProjector.project(
                lifecycle(
                    status = PracticalShoppingSavedLifecycleStatus.READY,
                    projection = projection(productCount = 2, storeCount = 0)
                )
            )

        assertNull(state.action)
        assertEquals("Watch My Staples", state.title)
        assertNull(state.supportingText)
        assertEquals(
            "Save a named store to set up Watch My Staples.",
            state.notice
        )
        assertNull(state.actionLabel)
    }

    @Test
    fun emptySavedContentDoesNotAddRedundantWatchSetupGuidance() {
        val state =
            PracticalShoppingSavedStapleLaunchUiProjector.project(
                lifecycle(
                    status = PracticalShoppingSavedLifecycleStatus.READY,
                    projection = projection(productCount = 0, storeCount = 0)
                )
            )

        assertNull(state.title)
        assertNull(state.notice)
        assertNull(state.action)
        assertNull(state.actionLabel)
    }

    @Test
    fun acceptedCleanupDegradationWithUsableVisibleChoicesStillOffersSetupNavigation() {
        val state =
            PracticalShoppingSavedStapleLaunchUiProjector.project(
                lifecycle(
                    status = PracticalShoppingSavedLifecycleStatus.DEGRADED,
                    projection = projection(productCount = 2, storeCount = 1),
                    displayCleanupDegraded = true
                )
            )

        assertEquals(
            PracticalShoppingSavedStapleLaunchAction.OpenStapleWatchSetup,
            state.action
        )
    }

    @Test
    fun busyErrorAndEmptyStatesNeverOfferSetupNavigation() {
        val retained = projection(productCount = 2, storeCount = 1)
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
                    failure = PracticalShoppingSavedLifecycleFailure.LOAD_FAILED
                ),
                lifecycle(
                    status = PracticalShoppingSavedLifecycleStatus.READY,
                    projection = projection(productCount = 0, storeCount = 0)
                )
            ).map(PracticalShoppingSavedStapleLaunchUiProjector::project)

        assertTrue(
            states.all {
                it.action == null && it.actionLabel == null && it.notice == null
            }
        )
    }

    @Test
    fun launcherBoundaryOwnsNavigationOnlyAndNeverSavedPersistenceFactsEconomicsOrDelivery() {
        val presentation = source("PracticalShoppingSavedStapleLaunchPresentation.kt").readText()
        val view = source("PracticalShoppingSavedStapleLaunchView.kt").readText()
        val combined = presentation + "\n" + view

        assertTrue(presentation.contains("PracticalShoppingSavedStapleLaunchAction"))
        assertTrue(presentation.contains("OpenStapleWatchSetup"))
        assertTrue(presentation.contains("supportingText"))
        assertTrue(presentation.contains("notice"))
        assertTrue(view.contains("state.title"))
        assertTrue(view.contains("state.supportingText"))
        assertTrue(view.contains("state.notice"))
        assertFalse(presentation.contains("PracticalShoppingSavedSurfaceAction.Preference"))
        assertFalse(view.contains("visibility = View.VISIBLE"))
        assertTrue(view.contains("setOnClickListener { onAction?.invoke(action) }"))
        assertTrue(view.contains("accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE"))

        listOf(
            "PracticalShoppingSavedExactPreferenceLocalStore",
            "PracticalShoppingSavedDisplayMetadataLocalStore",
            "PracticalShoppingSavedExperienceCoordinator",
            "ShoppingRequest",
            "Money",
            "SingleStorePlanCandidate",
            "ShoppingTravel",
            "StapleWatchEconomicEvaluator",
            "StapleWatchEconomicDecision",
            "NotificationManager",
            "WorkManager",
            "SharedPreferences",
            "System.currentTimeMillis"
        ).forEach { forbidden ->
            assertFalse("Saved staple launcher must not own $forbidden", combined.contains(forbidden))
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
        unresolvedProductCount: Int = 0
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
        val empty = productRows.isEmpty() && storeRows.isEmpty() && unresolvedProducts.isEmpty()

        return PracticalShoppingSavedExactPreferenceUiProjection(
            state =
                PracticalShoppingSavedExactPreferenceUiState(
                    headline = "Saved choices",
                    productRows = productRows,
                    storeRows = storeRows,
                    unresolvedDisplayNameCount = unresolvedProductCount,
                    notice =
                        if (unresolvedProductCount == 0) null
                        else "$unresolvedProductCount saved choice needs a display name.",
                    emptyMessage = if (empty) "No saved choices yet." else null,
                    clearAllAction =
                        if (empty) null
                        else PracticalShoppingSavedExactPreferenceUiAction.ClearAll
                ),
            unresolvedProductKeys = unresolvedProducts,
            unresolvedStoreKeys = emptyList()
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
