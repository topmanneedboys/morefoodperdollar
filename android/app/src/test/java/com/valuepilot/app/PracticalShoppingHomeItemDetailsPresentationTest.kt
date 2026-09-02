package com.valuepilot.app

import com.valuepilot.core.ShoppingBrandKey
import com.valuepilot.core.ShoppingBrandPreference
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingItemRequestDetail
import com.valuepilot.core.ShoppingProductSpecificity
import com.valuepilot.core.ShoppingRequestedQuantity
import org.junit.Assert.assertEquals
import org.junit.Test

class PracticalShoppingHomeItemDetailsPresentationTest {

    private val eggs = ShoppingItemKey("sample-eggs-large-12")

    @Test
    fun absentDetailsStayExplicitlyUnspecified() {
        assertEquals(
            "No extra preferences",
            PracticalShoppingHomeItemDetailsPresentation.summary(null)
        )
        assertEquals(
            "Add details",
            PracticalShoppingHomeItemDetailsPresentation.actionLabel(null)
        )
    }

    @Test
    fun summaryNamesOnlyExplicitSavedIntentInStableOrder() {
        val detail =
            ShoppingItemRequestDetail(
                itemKey = eggs,
                productSpecificity = ShoppingProductSpecificity.EXACT_PRODUCT_REQUIRED,
                requestedQuantity = ShoppingRequestedQuantity(packageCount = 2),
                brandPreference = ShoppingBrandPreference.exact(ShoppingBrandKey("Neilson"))
            )

        assertEquals(
            "2 packages · Brand: Neilson · Exact product",
            PracticalShoppingHomeItemDetailsPresentation.summary(detail)
        )
        assertEquals(
            "Edit details",
            PracticalShoppingHomeItemDetailsPresentation.actionLabel(detail)
        )
    }

    @Test
    fun rendererSurfacesSavedDetailsWithoutChangingTheProjectedPlanObject() {
        var state =
            PracticalShoppingHomeSession.submit(
                PracticalShoppingHomeSession.initialState(),
                "eggs milk"
            )
        val eggsDetail =
            ShoppingItemRequestDetail(
                itemKey = eggs,
                requestedQuantity = ShoppingRequestedQuantity(packageCount = 2)
            )
        state = PracticalShoppingHomeSession.withItemDetail(state, eggsDetail)

        val projected = requireNotNull(state.model.ui.result)
        val rendered =
            PracticalShoppingHomeRenderer.render(
                state.model.ui,
                state.requestDetails.details
            )

        assertEquals("2 packages", rendered.items.first { it.key == eggs }.requestDetailsSummary)
        assertEquals("Edit details", rendered.items.first { it.key == eggs }.requestDetailsActionLabel)
        assertEquals("No extra preferences", rendered.items.first { it.key.value.contains("milk") }.requestDetailsSummary)
        org.junit.Assert.assertSame(projected, rendered.result)
    }
}
