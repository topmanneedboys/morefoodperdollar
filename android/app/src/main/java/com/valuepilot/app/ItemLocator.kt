package com.valuepilot.app

import com.valuepilot.core.ProductMatching

data class ItemLocator(
    val packageName: String,
    val searchSessionId: String,
    val windowId: Int,
    val canonicalName: String,
    val currentPrice: Double,
    val memberPrice: Double?,
    val quantityKind: Quantity.Kind?,
    val quantityAmount: Double?,
    val quantityDisplay: String?,
    val viewId: String?,
    val contentDescription: String?,
    val originalBounds: ScreenBounds,
    val cardFingerprint: String
)

data class ItemMatchCandidate(
    val name: String,
    val currentPrice: Double,
    val memberPrice: Double?,
    val quantityKind: Quantity.Kind?,
    val quantityAmount: Double?,
    val viewId: String?,
    val cardFingerprint: String,
    val clickPath: NodePath?
)

data class ItemMatchDecision(
    val candidateIndex: Int?,
    val confidence: Double,
    val ambiguous: Boolean,
    val reason: String
)

object ItemMatcher {
    private data class Scored(val index: Int, val score: Double, val exactEvidence: Int)

    fun choose(locator: ItemLocator, candidates: List<ItemMatchCandidate>): ItemMatchDecision {
        val scored = candidates.mapIndexedNotNull { index, candidate ->
            if (candidate.clickPath == null) return@mapIndexedNotNull null
            if (candidate.cardFingerprint != locator.cardFingerprint) return@mapIndexedNotNull null
            val targetEvidence = DomainIdentity.matchEvidence(
                locator.canonicalName, locator.currentPrice, locator.memberPrice, locator.quantityKind, locator.quantityAmount
            )
            val candidateEvidence = DomainIdentity.matchEvidence(
                candidate.name, candidate.currentPrice, candidate.memberPrice, candidate.quantityKind, candidate.quantityAmount
            )
            val productMatch = ProductMatching.compare(targetEvidence, candidateEvidence)
            if (!productMatch.matches) return@mapIndexedNotNull null

            var exact = 0
            if (candidateEvidence.canonicalName == targetEvidence.canonicalName) exact++
            exact++ // exact current price is required by ProductMatching
            if (locator.quantityKind != null) exact++
            val viewMatch = locator.viewId != null && locator.viewId == candidate.viewId
            if (viewMatch) exact++

            val score = productMatch.score + (if (viewMatch) .05 else 0.0)
            Scored(index, score, exact)
        }.sortedWith(compareByDescending<Scored> { it.score }.thenByDescending { it.exactEvidence })

        val top = scored.firstOrNull() ?: return ItemMatchDecision(null, 0.0, false, "no sufficiently matching card")
        if (top.score < .82) return ItemMatchDecision(null, top.score, false, "match confidence too low")
        val second = scored.getOrNull(1)
        if (second != null && top.score - second.score < .10) {
            return ItemMatchDecision(null, top.score, true, "multiple cards match too closely")
        }
        return ItemMatchDecision(top.index, top.score.coerceAtMost(1.0), false, "confident exact-card match")
    }

    fun sessionIsCurrent(locator: ItemLocator, context: SearchContext?, currentPackage: String?): Boolean =
        context != null &&
            locator.searchSessionId == context.sessionId &&
            locator.packageName == context.packageName &&
            locator.packageName == currentPackage

}

fun ProductCardSnapshot.toLocator(item: ValueItem, context: SearchContext): ItemLocator = ItemLocator(
    packageName = context.packageName,
    searchSessionId = context.sessionId,
    windowId = locatorSeed.windowId,
    canonicalName = ValueEngine.canonicalName(item.name),
    currentPrice = item.offer.currentPrice,
    memberPrice = item.offer.memberPrice,
    quantityKind = item.quantity?.kind,
    quantityAmount = item.quantity?.amountBase,
    quantityDisplay = item.quantity?.display,
    viewId = locatorSeed.viewId,
    contentDescription = locatorSeed.contentDescription,
    originalBounds = locatorSeed.bounds,
    cardFingerprint = cardFingerprint
)
