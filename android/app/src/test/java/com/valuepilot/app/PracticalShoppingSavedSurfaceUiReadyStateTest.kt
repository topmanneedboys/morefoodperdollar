package com.valuepilot.app

import com.valuepilot.core.ShoppingItemKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PracticalShoppingSavedSurfaceUiReadyStateTest {

    @Test
    fun `idle and error provide consumer ready primary action labels`() {
        val controller = PracticalShoppingSavedLifecycleController()
        val idle = PracticalShoppingSavedSurfaceProjector.project(controller.initialState())
        val error =
            PracticalShoppingSavedSurfaceProjector.project(
                PracticalShoppingSavedLifecycleState(
                    status = PracticalShoppingSavedLifecycleStatus.ERROR,
                    projection = null,
                    activeRequestId = null,
                    nextRequestId = 4L,
                    pendingAction = null,
                    failure = PracticalShoppingSavedLifecycleFailure.LOAD_FAILED,
                    displayMetadataDegraded = false,
                    displayCleanupDegraded = false
                )
            )

        assertEquals("Load saved choices", idle.refreshActionLabel)
        assertEquals("Try again", error.refreshActionLabel)
    }

    @Test
    fun `content carries section and destructive action labels while busy state removes them`() {
        val action =
            PracticalShoppingSavedExactPreferenceUiAction.DeleteProduct(
                ShoppingItemKey("opaque-item-key-123456")
            )
        val projection =
            PracticalShoppingSavedExactPreferenceUiProjection(
                state =
                    PracticalShoppingSavedExactPreferenceUiState(
                        headline = "Saved choices",
                        productRows =
                            listOf(
                                PracticalShoppingSavedProductUiRow(
                                    title = "Free-range eggs",
                                    supportingText = "Exact product choice",
                                    action = action
                                )
                            ),
                        storeRows = emptyList(),
                        unresolvedDisplayNameCount = 0,
                        notice = null,
                        emptyMessage = null,
                        clearAllAction = PracticalShoppingSavedExactPreferenceUiAction.ClearAll
                    ),
                unresolvedProductKeys = emptyList(),
                unresolvedStoreKeys = emptyList()
            )
        val ready =
            PracticalShoppingSavedSurfaceProjector.project(
                lifecycle(
                    status = PracticalShoppingSavedLifecycleStatus.READY,
                    projection = projection
                )
            )
        val busy =
            PracticalShoppingSavedSurfaceProjector.project(
                lifecycle(
                    status = PracticalShoppingSavedLifecycleStatus.MUTATING,
                    projection = projection,
                    activeRequestId = 6L,
                    nextRequestId = 7L,
                    pendingAction = action
                )
            )

        assertEquals("Products", ready.productSectionTitle)
        assertEquals("Remove", ready.productRows.single().actionLabel)
        assertEquals("Check price", ready.productRows.single().secondaryActionLabel)
        assertEquals(
            PracticalShoppingSavedSurfaceAction.CheckProductPrice(
                itemKey = ShoppingItemKey("opaque-item-key-123456"),
                displayName = "Free-range eggs"
            ),
            ready.productRows.single().secondaryAction
        )
        assertEquals("Refresh", ready.refreshActionLabel)
        assertEquals("Clear all", ready.clearAllActionLabel)

        assertEquals("Products", busy.productSectionTitle)
        assertNull(busy.productRows.single().action)
        assertNull(busy.productRows.single().actionLabel)
        assertNull(busy.productRows.single().secondaryAction)
        assertNull(busy.productRows.single().secondaryActionLabel)
        assertNull(busy.refreshActionLabel)
        assertNull(busy.clearAllActionLabel)
    }

    private fun lifecycle(
        status: PracticalShoppingSavedLifecycleStatus,
        projection: PracticalShoppingSavedExactPreferenceUiProjection?,
        activeRequestId: Long? = null,
        nextRequestId: Long = 2L,
        pendingAction: PracticalShoppingSavedExactPreferenceUiAction? = null
    ): PracticalShoppingSavedLifecycleState =
        PracticalShoppingSavedLifecycleState(
            status = status,
            projection = projection,
            activeRequestId = activeRequestId,
            nextRequestId = nextRequestId,
            pendingAction = pendingAction,
            failure = null,
            displayMetadataDegraded = false,
            displayCleanupDegraded = false
        )
}
