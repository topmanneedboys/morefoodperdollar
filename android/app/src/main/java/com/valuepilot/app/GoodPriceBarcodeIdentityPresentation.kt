package com.valuepilot.app

import com.valuepilot.core.OfflineCatalogDiscoveryMatch
import com.valuepilot.core.OfflineCatalogDiscoveryResult
import com.valuepilot.core.OfflineCatalogMatchKind
import com.valuepilot.core.GtinValidation

/**
 * Immutable, identity-only projection for the Good Price barcode handoff.
 *
 * A barcode lookup can suggest a display name, but it never carries package quantity, price,
 * store, stock, availability, freshness or ranking authority into the price-check route.
 */
data class GoodPriceBarcodeIdentityPresentation(
    val gtin: String,
    val options: List<Option>,
    val evaluatedCandidateCount: Int
) {
    data class Option(
        val displayName: String,
        val brand: String?
    ) {
        init {
            require(displayName.isNotBlank())
            require(brand == null || brand.isNotBlank())
        }

        val label: String
            get() = brand?.let { "$displayName · $it" } ?: displayName
    }

    init {
        require(gtin == gtin.trim())
        require(GtinValidation.isValid(gtin))
        require(options.size <= 24)
        require(evaluatedCandidateCount >= options.size)
    }

    companion object {
        fun from(
            gtin: String,
            result: OfflineCatalogDiscoveryResult
        ): GoodPriceBarcodeIdentityPresentation {
            val options =
                result.matches
                    .asSequence()
                    .filter { it.kind == OfflineCatalogMatchKind.EXACT_GTIN }
                    .map(::mapOption)
                    .distinct()
                    .take(24)
                    .toList()
            return GoodPriceBarcodeIdentityPresentation(
                gtin = gtin.trim(),
                options = options,
                evaluatedCandidateCount = result.evaluatedCandidateCount
            )
        }

        private fun mapOption(match: OfflineCatalogDiscoveryMatch): Option =
            Option(
                displayName = match.product.displayName,
                brand = match.product.brand
            )
    }
}
