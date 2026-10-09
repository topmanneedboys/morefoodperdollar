package com.valuepilot.app.next

import com.valuepilot.app.CompareHerePrivatePriceMemoryState
import com.valuepilot.app.CompareHerePrivatePriceMemoryStore
import com.valuepilot.app.CompareHerePrivatePriceMemoryStoreIssue
import com.valuepilot.app.GoodPriceCheckRouteCoordinator
import com.valuepilot.app.GoodPriceCheckRouteState
import com.valuepilot.app.GoodPriceCheckRouteStatus
import com.valuepilot.app.GoodPriceActivityPrefill
import com.valuepilot.app.GoodPriceBarcodeIdentityPresentation
import com.valuepilot.core.CompareHerePriceSelection
import com.valuepilot.core.GtinValidation

private const val MAX_GOOD_PRICE_DRAFT_CHARS = 4_096

internal data class ValuePilotNextGoodPriceBarcodeChoice(
    val requestId: Long,
    val gtin: String,
    val options: List<GoodPriceBarcodeIdentityPresentation.Option>,
    val replacesDraft: Boolean
) {
    init {
        require(requestId > 0L)
        require(GtinValidation.isValid(gtin))
        require(options.isNotEmpty())
        require(options.size <= 24)
    }
}

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
    val canClearMemory: Boolean = false,
    val barcodeMessage: String? = null,
    val barcodeBusy: Boolean = false,
    val barcodeError: Boolean = false,
    val barcodeChoice: ValuePilotNextGoodPriceBarcodeChoice? = null,
    val barcodeFocusRequestId: Int = 0
)

