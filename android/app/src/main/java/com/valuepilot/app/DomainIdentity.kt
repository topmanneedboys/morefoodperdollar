package com.valuepilot.app

import com.valuepilot.core.ExactScale
import com.valuepilot.core.ProductIdentityKey
import com.valuepilot.core.ProductMatchEvidence

/** Maps legacy parsed values to stable, locale-independent repository evidence. */
object DomainIdentity {
    fun productKey(item: ValueItem, includeSessionScope: Boolean): ProductIdentityKey = ProductIdentityKey(
        canonicalName = ValueEngine.canonicalName(item.name),
        currentPriceMinor = ExactScale.fromDouble(item.offer.currentPrice, 2),
        memberPriceMinor = item.offer.memberPrice?.let { ExactScale.fromDouble(it, 2) },
        quantityUnit = item.quantity?.kind?.name,
        quantityMicros = item.quantity?.amountBase?.let { ExactScale.fromDouble(it, 6) },
        promotionCode = item.promotion.type,
        sourceId = item.sourcePackage.takeIf { includeSessionScope },
        sessionId = item.searchSessionId.takeIf { includeSessionScope }
    )

    fun matchEvidence(
        name: String,
        currentPrice: Double,
        memberPrice: Double?,
        quantityKind: Quantity.Kind?,
        quantityAmount: Double?
    ): ProductMatchEvidence = ProductMatchEvidence(
        canonicalName = ValueEngine.canonicalName(name),
        currentPriceMinor = ExactScale.fromDouble(currentPrice, 2),
        memberPriceMinor = memberPrice?.let { ExactScale.fromDouble(it, 2) },
        quantityUnit = quantityKind?.name,
        quantityMicros = quantityAmount?.let { ExactScale.fromDouble(it, 6) }
    )
}
