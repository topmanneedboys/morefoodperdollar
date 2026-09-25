package com.valuepilot.app

import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.PracticalShoppingProductIdentityCandidate
import com.valuepilot.core.PracticalShoppingProductIntentRelationship
import com.valuepilot.core.PracticalShoppingStoreIdentityCandidate
import com.valuepilot.core.PracticalShoppingStoreIdentityRelationship
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.SourceProductIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingRememberConfirmedChoiceCoordinatorTest {

    @Test
    fun `confirmed product plus user label becomes a visible saved row`() {
        val fixture = Fixture()

        val remembered =
            PracticalShoppingRememberConfirmedChoiceCoordinator.rememberProductWithUserLabel(
                exactStore = fixture.exactStore,
                displayStore = fixture.displayStore,
                confirmedCandidate = confirmedProduct("eggs", "036000291452"),
                displayName = "Example Eggs"
            )

        assertTrue(remembered.exactSaved)
        assertTrue(remembered.displaySaved)
        assertTrue(remembered.fullyLabeled)

        val loaded =
            PracticalShoppingSavedExperienceCoordinator.load(
                exactStore = fixture.exactStore,
                displayStore = fixture.displayStore
            )
        assertTrue(loaded.accepted)
        val state = requireNotNull(loaded.projection).state
        assertEquals(listOf("Example Eggs"), state.productRows.map { it.title })
        assertEquals(0, state.unresolvedDisplayNameCount)
    }

    @Test
    fun `unconfirmed exact request is rejected before display metadata is touched`() {
        val fixture = Fixture()
        val request =
            requireNotNull(
                PracticalShoppingExactProductConfirmationAdapter.exactBarcodeRequest(
                    itemKey = ShoppingItemKey("eggs"),
                    rawGtin = "036000291452",
                    candidateId = "barcode-request"
                ).candidate
            )

        val remembered =
            PracticalShoppingRememberConfirmedChoiceCoordinator.rememberProductWithUserLabel(
                exactStore = fixture.exactStore,
                displayStore = fixture.displayStore,
                confirmedCandidate = request,
                displayName = "Example Eggs"
            )

        assertFalse(remembered.exactSaved)
        assertFalse(remembered.displaySaved)
        assertEquals(
            PracticalShoppingSavedExactPreferenceTransactionIssue.PRODUCT_NOT_USER_CONFIRMED,
            remembered.exactResult.issue
        )
        assertTrue(remembered.displayFailures.isEmpty())
        assertNull(remembered.displayResult)
        assertEquals(0, fixture.displayBytes.readCount)
        assertEquals(0, fixture.displayBytes.replaceCount)
    }

    @Test
    fun `invalid product label does not roll back exact confirmation and loads unresolved`() {
        val fixture = Fixture()

        val remembered =
            PracticalShoppingRememberConfirmedChoiceCoordinator.rememberProductWithUserLabel(
                exactStore = fixture.exactStore,
                displayStore = fixture.displayStore,
                confirmedCandidate = confirmedProduct("eggs", "036000291452"),
                displayName = "   "
            )

        assertTrue(remembered.exactSaved)
        assertFalse(remembered.displaySaved)
        assertEquals(
            setOf(PracticalShoppingSavedDisplayMetadataFailure.DISPLAY_NAME_UNAVAILABLE),
            remembered.displayFailures
        )
        assertNull(remembered.displayResult)
        assertEquals(0, fixture.displayBytes.readCount)
        assertEquals(0, fixture.displayBytes.replaceCount)

        val loaded = PracticalShoppingSavedExperienceCoordinator.load(fixture.exactStore, fixture.displayStore)
        val state = requireNotNull(loaded.projection).state
        assertTrue(state.productRows.isEmpty())
        assertEquals(1, state.unresolvedDisplayNameCount)
        assertEquals(ShoppingItemKey("eggs"), requireNotNull(loaded.projection).unresolvedProductKeys.single())
    }

    @Test
    fun `display write failure preserves exact choice and remains safely unresolved`() {
        val fixture = Fixture()
        fixture.displayBytes.failReplace = true

        val remembered =
            PracticalShoppingRememberConfirmedChoiceCoordinator.rememberProductWithUserLabel(
                exactStore = fixture.exactStore,
                displayStore = fixture.displayStore,
                confirmedCandidate = confirmedProduct("eggs", "036000291452"),
                displayName = "Example Eggs"
            )

        assertTrue(remembered.exactSaved)
        assertFalse(remembered.displaySaved)
        assertEquals(
            PracticalShoppingSavedDisplayMetadataTransactionIssue.STORAGE_FAILURE,
            remembered.displayResult?.issue
        )
        assertEquals(
            PracticalShoppingSavedDisplayMetadataStorageIssue.WRITE_FAILED,
            remembered.displayResult?.storageIssue
        )
        assertTrue(fixture.exactStore.load().state?.productFor(ShoppingItemKey("eggs")) != null)

        fixture.displayBytes.failReplace = false
        val loaded = PracticalShoppingSavedExperienceCoordinator.load(fixture.exactStore, fixture.displayStore)
        val state = requireNotNull(loaded.projection).state
        assertTrue(state.productRows.isEmpty())
        assertEquals(1, state.unresolvedDisplayNameCount)
    }

    @Test
    fun `exact storage failure prevents display admission and storage work`() {
        val fixture = Fixture()
        fixture.exactBytes.failReplace = true

        val remembered =
            PracticalShoppingRememberConfirmedChoiceCoordinator.rememberProductWithUserLabel(
                exactStore = fixture.exactStore,
                displayStore = fixture.displayStore,
                confirmedCandidate = confirmedProduct("eggs", "036000291452"),
                displayName = "Example Eggs"
            )

        assertFalse(remembered.exactSaved)
        assertEquals(
            PracticalShoppingSavedExactPreferenceTransactionIssue.STORAGE_FAILURE,
            remembered.exactResult.issue
        )
        assertEquals(
            PracticalShoppingSavedExactPreferenceStorageIssue.WRITE_FAILED,
            remembered.exactResult.storageIssue
        )
        assertEquals(0, fixture.displayBytes.readCount)
        assertEquals(0, fixture.displayBytes.replaceCount)
    }

    @Test
    fun `confirmed store plus user label becomes a visible saved store row`() {
        val fixture = Fixture()

        val remembered =
            PracticalShoppingRememberConfirmedChoiceCoordinator.rememberStoreWithUserLabel(
                exactStore = fixture.exactStore,
                displayStore = fixture.displayStore,
                confirmedCandidate = confirmedStore("north", 11L),
                displayName = "North Market"
            )

        assertTrue(remembered.fullyLabeled)
        val loaded = PracticalShoppingSavedExperienceCoordinator.load(fixture.exactStore, fixture.displayStore)
        val state = requireNotNull(loaded.projection).state
        assertEquals(listOf("North Market"), state.storeRows.map { it.title })
        assertEquals(0, state.unresolvedDisplayNameCount)
    }

    @Test
    fun `Open Food Facts name is accepted only through matching confirmed source identity`() {
        val fixture = Fixture()
        val itemKey = ShoppingItemKey("eggs")
        val row =
            OpenFoodFactsImportedProduct(
                code = "036000291452",
                productName = "Source Eggs",
                productQuantity = null,
                productQuantityUnit = null
            )
        val suggestion =
            requireNotNull(
                OpenFoodFactsPracticalShoppingIdentityAdapter.catalogSuggestion(
                    itemKey = itemKey,
                    row = row,
                    candidateId = "off-suggestion"
                ).candidate
            )
        val confirmed =
            requireNotNull(
                PracticalShoppingExactProductConfirmationAdapter.confirmSelection(
                    itemKey = itemKey,
                    selectedCandidate = suggestion,
                    candidateId = "off-confirmed"
                ).candidate
            )

        val remembered =
            PracticalShoppingRememberConfirmedChoiceCoordinator.rememberOpenFoodFactsProduct(
                exactStore = fixture.exactStore,
                displayStore = fixture.displayStore,
                confirmedCandidate = confirmed,
                row = row
            )

        assertTrue(remembered.fullyLabeled)
        val loaded = PracticalShoppingSavedExperienceCoordinator.load(fixture.exactStore, fixture.displayStore)
        assertEquals(
            "Source Eggs",
            requireNotNull(loaded.projection).state.productRows.single().title
        )
    }

    @Test
    fun `OpenStreetMap name is accepted only through matching confirmed exact store scope`() {
        val fixture = Fixture()
        val storeKey = ShoppingStoreKey("north")
        val identity =
            OpenStreetMapPracticalShoppingStoreRecord(
                elementType = OpenStreetMapElementType.NODE,
                elementId = 11L,
                brandWikidataId = "Q483551"
            )
        val suggestion =
            requireNotNull(
                OpenStreetMapPracticalShoppingStoreSuggestionAdapter.locationSuggestion(
                    storeKey = storeKey,
                    row = identity,
                    candidateId = "osm-suggestion"
                ).candidate
            )
        val confirmed =
            requireNotNull(
                PracticalShoppingExactStoreConfirmationAdapter.confirmSelection(
                    storeKey = storeKey,
                    selectedCandidate = suggestion,
                    candidateId = "osm-confirmed"
                ).candidate
            )

        val remembered =
            PracticalShoppingRememberConfirmedChoiceCoordinator.rememberOpenStreetMapStore(
                exactStore = fixture.exactStore,
                displayStore = fixture.displayStore,
                confirmedCandidate = confirmed,
                row =
                    OpenStreetMapPracticalShoppingStoreDisplayRecord(
                        identity = identity,
                        name = "North Market"
                    )
            )

        assertTrue(remembered.fullyLabeled)
        val loaded = PracticalShoppingSavedExperienceCoordinator.load(fixture.exactStore, fixture.displayStore)
        assertEquals(
            "North Market",
            requireNotNull(loaded.projection).state.storeRows.single().title
        )
    }

    @Test
    fun `reconfirmed product replaces identity and old label cannot follow stable key`() {
        val fixture = Fixture()

        assertTrue(
            PracticalShoppingRememberConfirmedChoiceCoordinator.rememberProductWithUserLabel(
                exactStore = fixture.exactStore,
                displayStore = fixture.displayStore,
                confirmedCandidate = confirmedProduct("eggs", "036000291452"),
                displayName = "Old Eggs"
            ).fullyLabeled
        )
        val replacement =
            PracticalShoppingRememberConfirmedChoiceCoordinator.rememberProductWithUserLabel(
                exactStore = fixture.exactStore,
                displayStore = fixture.displayStore,
                confirmedCandidate = confirmedProduct("eggs", "042100005264"),
                displayName = "New Eggs"
            )

        assertTrue(replacement.fullyLabeled)
        assertEquals(
            listOf(ShoppingItemKey("eggs")),
            replacement.displayResult?.prunedStaleProductKeys
        )
        val loaded = PracticalShoppingSavedExperienceCoordinator.load(fixture.exactStore, fixture.displayStore)
        val state = requireNotNull(loaded.projection).state
        assertEquals(listOf("New Eggs"), state.productRows.map { it.title })
        assertEquals("042100005264", loaded.exactState?.productFor(ShoppingItemKey("eggs"))?.sourceIdentity?.gtin)
    }

    private class Fixture {
        val exactBytes = FakeExactByteStorage()
        val displayBytes = FakeDisplayByteStorage()
        val exactStore = PracticalShoppingSavedExactPreferenceLocalStore(exactBytes)
        val displayStore = PracticalShoppingSavedDisplayMetadataLocalStore(displayBytes)
    }

    private fun confirmedProduct(
        key: String,
        gtin: String
    ): PracticalShoppingProductIdentityCandidate =
        PracticalShoppingProductIdentityCandidate(
            candidateId = "confirmed-$key-$gtin",
            itemKey = ShoppingItemKey(key),
            providerId = EvidenceProviderId("confirmed-catalog"),
            sourceIdentity = SourceProductIdentity(gtin = gtin),
            relationship = PracticalShoppingProductIntentRelationship.USER_CONFIRMED_EXACT_PRODUCT
        )

    private fun confirmedStore(
        key: String,
        nodeId: Long
    ): PracticalShoppingStoreIdentityCandidate =
        PracticalShoppingStoreIdentityCandidate(
            candidateId = "confirmed-$key-$nodeId",
            storeKey = ShoppingStoreKey(key),
            scope =
                PracticalShoppingStoreIdentityScope(
                    merchantKey = "wikidata:Q483551",
                    locationKey = "osm:node:$nodeId",
                    commerceChannelKey = "PHYSICAL_STORE"
                ),
            relationship = PracticalShoppingStoreIdentityRelationship.USER_CONFIRMED_EXACT_STORE
        )

    private class FakeExactByteStorage : PracticalShoppingSavedExactPreferenceByteStorage {
        var bytes: ByteArray? = null
        var failReplace = false
        var readCount = 0
        var replaceCount = 0

        override fun read(maxBytes: Int): PracticalShoppingSavedExactPreferenceRawReadResult {
            readCount += 1
            val current = bytes
                ?: return PracticalShoppingSavedExactPreferenceRawReadResult(
                    bytes = null,
                    found = false
                )
            if (current.size > maxBytes) {
                return PracticalShoppingSavedExactPreferenceRawReadResult(
                    bytes = null,
                    found = true,
                    issue = PracticalShoppingSavedExactPreferenceRawReadIssue.INPUT_TOO_LARGE
                )
            }
            return PracticalShoppingSavedExactPreferenceRawReadResult(
                bytes = current.copyOf(),
                found = true
            )
        }

        override fun replace(bytes: ByteArray): Boolean {
            replaceCount += 1
            if (failReplace) return false
            this.bytes = bytes.copyOf()
            return true
        }

        override fun delete(): Boolean {
            bytes = null
            return true
        }
    }

    private class FakeDisplayByteStorage : PracticalShoppingSavedDisplayMetadataByteStorage {
        var bytes: ByteArray? = null
        var failReplace = false
        var readCount = 0
        var replaceCount = 0

        override fun read(maxBytes: Int): PracticalShoppingSavedDisplayMetadataRawReadResult {
            readCount += 1
            val current = bytes
                ?: return PracticalShoppingSavedDisplayMetadataRawReadResult(
                    bytes = null,
                    found = false
                )
            if (current.size > maxBytes) {
                return PracticalShoppingSavedDisplayMetadataRawReadResult(
                    bytes = null,
                    found = true,
                    issue = PracticalShoppingSavedDisplayMetadataRawReadIssue.INPUT_TOO_LARGE
                )
            }
            return PracticalShoppingSavedDisplayMetadataRawReadResult(
                bytes = current.copyOf(),
                found = true
            )
        }

        override fun replace(bytes: ByteArray): Boolean {
            replaceCount += 1
            if (failReplace) return false
            this.bytes = bytes.copyOf()
            return true
        }

        override fun delete(): Boolean {
            bytes = null
            return true
        }
    }
}