/** Compose presentation adapter for the existing exact Good Price route and private memory. */
internal class ValuePilotNextGoodPriceJourney(
    private val memoryStore: CompareHerePrivatePriceMemoryStore,
    private val publish: (ValuePilotNextGoodPriceUiState) -> Unit,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private var privateMemory = CompareHerePrivatePriceMemoryState.empty()
    private var memoryIssue: CompareHerePrivatePriceMemoryStoreIssue? = null
    private var barcodeRequestId = 0L
    private var state = ValuePilotNextGoodPriceUiState()

    init {
        reloadMemory(invalidateResult = false)
        publish(state)
    }

    fun openFromSaved(displayName: String) {
        invalidateBarcodeFlow()
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

    fun openForNewCheck() {
        invalidateBarcodeFlow()
        state =
            ValuePilotNextGoodPriceUiState(
                memoryNotice = unavailableNoticeIfNeeded(),
                memoryUnavailable = memoryIssue != null,
                canClearMemory = privateMemory.entries.isNotEmpty() || memoryIssue != null
            )
        publish(state)
    }

    fun onDraftChanged(rawBlock: String) {
        invalidateBarcodeFlow()
        state =
            state.copy(
                rawBlock = rawBlock.take(MAX_GOOD_PRICE_DRAFT_CHARS),
                route = idleRoute(),
                memoryNotice = unavailableNoticeIfNeeded(),
                memoryUnavailable = memoryIssue != null,
                canClearMemory = privateMemory.entries.isNotEmpty() || memoryIssue != null,
                barcodeMessage = null,
                barcodeBusy = false,
                barcodeError = false,
                barcodeChoice = null
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
        invalidateBarcodeFlow()
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
                canClearMemory = canClear,
                barcodeMessage = null,
                barcodeBusy = false,
                barcodeError = false,
                barcodeChoice = null
            )
        publish(state)
    }

    fun clearDraft() {
        invalidateBarcodeFlow()
        state =
            state.copy(
                rawBlock = "",
                priceSelection = CompareHerePriceSelection.CURRENT,
                route = idleRoute(),
                memoryNotice = unavailableNoticeIfNeeded(),
                memoryUnavailable = memoryIssue != null,
                canClearMemory = privateMemory.entries.isNotEmpty() || memoryIssue != null,
                barcodeMessage = null,
                barcodeBusy = false,
                barcodeError = false,
                barcodeChoice = null
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

    fun onBarcodeScanRequested() {
        invalidateBarcodeFlow()
        state =
            state.copy(
                barcodeMessage = "Opening the on-device barcode scanner…",
                barcodeBusy = true,
                barcodeError = false,
                barcodeChoice = null
            )
        publish(state)
    }

    fun onBarcodeScanCancelled() {
        invalidateBarcodeFlow()
        setBarcodeStatus("Barcode scan cancelled.")
    }

    fun onBarcodeScanUnavailable() {
        invalidateBarcodeFlow()
        setBarcodeStatus("The barcode scanner could not read a product code.", error = true)
    }

    fun beginBarcodeLookup(gtin: String): Long? {
        val normalized = gtin.trim()
        if (!GtinValidation.isValid(normalized)) {
            onBarcodeScanUnavailable()
            return null
        }
        barcodeRequestId = Math.addExact(barcodeRequestId, 1L)
        state =
            state.copy(
                barcodeMessage = "Looking for an exact name in the on-device catalog…",
                barcodeBusy = true,
                barcodeError = false,
                barcodeChoice = null
            )
        publish(state)
        return barcodeRequestId
    }

    fun onBarcodeLookupCompleted(
        requestId: Long,
        presentation: GoodPriceBarcodeIdentityPresentation?
    ) {
        if (requestId != barcodeRequestId) return
        if (presentation == null) {
            setBarcodeStatus("The on-device catalog could not be checked.", error = true)
            return
        }
        if (presentation.options.isEmpty()) {
            setBarcodeStatus(
                "No exact product name was found for barcode ${presentation.gtin}."
            )
            return
        }

        val replacesDraft = state.rawBlock.isNotBlank()
        if (presentation.options.size == 1 && !replacesDraft) {
            applyBarcodeOption(presentation.gtin, presentation.options.single())
            return
        }

        state =
            state.copy(
                barcodeMessage = "Choose the product name that matches the package.",
                barcodeBusy = false,
                barcodeError = false,
                barcodeChoice =
                    ValuePilotNextGoodPriceBarcodeChoice(
                        requestId = requestId,
                        gtin = presentation.gtin,
                        options = presentation.options,
                        replacesDraft = replacesDraft
                    )
            )
        publish(state)
    }

    fun onBarcodeLookupFailed(requestId: Long) {
        if (requestId != barcodeRequestId) return
        setBarcodeStatus("The on-device catalog could not be checked.", error = true)
    }

    fun dismissBarcodeChoice(requestId: Long) {
        if (state.barcodeChoice?.requestId != requestId) return
        invalidateBarcodeFlow()
        setBarcodeStatus("Barcode suggestion was not applied.")
    }

    fun chooseBarcodeOption(requestId: Long, optionIndex: Int) {
        val choice = state.barcodeChoice?.takeIf { it.requestId == requestId } ?: return
        val option = choice.options.getOrNull(optionIndex) ?: return
        applyBarcodeOption(choice.gtin, option)
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

    private fun applyBarcodeOption(
        gtin: String,
        option: GoodPriceBarcodeIdentityPresentation.Option
    ) {
        val safeName = GoodPriceActivityPrefill.sanitize(option.displayName)
        if (safeName == null) {
            setBarcodeStatus("That catalog name cannot be used. Enter the product name manually.", error = true)
            return
        }
        barcodeRequestId = Math.addExact(barcodeRequestId, 1L)
        state =
            state.copy(
                rawBlock = safeName,
                route =
                    GoodPriceCheckRouteState(
                        status = GoodPriceCheckRouteStatus.NEEDS_PRODUCT,
                        title = "Complete the product details",
                        guidance =
                            "The barcode suggested an editable name only. Add the exact package quantity and the price you observed."
                    ),
                memoryNotice = unavailableNoticeIfNeeded(),
                memoryUnavailable = memoryIssue != null,
                canClearMemory = privateMemory.entries.isNotEmpty() || memoryIssue != null,
                barcodeMessage = "${option.label} suggested from barcode $gtin. Add the package and price yourself.",
                barcodeBusy = false,
                barcodeError = false,
                barcodeChoice = null,
                barcodeFocusRequestId = Math.addExact(state.barcodeFocusRequestId, 1)
            )
        publish(state)
    }

    private fun setBarcodeStatus(message: String, error: Boolean = false) {
        state =
            state.copy(
                barcodeMessage = message,
                barcodeBusy = false,
                barcodeError = error,
                barcodeChoice = null
            )
        publish(state)
    }

    private fun invalidateBarcodeFlow() {
        barcodeRequestId = Math.addExact(barcodeRequestId, 1L)
    }

    private fun idleRoute(): GoodPriceCheckRouteState =
        GoodPriceCheckRouteState(
            status = GoodPriceCheckRouteStatus.NEEDS_PRODUCT,
            title = "Ready to check a price",
            guidance = "Enter one product, its package quantity, and the price you observed."
        )
}
