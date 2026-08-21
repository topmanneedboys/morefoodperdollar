package com.valuepilot.app

import java.util.Locale
import kotlin.math.abs

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
            val candidateName = ValueEngine.canonicalName(candidate.name)
            val nameSimilarity = tokenSimilarity(locator.canonicalName, candidateName)
            if (nameSimilarity < .72) return@mapIndexedNotNull null

            val priceMatch = pricesMatch(locator, candidate)
            if (!priceMatch) return@mapIndexedNotNull null

            val quantityMatch = quantitiesMatch(locator, candidate)
            if (locator.quantityKind != null && !quantityMatch) return@mapIndexedNotNull null

            var exact = 0
            if (candidateName == locator.canonicalName) exact++
            if (priceMatch) exact++
            if (quantityMatch && locator.quantityKind != null) exact++
            val viewMatch = locator.viewId != null && locator.viewId == candidate.viewId
            if (viewMatch) exact++

            val score = nameSimilarity * .55 + .25 +
                (if (locator.quantityKind == null) .10 else if (quantityMatch) .15 else 0.0) +
                (if (viewMatch) .05 else 0.0)
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

    private fun pricesMatch(locator: ItemLocator, candidate: ItemMatchCandidate): Boolean {
        if (abs(locator.currentPrice - candidate.currentPrice) > .011) return false
        val expectedMember = locator.memberPrice
        if (expectedMember != null) {
            val actualMember = candidate.memberPrice ?: return false
            if (abs(expectedMember - actualMember) > .011) return false
        }
        return true
    }

    private fun quantitiesMatch(locator: ItemLocator, candidate: ItemMatchCandidate): Boolean {
        if (locator.quantityKind == null) return true
        if (locator.quantityKind != candidate.quantityKind || locator.quantityAmount == null || candidate.quantityAmount == null) return false
        val tolerance = maxOf(.01, abs(locator.quantityAmount) * .005)
        return abs(locator.quantityAmount - candidate.quantityAmount) <= tolerance
    }

    private fun tokenSimilarity(left: String, right: String): Double {
        if (left == right && left.isNotBlank()) return 1.0
        val a = left.lowercase(Locale.ROOT).split(' ').filter(String::isNotBlank).toSet()
        val b = right.lowercase(Locale.ROOT).split(' ').filter(String::isNotBlank).toSet()
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val common = a.intersect(b).size.toDouble()
        return 2.0 * common / (a.size + b.size)
    }
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
