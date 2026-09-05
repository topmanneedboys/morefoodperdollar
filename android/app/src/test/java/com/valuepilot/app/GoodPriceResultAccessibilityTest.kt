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
                answerTitle = "Below your last remembered price",
                answerGuidance =
                    "This is about 8.0% lower per unit than your last matching observation.",
                answerTone = GoodPriceCheckAnswerTone.POSITIVE,
                historyText = "Last matching observation: 7.05 CAD for 4 L.",
                disclosure =
                    "Not live store pricing. Personal history matches exact package quantity, currency, price basis, and promotion terms."
            )

        assertEquals(
            "Price you entered. Below your last remembered price. Whole milk. 6.49 CAD. " +
                "4 L. 1.6225 CAD/L. This is about 8.0% lower per unit than your last matching observation.",
            goodPriceAnswerCardContentDescription(state)
        )
    }
}
