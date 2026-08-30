package com.valuepilot.app

import com.valuepilot.core.CompareHereComparisonIntentKey
import com.valuepilot.core.CompareHerePriceSelection

enum class CompareHereManualRouteStatus {
    NEEDS_PRODUCTS,
    NEEDS_LIKE_FOR_LIKE_CONFIRMATION,
    TOO_MANY_PRODUCTS,
    INPUT_TOO_LONG,
    PRODUCT_BLOCK_TOO_LONG,
    PRODUCTS_REJECTED,
    EVALUATED
}

/**
 * Consumer-safe route state for the manual Compare Here entry path.
 *
 * The route deliberately retains only the immutable projected comparison state. Capture ids,
 * adapter issue entries and exact-core candidate objects never cross this boundary.
 */
data class CompareHereManualRouteState(
    val status: CompareHereManualRouteStatus,
    val title: String,
    val guidance: String,
    val comparisonState: CompareHereUiState? = null,
    val rejectedProductCount: Int = 0
) {
    init {
        require(rejectedProductCount >= 0)
        require((comparisonState != null) == (status == CompareHereManualRouteStatus.EVALUATED))
        require(
            (rejectedProductCount > 0) ==
                (status == CompareHereManualRouteStatus.PRODUCTS_REJECTED)
        )
    }
}

/**
 * Pure application coordinator for the eventual manual Compare Here screen.
 *
 * User confirmation is the semantic boundary. Until the user explicitly confirms that all
 * submitted products are like-for-like alternatives, this coordinator does not create/pass a
 * comparison-intent key and does not invoke parsing or comparison. The opaque key is created only
 * after that confirmation; it is never derived from names, prices, barcodes, package units or
 * other product text.
 *
 * This coordinator owns no Android View, clock, persistence, network or ranking engine.
 */
object CompareHereManualRouteCoordinator {

    fun compareBlocks(
        rawBlocks: List<String>,
        observedAtEpochMillis: Long,
        userConfirmedLikeForLike: Boolean,
        priceSelection: CompareHerePriceSelection = CompareHerePriceSelection.CURRENT,
        parser: ProductParser = DeterministicProductParser
    ): CompareHereManualRouteState {
        val capture =
            ManualProductObservationAdapter.captureBlocks(
                rawBlocks = rawBlocks,
                observedAtEpochMillis = observedAtEpochMillis
            )

        val observations =
            when (capture) {
                is ManualCaptureResult.Failure -> return captureFailure(capture.reason)
                is ManualCaptureResult.Success -> capture.observations
            }

        if (observations.size < 2) {
            return CompareHereManualRouteState(
                status = CompareHereManualRouteStatus.NEEDS_PRODUCTS,
                title = "Add at least two products",
                guidance = "Enter two or more products before comparing exact value."
            )
        }

        if (observations.size > CompareHereManualInputAdapter.MAX_OBSERVATIONS) {
            return tooManyProducts()
        }

        if (!userConfirmedLikeForLike) {
            return CompareHereManualRouteState(
                status = CompareHereManualRouteStatus.NEEDS_LIKE_FOR_LIKE_CONFIRMATION,
                title = "Confirm comparable products",
                guidance =
                    "Confirm that every product is a like-for-like alternative before comparing exact value."
            )
        }

        val confirmedIntent = CompareHereComparisonIntentKey("manual:confirmed-comparison-v1")
        return when (
            val comparison =
                CompareHereManualComparisonService.compare(
                    comparisonIntentKey = confirmedIntent,
                    priceSelection = priceSelection,
                    observations = observations,
                    parser = parser
                )
        ) {
            is CompareHereManualComparisonResult.Success -> {
                val state = comparison.projection.state
                CompareHereManualRouteState(
                    status = CompareHereManualRouteStatus.EVALUATED,
                    title = state.statusTitle,
                    guidance = state.guidance,
                    comparisonState = state
                )
            }

            is CompareHereManualComparisonResult.InputFailure ->
                when (comparison.reason) {
                    CompareHereManualInputFailure.TOO_MANY_OBSERVATIONS -> tooManyProducts()
                    CompareHereManualInputFailure.DUPLICATE_OBSERVATION_IDS ->
                        rejectedProducts(1)
                }

            is CompareHereManualComparisonResult.RejectedObservations ->
                rejectedProducts(
                    comparison.issues.map { it.observationId }.distinct().size
                )
        }
    }

    private fun captureFailure(reason: ManualCaptureFailure): CompareHereManualRouteState =
        when (reason) {
            ManualCaptureFailure.INPUT_TOO_LONG ->
                CompareHereManualRouteState(
                    status = CompareHereManualRouteStatus.INPUT_TOO_LONG,
                    title = "Input is too long",
                    guidance = "Shorten the product details and try again."
                )

            ManualCaptureFailure.TOO_MANY_BLOCKS -> tooManyProducts()

            ManualCaptureFailure.BLOCK_TOO_LONG ->
                CompareHereManualRouteState(
                    status = CompareHereManualRouteStatus.PRODUCT_BLOCK_TOO_LONG,
                    title = "A product entry is too long",
                    guidance = "Shorten that product entry and try again."
                )
        }

    private fun tooManyProducts(): CompareHereManualRouteState =
        CompareHereManualRouteState(
            status = CompareHereManualRouteStatus.TOO_MANY_PRODUCTS,
            title = "Too many products",
            guidance =
                "Compare up to ${CompareHereManualInputAdapter.MAX_OBSERVATIONS} products at a time."
        )

    private fun rejectedProducts(count: Int): CompareHereManualRouteState =
        CompareHereManualRouteState(
            status = CompareHereManualRouteStatus.PRODUCTS_REJECTED,
            title = "Some products need clearer information",
            guidance =
                "Check currency, promotion, price, and package details, then try again.",
            rejectedProductCount = count.coerceAtLeast(1)
        )
}
