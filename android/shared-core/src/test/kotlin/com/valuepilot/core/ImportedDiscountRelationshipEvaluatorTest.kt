package com.valuepilot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ImportedDiscountRelationshipEvaluatorTest {

    @Test
    fun belowEqualAndAboveRelationshipsAreExactAndFailClosed() {
        val below = ImportedDiscountRelationshipEvaluator.assess(
            discountedFieldName = "Sale Price",
            discountedAmount = Money(999, "CAD"),
            referenceFieldName = "Retail Price",
            referenceAmount = Money(1299, "CAD")
        )
        assertEquals(
            ImportedDiscountRelationship.DISCOUNTED_BELOW_REFERENCE,
            below.relationship
        )
        assertTrue(below.structurallySupportsDiscountClaim)
        assertFalse(below.hasSemanticConflict)

        val equal = ImportedDiscountRelationshipEvaluator.assess(
            discountedFieldName = "Sale Price",
            discountedAmount = Money(1299, "CAD"),
            referenceFieldName = "Retail Price",
            referenceAmount = Money(1299, "CAD")
        )
        assertEquals(ImportedDiscountRelationship.EQUAL, equal.relationship)
        assertFalse(equal.structurallySupportsDiscountClaim)
        assertFalse(equal.hasSemanticConflict)

        val above = ImportedDiscountRelationshipEvaluator.assess(
            discountedFieldName = "Sale Price",
            discountedAmount = Money(1499, "CAD"),
            referenceFieldName = "Retail Price",
            referenceAmount = Money(1299, "CAD")
        )
        assertEquals(
            ImportedDiscountRelationship.DISCOUNTED_ABOVE_REFERENCE_CONFLICT,
            above.relationship
        )
        assertFalse(above.structurallySupportsDiscountClaim)
        assertTrue(above.hasSemanticConflict)
    }

    @Test
    fun missingMalformedOrNonPositiveMoneyRemainsUnavailable() {
        val missing = ImportedDiscountRelationshipEvaluator.assess(
            discountedFieldName = "Sale Price",
            discountedAmount = null,
            referenceFieldName = "Retail Price",
            referenceAmount = Money(1299, "CAD")
        )
        assertEquals(ImportedDiscountRelationship.UNAVAILABLE, missing.relationship)

        val malformed = ImportedDiscountRelationshipEvaluator.assess(
            discountedFieldName = "Sale Price",
            discountedAmount = null,
            referenceFieldName = "Retail Price",
            referenceAmount = null
        )
        assertEquals(ImportedDiscountRelationship.UNAVAILABLE, malformed.relationship)

        val zero = ImportedDiscountRelationshipEvaluator.assess(
            discountedFieldName = "Sale Price",
            discountedAmount = Money(0, "CAD"),
            referenceFieldName = "Retail Price",
            referenceAmount = Money(1299, "CAD")
        )
        assertEquals(ImportedDiscountRelationship.UNAVAILABLE, zero.relationship)

        val negative = ImportedDiscountRelationshipEvaluator.assess(
            discountedFieldName = "Sale Price",
            discountedAmount = Money(-1, "CAD"),
            referenceFieldName = "Retail Price",
            referenceAmount = Money(1299, "CAD")
        )
        assertEquals(ImportedDiscountRelationship.UNAVAILABLE, negative.relationship)
    }

    @Test
    fun currencyOrScaleMismatchIsNeverCompared() {
        val currencyMismatch = ImportedDiscountRelationshipEvaluator.assess(
            discountedFieldName = "Sale Price",
            discountedAmount = Money(999, "USD"),
            referenceFieldName = "Retail Price",
            referenceAmount = Money(1299, "CAD")
        )
        assertEquals(
            ImportedDiscountRelationship.INCOMPARABLE_MONEY,
            currencyMismatch.relationship
        )

        val scaleMismatch = ImportedDiscountRelationshipEvaluator.assess(
            discountedFieldName = "Sale Price",
            discountedAmount = Money(999, "CAD", 2),
            referenceFieldName = "Retail Price",
            referenceAmount = Money(12990, "CAD", 3)
        )
        assertEquals(
            ImportedDiscountRelationship.INCOMPARABLE_MONEY,
            scaleMismatch.relationship
        )
    }

    @Test
    fun sourceFieldRolesRemainExplicitAndDistinct() {
        val assessment = ImportedDiscountRelationshipEvaluator.assess(
            discountedFieldName = "Sale Price",
            discountedAmount = Money(999, "CAD"),
            referenceFieldName = "Retail Price",
            referenceAmount = Money(1299, "CAD")
        )
        assertEquals("Sale Price", assessment.discountedFieldName)
        assertEquals("Retail Price", assessment.referenceFieldName)

        try {
            ImportedDiscountRelationshipEvaluator.assess(
                discountedFieldName = "price",
                discountedAmount = Money(999, "CAD"),
                referenceFieldName = "PRICE",
                referenceAmount = Money(1299, "CAD")
            )
            fail("Expected distinct role field validation")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("distinct"))
        }
    }
}