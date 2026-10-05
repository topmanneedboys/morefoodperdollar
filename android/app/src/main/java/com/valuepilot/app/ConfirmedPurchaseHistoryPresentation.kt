package com.valuepilot.app

import com.valuepilot.core.ConfirmedPurchase
import java.math.BigDecimal

internal enum class ConfirmedPurchaseHistorySurfaceMode {
    EMPTY,
    CONTENT,
    UNAVAILABLE
}

internal sealed interface ConfirmedPurchaseHistoryUiAction {
    data class Repeat(val purchaseId: String) : ConfirmedPurchaseHistoryUiAction
    data class Edit(val purchaseId: String) : ConfirmedPurchaseHistoryUiAction
    data class Delete(val purchaseId: String) : ConfirmedPurchaseHistoryUiAction
}

internal data class ConfirmedPurchaseHistoryRow(
    val purchaseId: String,
    val title: String,
    val details: String,
    val repeatAction: ConfirmedPurchaseHistoryUiAction.Repeat,
    val editAction: ConfirmedPurchaseHistoryUiAction.Edit,
    val deleteAction: ConfirmedPurchaseHistoryUiAction.Delete,
    val priceWatchAction: PriceWatchUiAction.ToggleWatch? = null
) {
    init {
        require(purchaseId.isNotBlank())
        require(title.isNotBlank())
        require(details.isNotBlank())
    }
}

internal data class ConfirmedPurchaseHistorySurfaceState(
    val mode: ConfirmedPurchaseHistorySurfaceMode,
    val headline: String,
    val guidance: String,
    val notice: String?,
    val rows: List<ConfirmedPurchaseHistoryRow>
) {
    init {
        require(headline.isNotBlank())
        require(guidance.isNotBlank())
        require(notice == null || notice.isNotBlank())
        require(rows.size <= MAX_CONFIRMED_PURCHASES)
        require(rows.map { it.purchaseId }.distinct().size == rows.size)
        require(mode != ConfirmedPurchaseHistorySurfaceMode.EMPTY || rows.isEmpty())
        require(mode != ConfirmedPurchaseHistorySurfaceMode.UNAVAILABLE || rows.isEmpty())
    }
}

internal object ConfirmedPurchaseHistoryProjector {
    fun project(
        state: ConfirmedPurchaseHistoryState?,
        unavailable: Boolean,
        watchedProductIdentityKeys: Set<String> = emptySet()
    ): ConfirmedPurchaseHistorySurfaceState {
        if (unavailable || state == null) {
            return ConfirmedPurchaseHistorySurfaceState(
                mode = ConfirmedPurchaseHistorySurfaceMode.UNAVAILABLE,
                headline = "Bought unavailable",
                guidance = "Private purchases could not be read safely. Existing records were preserved.",
                notice = "Try again.",
                rows = emptyList()
            )
        }
        val rows = state.purchases.map { purchase ->
            row(purchase, watchedProductIdentityKeys)
        }
        return ConfirmedPurchaseHistorySurfaceState(
            mode = if (rows.isEmpty()) ConfirmedPurchaseHistorySurfaceMode.EMPTY else ConfirmedPurchaseHistorySurfaceMode.CONTENT,
            headline = "Bought",
            guidance = if (rows.isEmpty()) {
                "Nothing bought yet. Confirm a purchase from Basket when you are ready."
            } else {
                "Purchases you confirmed here. Not live offers or observed shelf prices."
            },
            notice = null,
            rows = rows
        )
    }

    private fun row(
        purchase: ConfirmedPurchase,
        watchedProductIdentityKeys: Set<String>
    ): ConfirmedPurchaseHistoryRow {
        val fields = mutableListOf<String>()
        purchase.quantity?.let { quantity ->
            fields += "Quantity ${PriceWatchProjector.formatQuantity(quantity)}"
        } ?: fields.add("Quantity unknown")
        purchase.amountPaid?.let { amount ->
            fields += "Paid ${formatMoney(amount)}"
        } ?: fields.add("Price unknown")
        purchase.purchaseDateIso?.let { fields += "Date $it" } ?: fields.add("Date unknown")
        purchase.store?.let { store ->
            fields += "Store ${store.displayName ?: "known exact store"}"
        } ?: fields.add("Store unknown")
        return ConfirmedPurchaseHistoryRow(
            purchaseId = purchase.purchaseId,
            title = purchase.displayName,
            details = fields.joinToString(" · "),
            repeatAction = ConfirmedPurchaseHistoryUiAction.Repeat(purchase.purchaseId),
            editAction = ConfirmedPurchaseHistoryUiAction.Edit(purchase.purchaseId),
            deleteAction = ConfirmedPurchaseHistoryUiAction.Delete(purchase.purchaseId),
            priceWatchAction =
                PriceWatchUiAction.ToggleWatch(
                    productIdentityKey = purchase.productIdentityKey,
                    displayName = purchase.displayName,
                    brand = purchase.brand,
                    packageQuantityLabel = purchase.packageQuantityLabel,
                    watched = purchase.productIdentityKey in watchedProductIdentityKeys
                )
        )
    }

    internal fun formatMoney(money: com.valuepilot.core.Money): String =
        "${BigDecimal.valueOf(money.minorUnits).movePointLeft(money.fractionDigits).toPlainString()} ${money.currencyCode}"
}
