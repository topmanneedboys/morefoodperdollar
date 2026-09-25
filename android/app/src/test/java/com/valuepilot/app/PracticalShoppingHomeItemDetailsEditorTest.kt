package com.valuepilot.app

import com.valuepilot.core.QuantityNormalization
import com.valuepilot.core.ShoppingBrandFlexibility
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingItemRequestDetail
import com.valuepilot.core.ShoppingProductSpecificity
import com.valuepilot.core.ShoppingRequestedQuantity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingHomeItemDetailsEditorTest {

    private val milk = ShoppingItemKey("sample-milk-2pct-4l")

    @Test
    fun explicitPackageBrandAndProductChoicesBecomeTypedIntent() {
        val outcome =
            PracticalShoppingHomeItemDetailsEditor.apply(
                itemKey = milk,
                current = null,
                draft =
                    PracticalShoppingHomeItemDetailsEditor.Draft(
                        packageCountText = "2",
                        brandText = "Neilson",
                        exactProduct = true
                    )
            )

        val detail = requireAccepted(outcome)
        assertEquals(milk, detail?.itemKey)
        assertEquals(ShoppingProductSpecificity.EXACT_PRODUCT_REQUIRED, detail?.productSpecificity)
        assertEquals(2L, detail?.requestedQuantity?.packageCount)
        assertEquals("Neilson", detail?.brandPreference?.exactBrandKey?.value)
        assertEquals(ShoppingBrandFlexibility.EXACT_REQUIRED, detail?.brandPreference?.flexibility)
    }

    @Test
    fun blankEditorClearsOnlyTheRepresentedPreferences() {
        val current =
            ShoppingItemRequestDetail(
                itemKey = milk,
                productSpecificity = ShoppingProductSpecificity.EXACT_PRODUCT_REQUIRED,
                requestedQuantity =
                    ShoppingRequestedQuantity(
                        totalQuantity = QuantityNormalization.litres(2),
                        packageCount = 2,
                        preferredPackageQuantity = QuantityNormalization.litres(1)
                    )
            )

        val outcome =
            PracticalShoppingHomeItemDetailsEditor.apply(
                itemKey = milk,
                current = current,
                draft =
                    PracticalShoppingHomeItemDetailsEditor.Draft(
                        packageCountText = "",
                        brandText = "",
                        exactProduct = false
                    )
            )

        val detail = requireAccepted(outcome)
        assertEquals(ShoppingProductSpecificity.BROAD_INTENT, detail?.productSpecificity)
        assertEquals(ShoppingBrandFlexibility.FLEXIBLE, detail?.brandPreference?.flexibility)
        assertEquals(2_000L, detail?.requestedQuantity?.totalQuantity?.amountMicros?.div(1_000_000L))
        assertNull(detail?.requestedQuantity?.packageCount)
        assertEquals(1_000_000_000L, detail?.requestedQuantity?.preferredPackageQuantity?.amountMicros)
    }

    @Test
    fun blankNewEditorProducesNoStoredDetail() {
        val outcome =
            PracticalShoppingHomeItemDetailsEditor.apply(
                itemKey = milk,
                current = null,
                draft = PracticalShoppingHomeItemDetailsEditor.Draft("", "", false)
            )

        assertNull(requireAccepted(outcome))
    }

    @Test
    fun packageCountIsBoundedAndWholeNumberOnly() {
        listOf("0", "1000001", "1.5", "two", "-1").forEach { value ->
            val outcome =
                PracticalShoppingHomeItemDetailsEditor.apply(
                    itemKey = milk,
                    current = null,
                    draft = PracticalShoppingHomeItemDetailsEditor.Draft(value, "", false)
                )

            assertTrue(outcome is PracticalShoppingHomeItemDetailsEditor.Outcome.Rejected)
            assertEquals(
                PracticalShoppingHomeItemDetailsEditor.Field.PACKAGE_COUNT,
                (outcome as PracticalShoppingHomeItemDetailsEditor.Outcome.Rejected).field
            )
        }
    }

    @Test
    fun brandTextUsesTheCoreBoundAndRejectsOversizedInput() {
        val outcome =
            PracticalShoppingHomeItemDetailsEditor.apply(
                itemKey = milk,
                current = null,
                draft = PracticalShoppingHomeItemDetailsEditor.Draft("", "b".repeat(161), false)
            )

        assertTrue(outcome is PracticalShoppingHomeItemDetailsEditor.Outcome.Rejected)
        assertEquals(
            PracticalShoppingHomeItemDetailsEditor.Field.BRAND,
            (outcome as PracticalShoppingHomeItemDetailsEditor.Outcome.Rejected).field
        )
    }

    private fun requireAccepted(
        outcome: PracticalShoppingHomeItemDetailsEditor.Outcome
    ): com.valuepilot.core.ShoppingItemRequestDetail? {
        assertTrue("Expected accepted editor outcome: $outcome", outcome is PracticalShoppingHomeItemDetailsEditor.Outcome.Accepted)
        return (outcome as PracticalShoppingHomeItemDetailsEditor.Outcome.Accepted).detail
    }
}
