package com.valuepilot.app

import com.valuepilot.core.PracticalShoppingProductIntentRelationship
import com.valuepilot.core.PracticalShoppingStoreIdentityRelationship
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingExactChoiceConfirmationPresentationTest {

    @Test
    fun `OFF suggestion exposes only safe recognition copy and confirms through opaque action`() {
        val itemKey = ShoppingItemKey("eggs")
        val row = offRow("036000291452", "Example Eggs")
        val candidate = offCandidate(itemKey, row, "source-candidate-secret")

        val projection =
            PracticalShoppingExactChoiceConfirmationProjector.projectOpenFoodFactsProducts(
                presentationGeneration = 1L,
                itemKey = itemKey,
                options = listOf(PracticalShoppingOpenFoodFactsConfirmationOption(candidate, row))
            )

        assertTrue(projection.rejectedOptions.isEmpty())
        assertEquals(1, projection.state.rows.size)
        val stateText = projection.state.toString()
        assertEquals("Example Eggs", projection.state.rows.single().title)
        assertFalse(stateText.contains(candidate.candidateId))
        assertFalse(stateText.contains("036000291452"))
        assertFalse(stateText.contains("open-food-facts"))
        assertEquals(1L, projection.state.rows.single().action.presentationGeneration)
        assertEquals(1, projection.state.rows.single().action.optionId)

        val selected =
            projection.confirm(
                action = projection.state.rows.single().action,
                confirmedCandidateId = "confirmed-product-1"
            )

        val choice = requireNotNull(selected.selection)
        assertNull(selected.issue)
        assertEquals(
            PracticalShoppingProductIntentRelationship.USER_CONFIRMED_EXACT_PRODUCT,
            choice.confirmedCandidate.relationship
        )
        assertEquals(candidate.itemKey, choice.confirmedCandidate.itemKey)
        assertEquals(candidate.providerId, choice.confirmedCandidate.providerId)
        assertEquals(candidate.sourceIdentity, choice.confirmedCandidate.sourceIdentity)
        assertEquals(candidate.dataset, choice.confirmedCandidate.dataset)
        assertEquals(choice.confirmedCandidate, choice.rememberRequest.confirmedCandidate)
        assertEquals(row, choice.rememberRequest.row)
    }

    @Test
    fun `OSM suggestion exposes safe place name and confirms exact source scope`() {
        val storeKey = ShoppingStoreKey("north")
        val row = osmDisplayRow(11L, "North Market")
        val candidate = osmCandidate(storeKey, row.identity, "source-store-secret")

        val projection =
            PracticalShoppingExactChoiceConfirmationProjector.projectOpenStreetMapStores(
                presentationGeneration = 2L,
                storeKey = storeKey,
                options = listOf(PracticalShoppingOpenStreetMapConfirmationOption(candidate, row))
            )

        assertTrue(projection.rejectedOptions.isEmpty())
        assertEquals("North Market", projection.state.rows.single().title)
        val stateText = projection.state.toString()
        assertFalse(stateText.contains(candidate.candidateId))
        assertFalse(stateText.contains("Q483551"))
        assertFalse(stateText.contains("osm:node:11"))
        assertFalse(stateText.contains("PHYSICAL_STORE"))
        assertEquals(2L, projection.state.rows.single().action.presentationGeneration)
        assertEquals(1, projection.state.rows.single().action.optionId)

        val selected =
            projection.confirm(
                action = projection.state.rows.single().action,
                confirmedCandidateId = "confirmed-store-1"
            )

        val choice = requireNotNull(selected.selection)
        assertEquals(
            PracticalShoppingStoreIdentityRelationship.USER_CONFIRMED_EXACT_STORE,
            choice.confirmedCandidate.relationship
        )
        assertEquals(candidate.scope, choice.confirmedCandidate.scope)
        assertEquals(candidate.providerId, choice.confirmedCandidate.providerId)
        assertEquals(candidate.dataset, choice.confirmedCandidate.dataset)
        assertEquals(choice.confirmedCandidate, choice.rememberRequest.confirmedCandidate)
        assertEquals(row, choice.rememberRequest.row)
    }

    @Test
    fun `product name cannot expose GTIN or provider identity`() {
        val itemKey = ShoppingItemKey("eggs")
        val gtinLabelRow = offRow("036000291452", "Eggs 036000291452")
        val providerLabelRow = offRow("042100005264", "open-food-facts")

        val projection =
            PracticalShoppingExactChoiceConfirmationProjector.projectOpenFoodFactsProducts(
                presentationGeneration = 3L,
                itemKey = itemKey,
                options =
                    listOf(
                        PracticalShoppingOpenFoodFactsConfirmationOption(
                            offCandidate(itemKey, gtinLabelRow, "gtin-label"),
                            gtinLabelRow
                        ),
                        PracticalShoppingOpenFoodFactsConfirmationOption(
                            offCandidate(itemKey, providerLabelRow, "provider-label"),
                            providerLabelRow
                        )
                    )
            )

        assertTrue(projection.state.rows.isEmpty())
        assertEquals(2, projection.state.omittedChoiceCount)
        assertEquals("No product choices can be shown safely yet.", projection.state.emptyMessage)
        assertTrue(
            projection.rejectedOptions.all {
                it.issues == setOf(PracticalShoppingExactChoicePresentationIssue.DISPLAY_NAME_UNAVAILABLE)
            }
        )
    }

    @Test
    fun `store name cannot expose merchant or location identity suffix`() {
        val storeKey = ShoppingStoreKey("north")
        val merchantLabel = osmDisplayRow(11L, "Market Q483551")
        val locationLabel = osmDisplayRow(123456L, "Branch 123456")

        val projection =
            PracticalShoppingExactChoiceConfirmationProjector.projectOpenStreetMapStores(
                presentationGeneration = 4L,
                storeKey = storeKey,
                options =
                    listOf(
                        PracticalShoppingOpenStreetMapConfirmationOption(
                            osmCandidate(storeKey, merchantLabel.identity, "merchant-label"),
                            merchantLabel
                        ),
                        PracticalShoppingOpenStreetMapConfirmationOption(
                            osmCandidate(storeKey, locationLabel.identity, "location-label"),
                            locationLabel
                        )
                    )
            )

        assertTrue(projection.state.rows.isEmpty())
        assertEquals(2, projection.state.omittedChoiceCount)
        assertTrue(
            projection.rejectedOptions.all {
                PracticalShoppingExactChoicePresentationIssue.DISPLAY_NAME_UNAVAILABLE in it.issues
            }
        )
    }

    @Test
    fun `same product name cannot hide a different source identity`() {
        val itemKey = ShoppingItemKey("eggs")
        val candidateRow = offRow("036000291452", "Same Name")
        val displayedRow = offRow("042100005264", "Same Name")
        val candidate = offCandidate(itemKey, candidateRow, "original")

        val projection =
            PracticalShoppingExactChoiceConfirmationProjector.projectOpenFoodFactsProducts(
                presentationGeneration = 5L,
                itemKey = itemKey,
                options = listOf(PracticalShoppingOpenFoodFactsConfirmationOption(candidate, displayedRow))
            )

        assertTrue(projection.state.rows.isEmpty())
        assertEquals(
            setOf(PracticalShoppingExactChoicePresentationIssue.SOURCE_IDENTITY_MISMATCH),
            projection.rejectedOptions.single().issues
        )
    }

    @Test
    fun `same store name cannot hide a different OSM location scope`() {
        val storeKey = ShoppingStoreKey("north")
        val original = osmDisplayRow(11L, "Same Market")
        val displayed = osmDisplayRow(22L, "Same Market")
        val candidate = osmCandidate(storeKey, original.identity, "original-store")

        val projection =
            PracticalShoppingExactChoiceConfirmationProjector.projectOpenStreetMapStores(
                presentationGeneration = 6L,
                storeKey = storeKey,
                options = listOf(PracticalShoppingOpenStreetMapConfirmationOption(candidate, displayed))
            )

        assertTrue(projection.state.rows.isEmpty())
        assertEquals(
            setOf(PracticalShoppingExactChoicePresentationIssue.SOURCE_IDENTITY_MISMATCH),
            projection.rejectedOptions.single().issues
        )
    }

    @Test
    fun `wrong logical key and already confirmed relationship are not offered as suggestions`() {
        val expectedItem = ShoppingItemKey("expected")
        val sourceRow = offRow("036000291452", "Example Eggs")
        val wrongKeyCandidate = offCandidate(ShoppingItemKey("other"), sourceRow, "wrong-key")
        val alreadyConfirmed =
            offCandidate(expectedItem, sourceRow, "already-confirmed")
                .copy(relationship = PracticalShoppingProductIntentRelationship.USER_CONFIRMED_EXACT_PRODUCT)

        val projection =
            PracticalShoppingExactChoiceConfirmationProjector.projectOpenFoodFactsProducts(
                presentationGeneration = 7L,
                itemKey = expectedItem,
                options =
                    listOf(
                        PracticalShoppingOpenFoodFactsConfirmationOption(wrongKeyCandidate, sourceRow),
                        PracticalShoppingOpenFoodFactsConfirmationOption(alreadyConfirmed, sourceRow)
                    )
            )

        assertTrue(projection.state.rows.isEmpty())
        assertTrue(
            PracticalShoppingExactChoicePresentationIssue.LOGICAL_KEY_MISMATCH in
                projection.rejectedOptions.first { it.candidateId == "wrong-key" }.issues
        )
        assertTrue(
            PracticalShoppingExactChoicePresentationIssue.RELATIONSHIP_NOT_SELECTABLE in
                projection.rejectedOptions.first { it.candidateId == "already-confirmed" }.issues
        )
    }

    @Test
    fun `invalid source row fails revalidation and remains generic in UI`() {
        val itemKey = ShoppingItemKey("eggs")
        val goodRow = offRow("036000291452", "Example Eggs")
        val invalidRow = offRow("036000291453", "Example Eggs")
        val candidate = offCandidate(itemKey, goodRow, "good-source")

        val projection =
            PracticalShoppingExactChoiceConfirmationProjector.projectOpenFoodFactsProducts(
                presentationGeneration = 8L,
                itemKey = itemKey,
                options = listOf(PracticalShoppingOpenFoodFactsConfirmationOption(candidate, invalidRow))
            )

        assertTrue(projection.state.rows.isEmpty())
        assertEquals(
            setOf(PracticalShoppingExactChoicePresentationIssue.SOURCE_REVALIDATION_FAILED),
            projection.rejectedOptions.single().issues
        )
        assertFalse(projection.state.toString().contains("036000291453"))
        assertFalse(projection.state.toString().contains(candidate.candidateId))
    }

    @Test
    fun `stale presentation action cannot select a current candidate`() {
        val itemKey = ShoppingItemKey("eggs")
        val row = offRow("036000291452", "Example Eggs")
        val projection =
            PracticalShoppingExactChoiceConfirmationProjector.projectOpenFoodFactsProducts(
                presentationGeneration = 9L,
                itemKey = itemKey,
                options =
                    listOf(
                        PracticalShoppingOpenFoodFactsConfirmationOption(
                            offCandidate(itemKey, row, "candidate"),
                            row
                        )
                    )
            )
        val stale = projection.state.rows.single().action.copy(presentationGeneration = 10L)

        val result = projection.confirm(stale, "confirmed-1")

        assertNull(result.selection)
        assertEquals(PracticalShoppingExactChoiceSelectionIssue.STALE_OR_UNKNOWN_ACTION, result.issue)
    }

    @Test
    fun `unknown option action cannot reconstruct identity`() {
        val storeKey = ShoppingStoreKey("north")
        val first = osmDisplayRow(11L, "North Market")
        val second = osmDisplayRow(22L, "South Market")
        val projection =
            PracticalShoppingExactChoiceConfirmationProjector.projectOpenStreetMapStores(
                presentationGeneration = 11L,
                storeKey = storeKey,
                options =
                    listOf(
                        PracticalShoppingOpenStreetMapConfirmationOption(
                            osmCandidate(storeKey, first.identity, "store-one"),
                            first
                        ),
                        PracticalShoppingOpenStreetMapConfirmationOption(
                            osmCandidate(storeKey, second.identity, "store-two"),
                            second
                        )
                    )
            )
        val unknown = projection.state.rows.first().action.copy(optionId = 3)

        val result = projection.confirm(unknown, "confirmed-store")

        assertNull(result.selection)
        assertEquals(PracticalShoppingExactChoiceSelectionIssue.STALE_OR_UNKNOWN_ACTION, result.issue)
    }

    @Test
    fun `invalid confirmation candidate id fails before confirmation adapter construction`() {
        val itemKey = ShoppingItemKey("eggs")
        val row = offRow("036000291452", "Example Eggs")
        val projection =
            PracticalShoppingExactChoiceConfirmationProjector.projectOpenFoodFactsProducts(
                presentationGeneration = 12L,
                itemKey = itemKey,
                options =
                    listOf(
                        PracticalShoppingOpenFoodFactsConfirmationOption(
                            offCandidate(itemKey, row, "candidate"),
                            row
                        )
                    )
            )

        val result = projection.confirm(projection.state.rows.single().action, " ")

        assertNull(result.selection)
        assertEquals(
            PracticalShoppingExactChoiceSelectionIssue.INVALID_CONFIRMATION_CANDIDATE_ID,
            result.issue
        )
    }

    @Test
    fun `safe and unsafe choices coexist without unsafe row manufacturing`() {
        val itemKey = ShoppingItemKey("eggs")
        val safe = offRow("036000291452", "Example Eggs")
        val unsafe = offRow("042100005264", "Milk 042100005264")

        val projection =
            PracticalShoppingExactChoiceConfirmationProjector.projectOpenFoodFactsProducts(
                presentationGeneration = 13L,
                itemKey = itemKey,
                options =
                    listOf(
                        PracticalShoppingOpenFoodFactsConfirmationOption(
                            offCandidate(itemKey, safe, "safe"),
                            safe
                        ),
                        PracticalShoppingOpenFoodFactsConfirmationOption(
                            offCandidate(itemKey, unsafe, "unsafe"),
                            unsafe
                        )
                    )
            )

        assertEquals(listOf("Example Eggs"), projection.state.rows.map { it.title })
        assertEquals(1, projection.state.omittedChoiceCount)
        assertEquals("1 choice could not be shown safely.", projection.state.notice)
        assertNull(projection.state.emptyMessage)
        assertEquals(1, projection.state.rows.single().action.optionId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `product option set is bounded before projection work`() {
        val itemKey = ShoppingItemKey("eggs")
        val row = offRow("036000291452", "Example Eggs")
        val base = offCandidate(itemKey, row, "candidate-0")
        val options =
            (0..32).map { index ->
                PracticalShoppingOpenFoodFactsConfirmationOption(
                    candidate = base.copy(candidateId = "candidate-$index"),
                    row = row
                )
            }

        PracticalShoppingExactChoiceConfirmationProjector.projectOpenFoodFactsProducts(
            presentationGeneration = 14L,
            itemKey = itemKey,
            options = options
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate source candidate ids are rejected before option ids are generated`() {
        val itemKey = ShoppingItemKey("eggs")
        val first = offRow("036000291452", "Eggs")
        val second = offRow("042100005264", "Milk")

        PracticalShoppingExactChoiceConfirmationProjector.projectOpenFoodFactsProducts(
            presentationGeneration = 15L,
            itemKey = itemKey,
            options =
                listOf(
                    PracticalShoppingOpenFoodFactsConfirmationOption(
                        offCandidate(itemKey, first, "duplicate"),
                        first
                    ),
                    PracticalShoppingOpenFoodFactsConfirmationOption(
                        offCandidate(itemKey, second, "duplicate"),
                        second
                    )
                )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `nonpositive presentation generation is rejected`() {
        val itemKey = ShoppingItemKey("eggs")
        val row = offRow("036000291452", "Eggs")

        PracticalShoppingExactChoiceConfirmationProjector.projectOpenFoodFactsProducts(
            presentationGeneration = 0L,
            itemKey = itemKey,
            options =
                listOf(
                    PracticalShoppingOpenFoodFactsConfirmationOption(
                        offCandidate(itemKey, row, "candidate"),
                        row
                    )
                )
        )
    }

    private fun offRow(
        code: String,
        productName: String?
    ): OpenFoodFactsImportedProduct =
        OpenFoodFactsImportedProduct(
            code = code,
            productName = productName,
            productQuantity = null,
            productQuantityUnit = null
        )

    private fun offCandidate(
        itemKey: ShoppingItemKey,
        row: OpenFoodFactsImportedProduct,
        candidateId: String
    ) =
        requireNotNull(
            OpenFoodFactsPracticalShoppingIdentityAdapter.catalogSuggestion(
                itemKey = itemKey,
                row = row,
                candidateId = candidateId
            ).candidate
        )

    private fun osmDisplayRow(
        nodeId: Long,
        name: String?
    ): OpenStreetMapPracticalShoppingStoreDisplayRecord =
        OpenStreetMapPracticalShoppingStoreDisplayRecord(
            identity =
                OpenStreetMapPracticalShoppingStoreRecord(
                    elementType = OpenStreetMapElementType.NODE,
                    elementId = nodeId,
                    brandWikidataId = "Q483551"
                ),
            name = name
        )

    private fun osmCandidate(
        storeKey: ShoppingStoreKey,
        row: OpenStreetMapPracticalShoppingStoreRecord,
        candidateId: String
    ) =
        requireNotNull(
            OpenStreetMapPracticalShoppingStoreSuggestionAdapter.locationSuggestion(
                storeKey = storeKey,
                row = row,
                candidateId = candidateId
            ).candidate
        )
}
