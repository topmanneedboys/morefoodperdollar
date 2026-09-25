package com.valuepilot.app

import com.valuepilot.core.PracticalShoppingProductionOrchestrationRequest
import com.valuepilot.core.ShoppingItemKey

private const val MAX_PRODUCTION_HOME_ITEMS = 128
private const val MAX_PRODUCTION_HOME_DISPLAY_CHARS = 160

/**
 * Consumer-ready item row for the future production Home surface.
 *
 * [itemKey] remains an opaque typed action key; it is never rendered as text.
 * All other fields are already display-safe or are an explicit unknown-price
 * notice. No row implies that identity data alone proves a price or stock.
 */
data class PracticalShoppingProductionHomeItemUiState(
    val itemKey: ShoppingItemKey,
    val name: String,
    val storeAssignment: String? = null,
    val plannedPriceText: String? = null,
    val plannedPriceNotice: String? = null,
    val coverageNotice: String? = null
) {
    init {
        require(itemKey.value.isNotBlank())
        require(name.isNotBlank())
        require(name.length <= MAX_PRODUCTION_HOME_DISPLAY_CHARS)
        require(name.none { character -> character.isISOControl() })
        require(storeAssignment == null || storeAssignment.isNotBlank())
        require(storeAssignment == null || storeAssignment.length <= MAX_PRODUCTION_HOME_DISPLAY_CHARS)
        require(storeAssignment == null || storeAssignment.none { character -> character.isISOControl() })
        require(plannedPriceText == null || plannedPriceText.isNotBlank())
        require(plannedPriceText == null || plannedPriceText.length <= MAX_PRODUCTION_HOME_DISPLAY_CHARS)
        require(plannedPriceText == null || plannedPriceText.none { character -> character.isISOControl() })
        require(plannedPriceNotice == null || plannedPriceNotice.isNotBlank())
        require(plannedPriceNotice == null || plannedPriceNotice.length <= MAX_PRODUCTION_HOME_DISPLAY_CHARS)
        require(plannedPriceNotice == null || plannedPriceNotice.none { character -> character.isISOControl() })
        require(plannedPriceText == null || plannedPriceNotice == null) {
            "An item cannot expose an exact price and a missing-breakdown notice together"
        }
        require(plannedPriceNotice == null || storeAssignment != null) {
            "A missing-breakdown notice requires a planned store assignment"
        }
        require(coverageNotice == null || coverageNotice.isNotBlank())
        require(coverageNotice == null || coverageNotice.length <= MAX_PRODUCTION_HOME_DISPLAY_CHARS)
        require(coverageNotice == null || coverageNotice.none { character -> character.isISOControl() })
    }

    /** Keep opaque item identity out of logs and accessibility/debug text. */
    override fun toString(): String =
        "PracticalShoppingProductionHomeItemUiState(" +
            "name=$name, storeAssignment=$storeAssignment, " +
            "plannedPriceText=$plannedPriceText, plannedPriceNotice=$plannedPriceNotice, " +
            "coverageNotice=$coverageNotice)"
}

/**
 * Demo-free, renderer-ready production Home state.
 *
 * This state contains no exact decision, internal store keys, provider facts or
 * sample/demo notice. A valid no-coverage result is [READY] with rows whose
 * [PracticalShoppingProductionHomeItemUiState.coverageNotice] stays explicit;
 * structural/reference failures are [UNAVAILABLE] instead.
 */
data class PracticalShoppingProductionHomeUiState(
    val status: PracticalShoppingProductionHomeStatus,
    val items: List<PracticalShoppingProductionHomeItemUiState> = emptyList(),
    val result: PracticalShoppingUiState? = null,
    val notice: String? = null
) {
    init {
        require(items.size <= MAX_PRODUCTION_HOME_ITEMS)
        require(items.map { item -> item.itemKey }.distinct().size == items.size)
        require((status == PracticalShoppingProductionHomeStatus.READY) == (result != null))
        require((status == PracticalShoppingProductionHomeStatus.UNAVAILABLE) == (notice != null))
        require(status == PracticalShoppingProductionHomeStatus.READY || items.isEmpty())
        require(status == PracticalShoppingProductionHomeStatus.UNAVAILABLE || notice == null)
        require(notice == null || notice.isNotBlank())
    }
}

/**
 * Maps the existing production Home projection to the smallest state a future
 * renderer needs. This is presentation validation only: it does not re-run a
 * planner, rank a store, calculate money, or infer coverage.
 */
object PracticalShoppingProductionHomeUiProjector {

    const val UNKNOWN_PRICE_NOTICE =
        "No usable price yet — not included in this plan."

    const val ITEM_PRICE_BREAKDOWN_NOTICE =
        PracticalShoppingHomeRenderer.ITEM_PRICE_BREAKDOWN_NOTICE

