package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingHomePresentationPolicyTest {

    @Test
    fun zeroHistoryHome_isOnlyWelcomeAndSearch() {
        val state =
            PracticalShoppingHomeRenderer.render(
                LocalSamplePracticalShoppingDemo.initialModel().ui
            )

        val presentation = PracticalShoppingHomePresentationPolicy.from(state)

        assertEquals(PracticalShoppingHomeMode.FIRST_TIME, presentation.mode)
        assertTrue(presentation.showWelcomePurpose)
        assertFalse(presentation.showActiveBasket)
        assertFalse(presentation.showBuyAgain)
        assertFalse(presentation.showBasketEditor)
        assertEquals(0, presentation.itemCount)
        assertEquals(null, presentation.collectionProgress)
    }

    @Test
    fun activeBasketHome_showsOneBasketCardAndKeepsTheExistingEditorAvailable() {
        val model =
            PracticalShoppingHomeSession.submit(
                PracticalShoppingHomeSession.initialState(),
                "eggs milk"
            )
        val state = PracticalShoppingHomeRenderer.render(model.model.ui)

        val presentation = PracticalShoppingHomePresentationPolicy.from(state)

        assertEquals(PracticalShoppingHomeMode.ACTIVE_BASKET, presentation.mode)
        assertFalse(presentation.showWelcomePurpose)
        assertTrue(presentation.showActiveBasket)
        assertTrue(presentation.showBasketEditor)
        assertEquals(2, presentation.itemCount)
        assertFalse(presentation.showBuyAgain)
    }

    @Test
    fun returningHome_doesNotTurnStoredFeaturesIntoAnAdvancedDashboard() {
        val base =
            PracticalShoppingHomeRenderer.render(
                LocalSamplePracticalShoppingDemo.initialModel().ui,
                requestDetails = null,
                confirmedPurchaseCount = 4
            )

        val presentation = PracticalShoppingHomePresentationPolicy.from(base)

        assertEquals(PracticalShoppingHomeMode.RETURNING, presentation.mode)
        assertFalse(presentation.showWelcomePurpose)
        assertFalse(presentation.showActiveBasket)
        assertFalse(presentation.showBasketEditor)
        assertTrue(presentation.showBuyAgain)
        assertEquals(0, presentation.itemCount)
    }

    @Test
    fun privateIdentityBasket_isActiveEvenBeforeAnythingIsCollected() {
        val identity =
            PracticalShoppingPrivateIdentity(
                identityKey = "sepa:yerba:1",
                displayName = "Yerba Mate",
                brand = "Marca",
                sourceLabel = "Argentina SEPA",
                packageQuantityLabel = "1 kg"
            )
        val state =
            PracticalShoppingHomeRenderer.render(
                LocalSamplePracticalShoppingDemo.initialModel().ui,
                requestDetails = null,
                privateIdentities = listOf(identity)
            )

        val presentation = PracticalShoppingHomePresentationPolicy.from(state)

        assertEquals(PracticalShoppingHomeMode.ACTIVE_BASKET, presentation.mode)
        assertTrue(presentation.showActiveBasket)
        assertEquals(1, presentation.itemCount)
        assertEquals(1, presentation.collectibleIdentityCount)
        assertEquals(0, presentation.collectedIdentityCount)
        assertEquals(0f, presentation.collectionProgress)
    }
}
