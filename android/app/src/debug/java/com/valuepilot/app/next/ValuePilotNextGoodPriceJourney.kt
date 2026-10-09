package com.valuepilot.app.next

import com.valuepilot.app.CompareHerePrivatePriceMemoryState
import com.valuepilot.app.CompareHerePrivatePriceMemoryStore
import com.valuepilot.app.CompareHerePrivatePriceMemoryStoreIssue
import com.valuepilot.app.GoodPriceCheckRouteCoordinator
import com.valuepilot.app.GoodPriceCheckRouteState
import com.valuepilot.app.GoodPriceCheckRouteStatus
import com.valuepilot.app.GoodPriceActivityPrefill
import com.valuepilot.core.CompareHerePriceSelection

private const val MAX_GOOD_PRICE_DRAFT_CHARS = 4_096

internal data class ValuePilotNextGoodPriceUiState(
    val rawBlock: String = "",
    val priceSelection: CompareHerePriceSelection = CompareHerePriceSelection.CURRENT,
    val route: GoodPriceCheckRouteState =
        GoodPriceCheckRouteState(
            status = GoodPriceCheckRouteStatus.NEEDS_PRODUCT,
            title = "Ready to check a price",
            guidance = "Enter one product, its package quantity, and the price you observed."
        ),
    val memoryNotice: String? = null,
    val memoryUnavailable: Boolean = false,
    val canClearMemory: Boolean = false
)

/** Compose presentation adapter for the existing exact Good Price route and private memory. */
internal class ValuePilotNextGoodPriceJourney(
    private val memoryStore: CompareHerePrivatePriceMemoryStore,
    private val publish: (ValuePilotNextGoodPriceUiState) -> Unit,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private var privateMemory = CompareHerePrivatePriceMemoryState.empty()
    private var memoryIssue: CompareHerePrivatePriceMemoryStoreIssue? = null
    private var state = ValuePilotNextGoodPriceUiState()

    init {
        reloadMemory(invalidateResult = false)
        publish(state)
    }

    fun openFromSaved(displayName: String) {
        val safeName = GoodPriceActivityPrefill.sanitize(displayName).orEmpty()
        state =
            ValuePilotNextGoodPriceUiState(
                rawBlock = safeName,
                priceSelection = CompareHerePriceSelection.CURRENT,
                route =
                    GoodPriceCheckRouteState(
                        status = GoodPriceCheckRouteStatus.NEEDS_PRODUCT,
                        title = "Add the package and price",
                        guidance =
                            "Complete this saved product with its exact package quantity and the price you observed."
                    ),
                memoryNotice = unavailableNoticeIfNeeded(),
                memoryUnavailable = memoryIssue != null,
                canClearMemory = privateMemory.entries.isNotEmpty() || memoryIssue != null
            )
        publish(state)
    }

    fun onDraftChanged(rawBlock: String) {
        state =
            state.copy(
                rawBlock = rawBlock.take(MAX_GOOD_PRICE_DRAFT_CHARS),
                route = idleRoute(),
                memoryNotice = unavailableNoticeIfNeeded(),
                memoryUnavailable = memoryIssue != null,
                canClearMemory = privateMemory.entries.isNotEmpty() || memoryIssue != null
            )
        publish(state)
    }

    fun onPriceSelectionChanged(selection: CompareHerePriceSelection) {
        if (state.priceSelection == selection) return
        state =
            state.copy(
                priceSelection = selection,
                route = idleRoute(),
                memoryNotice = unavailableNoticeIfNeeded(),
                memoryUnavailable = memoryIssue != null,
                canClearMemory = privateMemory.entries.isNotEmpty() || memoryIssue != null
            )
        publish(state)
    }

    fun checkPrice() {
        val evaluatedAt = nowMillis()
        val evaluation =
            GoodPriceCheckRouteCoordinator.checkBlock(
                rawBlock = state.rawBlock,
                observedAtEpochMillis = evaluatedAt,
                priceSelection = state.priceSelection,
                privateMemory = privateMemory
            )
        var notice = unavailableNoticeIfNeeded()
        var unavailable = memoryIssue != null
        var canClear = privateMemory.entries.isNotEmpty() || memoryIssue != null
        val capture = evaluation.privateMemoryCapture
        if (capture != null) {
            val saved = memoryStore.append(capture)
            if (saved.accepted) {
                privateMemory = requireNotNull(saved.state)
                memoryIssue = null
                notice = "This exact check was saved to private price memory on this device."
                unavailable = false
                canClear = true
            } else if (
                memoryIssue != null ||
                    saved.issue == CompareHerePrivatePriceMemoryStoreIssue.STORED_DATA_INVALID
            ) {
                memoryIssue = saved.issue ?: memoryIssue
                notice = "Private price memory is unavailable. Clear it to recover."
                unavailable = true
                canClear = true
            } else {
                notice = "The check is ready, but it could not be saved to private price memory."
                canClear = true
            }
        }
        state =
            state.copy(
                route = evaluation.state,
                memoryNotice = notice,
                memoryUnavailable = unavailable,
                canClearMemory = canClear
            )
        publish(state)
    }

    fun clearDraft() {
        state =
            state.copy(
                rawBlock = "",
                priceSelection = CompareHerePriceSelection.CURRENT,
                route = idleRoute(),
                memoryNotice = unavailableNoticeIfNeeded(),
                memoryUnavailable = memoryIssue != null,
                canClearMemory = privateMemory.entries.isNotEmpty() || memoryIssue != null
            )
        publish(state)
    }

    fun clearPrivateMemory() {
        val result = memoryStore.clear()
        if (result.accepted) {
            privateMemory = CompareHerePrivatePriceMemoryState.empty()
            memoryIssue = null
            state =
                state.copy(
                    route = idleRoute(),
                    memoryNotice = "Private price memory was cleared from this device.",
                    memoryUnavailable = false,
                    canClearMemory = false
                )
        } else {
            memoryIssue = result.issue ?: memoryIssue
            state =
                state.copy(
                    memoryNotice = "Private price memory could not be cleared.",
                    memoryUnavailable = memoryIssue != null,
                    canClearMemory = true
                )
        }
        publish(state)
    }

    fun refreshPrivateMemory() {
        reloadMemory(invalidateResult = true)
        publish(state)
    }

    private fun reloadMemory(invalidateResult: Boolean) {
        val previousMemory = privateMemory
        val previousIssue = memoryIssue
        val loaded = memoryStore.load()
        privateMemory = loaded.state ?: CompareHerePrivatePriceMemoryState.empty()
        memoryIssue = loaded.issue
        val memoryChanged = previousMemory != privateMemory || previousIssue != memoryIssue
        state =
            state.copy(
                route = if (invalidateResult && memoryChanged) idleRoute() else state.route,
                memoryNotice = if (memoryIssue == null) null else unavailableNoticeIfNeeded(),
                memoryUnavailable = memoryIssue != null,
                canClearMemory = privateMemory.entries.isNotEmpty() || memoryIssue != null
            )
    }

    private fun unavailableNoticeIfNeeded(): String? =
        if (memoryIssue == null) null else "Private price memory is unavailable. Clear it to recover."

    private fun idleRoute(): GoodPriceCheckRouteState =
        GoodPriceCheckRouteState(
            status = GoodPriceCheckRouteStatus.NEEDS_PRODUCT,
            title = "Ready to check a price",
            guidance = "Enter one product, its package quantity, and the price you observed."
        )
}
