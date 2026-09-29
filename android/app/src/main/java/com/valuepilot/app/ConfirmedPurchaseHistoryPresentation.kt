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
    val deleteAction: ConfirmedPurchaseHistoryUiAction.Delete
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
        unavailable: Boolean
    ): ConfirmedPurchaseHistorySurfaceState {
        if (unavailable || state == null) {
            return ConfirmedPurchaseHistorySurfaceState(
                mode = ConfirmedPurchaseHistorySurfaceMode.UNAVAILABLE,
                headline = "Purchase history unavailable",
                guidance = "ValuePilot could not safely read your private purchase history. Existing records were not replaced.",
                notice = "Try again after returning to Saved.",
                rows = emptyList()
            )
        }
        val rows = state.purchases.map(::row)
        return ConfirmedPurchaseHistorySurfaceState(
            mode = if (rows.isEmpty()) ConfirmedPurchaseHistorySurfaceMode.EMPTY else ConfirmedPurchaseHistorySurfaceMode.CONTENT,
            headline = "Confirmed purchases",
            guidance = if (rows.isEmpty()) {
                "Confirm a purchase from a Basket item when you are ready. Check-off alone never creates a purchase."
            } else {
                "Private history on this device. These are purchases you explicitly confirmed, not live offers or observed shelf prices."
            },
            notice = null,
            rows = rows
        )
    }

    private fun row(purchase: ConfirmedPurchase): ConfirmedPurchaseHistoryRow {
        val fields = mutableListOf<String>()
        purchase.quantity?.let { fields += "Quantity recorded" } ?: fields.add("Quantity unknown")
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
            deleteAction = ConfirmedPurchaseHistoryUiAction.Delete(purchase.purchaseId)
        )
    }

    internal fun formatMoney(money: com.valuepilot.core.Money): String =
        "${BigDecimal.valueOf(money.minorUnits).movePointLeft(money.fractionDigits).toPlainString()} ${money.currencyCode}"
}
