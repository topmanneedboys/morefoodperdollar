package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareHerePhotoTextHintsTest {

    @Test
    fun explicitCurrencyHintsRemainRecognized() {
        assertTrue(CompareHerePhotoTextHints.containsPriceLikeText("Cereal CA$4.99"))
        assertTrue(CompareHerePhotoTextHints.containsPriceLikeText("Cereal 4.99 CAD"))
    }

    @Test
    fun bareTwoDecimalShelfPricesBecomeReviewCandidates() {
        assertTrue(CompareHerePhotoTextHints.containsPriceLikeText("Cereal\n4.99"))
        assertTrue(CompareHerePhotoTextHints.containsPriceLikeText("4,999.99"))
        assertEquals("4.99", CompareHerePhotoTextHints.bareDecimalPriceText("Cereal\n4.99"))
    }

    @Test
    fun commonPackageDecimalsDoNotPassTheBarePriceHint() {
        assertFalse(CompareHerePhotoTextHints.containsPriceLikeText("Olive oil\n1.75 L"))
        assertFalse(CompareHerePhotoTextHints.containsPriceLikeText("Coffee\n250.00 g"))
        assertFalse(CompareHerePhotoTextHints.containsPriceLikeText("Rice\n2.00 kg"))
        assertEquals(null, CompareHerePhotoTextHints.bareDecimalPriceText("Olive oil\n1.75 L"))
    }

    @Test
    fun wholeNumbersAndLongDateLikeValuesStayOut() {
        assertFalse(CompareHerePhotoTextHints.containsPriceLikeText("12 count"))
        assertFalse(CompareHerePhotoTextHints.containsPriceLikeText("Best before 2025.12"))
        assertFalse(CompareHerePhotoTextHints.containsPriceLikeText("Product name only"))
    }

    @Test
    fun theGateIsDeterministicAndNeverConfirmsFacts() {
        val text = "Shelf tag\n4.99"
        val first = CompareHerePhotoTextHints.containsPriceLikeText(text)
        val second = CompareHerePhotoTextHints.containsPriceLikeText(text)
        assertEquals(first, second)
        assertTrue(first)
    }
}
