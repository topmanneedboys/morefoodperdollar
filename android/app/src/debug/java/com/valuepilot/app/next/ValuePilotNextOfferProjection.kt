package com.valuepilot.app.next

import com.valuepilot.app.ArgentinaBackendContract
import com.valuepilot.app.PracticalShoppingSearchIdentityPresentation
import java.math.BigDecimal
import java.math.MathContext
import java.util.Locale

internal data class ComparableCurrentUnitPriceBasis(
    val currency: String,
    val unitBasis: String
)

internal data class SearchStoreFilterOption(
    val storeName: String
)

internal data class SearchResultFilterOptions(
    val comparableUnitPriceBasis: ComparableCurrentUnitPriceBasis?,
    val stores: List<SearchStoreFilterOption>
)

internal data class CurrentOfferPriceRange(
    val currency: String,
    val unitBasis: String,
    val minimum: BigDecimal,
    val median: BigDecimal,
    val maximum: BigDecimal,
    val offerCount: Int
) {
    val medianPosition: Float
        get() {
            val spread = maximum.subtract(minimum)
            if (spread.signum() <= 0) return 0.5f
            return median.subtract(minimum)
                .divide(spread, MathContext.DECIMAL64)
                .toFloat()
                .coerceIn(0f, 1f)
        }
}

/** Arbitrary debug-only amounts for validating layout; never included in a release source set. */
internal val ValuePilotDesignPreviewPriceRange = CurrentOfferPriceRange(
    currency = "ARS",
    unitBasis = "L",
    minimum = BigDecimal("1240"),
    median = BigDecimal("1380"),
    maximum = BigDecimal("1510"),
    offerCount = 3
)

/** Search filters are projections of current, source-qualified offer evidence only. */
internal fun searchResultFilterOptions(
    matches: List<PracticalShoppingSearchIdentityPresentation.Match>
): SearchResultFilterOptions {
    val currentOffers = matches.flatMap { match ->
        match.qualifiedOffers
            .filter { it.freshnessStatus == ArgentinaBackendContract.Freshness.FRESH }
            .map { match.identityKey to it }
    }

    val comparableBasis = currentOffers
        .filter { (_, offer) -> offer.unitPrice != null && !offer.unitPriceBasis.isNullOrBlank() }
        .groupBy { (_, offer) ->
            PriceBasisKey(
                currency = offer.unitPrice!!.currency.trim().uppercase(Locale.ROOT),
                unitBasis = offer.unitPriceBasis!!.trim().lowercase(Locale.ROOT)
            )
        }
        .filterValues { offers -> offers.map { it.first }.distinct().size >= 2 }
        .keys
        .singleOrNull()
        ?.let { ComparableCurrentUnitPriceBasis(it.currency, it.unitBasis) }

    val stores = currentOffers
        .map { (_, offer) -> offer.storeName.trim() }
        .filter(String::isNotBlank)
        .distinctBy { it.lowercase(Locale.ROOT) }
        .map(::SearchStoreFilterOption)
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.storeName })

    return SearchResultFilterOptions(comparableBasis, stores)
}

internal fun filterAndSortSearchMatches(
    matches: List<PracticalShoppingSearchIdentityPresentation.Match>,
    selectedStoreName: String?,
    sortByLowestUnitPrice: Boolean,
    comparableBasis: ComparableCurrentUnitPriceBasis?
): List<PracticalShoppingSearchIdentityPresentation.Match> {
    val storeFiltered = if (selectedStoreName == null) {
        matches
    } else {
        matches.filter { match ->
            match.qualifiedOffers.any {
                it.freshnessStatus == ArgentinaBackendContract.Freshness.FRESH &&
                it.storeName.equals(selectedStoreName, ignoreCase = true)
            }
        }
    }
    if (!sortByLowestUnitPrice || comparableBasis == null) return storeFiltered

    return storeFiltered.withIndex()
        .sortedWith { left, right ->
            val leftPrice = comparableUnitPrice(left.value, comparableBasis, selectedStoreName)
            val rightPrice = comparableUnitPrice(right.value, comparableBasis, selectedStoreName)
            when {
                leftPrice == null && rightPrice != null -> 1
                leftPrice != null && rightPrice == null -> -1
                leftPrice != null && rightPrice != null -> {
                    leftPrice.compareTo(rightPrice).takeIf { it != 0 }
                        ?: left.index.compareTo(right.index)
                }
                else -> left.index.compareTo(right.index)
            }
        }
        .map { it.value }
}

