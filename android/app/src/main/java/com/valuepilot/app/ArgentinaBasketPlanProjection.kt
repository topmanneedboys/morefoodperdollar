package com.valuepilot.app

import com.valuepilot.core.Money
/**
 * Consumer-safe projection of the existing Argentina planner response.
 *
 * This type intentionally contains display text and exact identity keys only;
 * it does not choose a plan, add prices, infer inventory, or turn a partial
 * known subtotal into a complete basket total.
 */
internal enum class ArgentinaBasketPlanStatus {
    CURRENT_COMPLETE,
    CURRENT_PARTIAL,
    NO_CURRENT_PLAN
}

internal data class ArgentinaBasketPlanLineProjection(
    val productEvidenceKey: String,
    val productName: String,
    val brand: String?,
    val storeName: String,
    val packageCount: Int,
    val packageQuantityLabel: String,
    val lineTotalText: String,
    val unitPriceText: String? = null,
    val freshnessText: String,
    val availabilityText: String
) {
    init {
        require(productEvidenceKey.isNotBlank())
        require(productName.isNotBlank())
        require(storeName.isNotBlank())
        require(packageCount > 0)
        require(packageQuantityLabel.isNotBlank())
        require(lineTotalText.isNotBlank())
        require(unitPriceText == null || unitPriceText.isNotBlank())
        require(freshnessText.isNotBlank())
        require(availabilityText.isNotBlank())
    }
}

internal data class ArgentinaBasketPlanProjection(
    val status: ArgentinaBasketPlanStatus,
    val headline: String,
    val planType: String?,
    val totalText: String?,
    val merchandiseTotal: Money? = null,
    val evaluatedPlanSummary: String? = null,
    val coverageText: String,
    val storeSummary: String?,
    val notice: String,
    val releaseDate: String,
    val lines: List<ArgentinaBasketPlanLineProjection>,
    val missingIdentityNames: List<String>
) {
    init {
        require(headline.isNotBlank())
        require(coverageText.isNotBlank())
        require(notice.isNotBlank())
        require(releaseDate.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))
        require(lines.size <= ArgentinaBackendContract.MAX_PLAN_LINES)
        require(missingIdentityNames.none(String::isBlank))
        require(missingIdentityNames.size <= ArgentinaBackendContract.MAX_LINES)
        require((status == ArgentinaBasketPlanStatus.NO_CURRENT_PLAN) == (planType == null && lines.isEmpty()))
        require(totalText == null || totalText.isNotBlank())
        require(evaluatedPlanSummary == null || evaluatedPlanSummary.isNotBlank())
    }
}

internal object ArgentinaBasketPlanProjector {
    private const val SOURCE = "Argentina SEPA / Precios Claros"

