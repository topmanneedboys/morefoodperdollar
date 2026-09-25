package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PracticalShoppingSavedSurfacePresenterTest {

    @Test
    fun `presenter maps lifecycle through verified surface projector and nothing else`() {
        val rendered = mutableListOf<PracticalShoppingSavedSurfaceState>()
        val presenter =
            PracticalShoppingSavedSurfacePresenter(
                PracticalShoppingSavedSurfaceRenderer(rendered::add)
            )
        val lifecycle = PracticalShoppingSavedLifecycleController().initialState()

        presenter.render(lifecycle)

        assertEquals(
            listOf(PracticalShoppingSavedSurfaceProjector.project(lifecycle)),
            rendered
        )
    }

    @Test
    fun `presenter preserves updating action suppression from surface contract`() {
        val rendered = mutableListOf<PracticalShoppingSavedSurfaceState>()
        val presenter =
            PracticalShoppingSavedSurfacePresenter(
                PracticalShoppingSavedSurfaceRenderer(rendered::add)
            )
        val lifecycle =
            PracticalShoppingSavedLifecycleState(
                status = PracticalShoppingSavedLifecycleStatus.MUTATING,
                projection = null,
                activeRequestId = 3L,
                nextRequestId = 4L,
                pendingAction = PracticalShoppingSavedExactPreferenceUiAction.ClearAll,
                failure = null,
                displayMetadataDegraded = false,
                displayCleanupDegraded = false
            )

        presenter.render(lifecycle)

        val state = rendered.single()
        assertEquals(PracticalShoppingSavedSurfaceMode.UPDATING, state.mode)
        assertEquals(null, state.refreshAction)
        assertEquals(null, state.clearAllAction)
    }
}
