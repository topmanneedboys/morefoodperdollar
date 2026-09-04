package com.valuepilot.app

import com.valuepilot.core.ShoppingItemKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
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
        assertNull(basket.noCoverageSummary)
        assertNull(basket.extraStopRuleText)
        assertFalse(basket.collectionEnabled)
        assertNull(basket.collectionNotice)
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
        assertNull(basket.noCoverageSummary)
        assertNull(basket.extraStopRuleText)
        assertFalse(basket.collectionEnabled)
    }

    @Test
    fun typedButUnsubmittedDraftExplainsThatPlanningStartsOnHome() {
        val model =
            LocalSamplePracticalShoppingDemo.reduce(
                LocalSamplePracticalShoppingDemo.initialModel(),
                LocalSamplePracticalShoppingDemo.Intent.QueryChanged("eggs milk")
            )
        val home = PracticalShoppingHomeRenderer.render(model.ui)

        val basket = PracticalShoppingBasketRenderer.render(home)

        assertEquals(PracticalShoppingBasketStatus.NEEDS_ATTENTION, basket.status)
        assertEquals("Plan this list on Home", basket.headline)
        assertEquals(
            "Your list is ready to plan. Return to Home and tap Plan my shop to see the sample result.",
            basket.guidance
        )
        assertEquals("Plan this list on Home", basket.actionLabel)
        assertNull(basket.result)
        assertNull(basket.noCoverageSummary)
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
        assertNull(basket.noCoverageSummary)
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
        assertNull(basket.noCoverageSummary)
        assertSame(home.items, basket.items)
        assertEquals(home.extraStopSettings.summary, basket.extraStopRuleText)
        assertEquals("Basket 10.28 CAD", basket.result?.primary?.basketCostText)
        assertTrue(basket.collectionEnabled)
        assertTrue(basket.collectionScopeId?.isNotBlank() == true)
        assertEquals(
            "Check-off is only a local shopping-session aid; it does not place an order or change the plan.",
            basket.collectionNotice
        )
        assertNull(basket.extraStopRuleNotice)
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
        assertNull(basket.noCoverageSummary)
        assertEquals("Known subtotal 4.49 CAD", basket.result?.primary?.basketCostText)
        assertEquals("Missing price: Coffee", basket.result?.primary?.missingItemsText)
        assertTrue(basket.collectionEnabled)
        assertEquals(
            "Check-off is only a local shopping-session aid; it does not place an order or change the plan.",
            basket.collectionNotice
        )
        assertEquals(
            "Another stop is not evaluated until every requested item has a usable price.",
            basket.extraStopRuleNotice
        )
        assertEquals(listOf(home.items.first().key), basket.collectibleItemKeys)
        assertTrue(basket.collectionScopeId?.isNotBlank() == true)
        assertEquals(
            "Review the priced items before you shop. Items without a usable price stay unchecked until verified.",
            basket.guidance
        )
        assertEquals(home.sampleNotice, basket.sampleNotice)
        assertEquals(
            "Fictional sample data only — not live retailer prices or availability.",
            basket.sampleNotice
        )
    }

    @Test
    fun changedExtraStopPlanProducesANewCollectionScope() {
        var model =
            PracticalShoppingHomeSession.submit(
                PracticalShoppingHomeSession.initialState(),
                "bananas eggs milk bread rice chicken breast"
            )
        val defaultBasket =
            PracticalShoppingBasketRenderer.render(
                PracticalShoppingHomeRenderer.render(
                    model.model.ui,
                    model.requestDetails.details
                )
            )

        model =
            PracticalShoppingHomeSession.chooseExtraStopMinimumSavings(
                model,
                LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice.ONE_CAD
            )
        val splitBasket =
            PracticalShoppingBasketRenderer.render(
                PracticalShoppingHomeRenderer.render(
                    model.model.ui,
                    model.requestDetails.details
                )
            )

        assertNotEquals(defaultBasket.collectionScopeId, splitBasket.collectionScopeId)
        assertTrue(defaultBasket.collectionScopeId?.isNotBlank() == true)
        assertTrue(splitBasket.collectionScopeId?.isNotBlank() == true)
    }

    @Test
    fun noCoverageKeepsBasketHonestAndHidesRecommendationOnlyControls() {
        val model =
            PracticalShoppingHomeSession.submit(
                LocalSamplePracticalShoppingDemo.initialModel(),
                "coffee"
            )
        val home = PracticalShoppingHomeRenderer.render(model.ui)

        val basket = PracticalShoppingBasketRenderer.render(home)

        assertEquals(PracticalShoppingBasketStatus.PLANNED, basket.status)
        assertEquals("Price coverage needed", basket.headline)
        assertEquals("Not enough price coverage yet", basket.result?.headline)
        assertNull(basket.result?.primary)
        assertEquals("0 of 1 item priced yet.", basket.noCoverageSummary)
        assertEquals(
            listOf("No usable price yet — not included in this plan."),
            basket.items.map { it.priceCoverageNotice }
        )
        assertNull(basket.extraStopRuleText)
        assertNull(basket.extraStopRuleNotice)
        assertEquals(
            "No usable price coverage yet. Return to Home to adjust your sample list.",
            basket.guidance
        )
        assertFalse(basket.collectionEnabled)
        assertNull(basket.collectionScopeId)
        assertNull(basket.collectionNotice)
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
