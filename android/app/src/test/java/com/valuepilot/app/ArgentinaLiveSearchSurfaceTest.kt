package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArgentinaLiveSearchSurfaceTest {
    private val area = ArgentinaQualifiedLaunchAreas.BuenosAiresCaba
    private val presentation = PracticalShoppingSearchIdentityPresentation(
        query = "yerba",
        matches = listOf(
            PracticalShoppingSearchIdentityPresentation.Match(
                displayName = "Yerba Mate",
                brand = "Marca",
                matchLabel = "exact barcode"
            )
        ),
        evaluatedCandidateCount = 1,
        notice = "Argentina SEPA identity only."
    )

    @Test
    fun `qualified launch area is explicit and provenance backed`() {
        assertEquals("Buenos Aires (CABA)", area.label)
        assertEquals("-34.605506", area.latitude)
        assertEquals("-58.442562", area.longitude)
        assertEquals("25", area.radiusKm)
        assertEquals(ArgentinaSearchContextSource.QUALIFIED_LAUNCH_AREA, area.context().source)
        assertEquals("ar-caba", area.regionId)
        assertEquals("2026-09-06", area.releaseDate)
        assertTrue(area.derivation.contains("973"))
        assertTrue(area.manifestSha256.length == 64)
        assertTrue(area.storesSha256.length == 64)
        assertTrue(area.radiusKm.toBigDecimal() <= 50.toBigDecimal())
    }

    @Test
    fun `presenter has explicit idle loading result and empty states`() {
        assertEquals(
            ArgentinaLiveSearchSurfaceMode.IDLE,
            ArgentinaLiveSearchSurfacePresenter.initial(area).mode
        )
        assertEquals(
            ArgentinaLiveSearchSurfaceMode.LOADING,
            ArgentinaLiveSearchSurfacePresenter.from(
                ArgentinaLiveSearchState.Loading(1, "yerba"), area
            ).mode
        )
        val results = ArgentinaLiveSearchSurfacePresenter.from(
            ArgentinaLiveSearchState.Results(1, "yerba", presentation), area
        )
        assertEquals(ArgentinaLiveSearchSurfaceMode.RESULTS, results.mode)
        assertEquals(1, results.presentation?.matches?.size)
        val emptyPresentation = presentation.copy(matches = emptyList(), evaluatedCandidateCount = 0)
        assertEquals(
            ArgentinaLiveSearchSurfaceMode.NO_MATCHES,
            ArgentinaLiveSearchSurfacePresenter.from(
                ArgentinaLiveSearchState.Empty(2, "unknown", emptyPresentation), area
            ).mode
        )
    }

    @Test
    fun `typed failures map to short safe states without transport details`() {
        val failures = mapOf(
            ArgentinaBackendContract.FailureCode.SEARCH_NOT_AUTHORIZED to ArgentinaLiveSearchSurfaceMode.AUTHORIZATION_UNAVAILABLE,
            ArgentinaBackendContract.FailureCode.APP_CHECK_UNAVAILABLE to ArgentinaLiveSearchSurfaceMode.AUTHORIZATION_UNAVAILABLE,
            ArgentinaBackendContract.FailureCode.STALE_RELEASE to ArgentinaLiveSearchSurfaceMode.STALE_RELEASE,
            ArgentinaBackendContract.FailureCode.RELEASE_MISMATCH to ArgentinaLiveSearchSurfaceMode.STALE_RELEASE,
            ArgentinaBackendContract.FailureCode.TIMEOUT to ArgentinaLiveSearchSurfaceMode.TIMEOUT,
            ArgentinaBackendContract.FailureCode.NETWORK_ERROR to ArgentinaLiveSearchSurfaceMode.TEMPORARILY_UNAVAILABLE,
            ArgentinaBackendContract.FailureCode.SERVER_ERROR to ArgentinaLiveSearchSurfaceMode.TEMPORARILY_UNAVAILABLE
        )
        failures.forEach { (failure, expectedMode) ->
            val state = ArgentinaLiveSearchSurfacePresenter.from(
                ArgentinaLiveSearchState.Unavailable(3, "yerba", failure), area
            )
            assertEquals(expectedMode, state.mode)
            assertFalse(state.statusMessage.contains("http", ignoreCase = true))
            assertFalse(state.statusMessage.contains("token", ignoreCase = true))
            assertFalse(state.statusMessage.contains("run.app", ignoreCase = true))
            assertFalse(state.statusMessage.contains("private", ignoreCase = true))
        }
    }

    @Test
    fun `surface caveat does not authorize price or availability claims`() {
        val state = ArgentinaLiveSearchSurfacePresenter.from(
            ArgentinaLiveSearchState.Results(1, "yerba", presentation), area
        )
        assertTrue(state.caveat.contains("No current price"))
        assertTrue(state.caveat.contains("stock"))
        assertTrue(state.caveat.contains("ranking"))
    }

    @Test
    fun `main thread render gate drops a queued callback from an older query`() {
        val gate = ArgentinaLiveSearchMainThreadRenderGate()
        val rendered = mutableListOf<ArgentinaLiveSearchSurfaceState>()
        val oldResult =
            ArgentinaLiveSearchSurfacePresenter.from(
                ArgentinaLiveSearchState.Results(4, "yerba", presentation),
                area
            )
        val newLoading =
            ArgentinaLiveSearchSurfacePresenter.from(
                ArgentinaLiveSearchState.Loading(5, "mate"),
                area
            )

        gate.renderIfCurrent(newLoading, currentGeneration = 5, rendered::add)
        gate.renderIfCurrent(oldResult, currentGeneration = 5, rendered::add)
        gate.renderIfCurrent(oldResult, currentGeneration = 4, rendered::add)
        val callbackFromReplacedHost = newLoading.copy(generation = 6)
        gate.renderIfCurrent(callbackFromReplacedHost, currentGeneration = 5, rendered::add)

        assertEquals(listOf(newLoading), rendered)
        assertEquals(5L, rendered.single().generation)
        assertEquals(6L, ArgentinaLiveSearchSurfacePresenter.from(ArgentinaLiveSearchState.Idle(6), area).generation)
    }
}
