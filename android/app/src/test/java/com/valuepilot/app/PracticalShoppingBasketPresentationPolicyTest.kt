package com.valuepilot.app

import com.valuepilot.core.ShoppingItemKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingBasketPresentationPolicyTest {

    @Test
    fun emptyBasket_hasOnlySearchActionAndNoProgressOrDetails() {
        val state =
            PracticalShoppingBasketRenderer.render(
                PracticalShoppingHomeRenderer.render(
                    LocalSamplePracticalShoppingDemo.initialModel().ui
                )
            )

        val presentation = PracticalShoppingBasketPresentationPolicy.from(state, 0)

        assertTrue(presentation.isEmpty)
        assertFalse(presentation.showProgress)
        assertTrue(presentation.showPrimaryAction)
        assertFalse(presentation.showDetailsToggle)
        assertFalse(presentation.showTripAction)
    }

    @Test
    fun simpleActiveBasket_putsProductsAndProgressBeforeAdvancedDetails() {
        val state = planned("eggs milk")

        val presentation = PracticalShoppingBasketPresentationPolicy.from(state, 0)

        assertEquals(2, presentation.productCount)
        assertEquals(2, presentation.progressTotal)
        assertEquals(0, presentation.progressCollected)
        assertEquals(0, presentation.progressPercent)
        assertTrue(presentation.showProgress)
        assertTrue(presentation.showDetailsToggle)
        assertFalse(presentation.showRecordPurchase)
    }

    @Test
    fun largeBasket_keepsDerivedStateBoundedAndDeterministic() {
        val base = planned("eggs milk")
        val items =
            List(100) { index ->
                base.items.first().copy(
                    key = ShoppingItemKey("large-$index"),
                    name = "Product $index"
                )
            }
        val state =
            base.copy(
                items = items,
                collectibleItemKeys = items.map { it.key },
                collectionScopeId = "large-basket"
            )

        val first = PracticalShoppingBasketPresentationPolicy.from(state, 37)
        val second = PracticalShoppingBasketPresentationPolicy.from(state, 37)

        assertEquals(first, second)
        assertEquals(100, first.productCount)
        assertEquals(37, first.progressCollected)
        assertEquals(37, first.progressPercent)
    }

    @Test
    fun collectedProducts_exposeASeparateRecordPurchasePromptWithoutAutoHistory() {
        val state = planned("eggs milk")

        val presentation = PracticalShoppingBasketPresentationPolicy.from(state, 1)

        assertEquals(1, presentation.progressCollected)
        assertTrue(presentation.showRecordPurchase)
    }

    @Test
    fun historyAndWatchContext_doesNotChangeTheDefaultBasketDensity() {
        val base = planned("eggs milk")
        val state =
            base.copy(
                items = base.items.map {
                    it.copy(
                        watched = true,
                        personalHistoryNotice = "Bought before"
                    )
                }
            )

        val presentation = PracticalShoppingBasketPresentationPolicy.from(state, 0)

        assertEquals(2, presentation.productCount)
        assertFalse(presentation.showRecordPurchase)
        assertTrue(presentation.showDetailsToggle)
    }

    @Test
    fun selectedStore_addsCompactContextAndTripCostOnlyWhenMeaningful() {
        val base = planned("eggs milk")
        val state = base.copy(items = base.items.map { it.copy(storeAssignment = "Sample Market") })

        val presentation = PracticalShoppingBasketPresentationPolicy.from(state, 0)

        assertTrue(presentation.showStoreContext)
        assertTrue(presentation.showTripAction)
    }

    private fun planned(query: String): PracticalShoppingBasketRenderState {
        val model =
            PracticalShoppingHomeSession.submit(
                LocalSamplePracticalShoppingDemo.initialModel(),
                query
            )
        return PracticalShoppingBasketRenderer.render(
            PracticalShoppingHomeRenderer.render(model.ui)
        )
    }
}
