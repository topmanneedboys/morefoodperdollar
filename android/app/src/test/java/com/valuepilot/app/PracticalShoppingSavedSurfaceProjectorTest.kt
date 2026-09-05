package com.valuepilot.app

import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingSavedSurfaceProjectorTest {

    private val itemKey = ShoppingItemKey("internal-item-key-123456")
    private val storeKey = ShoppingStoreKey("internal-store-key-654321")
    private val deleteProduct =
        PracticalShoppingSavedExactPreferenceUiAction.DeleteProduct(itemKey)
    private val deleteStore =
        PracticalShoppingSavedExactPreferenceUiAction.DeleteStore(storeKey)

    @Test
    fun `check price action keeps the saved key typed and rejects unsafe display labels`() {
        val action =
            PracticalShoppingSavedSurfaceAction.CheckProductPrice(
                itemKey = itemKey,
                displayName = "Free-range eggs"
            )

        assertEquals(itemKey, action.itemKey)
        assertEquals("Free-range eggs", action.displayName)
        assertThrows(IllegalArgumentException::class.java) {
            PracticalShoppingSavedSurfaceAction.CheckProductPrice(itemKey, " ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            PracticalShoppingSavedSurfaceAction.CheckProductPrice(itemKey, "unsafe\u0000name")
        }
        assertThrows(IllegalArgumentException::class.java) {
            PracticalShoppingSavedSurfaceAction.CheckProductPrice(itemKey, "x".repeat(161))
        }
    }

    @Test
    fun `idle surface is explicit and offers refresh without content`() {
        val surface =
            PracticalShoppingSavedSurfaceProjector.project(
                PracticalShoppingSavedLifecycleController().initialState()
            )

        assertEquals(PracticalShoppingSavedSurfaceMode.IDLE, surface.mode)
        assertEquals("Saved choices", surface.headline)
        assertEquals(PracticalShoppingSavedSurfaceAction.Refresh, surface.refreshAction)
        assertFalse(surface.progressVisible)
        assertTrue(surface.productRows.isEmpty())
        assertTrue(surface.storeRows.isEmpty())
        assertNull(surface.clearAllAction)
    }

    @Test
    fun `initial load shows progress and exposes no mutation action`() {
        val surface =
            PracticalShoppingSavedSurfaceProjector.project(
                lifecycle(
                    status = PracticalShoppingSavedLifecycleStatus.LOADING,
                    activeRequestId = 1L,
                    nextRequestId = 2L
                )
            )

        assertEquals(PracticalShoppingSavedSurfaceMode.LOADING, surface.mode)
        assertTrue(surface.progressVisible)
        assertEquals("Loading saved choices…", surface.statusMessage)
        assertNull(surface.refreshAction)
        assertNull(surface.clearAllAction)
    }

    @Test
    fun `refresh retains visible rows but suppresses all mutations while load is active`() {
        val surface =
            PracticalShoppingSavedSurfaceProjector.project(
                lifecycle(
                    status = PracticalShoppingSavedLifecycleStatus.LOADING,
                    projection = contentProjection(),
                    activeRequestId = 7L,
                    nextRequestId = 8L
                )
            )

        assertEquals(PracticalShoppingSavedSurfaceMode.REFRESHING, surface.mode)
        assertTrue(surface.progressVisible)
        assertEquals(1, surface.productRows.size)
        assertEquals(1, surface.storeRows.size)
        assertNull(surface.productRows.single().action)
        assertNull(surface.storeRows.single().action)
        assertNull(surface.clearAllAction)
        assertNull(surface.refreshAction)
    }

    @Test
    fun `ready content exposes only projector supplied labels and typed actions`() {
        val surface =
            PracticalShoppingSavedSurfaceProjector.project(
                lifecycle(
                    status = PracticalShoppingSavedLifecycleStatus.READY,
                    projection = contentProjection()
                )
            )

        assertEquals(PracticalShoppingSavedSurfaceMode.CONTENT, surface.mode)
        assertEquals("Free-range eggs", surface.productRows.single().title)
        assertEquals("Neighbourhood Market", surface.storeRows.single().title)
        assertEquals(
            PracticalShoppingSavedSurfaceAction.Preference(deleteProduct),
            surface.productRows.single().action
        )
        assertEquals(
            PracticalShoppingSavedSurfaceAction.Preference(deleteStore),
            surface.storeRows.single().action
        )
        assertEquals(
            "Remove saved product Free-range eggs",
            surface.productRows.single().actionDescription
        )
        assertEquals(
            PracticalShoppingSavedSurfaceAction.CheckProductPrice(
                itemKey = itemKey,
                displayName = "Free-range eggs"
            ),
            surface.productRows.single().secondaryAction
        )
        assertEquals("Check price", surface.productRows.single().secondaryActionLabel)
        assertEquals(
            "Check a price for Free-range eggs; package quantity and price are still required",
            surface.productRows.single().secondaryActionDescription
        )
        assertEquals(
            "Remove saved store Neighbourhood Market",
            surface.storeRows.single().actionDescription
        )
        assertEquals(
            PracticalShoppingSavedSurfaceAction.Preference(
                PracticalShoppingSavedExactPreferenceUiAction.ClearAll
            ),
            surface.clearAllAction
        )
        assertEquals(PracticalShoppingSavedSurfaceAction.Refresh, surface.refreshAction)
        assertFalse(surface.progressVisible)
    }

    @Test
    fun `empty ready projection is a real empty state with no clear action`() {
        val surface =
            PracticalShoppingSavedSurfaceProjector.project(
                lifecycle(
                    status = PracticalShoppingSavedLifecycleStatus.READY,
                    projection = emptyProjection()
                )
            )

        assertEquals(PracticalShoppingSavedSurfaceMode.EMPTY, surface.mode)
        assertEquals("No saved choices yet.", surface.emptyMessage)
        assertTrue(surface.productRows.isEmpty())
        assertTrue(surface.storeRows.isEmpty())
        assertNull(surface.clearAllAction)
        assertEquals(PracticalShoppingSavedSurfaceAction.Refresh, surface.refreshAction)
    }

    @Test
    fun `display metadata degradation stays usable and explains exact choices are unchanged`() {
        val surface =
            PracticalShoppingSavedSurfaceProjector.project(
                lifecycle(
                    status = PracticalShoppingSavedLifecycleStatus.DEGRADED,
                    projection = unresolvedProjection(),
                    displayMetadataDegraded = true
                )
            )

        assertEquals(PracticalShoppingSavedSurfaceMode.DEGRADED, surface.mode)
        assertEquals(
            "Some saved names couldn't be loaded. Exact saved choices are unchanged.",
            surface.statusMessage
        )
        assertEquals(
            "1 saved choice needs a display name before it can be shown.",
            surface.notice
        )
        assertTrue(surface.productRows.isEmpty())
        assertEquals(1, surface.storeRows.size)
        assertNotNull(surface.storeRows.single().action)
        assertEquals(PracticalShoppingSavedSurfaceAction.Refresh, surface.refreshAction)
    }

    @Test
    fun `cleanup degradation reports display cleanup without rolling back content`() {
        val surface =
            PracticalShoppingSavedSurfaceProjector.project(
                lifecycle(
                    status = PracticalShoppingSavedLifecycleStatus.DEGRADED,
                    projection = contentProjection(),
                    displayCleanupDegraded = true
                )
            )

        assertEquals(PracticalShoppingSavedSurfaceMode.DEGRADED, surface.mode)
        assertEquals(
            "Saved choices were updated, but some display cleanup is still pending.",
            surface.statusMessage
        )
        assertEquals(1, surface.productRows.size)
        assertEquals(1, surface.storeRows.size)
        assertNotNull(surface.clearAllAction)
    }

    @Test
    fun `mutation retains labels but removes every interactive action until authoritative reload`() {
        val surface =
            PracticalShoppingSavedSurfaceProjector.project(
                lifecycle(
                    status = PracticalShoppingSavedLifecycleStatus.MUTATING,
                    projection = contentProjection(),
                    activeRequestId = 11L,
                    nextRequestId = 12L,
                    pendingAction = deleteProduct
                )
            )

        assertEquals(PracticalShoppingSavedSurfaceMode.UPDATING, surface.mode)
        assertEquals("Updating saved choices…", surface.statusMessage)
        assertTrue(surface.progressVisible)
        assertEquals("Free-range eggs", surface.productRows.single().title)
        assertNull(surface.productRows.single().action)
        assertNull(surface.productRows.single().actionDescription)
        assertNull(surface.productRows.single().secondaryAction)
        assertNull(surface.productRows.single().secondaryActionLabel)
        assertNull(surface.productRows.single().secondaryActionDescription)
        assertNull(surface.storeRows.single().action)
        assertNull(surface.storeRows.single().actionDescription)
        assertNull(surface.clearAllAction)
        assertNull(surface.refreshAction)
    }

    @Test
    fun `load error exposes retry but never stale content or destructive actions`() {
        val surface =
            PracticalShoppingSavedSurfaceProjector.project(
                lifecycle(
                    status = PracticalShoppingSavedLifecycleStatus.ERROR,
                    nextRequestId = 4L,
                    failure = PracticalShoppingSavedLifecycleFailure.LOAD_FAILED
                )
            )

        assertEquals(PracticalShoppingSavedSurfaceMode.ERROR, surface.mode)
        assertEquals("Saved choices couldn't be loaded.", surface.statusMessage)
        assertEquals(PracticalShoppingSavedSurfaceAction.Refresh, surface.refreshAction)
        assertTrue(surface.productRows.isEmpty())
        assertTrue(surface.storeRows.isEmpty())
        assertNull(surface.clearAllAction)
    }

    @Test
    fun `action error uses consumer safe message and retry`() {
        val surface =
            PracticalShoppingSavedSurfaceProjector.project(
                lifecycle(
                    status = PracticalShoppingSavedLifecycleStatus.ERROR,
                    nextRequestId = 9L,
                    failure = PracticalShoppingSavedLifecycleFailure.ACTION_FAILED
                )
            )

        assertEquals(PracticalShoppingSavedSurfaceMode.ERROR, surface.mode)
        assertEquals(
            "That saved-choice change couldn't be completed.",
            surface.statusMessage
        )
        assertEquals(PracticalShoppingSavedSurfaceAction.Refresh, surface.refreshAction)
    }

    @Test
    fun `technical stable keys remain inside typed actions and never become display strings`() {
        val surface =
            PracticalShoppingSavedSurfaceProjector.project(
                lifecycle(
                    status = PracticalShoppingSavedLifecycleStatus.READY,
                    projection = contentProjection()
                )
            )

        val visibleStrings =
            buildList {
                add(surface.headline)
                surface.statusMessage?.let(::add)
                surface.notice?.let(::add)
                surface.emptyMessage?.let(::add)
                surface.productRows.forEach { row ->
                    add(row.title)
                    add(row.supportingText)
                }
                surface.storeRows.forEach { row ->
                    add(row.title)
                    add(row.supportingText)
                }
            }

        assertFalse(visibleStrings.any { it.contains(itemKey.value) })
        assertFalse(visibleStrings.any { it.contains(storeKey.value) })
        assertEquals(
            deleteProduct,
            surface.productRows.single().action?.action
        )
        assertEquals(
            deleteStore,
            surface.storeRows.single().action?.action
        )
    }

    private fun lifecycle(
        status: PracticalShoppingSavedLifecycleStatus,
        projection: PracticalShoppingSavedExactPreferenceUiProjection? = null,
        activeRequestId: Long? = null,
        nextRequestId: Long = 2L,
        pendingAction: PracticalShoppingSavedExactPreferenceUiAction? = null,
        failure: PracticalShoppingSavedLifecycleFailure? = null,
        displayMetadataDegraded: Boolean = false,
        displayCleanupDegraded: Boolean = false
    ): PracticalShoppingSavedLifecycleState =
        PracticalShoppingSavedLifecycleState(
            status = status,
            projection = projection,
            activeRequestId = activeRequestId,
            nextRequestId = nextRequestId,
            pendingAction = pendingAction,
            failure = failure,
            displayMetadataDegraded = displayMetadataDegraded,
            displayCleanupDegraded = displayCleanupDegraded
        )

    private fun contentProjection(): PracticalShoppingSavedExactPreferenceUiProjection =
        PracticalShoppingSavedExactPreferenceUiProjection(
            state =
                PracticalShoppingSavedExactPreferenceUiState(
                    headline = "Saved choices",
                    productRows =
                        listOf(
                            PracticalShoppingSavedProductUiRow(
                                title = "Free-range eggs",
                                supportingText = "Exact product choice",
                                action = deleteProduct
                            )
                        ),
                    storeRows =
                        listOf(
                            PracticalShoppingSavedStoreUiRow(
                                title = "Neighbourhood Market",
                                supportingText = "Exact store choice",
                                action = deleteStore
                            )
                        ),
                    unresolvedDisplayNameCount = 0,
                    notice = null,
                    emptyMessage = null,
                    clearAllAction = PracticalShoppingSavedExactPreferenceUiAction.ClearAll
                ),
            unresolvedProductKeys = emptyList(),
            unresolvedStoreKeys = emptyList()
        )

    private fun emptyProjection(): PracticalShoppingSavedExactPreferenceUiProjection =
        PracticalShoppingSavedExactPreferenceUiProjection(
            state =
                PracticalShoppingSavedExactPreferenceUiState(
                    headline = "Saved choices",
                    productRows = emptyList(),
                    storeRows = emptyList(),
                    unresolvedDisplayNameCount = 0,
                    notice = null,
                    emptyMessage = "No saved choices yet.",
                    clearAllAction = null
                ),
            unresolvedProductKeys = emptyList(),
            unresolvedStoreKeys = emptyList()
        )

    private fun unresolvedProjection(): PracticalShoppingSavedExactPreferenceUiProjection =
        PracticalShoppingSavedExactPreferenceUiProjection(
            state =
                PracticalShoppingSavedExactPreferenceUiState(
                    headline = "Saved choices",
                    productRows = emptyList(),
                    storeRows =
                        listOf(
                            PracticalShoppingSavedStoreUiRow(
                                title = "Neighbourhood Market",
                                supportingText = "Exact store choice",
                                action = deleteStore
                            )
                        ),
                    unresolvedDisplayNameCount = 1,
                    notice = "1 saved choice needs a display name before it can be shown.",
                    emptyMessage = null,
                    clearAllAction = PracticalShoppingSavedExactPreferenceUiAction.ClearAll
                ),
            unresolvedProductKeys = listOf(itemKey),
            unresolvedStoreKeys = emptyList()
        )
}
