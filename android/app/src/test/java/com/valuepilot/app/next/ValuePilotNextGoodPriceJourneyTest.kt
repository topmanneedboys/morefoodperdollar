package com.valuepilot.app.next

import com.valuepilot.app.CompareHerePrivatePriceMemoryLoadResult
import com.valuepilot.app.CompareHerePrivatePriceMemoryMutationResult
import com.valuepilot.app.CompareHerePrivatePriceMemoryState
import com.valuepilot.app.CompareHerePrivatePriceMemoryStateManager
import com.valuepilot.app.CompareHerePrivatePriceMemoryStore
import com.valuepilot.app.CompareHerePrivatePriceMemoryCapture
import com.valuepilot.app.GoodPriceCheckRouteStatus
import com.valuepilot.core.CompareHerePriceSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValuePilotNextGoodPriceJourneyTest {

    @Test
    fun `saved product opens an editable draft and exact check is remembered privately`() {
        val store = InMemoryPriceMemoryStore()
        val states = mutableListOf<ValuePilotNextGoodPriceUiState>()
        val journey = ValuePilotNextGoodPriceJourney(store, states::add) { 100L }

        journey.openFromSaved("Whole Milk")
        assertEquals("Whole Milk", states.last().rawBlock)
        assertNull(states.last().route.result)

        journey.onDraftChanged("Whole Milk\nCA$6.49\n4 L")
        journey.checkPrice()

        val checked = states.last()
        assertEquals(GoodPriceCheckRouteStatus.EVALUATED, checked.route.status)
        assertEquals("1.6225 CAD/L", checked.route.result?.unitRateText)
        assertEquals(1, store.state.entries.size)
        assertTrue(checked.memoryNotice.orEmpty().contains("saved to private price memory"))
        assertFalse(checked.route.shareCard?.text.orEmpty().contains("Whole Milk"))
    }

    @Test
    fun `member price selection does not fall back or create a memory entry`() {
        val store = InMemoryPriceMemoryStore()
        val states = mutableListOf<ValuePilotNextGoodPriceUiState>()
        val journey = ValuePilotNextGoodPriceJourney(store, states::add) { 100L }

        journey.onDraftChanged("Whole Milk\nCurrent price CA$8.00\n4 L")
        journey.onPriceSelectionChanged(CompareHerePriceSelection.MEMBER)
        journey.checkPrice()

        assertEquals(GoodPriceCheckRouteStatus.NEEDS_EXACT_INFORMATION, states.last().route.status)
        assertTrue(states.last().route.guidance.contains("Member price unavailable"))
        assertTrue(store.state.entries.isEmpty())
    }

    @Test
    fun `clearing private memory clears the visible answer and stored observations`() {
        val store = InMemoryPriceMemoryStore()
        val states = mutableListOf<ValuePilotNextGoodPriceUiState>()
        val journey = ValuePilotNextGoodPriceJourney(store, states::add) { 100L }

        journey.onDraftChanged("Whole Milk\nCA$6.49\n4 L")
        journey.checkPrice()
        journey.clearPrivateMemory()

        assertTrue(store.state.entries.isEmpty())
        assertNull(states.last().route.result)
        assertEquals("Private price memory was cleared from this device.", states.last().memoryNotice)
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
}
