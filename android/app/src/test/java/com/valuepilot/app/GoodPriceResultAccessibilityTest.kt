package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Test

class GoodPriceResultAccessibilityTest {

    @Test
    fun `answer card summary keeps projected facts in a single stable order`() {
        val state =
            GoodPriceCheckUiState(
                headline = "Is this a good price?",
                priceModeText = "Price you entered",
                productName = "Whole milk",
                priceText = "6.49 CAD",
                quantityText = "4 L",
                unitRateText = "1.6225 CAD/L",
                answerTitle = "Lower than your last recorded price",
                answerGuidance =
                    "This is about 8.0% lower per unit than your last matching price.",
                answerTone = GoodPriceCheckAnswerTone.POSITIVE,
                historyText = "Last matching observation: 7.05 CAD for 4 L.",
                disclosure =
                    "This uses your exact package, currency and personal price history. It is not live store pricing."
            )

        assertEquals(
            "Price you entered. Lower than your last recorded price. Whole milk. 6.49 CAD. " +
                "4 L. 1.6225 CAD/L. This is about 8.0% lower per unit than your last matching price.",
            goodPriceAnswerCardContentDescription(state)
        )
    }
}
