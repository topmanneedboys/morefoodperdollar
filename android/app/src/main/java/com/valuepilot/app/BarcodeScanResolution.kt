package com.valuepilot.app

import com.valuepilot.core.GtinValidation

/**
 * The small, fail-closed boundary between a barcode decoder and a product-identity request.
 *
 * A camera frame may contain QR codes, shelf labels, or several barcodes. Only one distinct,
 * checksum-valid GTIN is accepted. This helper does not claim that the code is allocated,
 * belongs to a product, or proves a price, package quantity, store, or availability.
 */
internal data class BarcodeScanResolution(
    val rawGtin: String?,
    val canonicalGtin: String?,
    val issue: BarcodeScanIssue?
) {
    init {
        require((rawGtin != null) == (canonicalGtin != null && issue == null))
        require((issue == null) == (rawGtin != null))
        require(rawGtin == null || rawGtin == rawGtin.trim())
        require(canonicalGtin == null || GtinValidation.isValid(canonicalGtin))
    }

    val accepted: Boolean
        get() = rawGtin != null
}

internal enum class BarcodeScanIssue {
    NO_VALID_GTIN,
    MULTIPLE_GTINS
}

internal object BarcodeScanResolutionResolver {

    /**
     * Resolves decoder values deterministically. Invalid/non-GTIN values are ignored because a
     * frame can contain unrelated QR or logistics codes. Equivalent UPC/EAN representations are
     * one identity, and the lexicographically smallest raw representation is retained.
     */
    fun resolve(rawValues: List<String>): BarcodeScanResolution {
        val byCanonical =
            rawValues
                .asSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct()
                .mapNotNull { raw ->
                    GtinValidation.canonicalOrNull(raw)?.let { canonical ->
                        canonical to raw
                    }
                }
                .groupBy({ it.first }, { it.second })

        return when {
            byCanonical.isEmpty() ->
                BarcodeScanResolution(
                    rawGtin = null,
                    canonicalGtin = null,
                    issue = BarcodeScanIssue.NO_VALID_GTIN
                )

            byCanonical.size > 1 ->
                BarcodeScanResolution(
                    rawGtin = null,
                    canonicalGtin = null,
                    issue = BarcodeScanIssue.MULTIPLE_GTINS
                )

            else -> {
                val (canonical, rawRepresentations) = byCanonical.entries.single()
                BarcodeScanResolution(
                    rawGtin = rawRepresentations.minOrNull(),
                    canonicalGtin = canonical,
                    issue = null
                )
            }
        }
    }
}
