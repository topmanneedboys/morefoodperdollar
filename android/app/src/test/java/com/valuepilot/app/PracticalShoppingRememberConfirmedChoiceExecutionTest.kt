package com.valuepilot.app

import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.PracticalShoppingProductIdentityCandidate
import com.valuepilot.core.PracticalShoppingProductIntentRelationship
import com.valuepilot.core.PracticalShoppingStoreIdentityCandidate
import com.valuepilot.core.PracticalShoppingStoreIdentityRelationship
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.SourceProductIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingRememberConfirmedChoiceExecutionTest {

    @Test
    fun `remember runs on worker and completion is applied only through owner dispatcher`() {
        val worker = QueueScheduler()
        val owner = QueueDispatcher()
        val gateway = RecordingGateway(completedResult())
        val completions = mutableListOf<PracticalShoppingRememberConfirmedChoiceCompletion>()
        val host = host(gateway, worker, owner, completions)

        assertTrue(host.remember(productRequest()))
        assertTrue(host.isBusy())
        assertEquals(1, worker.pendingCount)
        assertEquals(0, owner.pendingCount)
        assertEquals(0, gateway.calls)
        assertTrue(completions.isEmpty())

        worker.runNext()

        assertEquals(1, gateway.calls)
        assertEquals(1, owner.pendingCount)
        assertTrue(host.isBusy())
        assertTrue(completions.isEmpty())

        owner.runNext()

        assertFalse(host.isBusy())
        assertEquals(1, completions.size)
        assertEquals(1L, completions.single().requestId)
        assertTrue(
            completions.single().outcome is
                PracticalShoppingRememberConfirmedChoiceExecutionOutcome.Completed
        )
    }

    @Test
    fun `double submit while one remember is in flight schedules only first request`() {
        val worker = QueueScheduler()
        val owner = QueueDispatcher()
        val gateway = RecordingGateway(completedResult())
        val host = host(gateway, worker, owner, mutableListOf())

        assertTrue(host.remember(productRequest("first")))
        assertFalse(host.remember(productRequest("second")))

        assertEquals(1, worker.pendingCount)
        worker.runNext()
        owner.runNext()
        assertEquals(1, gateway.calls)
        assertEquals("first", gateway.requests.single().displayName())
    }

    @Test
    fun `unexpected gateway exception becomes typed failure and host can accept later request`() {
        val worker = QueueScheduler()
        val owner = QueueDispatcher()
        val gateway = ThrowOnceGateway()
        val completions = mutableListOf<PracticalShoppingRememberConfirmedChoiceCompletion>()
        val host = host(gateway, worker, owner, completions)

        assertTrue(host.remember(productRequest("first")))
        worker.runNext()
        owner.runNext()

        assertEquals(
            PracticalShoppingRememberConfirmedChoiceExecutionOutcome.Failed,
            completions.single().outcome
        )
        assertFalse(host.isBusy())

        assertTrue(host.remember(productRequest("second")))
        worker.runNext()
        owner.runNext()

        assertEquals(2, completions.size)
        assertEquals(2L, completions.last().requestId)
        assertTrue(
            completions.last().outcome is
                PracticalShoppingRememberConfirmedChoiceExecutionOutcome.Completed
        )
    }

    @Test
    fun `close does not cancel queued persistence but suppresses late completion and clears busy state`() {
        val worker = QueueScheduler()
        val owner = QueueDispatcher()
        val gateway = RecordingGateway(completedResult())
        val completions = mutableListOf<PracticalShoppingRememberConfirmedChoiceCompletion>()
        val host = host(gateway, worker, owner, completions)

        assertTrue(host.remember(productRequest()))
        host.close()

        assertTrue(host.isClosed())
        assertFalse(host.isBusy())
        assertFalse(host.remember(productRequest("later")))

        worker.runNext()
        assertEquals(1, gateway.calls)
        assertEquals(1, owner.pendingCount)

        owner.runNext()
        assertTrue(completions.isEmpty())
        assertFalse(host.isBusy())
    }

    @Test
    fun `same serial worker keeps previously queued Saved work ahead of remember write`() {
        val worker = QueueScheduler()
        val owner = QueueDispatcher()
        val order = mutableListOf<String>()
        val gateway =
            object : PracticalShoppingRememberConfirmedChoiceGateway {
                override fun remember(
                    request: PracticalShoppingRememberConfirmedChoiceRequest
                ): PracticalShoppingRememberConfirmedChoiceResult {
                    order += "remember"
                    return completedResult()
                }
            }
        val host = host(gateway, worker, owner, mutableListOf())

        worker.schedule { order += "saved" }
        assertTrue(host.remember(productRequest()))

        worker.runNext()
        assertEquals(listOf("saved"), order)

        worker.runNext()
        assertEquals(listOf("saved", "remember"), order)
        owner.runNext()
    }

    @Test
    fun `local gateway routes all remember request variants through verified persistence boundaries`() {
        val exactBytes = FakeExactByteStorage()
        val displayBytes = FakeDisplayByteStorage()
        val exactStore = PracticalShoppingSavedExactPreferenceLocalStore(exactBytes)
        val displayStore = PracticalShoppingSavedDisplayMetadataLocalStore(displayBytes)
        val gateway = PracticalShoppingRememberConfirmedChoiceLocalGateway(exactStore, displayStore)

        val offRow =
            OpenFoodFactsImportedProduct(
                code = "042100005264",
                productName = "Source Milk",
                productQuantity = null,
                productQuantityUnit = null
            )
        val offSuggestion =
            requireNotNull(
                OpenFoodFactsPracticalShoppingIdentityAdapter.catalogSuggestion(
                    itemKey = ShoppingItemKey("source-milk"),
                    row = offRow,
                    candidateId = "off-suggestion"
                ).candidate
            )
        val offConfirmed =
            requireNotNull(
                PracticalShoppingExactProductConfirmationAdapter.confirmSelection(
                    itemKey = ShoppingItemKey("source-milk"),
                    selectedCandidate = offSuggestion,
                    candidateId = "off-confirmed"
                ).candidate
            )

        val osmIdentity =
            OpenStreetMapPracticalShoppingStoreRecord(
                elementType = OpenStreetMapElementType.NODE,
                elementId = 22L,
                brandWikidataId = "Q483551"
            )
        val osmSuggestion =
            requireNotNull(
                OpenStreetMapPracticalShoppingStoreSuggestionAdapter.locationSuggestion(
                    storeKey = ShoppingStoreKey("source-store"),
                    row = osmIdentity,
                    candidateId = "osm-suggestion"
                ).candidate
            )
        val osmConfirmed =
            requireNotNull(
                PracticalShoppingExactStoreConfirmationAdapter.confirmSelection(
                    storeKey = ShoppingStoreKey("source-store"),
                    selectedCandidate = osmSuggestion,
                    candidateId = "osm-confirmed"
                ).candidate
            )

        val results =
            listOf(
                gateway.remember(productRequest("User Eggs")),
                gateway.remember(
                    PracticalShoppingRememberConfirmedChoiceRequest.OpenFoodFactsProduct(
                        confirmedCandidate = offConfirmed,
                        row = offRow
                    )
                ),
                gateway.remember(
                    PracticalShoppingRememberConfirmedChoiceRequest.StoreWithUserLabel(
                        confirmedCandidate = confirmedStore("user-store", 11L),
                        displayName = "User Market"
                    )
                ),
                gateway.remember(
                    PracticalShoppingRememberConfirmedChoiceRequest.OpenStreetMapStore(
                        confirmedCandidate = osmConfirmed,
                        row =
                            OpenStreetMapPracticalShoppingStoreDisplayRecord(
                                identity = osmIdentity,
                                name = "Source Market"
                            )
                    )
                )
            )

        assertTrue(results.all { it.fullyLabeled })

        val loaded = PracticalShoppingSavedExperienceCoordinator.load(exactStore, displayStore)
        val state = requireNotNull(loaded.projection).state
        assertEquals(setOf("User Eggs", "Source Milk"), state.productRows.map { it.title }.toSet())
        assertEquals(setOf("User Market", "Source Market"), state.storeRows.map { it.title }.toSet())
        assertEquals(0, state.unresolvedDisplayNameCount)
    }

    private fun host(
        gateway: PracticalShoppingRememberConfirmedChoiceGateway,
        worker: QueueScheduler,
        owner: QueueDispatcher,
        completions: MutableList<PracticalShoppingRememberConfirmedChoiceCompletion>
    ): PracticalShoppingRememberConfirmedChoiceHost =
        PracticalShoppingRememberConfirmedChoiceHost(
            gateway = gateway,
            worker = worker,
            completionDispatcher = owner,
            completionListener =
                PracticalShoppingRememberConfirmedChoiceCompletionListener(completions::add)
        )

    private class RecordingGateway(
        private val result: PracticalShoppingRememberConfirmedChoiceResult
    ) : PracticalShoppingRememberConfirmedChoiceGateway {
        var calls = 0
        val requests = mutableListOf<PracticalShoppingRememberConfirmedChoiceRequest>()

        override fun remember(
            request: PracticalShoppingRememberConfirmedChoiceRequest
        ): PracticalShoppingRememberConfirmedChoiceResult {
            calls += 1
            requests += request
            return result
        }
    }

    private class ThrowOnceGateway : PracticalShoppingRememberConfirmedChoiceGateway {
        private var calls = 0

        override fun remember(
            request: PracticalShoppingRememberConfirmedChoiceRequest
        ): PracticalShoppingRememberConfirmedChoiceResult {
            calls += 1
            if (calls == 1) throw IllegalStateException("synthetic")
            return completedResult()
        }
    }

    private class QueueScheduler : PracticalShoppingSavedWorkScheduler {
        private val queue = ArrayDeque<() -> Unit>()

        val pendingCount: Int
            get() = queue.size

        override fun schedule(block: () -> Unit) {
            queue.addLast(block)
        }

        fun runNext() {
            queue.removeFirst().invoke()
        }
    }

    private class QueueDispatcher : PracticalShoppingSavedCompletionDispatcher {
        private val queue = ArrayDeque<() -> Unit>()

        val pendingCount: Int
            get() = queue.size

        override fun dispatch(block: () -> Unit) {
            queue.addLast(block)
        }

        fun runNext() {
            queue.removeFirst().invoke()
        }
    }

    private class FakeExactByteStorage : PracticalShoppingSavedExactPreferenceByteStorage {
        private var bytes: ByteArray? = null

        override fun read(maxBytes: Int): PracticalShoppingSavedExactPreferenceRawReadResult {
            val current = bytes
                ?: return PracticalShoppingSavedExactPreferenceRawReadResult(
                    bytes = null,
                    found = false
                )
            return PracticalShoppingSavedExactPreferenceRawReadResult(
                bytes = current.copyOf(),
                found = true
            )
        }

        override fun replace(bytes: ByteArray): Boolean {
            this.bytes = bytes.copyOf()
            return true
        }

        override fun delete(): Boolean {
            bytes = null
            return true
        }
    }

    private class FakeDisplayByteStorage : PracticalShoppingSavedDisplayMetadataByteStorage {
        private var bytes: ByteArray? = null

        override fun read(maxBytes: Int): PracticalShoppingSavedDisplayMetadataRawReadResult {
            val current = bytes
                ?: return PracticalShoppingSavedDisplayMetadataRawReadResult(
                    bytes = null,
                    found = false
                )
            return PracticalShoppingSavedDisplayMetadataRawReadResult(
                bytes = current.copyOf(),
                found = true
            )
        }

        override fun replace(bytes: ByteArray): Boolean {
            this.bytes = bytes.copyOf()
            return true
        }

        override fun delete(): Boolean {
            bytes = null
            return true
        }
    }

    companion object {
        private fun productRequest(
            displayName: String = "Example Eggs"
        ): PracticalShoppingRememberConfirmedChoiceRequest.ProductWithUserLabel =
            PracticalShoppingRememberConfirmedChoiceRequest.ProductWithUserLabel(
                confirmedCandidate =
                    PracticalShoppingProductIdentityCandidate(
                        candidateId = "confirmed-eggs",
                        itemKey = ShoppingItemKey("user-eggs"),
                        providerId = EvidenceProviderId("confirmed-catalog"),
                        sourceIdentity = SourceProductIdentity(gtin = "036000291452"),
                        relationship =
                            PracticalShoppingProductIntentRelationship.USER_CONFIRMED_EXACT_PRODUCT
                    ),
                displayName = displayName
            )

        private fun confirmedStore(
            key: String,
            nodeId: Long
        ): PracticalShoppingStoreIdentityCandidate =
            PracticalShoppingStoreIdentityCandidate(
                candidateId = "confirmed-$key-$nodeId",
                storeKey = ShoppingStoreKey(key),
                scope =
                    PracticalShoppingStoreIdentityScope(
                        merchantKey = "wikidata:Q483551",
                        locationKey = "osm:node:$nodeId",
                        commerceChannelKey = "PHYSICAL_STORE"
                    ),
                relationship = PracticalShoppingStoreIdentityRelationship.USER_CONFIRMED_EXACT_STORE,
                providerId = EvidenceProviderId("confirmed-store")
            )

        private fun PracticalShoppingRememberConfirmedChoiceRequest.displayName(): String? =
            (this as? PracticalShoppingRememberConfirmedChoiceRequest.ProductWithUserLabel)
                ?.displayName

        private fun completedResult(): PracticalShoppingRememberConfirmedChoiceResult =
            PracticalShoppingRememberConfirmedChoiceResult(
                exactResult =
                    PracticalShoppingSavedExactPreferenceTransactionResult(
                        state = PracticalShoppingSavedExactPreferenceState.empty()
                    ),
                displayFailures =
                    setOf(PracticalShoppingSavedDisplayMetadataFailure.DISPLAY_NAME_UNAVAILABLE)
            )
    }
}