    fun project(
        response: ArgentinaBackendContract.ShopResponse,
        identities: List<PracticalShoppingPrivateIdentity>
    ): ArgentinaBasketPlanProjection {
        val decision = response.result
        // The backend remains the only authority for plan selection.  The
        // fallback is still a backend-provided plan, never a local re-plan.
        val selected = decision.bestSensibleChoice.selectedPlan
            ?: decision.cheapestUpToTwoStores
            ?: decision.cheapestSingleStore
            ?: decision.cheapestPerLineUnboundedStores

        if (selected == null) {
            return ArgentinaBasketPlanProjection(
                status = ArgentinaBasketPlanStatus.NO_CURRENT_PLAN,
                headline = "No current price plan found",
                planType = null,
                totalText = null,
                merchandiseTotal = null,
                evaluatedPlanSummary = null,
                coverageText = "No exact current price evidence was returned.",
                storeSummary = null,
                notice = "$SOURCE release ${response.common.releaseDate}; unknown prices remain unknown.",
                releaseDate = response.common.releaseDate,
                lines = emptyList(),
                missingIdentityNames = identities.map { it.displayName }.distinct().take(ArgentinaBackendContract.MAX_LINES)
            )
        }

        val requestNames = response.result.request.items.associate { it.lineId to it.query }
        val lines = selected.lines.map { line ->
            ArgentinaBasketPlanLineProjection(
                productEvidenceKey = line.productEvidenceKey,
                productName = line.productName.ifBlank { requestNames[line.lineId] ?: line.lineId },
                brand = line.brand,
                storeName = line.storeName,
                packageCount = line.packageCount,
                packageQuantityLabel = quantityLabel(line.packageQuantity),
                lineTotalText = moneyText(line.lineTotal),
                unitPriceText = ArgentinaExactUnitPriceMath.text(line.packagePrice, line.packageQuantity),
                freshnessText = when (line.freshnessStatus) {
                    ArgentinaBackendContract.Freshness.FRESH -> "Fresh qualified release"
                    ArgentinaBackendContract.Freshness.STALE -> "Older release · not current"
                    ArgentinaBackendContract.Freshness.UNKNOWN -> "Freshness unknown · not current"
                },
                availabilityText = "Availability unknown"
            )
        }.take(ArgentinaBackendContract.MAX_PLAN_LINES)
        val missingNames = selected.missingLineIds.mapNotNull(requestNames::get)
            .plus(
                identities
                    .filter { identity -> lines.none { it.productEvidenceKey == identity.identityKey } }
                    .map { it.displayName }
            )
            .distinct()
            .take(ArgentinaBackendContract.MAX_LINES)
        val complete = selected.completePriceEvidence && selected.missingLineIds.isEmpty()
        val total = moneyText(selected.total)
        val evaluatedPlans = listOfNotNull(
            decision.cheapestSingleStore,
            decision.closestCompletePriceEvidenceStore,
            decision.cheapestTwoStoreCombination,
            decision.cheapestUpToTwoStores,
            decision.cheapestPerLineUnboundedStores,
            decision.bestSensibleChoice.selectedPlan
        ).plus(decision.decisionFrontier)
            .distinctBy { it.planId }
            .take(4)
        val evaluatedPlanSummary = evaluatedPlans.joinToString(" | ") { plan ->
            "${plan.type}: ${moneyText(plan.total)} at ${plan.stores.joinToString { it.storeName }}"
        }.take(512).takeIf { it.isNotBlank() }
        return ArgentinaBasketPlanProjection(
            status = if (complete) ArgentinaBasketPlanStatus.CURRENT_COMPLETE else ArgentinaBasketPlanStatus.CURRENT_PARTIAL,
            headline = if (complete) "Current listed-price plan" else "Current price coverage is incomplete",
            planType = selected.type,
            totalText = if (complete) "Listed total · $total" else "Known subtotal · $total",
            merchandiseTotal = coreMoneyOrNull(selected.total),
            evaluatedPlanSummary = evaluatedPlanSummary,
            coverageText = "${lines.size} of ${selected.lines.size + selected.missingLineIds.size} requested items have exact price evidence",
            storeSummary = selected.stores.map { it.storeName }.distinct().joinToString().takeIf { it.isNotBlank() },
            notice = buildString {
                append("$SOURCE release ")
                append(response.common.releaseDate)
                append(". Published prices do not establish live shelf inventory; availability remains unknown.")
                if (missingNames.isNotEmpty()) {
                    append(" Missing: ")
                    append(missingNames.joinToString())
                    append(".")
                }
            },
            releaseDate = response.common.releaseDate,
            lines = lines,
            missingIdentityNames = missingNames
        )
    }

    private fun quantityLabel(quantity: ArgentinaBackendContract.Quantity): String =
        "${quantity.amount} ${quantity.unit}"

    private fun moneyText(money: ArgentinaBackendContract.Money): String =
        "${money.currency} ${money.amount}"

    private fun coreMoneyOrNull(money: ArgentinaBackendContract.Money): Money? = runCatching {
        val fractionDigits = maxOf(2, money.decimal.scale()).also { require(it <= 6) }
        Money.parse(money.amount, money.currency, fractionDigits)
    }.getOrNull()

}
