package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingHomeQuickAddTest {

    @Test
    fun choicesAreStableAndCoverTheSmallSampleVocabulary() {
        assertEquals(
            listOf("Eggs", "Milk", "Bananas", "Bread", "Chicken"),
            PracticalShoppingHomeQuickAddPolicy.choices.map { it.label }
        )
    }

    @Test
    fun appendStartsAListAndKeepsExistingTokensIdempotent() {
        assertEquals(
            "eggs",
            PracticalShoppingHomeQuickAddPolicy.appendToQuery(
                rawQuery = "",
                choice = PracticalShoppingHomeQuickAdd.EGGS
            )
        )
        assertEquals(
            "eggs milk",
            PracticalShoppingHomeQuickAddPolicy.appendToQuery(
                rawQuery = "eggs",
                choice = PracticalShoppingHomeQuickAdd.MILK
            )
        )
        assertEquals(
            "eggs",
            PracticalShoppingHomeQuickAddPolicy.appendToQuery(
                rawQuery = "  eggs  ",
                choice = PracticalShoppingHomeQuickAdd.EGGS
            )
        )
    }

    @Test
    fun appendRejectsAQueryThatWouldExceedTheExistingHomeBound() {
        val nearLimit = "x".repeat(LocalSamplePracticalShoppingDemo.MAX_QUERY_CHARACTERS)

        assertNull(
            PracticalShoppingHomeQuickAddPolicy.appendToQuery(
                rawQuery = nearLimit,
                choice = PracticalShoppingHomeQuickAdd.MILK
            )
        )
    }

    @Test
    fun sessionRoutesShortcutThroughTheExistingQueryReducerOnly() {
        val initial = PracticalShoppingHomeSession.initialState()
        val next =
            PracticalShoppingHomeSession.addQuickItem(
                initial,
                PracticalShoppingHomeQuickAdd.BANANAS
            )

        assertEquals("bananas", next.model.ui.query)
        assertEquals(LocalSamplePracticalShoppingDemo.Status.IDLE, next.model.ui.status)
        assertTrue(next.model.ui.items.isEmpty())
        assertSame(initial.requestDetails, next.requestDetails)
    }

    @Test
    fun identityDraftKeepsARealMultiWordNameAsOneUnresolvedItem() {
        val initial = PracticalShoppingHomeSession.initialState()
        val next =
            PracticalShoppingHomeSession.addIdentityDraft(
                initial,
                "Yerba Mate 1 kg"
            )

        assertEquals("\"Yerba Mate 1 kg\"", next.model.ui.query)
        val submitted = PracticalShoppingHomeSession.submit(next, next.model.ui.query)
        assertEquals(
            listOf("yerba mate 1 kg"),
            submitted.model.ui.unknownItems
        )
    }

    @Test
    fun identityDraftIsIdempotentAndRespectsTheExistingBound() {
        val once =
            PracticalShoppingHomeQuickAddPolicy.appendIdentityNameToQuery(
                rawQuery = "\"Yerba Mate 1 kg\"",
                identityName = "Yerba Mate 1 kg"
            )
        assertEquals("\"Yerba Mate 1 kg\"", once)

        val tooLong = "x".repeat(LocalSamplePracticalShoppingDemo.MAX_QUERY_CHARACTERS)
        assertEquals(
            null,
            PracticalShoppingHomeQuickAddPolicy.appendIdentityNameToQuery(
                rawQuery = tooLong,
                identityName = "Milk"
            )
        )
    }
}
