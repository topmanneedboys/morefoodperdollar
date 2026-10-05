package com.valuepilot.app

import com.valuepilot.core.ConfirmedPurchase
import com.valuepilot.core.Money
import com.valuepilot.core.NormalizedQuantity
import com.valuepilot.core.PriceObservation
import com.valuepilot.core.PriceWatch
import com.valuepilot.core.PriceWatchState
import java.math.BigDecimal

internal enum class PriceWatchSurfaceMode {
    EMPTY,
    CONTENT,
    UNAVAILABLE
}

sealed interface PriceWatchUiAction {
    data class ToggleWatch(
        val productIdentityKey: String,
        val displayName: String,
        val brand: String?,
        val packageQuantityLabel: String?,
        val watched: Boolean
    ) : PriceWatchUiAction

    data class AddPersonalObservation(val productIdentityKey: String) : PriceWatchUiAction

    data class EditTarget(val productIdentityKey: String) : PriceWatchUiAction

    data class DeleteObservation(val observationId: String) : PriceWatchUiAction

    data class EditObservation(val observationId: String) : PriceWatchUiAction
}

internal data class PriceWatchHistoryLine(
    val recordId: String,
    val sourceText: String,
    val priceText: String,
    val quantityText: String,
    val storeText: String,
    val dateText: String
) {
    init {
        require(recordId.isNotBlank())
        listOf(sourceText, priceText, quantityText, storeText, dateText).forEach {
            require(it.isNotBlank())
        }
    }

    fun asText(): String =
        "$sourceText · $priceText · $quantityText · $storeText · $dateText"
}

internal data class PriceWatchRow(
    val productIdentityKey: String,
    val title: String,
    val packageText: String,
    val targetText: String,
    val currentOfferText: String,
    val purchaseHistory: List<PriceWatchHistoryLine>,
    val personalHistory: List<PriceWatchHistoryLine>,
    val toggleAction: PriceWatchUiAction.ToggleWatch,
    val addObservationAction: PriceWatchUiAction.AddPersonalObservation,
    val editTargetAction: PriceWatchUiAction.EditTarget
) {
    init {
        require(productIdentityKey.isNotBlank())
        require(title.isNotBlank())
        require(packageText.isNotBlank())
        require(targetText.isNotBlank())
        require(currentOfferText.isNotBlank())
    }
}

internal data class PriceWatchSurfaceState(
    val mode: PriceWatchSurfaceMode,
    val headline: String,
    val guidance: String,
    val rows: List<PriceWatchRow>
) {
    init {
        require(headline.isNotBlank())
        require(guidance.isNotBlank())
        require(rows.size <= PriceWatchState.MAX_PRICE_WATCHES)
        require(rows.map { it.productIdentityKey }.distinct().size == rows.size)
        require(mode != PriceWatchSurfaceMode.EMPTY || rows.isEmpty())
        require(mode != PriceWatchSurfaceMode.UNAVAILABLE || rows.isEmpty())
    }
}

internal object PriceWatchProjector {
    fun project(
        state: PriceWatchState?,
        purchases: List<ConfirmedPurchase>,
        unavailable: Boolean
    ): PriceWatchSurfaceState {
        if (unavailable || state == null) {
            return PriceWatchSurfaceState(
                mode = PriceWatchSurfaceMode.UNAVAILABLE,
                headline = "Watching unavailable",
                guidance = "Private watches could not be read safely. Existing data was preserved.",
                rows = emptyList()
            )
        }
        val rows =
            state.watches
                .sortedBy { it.productIdentityKey }
                .map { watch ->
                    row(
                        watch = watch,
                        purchases = purchases.filter { it.productIdentityKey == watch.productIdentityKey },
                        observations = state.observations.filter { it.productIdentityKey == watch.productIdentityKey }
                    )
                }
        return PriceWatchSurfaceState(
            mode = if (rows.isEmpty()) PriceWatchSurfaceMode.EMPTY else PriceWatchSurfaceMode.CONTENT,
            headline = "Watching",
            guidance =
                if (rows.isEmpty()) {
                    "Nothing watched yet. Add from Search or Saved."
                } else {
                    "Private watch. No current offer is known."
                },
            rows = rows
        )
    }

    private fun row(
        watch: PriceWatch,
        purchases: List<ConfirmedPurchase>,
        observations: List<PriceObservation>
    ): PriceWatchRow {
        val purchaseLines =
            purchases
                .sortedWith(compareByDescending<ConfirmedPurchase> { it.purchaseDateIso ?: "" }.thenBy { it.purchaseId })
                .map { purchaseLine(it) }
        val personalLines =
            observations
                .sortedWith(compareByDescending<PriceObservation> { it.observedDateIso ?: "" }.thenBy { it.observationId })
                .map { observationLine(it) }
        return PriceWatchRow(
            productIdentityKey = watch.productIdentityKey,
            title = watch.displayName,
            packageText = watch.packageQuantityLabel?.let { "Package: $it" } ?: "Package quantity unknown",
            targetText = watch.targetPrice?.let { "Target: ${formatMoney(it)}" } ?: "No target price set",
            currentOfferText = "No current offer",
            purchaseHistory = purchaseLines,
            personalHistory = personalLines,
            toggleAction =
                PriceWatchUiAction.ToggleWatch(
                    productIdentityKey = watch.productIdentityKey,
                    displayName = watch.displayName,
                    brand = watch.brand,
                    packageQuantityLabel = watch.packageQuantityLabel,
                    watched = true
                ),
            addObservationAction = PriceWatchUiAction.AddPersonalObservation(watch.productIdentityKey),
            editTargetAction = PriceWatchUiAction.EditTarget(watch.productIdentityKey)
        )
    }

    private fun purchaseLine(purchase: ConfirmedPurchase): PriceWatchHistoryLine =
        PriceWatchHistoryLine(
            recordId = purchase.purchaseId,
            sourceText = "Confirmed purchase",
            priceText = purchase.amountPaid?.let(::formatMoney) ?: "Price unknown",
            quantityText = purchase.quantity?.let(::formatQuantity) ?: "Quantity unknown",
            storeText = purchase.store?.let { it.displayName ?: "Store identity recorded" } ?: "Store unknown",
            dateText = purchase.purchaseDateIso ?: "Date unknown"
        )

    private fun observationLine(observation: PriceObservation): PriceWatchHistoryLine =
        PriceWatchHistoryLine(
            recordId = observation.observationId,
            sourceText = "Personal price observation",
            priceText = formatMoney(observation.price),
            quantityText = observation.packageQuantity?.let(::formatQuantity) ?: "Quantity unknown",
            storeText = observation.store?.let { it.displayName ?: "Store identity recorded" } ?: "Store unknown",
            dateText = observation.observedDateIso ?: "Date unknown"
        )

    internal fun formatMoney(money: Money): String =
        "${BigDecimal.valueOf(money.minorUnits).movePointLeft(money.fractionDigits).toPlainString()} ${money.currencyCode}"

    internal fun formatQuantity(quantity: NormalizedQuantity): String {
        val amount = BigDecimal.valueOf(quantity.amountMicros).movePointLeft(6).stripTrailingZeros().toPlainString()
        val unit =
            when (quantity.unit) {
                com.valuepilot.core.BaseUnit.COUNT -> "count"
                com.valuepilot.core.BaseUnit.GRAM -> "g"
                com.valuepilot.core.BaseUnit.MILLILITRE -> "ml"
                com.valuepilot.core.BaseUnit.SQUARE_INCH -> "square inch"
            }
        return "$amount $unit"
    }
}
