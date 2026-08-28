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
}
