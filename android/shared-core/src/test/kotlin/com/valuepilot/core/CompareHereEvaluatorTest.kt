package com.valuepilot.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompareHereEvaluatorTest {

    private val milk = CompareHereComparisonIntentKey("intent:milk")

    @Test
    fun higherStickerPriceWinsWhenExactPerKilogramRateIsLower() {
        val result =
            CompareHereEvaluator.evaluate(
                comparisonIntentKey = milk,
                priceSelection = CompareHerePriceSelection.CURRENT,
                candidates =
                    listOf(
                        candidate("small", milk, "4.00", QuantityNormalization.grams(500)),
                        candidate("large", milk, "7.00", QuantityNormalization.grams(1_000))
                    )
            )

        assertEquals(CompareHereComparisonStatus.READY, result.status)
        assertEquals(listOf("large"), result.bestValueCandidateIds)
        assertEquals(
            listOf("large", "small"),
            result.rankedCandidates.map { it.candidate.candidateId }
        )
        assertEquals(7_000_000L, result.rankedCandidates[0].candidate.rate.currencyMicrosPerUnit)
        assertEquals(8_000_000L, result.rankedCandidates[1].candidate.rate.currencyMicrosPerUnit)
    }

    @Test
    fun explicitPromotionTermsAffectExactUnitRateWithoutHiddenDiscountInference() {
        val promoted =
            CompareHereCandidate(
                candidateId = "two-for-one",
                comparisonIntentKey = milk,
                offer =
                    Offer(
                        current = Money.parse("6.00", "CAD"),
                        promotion = PromotionTerms(label = "2 for 1", receivedUnits = 2, paidUnits = 1)
                    ),
                quantity = QuantityNormalization.grams(500)
            )
        val plain = candidate("plain", milk, "7.00", QuantityNormalization.grams(1_000))

        val result = current(promoted, plain)

        assertEquals(CompareHereComparisonStatus.READY, result.status)
        assertEquals(listOf("two-for-one"), result.bestValueCandidateIds)
        assertEquals(
            6_000_000L,
            result.rankedCandidates.first().candidate.rate.currencyMicrosPerUnit
        )
    }

    @Test
    fun equalExactRatesCoRankAndCandidateIdOnlyStabilizesDisplayOrder() {
        val result =
            current(
                candidate("z-large", milk, "8.00", QuantityNormalization.grams(1_000)),
                candidate("a-small", milk, "4.00", QuantityNormalization.grams(500))
            )

        assertEquals(CompareHereComparisonStatus.READY, result.status)
        assertEquals(listOf(1, 1), result.rankedCandidates.map { it.valueRank })
        assertEquals(
            listOf("a-small", "z-large"),
            result.rankedCandidates.map { it.candidate.candidateId }
        )
        assertEquals(listOf("a-small", "z-large"), result.bestValueCandidateIds)
    }

    @Test
    fun unknownQuantityIsBlockedWhileTwoOtherExactCandidatesStillCompare() {
        val unknown = candidate("unknown", milk, "2.00", null)
        val result =
            current(
                unknown,
                candidate("small", milk, "4.00", QuantityNormalization.grams(500)),
                candidate("large", milk, "7.00", QuantityNormalization.grams(1_000))
            )

        assertEquals(CompareHereComparisonStatus.READY, result.status)
        assertEquals(listOf("large"), result.bestValueCandidateIds)
        assertEquals(listOf("unknown"), result.blockedCandidates.map { it.candidateId })
        assertEquals(
            setOf(CompareHereCandidateBlocker.QUANTITY_UNKNOWN),
            result.blockedCandidates.single().blockers
        )
    }

    @Test
    fun memberModeNeverFallsBackToCurrentPriceWhenMemberPriceIsMissing() {
        val memberOnly =
            candidate(
                id = "member",
                group = milk,
                current = "5.00",
                quantity = QuantityNormalization.grams(500),
                member = "3.00"
            )
        val noMember = candidate("no-member", milk, "4.00", QuantityNormalization.grams(500))

        val result =
            CompareHereEvaluator.evaluate(
                comparisonIntentKey = milk,
                priceSelection = CompareHerePriceSelection.MEMBER,
                candidates = listOf(memberOnly, noMember)
            )

        assertEquals(CompareHereComparisonStatus.NOT_ENOUGH_EXACT_CANDIDATES, result.status)
        assertEquals(listOf("member"), result.exactCandidates.map { it.candidateId })
        assertEquals(300L, result.exactCandidates.single().selectedPrice.minorUnits)
        assertEquals(
            setOf(CompareHereCandidateBlocker.SELECTED_PRICE_UNAVAILABLE),
            result.blockedCandidates.single { it.candidateId == "no-member" }.blockers
        )
        assertTrue(result.bestValueCandidateIds.isEmpty())
    }

    @Test
    fun wrongSemanticComparisonGroupIsBlockedInsteadOfCrossRanked() {
        val juice = CompareHereComparisonIntentKey("intent:juice")
        val result =
            current(
                candidate("milk-a", milk, "4.00", QuantityNormalization.grams(500)),
                candidate("juice", juice, "1.00", QuantityNormalization.grams(1_000)),
                candidate("milk-b", milk, "7.00", QuantityNormalization.grams(1_000))
            )

        assertEquals(CompareHereComparisonStatus.READY, result.status)
        assertEquals(listOf("milk-b"), result.bestValueCandidateIds)
        assertEquals(
            setOf(CompareHereCandidateBlocker.COMPARISON_INTENT_MISMATCH),
            result.blockedCandidates.single { it.candidateId == "juice" }.blockers
        )
        assertTrue(result.rankedCandidates.none { it.candidate.candidateId == "juice" })
    }

    @Test
    fun mixedCurrenciesMakeSingleIntentComparisonIncompatibleInsteadOfSplittingWinners() {
        val result =
            CompareHereEvaluator.evaluate(
                comparisonIntentKey = milk,
                priceSelection = CompareHerePriceSelection.CURRENT,
                candidates =
                    listOf(
                        candidate("cad", milk, "4.00", QuantityNormalization.grams(500), currency = "CAD"),
                        candidate("usd", milk, "3.00", QuantityNormalization.grams(500), currency = "USD")
                    )
            )

        assertEquals(CompareHereComparisonStatus.INCOMPATIBLE_DIMENSIONS, result.status)
        assertEquals(setOf(CompareHereComparisonIssue.MIXED_CURRENCIES), result.comparisonIssues)
        assertTrue(result.rankedCandidates.isEmpty())
        assertTrue(result.bestValueCandidateIds.isEmpty())
        assertEquals(2, result.exactCandidates.size)
    }

    @Test
    fun mixedRateUnitsMakeSingleIntentComparisonIncompatible() {
        val result =
            current(
                candidate("mass", milk, "4.00", QuantityNormalization.grams(500)),
                candidate("count", milk, "4.00", QuantityNormalization.count(2))
            )

        assertEquals(CompareHereComparisonStatus.INCOMPATIBLE_DIMENSIONS, result.status)
        assertEquals(setOf(CompareHereComparisonIssue.MIXED_RATE_UNITS), result.comparisonIssues)
        assertTrue(result.rankedCandidates.isEmpty())
        assertTrue(result.bestValueCandidateIds.isEmpty())
    }

    @Test
    fun mixedCurrencyAndRateUnitReportsBothExplicitDimensionProblems() {
        val result =
            CompareHereEvaluator.evaluate(
                comparisonIntentKey = milk,
                priceSelection = CompareHerePriceSelection.CURRENT,
                candidates =
                    listOf(
                        candidate("cad-mass", milk, "4.00", QuantityNormalization.grams(500), currency = "CAD"),
                        candidate("usd-count", milk, "4.00", QuantityNormalization.count(2), currency = "USD")
                    )
            )

        assertEquals(CompareHereComparisonStatus.INCOMPATIBLE_DIMENSIONS, result.status)
        assertEquals(
            setOf(
                CompareHereComparisonIssue.MIXED_CURRENCIES,
                CompareHereComparisonIssue.MIXED_RATE_UNITS
            ),
            result.comparisonIssues
        )
    }

    @Test
    fun zeroOrNegativeSelectedPriceIsBlocked() {
        val zero = candidate("zero", milk, "0.00", QuantityNormalization.grams(500))
        val negative = candidate("negative", milk, "-1.00", QuantityNormalization.grams(500))
        val valid = candidate("valid", milk, "4.00", QuantityNormalization.grams(500))

        val result = current(zero, negative, valid)

        assertEquals(CompareHereComparisonStatus.NOT_ENOUGH_EXACT_CANDIDATES, result.status)
        assertEquals(listOf("valid"), result.exactCandidates.map { it.candidateId })
        assertEquals(
            setOf("negative", "zero"),
            result.blockedCandidates.map { it.candidateId }.toSet()
        )
        result.blockedCandidates.forEach {
            assertEquals(setOf(CompareHereCandidateBlocker.NON_POSITIVE_SELECTED_PRICE), it.blockers)
        }
    }

    @Test
    fun arithmeticOverflowIsBlockedInsteadOfEscapingOrProducingWrappedRate() {
        val overflow =
            CompareHereCandidate(
                candidateId = "overflow",
                comparisonIntentKey = milk,
                offer = Offer(current = Money(Long.MAX_VALUE, "CAD", fractionDigits = 0)),
                quantity = QuantityNormalization.count(1)
            )
        val result =
            current(
                overflow,
                candidate("valid", milk, "1.00", QuantityNormalization.count(1))
            )

        assertEquals(CompareHereComparisonStatus.NOT_ENOUGH_EXACT_CANDIDATES, result.status)
        assertEquals(
            setOf(CompareHereCandidateBlocker.ARITHMETIC_OVERFLOW),
            result.blockedCandidates.single { it.candidateId == "overflow" }.blockers
        )
    }

    @Test
    fun positivePriceWhoseRepresentableUnitRateRoundsToZeroIsBlockedExplicitly() {
        val tooSmallPerUnit =
            CompareHereCandidate(
                candidateId = "tiny-rate",
                comparisonIntentKey = milk,
                offer = Offer(current = Money.parse("0.01", "CAD")),
                quantity = NormalizedQuantity(Long.MAX_VALUE, BaseUnit.COUNT)
            )
        val result =
            current(
                tooSmallPerUnit,
                candidate("valid", milk, "1.00", QuantityNormalization.count(1))
            )

        assertEquals(CompareHereComparisonStatus.NOT_ENOUGH_EXACT_CANDIDATES, result.status)
        assertEquals(
            setOf(CompareHereCandidateBlocker.UNIT_RATE_NOT_POSITIVE),
            result.blockedCandidates.single { it.candidateId == "tiny-rate" }.blockers
        )
    }

    @Test
    fun oneExactCandidateNeverClaimsBestValue() {
        val result = current(candidate("only", milk, "4.00", QuantityNormalization.grams(500)))

        assertEquals(CompareHereComparisonStatus.NOT_ENOUGH_EXACT_CANDIDATES, result.status)
        assertFalse(result.hasMeaningfulComparison)
        assertTrue(result.rankedCandidates.isEmpty())
        assertTrue(result.bestValueCandidateIds.isEmpty())
    }

    @Test
    fun candidateInputOrderCannotChangeDeterministicComparisonOutput() {
        val a = candidate("a", milk, "8.00", QuantityNormalization.grams(1_000))
        val b = candidate("b", milk, "4.00", QuantityNormalization.grams(500))
        val c = candidate("c", milk, "9.00", QuantityNormalization.grams(1_000))
        val unknown = candidate("unknown", milk, "2.00", null)

        val forward = current(a, b, c, unknown)
        val reverse = current(unknown, c, b, a)

        assertEquals(forward, reverse)
    }

    @Test
    fun duplicateCandidateIdsFailClosed() {
        val first = candidate("same", milk, "4.00", QuantityNormalization.grams(500))
        val second = candidate("same", milk, "7.00", QuantityNormalization.grams(1_000))

        assertFailsWith<IllegalArgumentException> {
            current(first, second)
        }
    }

    @Test
    fun moreThanThirtyTwoCandidatesFailClosedBeforeComparisonWork() {
        val candidates =
            (1..33).map { index ->
                candidate(
                    id = "candidate-$index",
                    group = milk,
                    current = "4.00",
                    quantity = QuantityNormalization.grams(500)
                )
            }

        assertFailsWith<IllegalArgumentException> {
            CompareHereEvaluator.evaluate(
                comparisonIntentKey = milk,
                priceSelection = CompareHerePriceSelection.CURRENT,
                candidates = candidates
            )
        }
    }

    @Test
    fun comparisonIntentKeyMustBeStableBoundedAndControlFree() {
        assertFailsWith<IllegalArgumentException> { CompareHereComparisonIntentKey("  intent:milk") }
        assertFailsWith<IllegalArgumentException> { CompareHereComparisonIntentKey("intent:\nmilk") }
        assertFailsWith<IllegalArgumentException> { CompareHereComparisonIntentKey("x".repeat(241)) }
    }

    private fun current(vararg candidates: CompareHereCandidate): CompareHereComparisonResult =
        CompareHereEvaluator.evaluate(
            comparisonIntentKey = milk,
            priceSelection = CompareHerePriceSelection.CURRENT,
            candidates = candidates.toList()
        )

    private fun candidate(
        id: String,
        group: CompareHereComparisonIntentKey,
        current: String,
        quantity: NormalizedQuantity?,
        member: String? = null,
        currency: String = "CAD"
    ): CompareHereCandidate =
        CompareHereCandidate(
            candidateId = id,
            comparisonIntentKey = group,
            offer =
                Offer(
                    current = Money.parse(current, currency),
                    member = member?.let { Money.parse(it, currency) }
                ),
            quantity = quantity
        )
}
