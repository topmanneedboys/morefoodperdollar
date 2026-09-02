package com.valuepilot.app

import com.valuepilot.core.ShoppingItemKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingBasketRendererTest {

    @Test
    fun blankHomeStateProducesAnEmptyBasketWithAHomeAction() {
        val home =
            PracticalShoppingHomeRenderer.render(
                LocalSamplePracticalShoppingDemo.initialModel().ui
            )

        val basket = PracticalShoppingBasketRenderer.render(home)

        assertEquals(PracticalShoppingBasketStatus.EMPTY, basket.status)
        assertEquals("No basket planned yet", basket.headline)
        assertTrue(basket.items.isEmpty())
        assertTrue(basket.unknownItems.isEmpty())
        assertNull(basket.result)
        assertNull(basket.extraStopRuleText)
        assertFalse(basket.collectionEnabled)
        assertEquals("Build my basket on Home", basket.actionLabel)
        assertSame(home.items, basket.items)
        assertSame(home.unknownItems, basket.unknownItems)
    }

    @Test
    fun unresolvedInputStaysUnresolvedAndNeverBecomesAPlanOrCheckOffSession() {
        val model =
            PracticalShoppingHomeSession.submit(
                LocalSamplePracticalShoppingDemo.initialModel(),
                "eggs dragonfruit"
            )
        val home = PracticalShoppingHomeRenderer.render(model.ui)

        val basket = PracticalShoppingBasketRenderer.render(home)

        assertEquals(PracticalShoppingBasketStatus.NEEDS_ATTENTION, basket.status)
        assertEquals(listOf("Eggs"), basket.items.map { it.name })
        assertEquals(listOf("dragonfruit"), basket.unknownItems)
        assertEquals(home.message, basket.guidance)
        assertNull(basket.result)
        assertNull(basket.extraStopRuleText)
        assertFalse(basket.collectionEnabled)
    }

    @Test
    fun refinementCannotBeMistakenForACompletedBasket() {
        val model =
            PracticalShoppingHomeSession.submit(
                LocalSamplePracticalShoppingDemo.initialModel(),
                "chicken eggs milk"
            )
        val home = PracticalShoppingHomeRenderer.render(model.ui)

        val basket = PracticalShoppingBasketRenderer.render(home)

        assertEquals(PracticalShoppingBasketStatus.NEEDS_ATTENTION, basket.status)
        assertEquals("Finish your shopping list", basket.headline)
        assertEquals(home.message, basket.guidance)
        assertNull(basket.result)
        assertFalse(basket.collectionEnabled)
    }

    @Test
    fun completedPlanPassesThroughTheExactProjectionAndEnablesCheckOff() {
        val model =
            PracticalShoppingHomeSession.submit(
                LocalSamplePracticalShoppingDemo.initialModel(),
                "eggs milk"
            )
        val home = PracticalShoppingHomeRenderer.render(model.ui)

        val basket = PracticalShoppingBasketRenderer.render(home)

        assertEquals(PracticalShoppingBasketStatus.PLANNED, basket.status)
        assertSame(home.result, basket.result)
        assertSame(home.items, basket.items)
        assertEquals(home.extraStopSettings.summary, basket.extraStopRuleText)
        assertEquals("Basket 10.28 CAD", basket.result?.primary?.basketCostText)
        assertTrue(basket.collectionEnabled)
        assertEquals("Edit on Home", basket.actionLabel)
    }

    @Test
    fun incompleteKnownSubtotalAndMissingPriceRemainNonCheckable() {
        val model =
            PracticalShoppingHomeSession.submit(
                LocalSamplePracticalShoppingDemo.initialModel(),
                "eggs coffee"
            )
        val home = PracticalShoppingHomeRenderer.render(model.ui)

        val basket = PracticalShoppingBasketRenderer.render(home)

        assertEquals(PracticalShoppingBasketStatus.PLANNED, basket.status)
        assertSame(home.result, basket.result)
        assertEquals("Known subtotal 4.49 CAD", basket.result?.primary?.basketCostText)
        assertEquals("Missing price: Coffee", basket.result?.primary?.missingItemsText)
        assertFalse(basket.collectionEnabled)
        assertEquals(home.sampleNotice, basket.sampleNotice)
        assertEquals(
            "Fictional sample data only — not live retailer prices or availability.",
            basket.sampleNotice
        )
    }

    @Test
    fun collectionActionDescriptionKeepsItemDetailAndPreferenceBoundary() {
        val item =
            PracticalShoppingHomeItemRenderState(
                key = ShoppingItemKey("sample-eggs-large-12"),
                name = "Eggs",
                detail = "12 pack",
                requestDetailsSummary = "2 packages",
                requestDetailsNotice =
                    "Preference only — not applied to this sample plan.",
                requestDetailsActionLabel = "Edit details"
            )

        assertEquals(
            "Mark Eggs (12 pack) as collected. 2 packages. " +
                "Preference only — not applied to this sample plan.",
            practicalShoppingBasketCollectionActionDescription(item, collected = false)
        )
        assertEquals(
            "Mark Eggs (12 pack) as not collected. 2 packages. " +
                "Preference only — not applied to this sample plan.",
            practicalShoppingBasketCollectionActionDescription(item, collected = true)
        )
    }

    @Test
    fun collectionActionDescriptionIncludesTheExactPlannedStoreWhenAvailable() {
        val item =
            PracticalShoppingHomeItemRenderState(
                key = ShoppingItemKey("sample-eggs-large-12"),
                name = "Eggs",
                detail = "12 pack",
                requestDetailsSummary = "No extra preferences",
                requestDetailsNotice = null,
                requestDetailsActionLabel = "Add details",
                storeAssignment = "Sample Market"
            )

        assertEquals(
            "Mark Eggs (12 pack) as collected. Buy at Sample Market. No extra preferences.",
            practicalShoppingBasketCollectionActionDescription(item, collected = false)
        )
    }
}
