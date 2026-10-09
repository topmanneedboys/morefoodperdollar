package com.valuepilot.app.next

import com.valuepilot.app.CompareHereManualRouteStatus
import com.valuepilot.app.CompareHerePrivatePriceMemoryCapture
import com.valuepilot.app.CompareHerePrivatePriceMemoryLoadResult
import com.valuepilot.app.CompareHerePrivatePriceMemoryMutationResult
import com.valuepilot.app.CompareHerePrivatePriceMemoryState
import com.valuepilot.app.CompareHerePrivatePriceMemoryStateManager
import com.valuepilot.app.CompareHerePrivatePriceMemoryStore
import com.valuepilot.core.CompareHerePriceSelection
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValuePilotNextCompareHereJourneyTest {

    @Test
    fun `comparison requires fresh like for like confirmation and remembers successful result`() {
        val store = InMemoryPriceMemoryStore()
        val states = mutableListOf<ValuePilotNextCompareHereUiState>()
        val journey =
            ValuePilotNextCompareHereJourney(
                publish = states::add,
                memoryStore = store,
                executor = DirectExecutorService(),
                dispatchMain = { action -> action() },
                nowEpochMillis = { 100L }
            )

        try {
            journey.onProductChanged(0, "Small Milk\nCA$4.00\n500 g")
            journey.onProductChanged(1, "Large Milk\nCA$7.00\n1 kg")
            journey.compare()

            assertNull(states.last().routeState)
            assertFalse(states.last().isComparing)
            assertTrue(store.state.entries.isEmpty())

            journey.onLikeForLikeChanged(true)
            journey.compare()

            assertEquals(CompareHereManualRouteStatus.EVALUATED, states.last().routeState?.status)
            assertEquals(2, store.state.entries.size)
            assertTrue(states.last().notice.orEmpty().contains("Remembered"))

            journey.onProductChanged(1, "Large Milk\nCA$7.50\n1 kg")
            assertFalse(states.last().likeForLikeConfirmed)
            assertNull(states.last().routeState)
        } finally {
            journey.close()
        }
    }

    @Test
    fun `member price choice is kept explicit for the comparison`() {
        val states = mutableListOf<ValuePilotNextCompareHereUiState>()
        val journey =
            ValuePilotNextCompareHereJourney(
                publish = states::add,
                memoryStore = InMemoryPriceMemoryStore(),
                executor = DirectExecutorService(),
                dispatchMain = { action -> action() },
                nowEpochMillis = { 100L }
            )

        try {
            journey.onProductChanged(
                0,
                "Small Milk\nCurrent price CA$5.00\nMember price CA$3.00\n500 g"
            )
            journey.onProductChanged(
                1,
                "Large Milk\nCurrent price CA$7.00\nMember price CA$4.00\n500 g"
            )
            journey.onPriceSelectionChanged(CompareHerePriceSelection.MEMBER)
            journey.onLikeForLikeChanged(true)
            journey.compare()

            assertEquals(CompareHereManualRouteStatus.EVALUATED, states.last().routeState?.status)
            assertEquals(
                "Member prices",
                states.last().routeState?.comparisonState?.priceModeText
            )
            assertEquals(
                listOf("3.00 CAD", "4.00 CAD"),
                states.last().routeState?.comparisonState?.rows?.map { it.priceText }
            )
        } finally {
            journey.close()
        }
    }

    private class InMemoryPriceMemoryStore : CompareHerePrivatePriceMemoryStore {
        var state = CompareHerePrivatePriceMemoryState.empty()
            private set

        override fun load() =
            CompareHerePrivatePriceMemoryLoadResult(
                state = state,
                foundStoredDocument = state.entries.isNotEmpty()
            )

        override fun append(capture: CompareHerePrivatePriceMemoryCapture): CompareHerePrivatePriceMemoryMutationResult {
            state = CompareHerePrivatePriceMemoryStateManager.append(state, capture)
            return CompareHerePrivatePriceMemoryMutationResult(state)
        }

        override fun remove(observationId: String): CompareHerePrivatePriceMemoryMutationResult {
            state = CompareHerePrivatePriceMemoryState(
                state.entries.filterNot { it.observationId == observationId }
            )
            return CompareHerePrivatePriceMemoryMutationResult(state)
        }

        override fun clear(): CompareHerePrivatePriceMemoryMutationResult {
            state = CompareHerePrivatePriceMemoryState.empty()
            return CompareHerePrivatePriceMemoryMutationResult(state)
        }
    }

    private class DirectExecutorService : AbstractExecutorService() {
        @Volatile
        private var shutdown = false

        override fun execute(command: Runnable) {
            check(!shutdown)
            command.run()
        }

        override fun shutdown() {
            shutdown = true
        }

        override fun shutdownNow(): MutableList<Runnable> {
            shutdown = true
            return mutableListOf()
        }

        override fun isShutdown(): Boolean = shutdown

        override fun isTerminated(): Boolean = shutdown

        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = shutdown
    }
}
