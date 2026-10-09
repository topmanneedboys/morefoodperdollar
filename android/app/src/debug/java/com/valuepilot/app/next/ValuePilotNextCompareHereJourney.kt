package com.valuepilot.app.next

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.valuepilot.app.CompareHereManualDraftActionEvaluator
import com.valuepilot.app.CompareHereManualInputAdapter
import com.valuepilot.app.CompareHereManualRouteCoordinator
import com.valuepilot.app.CompareHereManualRouteState
import com.valuepilot.app.CompareHerePrivatePriceMemoryAndroidStore
import com.valuepilot.app.CompareHerePrivatePriceMemoryCapture
import com.valuepilot.app.CompareHerePrivatePriceMemoryStore
import com.valuepilot.core.CompareHerePriceSelection
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal data class ValuePilotNextCompareHereUiState(
    val productBlocks: List<String> = listOf("", ""),
    val priceSelection: CompareHerePriceSelection = CompareHerePriceSelection.CURRENT,
    val likeForLikeConfirmed: Boolean = false,
    val routeState: CompareHereManualRouteState? = null,
    val isComparing: Boolean = false,
    val notice: String? = null
)

/** Compose route adapter over the existing exact manual-comparison and device-only memory flows. */
internal class ValuePilotNextCompareHereJourney(
    private val publish: (ValuePilotNextCompareHereUiState) -> Unit,
    private val memoryStore: CompareHerePrivatePriceMemoryStore,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(),
    private val dispatchMain: ((() -> Unit) -> Unit) = { action ->
        Handler(Looper.getMainLooper()).post(action)
    },
    private val nowEpochMillis: () -> Long = System::currentTimeMillis
) : AutoCloseable {

    constructor(
        context: Context,
        publish: (ValuePilotNextCompareHereUiState) -> Unit,
        memoryStore: CompareHerePrivatePriceMemoryStore? = null
    ) : this(
        publish = publish,
        memoryStore = memoryStore ?: CompareHerePrivatePriceMemoryAndroidStore(
            context.applicationContext ?: context
        )
    )

    private var state = ValuePilotNextCompareHereUiState()
    @Volatile
    private var latestRequestId = 0L
    @Volatile
    private var closed = false

    fun currentState(): ValuePilotNextCompareHereUiState = state

    fun onProductChanged(index: Int, value: String) {
        if (!acceptsInput()) return
        require(index in state.productBlocks.indices)
        if (state.productBlocks[index] == value) return
        val next = state.productBlocks.toMutableList().also { it[index] = value }
        updateDraft(
            state.copy(
                productBlocks = next,
                likeForLikeConfirmed = false
            )
        )
    }

    fun addProduct() {
        if (!acceptsInput()) return
        if (state.productBlocks.size >= CompareHereManualInputAdapter.MAX_OBSERVATIONS) {
            state = state.copy(
                notice = "Compare up to ${CompareHereManualInputAdapter.MAX_OBSERVATIONS} products at a time."
            )
            publish(state)
            return
        }
        updateDraft(state.copy(productBlocks = state.productBlocks + ""))
    }

    fun removeProduct(index: Int) {
        if (!acceptsInput() || state.productBlocks.size <= 2) return
        require(index in state.productBlocks.indices)
        updateDraft(
            state.copy(
                productBlocks = state.productBlocks.filterIndexed { row, _ -> row != index },
                likeForLikeConfirmed = false
            )
        )
    }

    fun onLikeForLikeChanged(confirmed: Boolean) {
        if (!acceptsInput() || state.likeForLikeConfirmed == confirmed) return
        updateDraft(state.copy(likeForLikeConfirmed = confirmed))
    }

    fun onPriceSelectionChanged(selection: CompareHerePriceSelection) {
        if (!acceptsInput() || state.priceSelection == selection) return
        updateDraft(state.copy(priceSelection = selection))
    }

    fun compare() {
        if (!acceptsInput() || state.isComparing) return
        val actionState =
            CompareHereManualDraftActionEvaluator.evaluate(
                rawBlocks = state.productBlocks,
                likeForLikeConfirmed = state.likeForLikeConfirmed
            )
        if (!actionState.compareEnabled) return

        latestRequestId = Math.addExact(latestRequestId, 1L)
        val requestId = latestRequestId
        val blocks = state.productBlocks.toList()
        val selection = state.priceSelection
        val confirmed = state.likeForLikeConfirmed
        val observedAtEpochMillis = nowEpochMillis()
        state = state.copy(routeState = null, isComparing = true, notice = null)
        publish(state)

        executor.execute {
            val evaluation =
                runCatching {
                    CompareHereManualRouteCoordinator.evaluateBlocks(
                        rawBlocks = blocks,
                        observedAtEpochMillis = observedAtEpochMillis,
                        userConfirmedLikeForLike = confirmed,
                        priceSelection = selection
                    )
                }.getOrNull()

            if (requestId != latestRequestId || closed) return@execute

            val notice =
                evaluation?.privateMemoryCapture?.let(::rememberPrivately)
            dispatchMain {
                if (closed || requestId != latestRequestId) return@dispatchMain
                state =
                    if (evaluation == null) {
                        state.copy(
                            isComparing = false,
                            notice = "The comparison couldn't be completed. Check the product details and try again."
                        )
                    } else {
                        state.copy(
                            routeState = evaluation.state,
                            isComparing = false,
                            notice = notice
                        )
                    }
                publish(state)
            }
        }
    }

    fun clearDraft() {
        if (closed) return
        latestRequestId = Math.addExact(latestRequestId, 1L)
        state = ValuePilotNextCompareHereUiState()
        publish(state)
    }

    override fun close() {
        if (closed) return
        closed = true
        latestRequestId = Math.addExact(latestRequestId, 1L)
        executor.shutdownNow()
    }

    private fun updateDraft(next: ValuePilotNextCompareHereUiState) {
        latestRequestId = Math.addExact(latestRequestId, 1L)
        state = next.copy(routeState = null, isComparing = false, notice = null)
        publish(state)
    }

    private fun acceptsInput(): Boolean = !closed

    private fun rememberPrivately(capture: CompareHerePrivatePriceMemoryCapture): String =
        runCatching { memoryStore.append(capture) }
            .getOrNull()
            ?.takeIf { it.accepted }
            ?.let {
                "Remembered ${capture.entries.size} product prices privately on this device. This is comparison history, not live store pricing."
            }
            ?: "The comparison is ready, but private history couldn't be updated. Nothing was sent online."
}
