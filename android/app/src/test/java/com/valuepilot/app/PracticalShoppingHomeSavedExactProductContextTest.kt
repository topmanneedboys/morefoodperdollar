package com.valuepilot.app

import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.SourceProductIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PracticalShoppingHomeSavedExactProductContextTest {

    private val eggs = ShoppingItemKey("sample-eggs-large-12")
    private val milk = ShoppingItemKey("sample-milk-2pct-4l")

    @Test
    fun `validated Saved label becomes a private Home notice without a price claim`() {
        val preference = product(eggs)
        val context =
            PracticalShoppingHomeSavedExactProductContext.fromSnapshot(
                PracticalShoppingSavedValidatedSnapshot(
                    exactState = state(listOf(preference)),
                    displayMetadata =
                        PracticalShoppingSavedExactPreferenceDisplayMetadata(
                            productDisplayNames = mapOf(eggs to "Large Eggs 12 Pack")
                        )
                )
            )

        assertEquals(
            "Saved exact product: Large Eggs 12 Pack. Private choice only — not applied to this fictional sample plan or any current price, stock or availability claim.",
            context.noticeFor(eggs)
        )
        assertNull(context.noticeFor(milk))
        val source =
            PracticalShoppingHomeSession.submit(
                PracticalShoppingHomeSession.initialState(),
                "eggs milk"
            ).model.ui
        val projected = requireNotNull(source.result)
        val rendered =
            PracticalShoppingHomeRenderer.render(
                source = source,
                requestDetails = null,
                savedExactProductContext = context
            )
        assertSame(
            "The Saved display label must never replace the already projected sample plan.",
            projected,
            rendered.result
        )
        assertEquals(
            context.noticeFor(eggs),
            rendered.items.first { it.key == eggs }.savedExactProductNotice
        )
    }

    @Test
    fun `unresolved Saved identity stays explicit without leaking its key`() {
        val context =
            PracticalShoppingHomeSavedExactProductContext.fromSnapshot(
                PracticalShoppingSavedValidatedSnapshot(
                    exactState = state(listOf(product(eggs))),
                    displayMetadata = PracticalShoppingSavedExactPreferenceDisplayMetadata()
                )
            )

        assertEquals(
            "An exact product choice is saved for this item, but its display name is unavailable. Saved keeps the identity without inventing a label; it is not applied to this fictional sample plan.",
            context.noticeFor(eggs)
        )
        assertNull(context.noticeFor(milk))
    }

    @Test
    fun `accepted replacement never keeps an older display label`() {
        val initial =
            requireNotNull(
                PracticalShoppingHomeSavedExactProductContext()
                    .withNamedProduct(eggs, "Old Eggs")
            )
        val replaced = requireNotNull(initial.withUnresolvedProduct(eggs))

        assertNull(replaced.namedProductLabels[eggs])
        assertEquals(
            "An exact product choice is saved for this item, but its display name is unavailable. Saved keeps the identity without inventing a label; it is not applied to this fictional sample plan.",
            replaced.noticeFor(eggs)
        )
        assertNotNull(replaced.withNamedProduct(eggs, "New Eggs"))
    }

    @Test
    fun `immediate label path keeps the Saved projector identifier leakage guard`() {
        assertNull(
            PracticalShoppingHomeSavedExactProductContext().withNamedProduct(
                itemKey = eggs,
                displayName = "036000291452",
                forbiddenIdentifiers = listOf("036000291452")
            )
        )
    }

    private fun product(itemKey: ShoppingItemKey): PracticalShoppingSavedExactProductPreference =
        PracticalShoppingSavedExactProductPreference(
            itemKey = itemKey,
            providerId = EvidenceProviderId("openfoodfacts"),
            sourceIdentity = SourceProductIdentity(gtin = "036000291452")
        )

    private fun state(
        products: List<PracticalShoppingSavedExactProductPreference>
    ): PracticalShoppingSavedExactPreferenceState =
        requireNotNull(
            PracticalShoppingSavedExactPreferenceStateManager.load(
                PracticalShoppingSavedExactPreferenceDocument(
                    schemaVersion = PracticalShoppingSavedExactPreferenceStateManager.currentSchemaVersion,
                    productPreferences = products,
                    storePreferences = emptyList()
                )
            ).state
        )
}