    fun project(
        request: PracticalShoppingProductionOrchestrationRequest,
        projection: PracticalShoppingProductionHomeProjection,
        itemDisplayNames: Map<ShoppingItemKey, String>
    ): PracticalShoppingProductionHomeUiState {
        if (
            projection.status != PracticalShoppingProductionHomeStatus.READY ||
                projection.result == null
        ) {
            return unavailable()
        }

        val projectedResult = projection.result
        val state = projectedResult.state
        val requestedItems = request.shoppingRequest.itemKeys
        if (requestedItems.isEmpty() || requestedItems.size > MAX_PRODUCTION_HOME_ITEMS) {
            return unavailable()
        }

        val assignmentByItem =
            projectedResult.state.itemStoreAssignments
                .takeIf { assignments ->
                    assignments.map { assignment -> assignment.itemKey }.distinct().size ==
                        assignments.size
                }
                ?.associateBy { assignment -> assignment.itemKey }
                ?: return unavailable()

        if (assignmentByItem.keys.any { itemKey -> itemKey !in requestedItems }) {
            return unavailable()
        }

        val primary = state.primary
        if (primary == null && assignmentByItem.isNotEmpty()) {
            return unavailable()
        }

        val completePrimary = primary != null && primary.missingItemsText == null
        if (completePrimary && assignmentByItem.keys != requestedItems.toSet()) {
            return unavailable()
        }

        val opaqueStoreIdentifiers =
            buildSet {
                projectedResult.primaryStoreKey?.value?.let(::add)
                projectedResult.addedStoreKey?.value?.let(::add)
            }

        val names = linkedMapOf<ShoppingItemKey, String>()
        requestedItems.forEach { itemKey ->
            val safeName = safeDisplayText(itemDisplayNames[itemKey], itemKey.value) ?: return unavailable()
            names[itemKey] = safeName
        }

        if (containsOpaqueIdentifier(state, opaqueStoreIdentifiers)) {
            return unavailable()
        }

        val items =
            requestedItems.map { itemKey ->
                val assignment = assignmentByItem[itemKey]
                val safeStore =
                    assignment?.storeName?.let { storeName ->
                        safeDisplayText(storeName, opaqueStoreIdentifiers)
                    }
                val safePrice =
                    assignment?.priceText?.let { priceText ->
                        safeDisplayText(priceText, emptySet())
                    }
                if (assignment != null && safeStore == null) return unavailable()
                if (assignment?.priceText != null && safePrice == null) return unavailable()

                PracticalShoppingProductionHomeItemUiState(
                    itemKey = itemKey,
                    name = names.getValue(itemKey),
                    storeAssignment = safeStore,
                    plannedPriceText = safePrice,
                    plannedPriceNotice =
                        if (assignment != null && safePrice == null) {
                            ITEM_PRICE_BREAKDOWN_NOTICE
                        } else {
                            null
                        },
                    coverageNotice =
                        if (assignment == null) UNKNOWN_PRICE_NOTICE else null
                )
            }

        return PracticalShoppingProductionHomeUiState(
            status = PracticalShoppingProductionHomeStatus.READY,
            items = items,
            result = state,
            notice = null
        )
    }

    private fun unavailable(): PracticalShoppingProductionHomeUiState =
        PracticalShoppingProductionHomeUiState(
            status = PracticalShoppingProductionHomeStatus.UNAVAILABLE,
            items = emptyList(),
            result = null,
            notice = PracticalShoppingProductionHomeAdapter.UNAVAILABLE_NOTICE
        )

    private fun safeDisplayText(value: String?, forbiddenIdentifier: String): String? {
        val normalized = value?.trim() ?: return null
        if (
            normalized.isEmpty() ||
                normalized.length > MAX_PRODUCTION_HOME_DISPLAY_CHARS ||
                normalized.any { character -> character.isISOControl() } ||
                normalized == forbiddenIdentifier
        ) {
            return null
        }
        return normalized
    }

    private fun safeDisplayText(value: String, forbiddenIdentifiers: Set<String>): String? {
        val normalized = value.trim()
        if (
            normalized.isEmpty() ||
                normalized.length > MAX_PRODUCTION_HOME_DISPLAY_CHARS ||
                normalized.any { character -> character.isISOControl() } ||
                forbiddenIdentifiers.any { identifier ->
                    identifier.length >= 8 && normalized.contains(identifier)
                }
        ) {
            return null
        }
        return normalized
    }

    private fun containsOpaqueIdentifier(
        state: PracticalShoppingUiState,
        identifiers: Set<String>
    ): Boolean {
        val visibleText = buildList {
            add(state.headline)
            state.primary?.let { primary ->
                add(primary.badge)
                add(primary.storeName)
                add(primary.basketCostText)
                add(primary.coverageText)
                primary.missingItemsText?.let(::add)
                add(primary.travelText)
                add(primary.evidenceText)
                add(primary.whyText)
                primary.notice?.let(::add)
                primary.freshnessNotice?.let(::add)
            }
            state.secondStop?.let { secondStop ->
                add(secondStop.badge)
                add(secondStop.storeName)
                add(secondStop.baseItemsText)
                add(secondStop.addedItemsText)
                add(secondStop.combinedBasketCostText)
                add(secondStop.savingsText)
                add(secondStop.additionalTravelText)
                add(secondStop.evidenceText)
            }
            state.secondaryMessage?.let(::add)
            state.itemStoreAssignments.forEach { assignment -> add(assignment.storeName) }
        }
        return visibleText.any { textValue ->
            identifiers.any { identifier ->
                identifier.length >= 8 && textValue.contains(identifier)
            }
        }
    }
}
