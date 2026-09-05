package com.valuepilot.app

import com.valuepilot.core.CompareHereComparisonIntentKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareHerePhotoSuggestionPresentationTest {
    @Test
    fun exactSignalsAreShownAsReviewOnlyWithoutChangingTheRawFacts() {
        val presentation =
            CompareHerePhotoSuggestionPresentationFactory.forCandidate(
                "Honeycrisp apples\n3 lb\nCA$5.99"
            )

        assertEquals("Honeycrisp apples", presentation.nameSuggestion)
        assertEquals("CA$5.99", presentation.priceSignal)
        assertEquals("3 lb", presentation.quantitySignal)
        assertEquals(
            "Honeycrisp apples\nCA$5.99\n3 lb",
            presentation.editorPrefill
        )
        assertTrue(presentation.reviewNotice.startsWith("Review only —"))
        assertTrue(presentation.displayLabel.contains("name: Honeycrisp apples"))
        assertTrue(presentation.displayLabel.contains("price text: CA$5.99"))
        assertTrue(presentation.displayLabel.contains("package: 3 lb"))
        assertTrue(presentation.displayLabel.contains("OCR text: Honeycrisp apples 3 lb CA$5.99"))
        assertFalse(presentation.displayLabel.contains("confirmed price"))
    }

    @Test
    fun multiplePricesStayAChoiceAndNeverBecomeASelectedCurrentPrice() {
        val presentation =
            CompareHerePhotoSuggestionPresentationFactory.forCandidate(
                "Cereal\n500 g\nRegular CA$6.99\nMember CA$4.99"
            )

        assertEquals("Cereal", presentation.nameSuggestion)
        assertTrue(presentation.priceSignal!!.contains("CA$6.99 / CA$4.99"))
        assertTrue(presentation.priceSignal.contains("choose current/member price"))
        assertEquals(null, presentation.editorPrefill)
        assertTrue(presentation.reviewNotice.contains("current price and currency"))
        assertTrue(presentation.displayLabel.contains("price text:"))
        assertTrue(presentation.displayLabel.contains("Review only —"))
    }

    @Test
    fun ambiguousCurrencyAndMissingQuantityAreExplicitlyMarkedForReview() {
        val presentation =
            CompareHerePhotoSuggestionPresentationFactory.forCandidate("Eggs $4.99")

        assertEquals("$4.99 — currency needs review", presentation.priceSignal)
        assertEquals(null, presentation.quantitySignal)
        assertEquals(null, presentation.editorPrefill)
        assertTrue(presentation.reviewNotice.contains("current price and currency"))
        assertTrue(presentation.reviewNotice.contains("package size"))
        assertTrue(presentation.displayLabel.contains("currency needs review"))
    }

    @Test
    fun estimatedQuantityIsShownButCannotLookExact() {
        val presentation =
            CompareHerePhotoSuggestionPresentationFactory.forCandidate(
                "Potatoes\n1–2 kg\nCA$4.99"
            )

        assertEquals("1–2 kg avg — verify", presentation.quantitySignal)
        assertEquals(null, presentation.editorPrefill)
        assertTrue(presentation.reviewNotice.contains("package size"))
        assertTrue(presentation.displayLabel.contains("verify"))
    }

    @Test
    fun labelsAreBoundedSanitizedAndDeterministic() {
        val raw = "Product\u0000\tCA$9.99 " + "x".repeat(1_000)
        val first = CompareHerePhotoSuggestionPresentationFactory.forCandidate(raw)
        val second = CompareHerePhotoSuggestionPresentationFactory.forCandidate(raw)

        assertEquals(first, second)
        assertTrue(
            first.displayLabel.length <=
                CompareHerePhotoSuggestionPresentation.MAX_DISPLAY_LABEL_CHARS
        )
        assertFalse(first.displayLabel.any { it.isISOControl() && it !in "\r\n\t" })
        assertTrue(first.displayLabel.endsWith("…"))
        assertEquals(null, first.editorPrefill)
    }

    @Test
    fun priceAffectingPromotionIsKeptOnlyWhenTheExactRouteCanRepresentIt() {
        val supported =
            CompareHerePhotoSuggestionPresentationFactory.forCandidate(
                "Cereal\n500 g\nCA$4.99\nBOGO"
            )
        val unsupported =
            CompareHerePhotoSuggestionPresentationFactory.forCandidate(
                "Cereal\n500 g\n2 for CA$5.00"
            )

        assertEquals(
            "Cereal\nCA$4.99\n500 g\nBuy one get one free",
            supported.editorPrefill
        )
        assertEquals(null, unsupported.editorPrefill)
    }

    @Test
    fun completePrefillRemainsAdmissibleToTheExistingExactAdapter() {
        val presentation =
            CompareHerePhotoSuggestionPresentationFactory.forCandidate(
                "Cereal\n500 g\nCA$4.99\nBOGO"
            )
        val observations =
            ManualProductObservationAdapter.captureBlocks(
                rawBlocks = listOf(requireNotNull(presentation.editorPrefill)),
                observedAtEpochMillis = 1L
            )

        assertTrue(observations is ManualCaptureResult.Success)
        val adaptation =
            CompareHereManualInputAdapter.adapt(
                comparisonIntentKey = CompareHereComparisonIntentKey("intent:cereal"),
                observations =
                    (observations as ManualCaptureResult.Success).observations
            )

        assertTrue(adaptation is CompareHereManualInputResult.Success)
        val candidate =
            (adaptation as CompareHereManualInputResult.Success).adaptation.candidates.single()
        assertEquals(2L, candidate.offer.promotion.receivedUnits)
        assertEquals(1L, candidate.offer.promotion.paidUnits)
    }

    @Test
    fun derivedPizzaAreaDoesNotLookLikeAnExactManualPackagePrefill() {
        val presentation =
            CompareHerePhotoSuggestionPresentationFactory.forCandidate(
                "Pizza\n12 in\nCA$9.99"
            )

        assertEquals(null, presentation.editorPrefill)
        assertTrue(presentation.reviewNotice.contains("package size"))
    }
}
