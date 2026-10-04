package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchResultCardPresentationPolicyTest {

    private fun match(sourceLabel: String? = "Argentina SEPA / Precios Claros") =
        PracticalShoppingSearchIdentityPresentation.Match(
            displayName = "Yerba Mate",
            brand = "Marca",
            matchLabel = "Exact match",
            packageQuantityLabel = "1 kg",
            sourceLabel = sourceLabel,
            identityKey = "sepa:yerba:1"
        )

    @Test
    fun newResultShowsOneAddActionAndOnlyMeaningfulSecondaryActions() {
        val card = SearchResultCardPresentationPolicy.project(
            match = match(),
            mapEligible = false,
            historyAvailable = false,
            saved = false,
            watched = false
        )

        assertEquals(SearchResultPrimaryAction.ADD_TO_BASKET, card.primaryAction)
        assertTrue(card.showPhotos)
        assertTrue(card.showWatch)
        assertFalse(card.showNearby)
        assertFalse(card.showHistory)
    }

    @Test
    fun returningResultKeepsOneRemoveActionAndRevealsContextOnlyOnDemand() {
        val card = SearchResultCardPresentationPolicy.project(
            match = match(),
            mapEligible = true,
            historyAvailable = true,
            saved = true,
            watched = true
        )

        assertEquals(SearchResultPrimaryAction.REMOVE_FROM_BASKET, card.primaryAction)
        assertTrue(card.showPhotos)
        assertTrue(card.showWatch)
        assertTrue(card.showNearby)
        assertTrue(card.showHistory)
    }

    @Test
    fun incompleteSourceFailsClosedWithoutInventingPhotoOrBasketAction() {
        val card = SearchResultCardPresentationPolicy.project(
            match = match(sourceLabel = null),
            mapEligible = false,
            historyAvailable = false,
            saved = false,
            watched = false
        )

        assertEquals(SearchResultPrimaryAction.NONE, card.primaryAction)
        assertFalse(card.showPhotos)
        assertTrue(card.showWatch)
        assertFalse(card.showNearby)
        assertFalse(card.showHistory)
    }
}
