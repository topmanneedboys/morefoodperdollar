package com.valuepilot.app

import com.valuepilot.core.ShoppingItemKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingHomeRendererTest {

    @Test
    fun blankDraftStaysSimpleAndCannotSubmit() {
        val rendered =
            PracticalShoppingHomeRenderer.render(
                LocalSamplePracticalShoppingDemo.initialModel().ui
            )

        assertEquals("", rendered.query)
        assertEquals(240, rendered.queryCharacterLimit)
        assertFalse(rendered.submitEnabled)
        assertFalse(rendered.shopAgainVisible)
        assertEquals(PracticalShoppingHomeMessageTone.NEUTRAL, rendered.messageTone)
        assertTrue(rendered.items.isEmpty())
        assertNull(rendered.refinement)
        assertTrue(rendered.unknownItems.isEmpty())
        assertNull(rendered.result)
        assertEquals(
            PracticalShoppingHomePrivateMemoryStatus.AVAILABLE,
            rendered.privateMemoryStatus
        )
        assertNull(rendered.privateMemorySummary)
        assertFalse(rendered.privateMemoryReviewActionVisible)
        assertFalse(rendered.privateMemoryExportActionVisible)
        assertFalse(rendered.privateMemoryForgetActionVisible)
        assertFalse(rendered.extraStopSettings.visible)
        assertEquals("Extra-stop rule · Save at least 15.00 CAD", rendered.extraStopSettings.summary)
        assertEquals(
            listOf("1.00 CAD", "15.00 CAD", "25.00 CAD"),
            rendered.extraStopSettings.choices.map { it.label }
        )
        assertEquals(
            listOf(false, true, false),
            rendered.extraStopSettings.choices.map { it.selected }
        )
        assertTrue(rendered.sampleNotice.contains("Fictional sample data"))
    }

    @Test
    fun sampleDisclosureExplicitlyRejectsLiveInterpretation() {
        val rendered =
            PracticalShoppingHomeRenderer.render(
                LocalSamplePracticalShoppingDemo.initialModel().ui
            )

        assertEquals(
            "Fictional sample data only — not live retailer prices or availability.",
            rendered.sampleNotice
        )
    }

    @Test
    fun chickenRefinementKeepsResolvedItemsAndOneTapChoicesVisible() {
        val model =
            PracticalShoppingHomeSession.submit(
                LocalSamplePracticalShoppingDemo.initialModel(),
                "chicken eggs milk"
            )
        val rendered = PracticalShoppingHomeRenderer.render(model.ui)

        assertFalse(rendered.submitEnabled)
        assertEquals(
            PracticalShoppingHomeMessageTone.ACTION_REQUIRED,
            rendered.messageTone
        )
        assertEquals(listOf("Eggs", "Milk"), rendered.items.map { it.name })
        val refinement = requireNotNull(rendered.refinement)
        assertEquals("Chicken", refinement.prompt)
        assertEquals(
            listOf("Breast", "Thighs", "Drumsticks", "Whole chicken", "Ground chicken"),
            refinement.choices.map { it.label }
        )
        assertEquals(
            LocalSamplePracticalShoppingDemo.ChickenChoice.entries,
            refinement.choices.map { it.choice }
        )
        assertNull(rendered.result)
        assertFalse(rendered.extraStopSettings.visible)
    }

    @Test
    fun unresolvedItemsDisableRepeatSubmitUntilTheListIsCorrected() {
        val model =
            PracticalShoppingHomeSession.submit(
                LocalSamplePracticalShoppingDemo.initialModel(),
                "eggs dragonfruit"
            )

        val rendered = PracticalShoppingHomeRenderer.render(model.ui)

        assertEquals(LocalSamplePracticalShoppingDemo.Status.NEEDS_REFINEMENT, model.ui.status)
        assertFalse(rendered.submitEnabled)
        assertTrue(rendered.unknownItems.contains("dragonfruit"))
    }

    @Test
    fun resolvedItemRenderRowsKeepOpaqueKeysForTypedRemovalActions() {
        val model =
            PracticalShoppingHomeSession.submit(
                LocalSamplePracticalShoppingDemo.initialModel(),
                "eggs milk"
            )
        val rendered = PracticalShoppingHomeRenderer.render(model.ui)

        assertEquals(
            model.ui.items.map { it.key },
            rendered.items.map { it.key }
        )
        assertEquals(listOf("Eggs", "Milk"), rendered.items.map { it.name })
        assertTrue(rendered.items.all { it.exactProductActionVisible })
    }

    @Test
    fun unknownItemsRemainVisibleInsteadOfProducingAFalseCompleteResult() {
        val model =
            PracticalShoppingHomeSession.submit(
                LocalSamplePracticalShoppingDemo.initialModel(),
                "eggs dragonfruit"
            )
        val rendered = PracticalShoppingHomeRenderer.render(model.ui)

        assertEquals(listOf("Eggs"), rendered.items.map { it.name })
        assertEquals(listOf("dragonfruit"), rendered.unknownItems)
        assertEquals(
            PracticalShoppingHomeMessageTone.ACTION_REQUIRED,
            rendered.messageTone
        )
        assertNull(rendered.result)
    }

    @Test
    fun completedHomeResultPassesTheAlreadyProjectedDecisionThroughUnchanged() {
        var model =
            PracticalShoppingHomeSession.submit(
                LocalSamplePracticalShoppingDemo.initialModel(),
                "chicken eggs milk"
            )
        model =
            PracticalShoppingHomeSession.chooseChicken(
                model,
                LocalSamplePracticalShoppingDemo.ChickenChoice.DRUMSTICKS
            )

        val sourceResult = requireNotNull(model.ui.result)
        val rendered = PracticalShoppingHomeRenderer.render(model.ui)

        assertSame(sourceResult, rendered.result)
        assertTrue(rendered.extraStopSettings.visible)
        assertTrue(rendered.shopAgainVisible)
        assertEquals("Your best practical shop", rendered.result?.headline)
        assertEquals("Example Grocer East", rendered.result?.primary?.storeName)
        assertEquals("Basket 18.77 CAD", rendered.result?.primary?.basketCostText)
        assertEquals(
            listOf("Example Grocer East", "Example Grocer East", "Example Grocer East"),
            rendered.items.map { it.storeAssignment }
        )
        assertEquals(
            listOf("8.49 CAD", "4.29 CAD", "5.99 CAD"),
            rendered.items.map { it.plannedPriceText }
        )
        assertNull(rendered.refinement)
        assertTrue(rendered.unknownItems.isEmpty())
    }

    @Test
    fun incompleteHomeResultKeepsTheMissingPriceItemExplicit() {
        val model =
            PracticalShoppingHomeSession.submit(
                LocalSamplePracticalShoppingDemo.initialModel(),
                "eggs coffee"
            )

        val sourceResult = requireNotNull(model.ui.result)
        val rendered = PracticalShoppingHomeRenderer.render(model.ui)

        assertSame(sourceResult, rendered.result)
        assertTrue(rendered.extraStopSettings.visible)
        assertTrue(rendered.shopAgainVisible)
        assertEquals("Known subtotal 4.49 CAD", rendered.result?.primary?.basketCostText)
        assertEquals("Missing price: Coffee", rendered.result?.primary?.missingItemsText)
        assertEquals(listOf("Sample Market North", null), rendered.items.map { it.storeAssignment })
        assertEquals(
            listOf("4.49 CAD", null),
            rendered.items.map { it.plannedPriceText }
        )
        assertEquals(
            listOf(null, "No usable price yet — not included in this plan."),
            rendered.items.map { it.priceCoverageNotice }
        )
        assertEquals(listOf(null, null), rendered.items.map { it.plannedPriceNotice })
        assertEquals(
            listOf(false, true),
            rendered.items.map { it.observedPriceActionVisible }
        )
        assertTrue(rendered.extraStopSettings.visible)
        assertEquals(
            "Extra-stop rule · Save at least 15.00 CAD · Not evaluated yet",
            rendered.extraStopSettings.summary
        )
        assertEquals(
            "Another stop is not evaluated until every requested item has a usable price.",
            rendered.extraStopSettings.notice
        )
        assertTrue(rendered.sampleNotice.contains("Fictional sample data"))
    }

    @Test
    fun completeHomeResultDoesNotCarryAnIncompleteExtraStopNotice() {
        val model =
            PracticalShoppingHomeSession.submit(
                PracticalShoppingHomeSession.initialState(),
                "eggs milk"
            )
        val rendered = PracticalShoppingHomeRenderer.render(model.model.ui)

        assertTrue(rendered.extraStopSettings.visible)
        assertEquals(
            "Extra-stop rule · Save at least 15.00 CAD",
            rendered.extraStopSettings.summary
        )
        assertNull(rendered.extraStopSettings.notice)
        assertTrue(rendered.items.all { it.priceCoverageNotice == null })
        assertTrue(rendered.items.all { it.plannedPriceText != null })
        assertTrue(rendered.items.none { it.observedPriceActionVisible })
    }

    @Test
    fun coveredItemsWithoutOptionalBreakdownAreMarkedAsIncludedButNotIndividuallyPriced() {
        val model =
            PracticalShoppingHomeSession.submit(
                LocalSamplePracticalShoppingDemo.initialModel(),
                "eggs milk"
            )
        val sourceResult = requireNotNull(model.ui.result)
        val withoutBreakdown =
            sourceResult.copy(
                itemStoreAssignments =
                    sourceResult.itemStoreAssignments.map { assignment ->
                        assignment.copy(priceText = null)
                    }
            )

        val rendered =
            PracticalShoppingHomeRenderer.render(
                model.ui.copy(result = withoutBreakdown)
            )

        assertEquals(listOf(null, null), rendered.items.map { it.plannedPriceText })
        assertEquals(
            listOf(
                PracticalShoppingHomeRenderer.ITEM_PRICE_BREAKDOWN_NOTICE,
                PracticalShoppingHomeRenderer.ITEM_PRICE_BREAKDOWN_NOTICE
            ),
            rendered.items.map { it.plannedPriceNotice }
        )
        assertEquals(listOf(null, null), rendered.items.map { it.priceCoverageNotice })
        assertTrue(rendered.items.none { it.observedPriceActionVisible })
    }

    @Test
    fun noCoverageResultDoesNotExposeAnExtraStopRuleWithoutAStorePlan() {
        val model =
            PracticalShoppingHomeSession.submit(
                LocalSamplePracticalShoppingDemo.initialModel(),
                "coffee"
            )

        val rendered = PracticalShoppingHomeRenderer.render(model.ui)

        assertEquals("Not enough price coverage yet", rendered.result?.headline)
        assertNull(rendered.result?.primary)
        assertEquals(
            listOf("No usable price yet — not included in this plan."),
            rendered.items.map { it.priceCoverageNotice }
        )
        assertEquals("0 of 1 item priced yet.", rendered.noCoverageSummary)
        assertFalse(rendered.extraStopSettings.visible)
        assertTrue(rendered.shopAgainVisible)
        assertTrue(rendered.items.single().observedPriceActionVisible)
    }

    @Test
    fun noCoverageSummaryCountsEveryRequestedItem() {
        val source =
            LocalSamplePracticalShoppingDemo.initialModel().ui.copy(
                query = "coffee rice",
                status = LocalSamplePracticalShoppingDemo.Status.RESULT,
                items =
                    listOf(
                        LocalSamplePracticalShoppingDemo.ResolvedItemUiState(
                            key = ShoppingItemKey("sample-coffee"),
                            name = "Coffee",
                            detail = "340 g"
                        ),
                        LocalSamplePracticalShoppingDemo.ResolvedItemUiState(
                            key = ShoppingItemKey("sample-rice"),
                            name = "Rice",
                            detail = "2 kg"
                        )
                    ),
                result =
                    PracticalShoppingUiState(
                        headline = "Not enough price coverage yet",
                        primary = null,
                        secondStop = null,
                        secondaryMessage = "No requested item has a usable price yet."
                    ),
                message = null
            )

        val rendered = PracticalShoppingHomeRenderer.render(source)

        assertEquals("0 of 2 items priced yet.", rendered.noCoverageSummary)
    }

    @Test
    fun coverageSummaryStaysHiddenWhenAProjectedPrimaryPlanExists() {
        val model =
            PracticalShoppingHomeSession.submit(
                LocalSamplePracticalShoppingDemo.initialModel(),
                "eggs milk"
            )

        val rendered = PracticalShoppingHomeRenderer.render(model.ui)

        assertNull(rendered.noCoverageSummary)
    }

    @Test
    fun selectedExactExtraStopPreferenceIsUiReadyAfterAResultAndRemainsTyped() {
        var model = LocalSamplePracticalShoppingDemo.initialModel()
        model =
            PracticalShoppingHomeSession.chooseExtraStopMinimumSavings(
                model,
                LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice.TWENTY_FIVE_CAD
            )
        model = PracticalShoppingHomeSession.submit(model, "eggs milk")

        val rendered = PracticalShoppingHomeRenderer.render(model.ui)

        assertTrue(rendered.extraStopSettings.visible)
        assertEquals("Extra-stop rule · Save at least 25.00 CAD", rendered.extraStopSettings.summary)
        val selected = rendered.extraStopSettings.choices.single { it.selected }
        assertEquals("25.00 CAD", selected.label)
        assertEquals(
            LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice.TWENTY_FIVE_CAD,
            selected.choice
        )
    }

    @Test
    fun extraStopSettingsDescriptionNamesTheProgressiveDisclosureDirection() {
        val summary = "Extra-stop rule · Save at least 15.00 CAD"

        assertEquals(
            "Show extra-stop rule settings. $summary",
            practicalShoppingExtraStopSettingsContentDescription(summary, expanded = false)
        )
        assertEquals(
            "Hide extra-stop rule settings. $summary",
            practicalShoppingExtraStopSettingsContentDescription(summary, expanded = true)
        )
    }

    @Test
    fun incompleteExtraStopSettingsDescriptionKeepsEvaluationBoundaryExplicit() {
        val summary = "Extra-stop rule · Save at least 15.00 CAD"
        val notice =
            "Another stop is not evaluated until every requested item has a usable price."

        assertEquals(
            "Show extra-stop rule settings. $summary. $notice.",
            practicalShoppingExtraStopSettingsContentDescription(
                summary,
                expanded = false,
                notice = notice
            )
        )
    }

    @Test
    fun overlongQueryIsRenderedAsAnErrorAndCannotSubmit() {
        val tooLong = "x".repeat(241)
        val model =
            LocalSamplePracticalShoppingDemo.reduce(
                LocalSamplePracticalShoppingDemo.initialModel(),
                LocalSamplePracticalShoppingDemo.Intent.QueryChanged(tooLong)
            )
        val rendered = PracticalShoppingHomeRenderer.render(model.ui)

        assertEquals(tooLong, rendered.query)
        assertEquals(240, rendered.queryCharacterLimit)
        assertEquals(rendered.queryCharacterLimit + 1, rendered.query.length)
        assertFalse(rendered.submitEnabled)
        assertFalse(rendered.shopAgainVisible)
        assertFalse(rendered.extraStopSettings.visible)
        assertEquals(PracticalShoppingHomeMessageTone.ERROR, rendered.messageTone)
        assertTrue(rendered.items.isEmpty())
        assertTrue(rendered.unknownItems.isEmpty())
        assertNull(rendered.result)
    }
}
