package com.valuepilot.core

/**
 * Provider-neutral GS1 modulo-10 validation for GTIN-8/12/13/14.
 *
 * Shape and check digit are validated only. This does not prove that the GTIN
 * is allocated, current, or actually belongs to a claimed product; source
 * provenance still has to establish that separately.
 */
object GtinValidation {
    fun isValid(value: String): Boolean {
        if (
            value.length !in setOf(8, 12, 13, 14) ||
            !value.all(Char::isDigit)
        ) {
            return false
        }

        val body = value.dropLast(1)
        val suppliedCheckDigit = value.last().digitToInt()
        var sum = 0
        var weight = 3

        for (index in body.indices.reversed()) {
            sum += body[index].digitToInt() * weight
            weight = if (weight == 3) 1 else 3
        }

        val expectedCheckDigit = (10 - (sum % 10)) % 10
        return suppliedCheckDigit == expectedCheckDigit
    }

    /**
     * Canonical cross-source representation for a checksum-valid GTIN.
     *
     * Leading-zero representations can describe the same GS1 item. In
     * particular, UPC-A / GTIN-12 becomes its equivalent 13-digit form by
     * prefixing zero, while a leading-zero GTIN-14 can collapse to that same
     * item representation. GTIN-8 stays 8 digits and a non-zero-indicator
     * GTIN-14 remains distinct.
     *
     * Invalid values are never repaired.
     */
    fun canonicalOrNull(value: String): String? {
        if (!isValid(value)) return null

        val significant = value.dropWhile { it == '0' }
        if (significant.isEmpty()) return null

        val canonical =
            when (significant.length) {
                in 1..7 -> significant.padStart(8, '0')
                8 -> significant
                in 9..12 -> significant.padStart(13, '0')
                else -> significant
            }

        return canonical.takeIf(::isValid)
    }
}