internal fun preferredSearchOffer(
    match: PracticalShoppingSearchIdentityPresentation.Match,
    selectedStoreName: String?,
    sortByLowestUnitPrice: Boolean,
    comparableBasis: ComparableCurrentUnitPriceBasis?
): ArgentinaBackendContract.SearchOffer? {
    val current = match.qualifiedOffers.filter {
        it.freshnessStatus == ArgentinaBackendContract.Freshness.FRESH &&
            (selectedStoreName == null || it.storeName.equals(selectedStoreName, ignoreCase = true))
    }
    if (sortByLowestUnitPrice && comparableBasis != null) {
        return current
            .filter { isComparable(it, comparableBasis) }
            .minByOrNull { requireNotNull(it.unitPrice).decimal }
            ?: current.firstOrNull()
    }
    return current.firstOrNull()
}

/** Only a same-currency, same-basis set of fresh offers can form a current range. */
internal fun currentOfferPriceRange(
    offers: List<ArgentinaBackendContract.SearchOffer>
): CurrentOfferPriceRange? {
    val pricedOffers = offers
        .filter {
            it.freshnessStatus == ArgentinaBackendContract.Freshness.FRESH &&
                it.unitPrice != null &&
                !it.unitPriceBasis.isNullOrBlank() &&
                it.unitPrice.decimal.signum() > 0
        }
        .distinctBy { it.offerKey }

    val basisGroups = pricedOffers.groupBy {
        PriceBasisKey(
            currency = requireNotNull(it.unitPrice).currency.trim().uppercase(Locale.ROOT),
            unitBasis = requireNotNull(it.unitPriceBasis).trim().lowercase(Locale.ROOT)
        )
    }
    val singleBasisOffers = basisGroups.values.singleOrNull() ?: return null
    val comparableOffers = singleBasisOffers.sortedBy { requireNotNull(it.unitPrice).decimal }
    if (comparableOffers.size < 2) return null

    val prices = comparableOffers.map { requireNotNull(it.unitPrice).decimal }
    val middle = prices.size / 2
    val median = if (prices.size % 2 == 1) {
        prices[middle]
    } else {
        prices[middle - 1].add(prices[middle]).divide(BigDecimal("2"), MathContext.DECIMAL64)
    }
    val firstUnitPrice = requireNotNull(comparableOffers.first().unitPrice)
    return CurrentOfferPriceRange(
        currency = firstUnitPrice.currency,
        unitBasis = requireNotNull(comparableOffers.first().unitPriceBasis).trim(),
        minimum = prices.first(),
        median = median,
        maximum = prices.last(),
        offerCount = comparableOffers.size
    )
}

private fun comparableUnitPrice(
    match: PracticalShoppingSearchIdentityPresentation.Match,
    basis: ComparableCurrentUnitPriceBasis,
    selectedStoreName: String?
): BigDecimal? = match.qualifiedOffers
    .asSequence()
    .filter {
        it.freshnessStatus == ArgentinaBackendContract.Freshness.FRESH &&
            (selectedStoreName == null || it.storeName.equals(selectedStoreName, ignoreCase = true)) &&
            isComparable(it, basis)
    }
    .mapNotNull { it.unitPrice?.decimal }
    .minOrNull()

private fun isComparable(
    offer: ArgentinaBackendContract.SearchOffer,
    basis: ComparableCurrentUnitPriceBasis
): Boolean = offer.freshnessStatus == ArgentinaBackendContract.Freshness.FRESH &&
    offer.unitPrice?.currency?.trim()?.uppercase(Locale.ROOT) == basis.currency &&
    offer.unitPriceBasis?.trim()?.lowercase(Locale.ROOT) == basis.unitBasis

private data class PriceBasisKey(val currency: String, val unitBasis: String)
