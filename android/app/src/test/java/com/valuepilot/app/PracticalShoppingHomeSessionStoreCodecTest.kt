package com.valuepilot.app

import com.valuepilot.core.ShoppingBrandKey
import com.valuepilot.core.ShoppingBrandPreference
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingItemRequestDetail
import com.valuepilot.core.ShoppingProductSpecificity
import com.valuepilot.core.ShoppingRequest
import com.valuepilot.core.ShoppingRequestDetails
import com.valuepilot.core.ShoppingRequestDetailsCodec
import com.valuepilot.core.ShoppingRequestedQuantity
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingHomeSessionStoreCodecTest {

    private val milk = ShoppingItemKey("sample-milk-2pct-4l")

    private val privateIdentity =
        PracticalShoppingPrivateIdentity(
            identityKey = "sepa:yerba:001",
            displayName = "Yerba Mate, Miércoles",
            brand = "Marca / Sábado",
            sourceLabel = "Argentina SEPA / Precios Claros",
            packageQuantityLabel = "1 kg"
        )

    @Test
    fun validDurableEnvelopeRestoresTypedSnapshotAndDetachesBytes() {
        val details =
            ShoppingRequestDetails(
                request = ShoppingRequest(listOf(milk)),
                itemDetails =
                    listOf(
                        ShoppingItemRequestDetail(
                            itemKey = milk,
                            productSpecificity = ShoppingProductSpecificity.EXACT_PRODUCT_REQUIRED,
                            requestedQuantity = ShoppingRequestedQuantity(packageCount = 2),
                            brandPreference = ShoppingBrandPreference.exact(ShoppingBrandKey("Neilson"))
                        )
                    )
            )
        val bytes = requireNotNull(ShoppingRequestDetailsCodec.encode(details).bytes)

        val decoded =
            requireNotNull(
                PracticalShoppingHomeSessionStoreCodec.decode(
                    query = "milk",
                    wasSubmitted = true,
                    chickenChoice = null,
                    extraStopMinimumSavingsChoice =
                        LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice.TWENTY_FIVE_CAD.name,
                    requestDetailsLifecycleState = bytes
                )
            )

        assertEquals("milk", decoded.query)
        assertTrue(decoded.wasSubmitted)
        assertEquals(
            LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice.TWENTY_FIVE_CAD,
            decoded.extraStopMinimumSavingsChoice
        )
        assertArrayEquals(bytes, requireNotNull(decoded.requestDetailsLifecycleState))
        bytes[0] = (bytes[0].toInt() xor 0x01).toByte()
        assertArrayEquals(
            requireNotNull(ShoppingRequestDetailsCodec.encode(details).bytes),
            requireNotNull(decoded.requestDetailsLifecycleState)
        )
    }

    @Test
    fun missingOrOversizedQueryFailsClosedBeforeRestoration() {
        assertNull(
            PracticalShoppingHomeSessionStoreCodec.decode(
                query = null,
                wasSubmitted = true,
                chickenChoice = null,
                extraStopMinimumSavingsChoice = null,
                requestDetailsLifecycleState = null
            )
        )
        assertNull(
            PracticalShoppingHomeSessionStoreCodec.decode(
                query = "x".repeat(242),
                wasSubmitted = true,
                chickenChoice = null,
                extraStopMinimumSavingsChoice = null,
                requestDetailsLifecycleState = null
            )
        )
    }

    @Test
    fun unknownEnumValuesUseSafeDefaultsAndOversizedDetailsAreDropped() {
        val decoded =
            requireNotNull(
                PracticalShoppingHomeSessionStoreCodec.decode(
                    query = "eggs",
                    wasSubmitted = true,
                    chickenChoice = "not-a-choice",
                    extraStopMinimumSavingsChoice = "not-a-threshold",
                    requestDetailsLifecycleState =
                        ByteArray(ShoppingRequestDetailsCodec.maximumEncodedBytes + 1)
                )
            )

        assertNull(decoded.chickenChoice)
        assertEquals(
            LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice.DEFAULT,
            decoded.extraStopMinimumSavingsChoice
        )
        assertNull(decoded.requestDetailsLifecycleState)
    }

    @Test
    fun privateIdentityEncodingIsDeterministicAndRoundTripsAccentsAndMissingMetadata() {
        val values =
            listOf(
                privateIdentity,
                privateIdentity.copy(
                    identityKey = "sepa:variant",
                    displayName = "Sin marca",
                    brand = null,
                    packageQuantityLabel = null
                )
            )
        val encoded = requireNotNull(PracticalShoppingHomeSessionStoreCodec.encodePrivateIdentities(values))
        assertEquals(encoded, PracticalShoppingHomeSessionStoreCodec.encodePrivateIdentities(values))
        assertEquals(
            values,
            PracticalShoppingHomeSessionStoreCodec.decodePrivateIdentities(encoded)
        )
    }

    @Test
    fun privateBasketChecklistEncodingIsDeterministicAndFailsClosed() {
        val keys = listOf("sepa:yerba:001", "sepa:second")
        val encoded =
            requireNotNull(
                PracticalShoppingHomeSessionStoreCodec.encodePrivateBasketCollectedIdentityKeys(
                    keys
                )
            )

        assertEquals(encoded, PracticalShoppingHomeSessionStoreCodec.encodePrivateBasketCollectedIdentityKeys(keys))
        assertEquals(
            keys,
            PracticalShoppingHomeSessionStoreCodec.decodePrivateBasketCollectedIdentityKeys(encoded)
        )
        assertTrue(
            PracticalShoppingHomeSessionStoreCodec.decodePrivateBasketCollectedIdentityKeys(
                "v1:bad"
            ).isEmpty()
        )
        assertTrue(
            PracticalShoppingHomeSessionStoreCodec.decodePrivateBasketCollectedIdentityKeys(
                "v1:1:736570613a6b6579:736570613a6b6579"
            ).isEmpty()
        )
    }

    @Test
    fun malformedPrivateIdentityStorageFailsClosedWithoutAffectingTheHomeEnvelope() {
        val decoded =
            requireNotNull(
                PracticalShoppingHomeSessionStoreCodec.decode(
                    query = "milk",
                    wasSubmitted = false,
                    chickenChoice = null,
                    extraStopMinimumSavingsChoice = null,
                    requestDetailsLifecycleState = null,
                    privateIdentities = "v1:not-valid",
                    privateBasketCollectedIdentityKeys = "v1:not-valid"
                )
            )

        assertEquals("milk", decoded.query)
        assertTrue(decoded.privateIdentities.isEmpty())
        assertTrue(decoded.privateBasketCollectedIdentityKeys.isEmpty())
    }

    @Test
    fun partialLifecycleBundleUsesTheCompleteDurablePrivateList() {
        val first = privateIdentity
        val durable =
            PracticalShoppingHomeSession.Snapshot(
                query = "yerba",
                wasSubmitted = false,
                chickenChoice = null,
                privateIdentities = listOf(first),
                privateBasketCollectedIdentityKeys = listOf(first.identityKey)
            )

        val recovered =
            requireNotNull(
                PracticalShoppingHomeSessionStoreCodec.recoverPrivateIdentityState(
                    savedStateComplete = false,
                    savedPrivateIdentities = null,
                    savedPrivateBasketCollectedIdentityKeys = null,
                    durableSnapshot = durable,
                    durableStateTrusted = true
                )
            )

        assertEquals(listOf(first), recovered.identities)
        assertEquals(listOf(first.identityKey), recovered.collectedIdentityKeys)
    }

    @Test
    fun explicitEmptyLifecycleBundleDoesNotResurrectAnIntentionallyRemovedIdentity() {
        val durable =
            PracticalShoppingHomeSession.Snapshot(
                query = "yerba",
                wasSubmitted = false,
                chickenChoice = null,
                privateIdentities = listOf(privateIdentity),
                privateBasketCollectedIdentityKeys = listOf(privateIdentity.identityKey)
            )

        val recovered =
            requireNotNull(
                PracticalShoppingHomeSessionStoreCodec.recoverPrivateIdentityState(
                    savedStateComplete = true,
                    savedPrivateIdentities = null,
                    savedPrivateBasketCollectedIdentityKeys = null,
                    durableSnapshot = durable,
                    durableStateTrusted = true
                )
            )

        assertTrue(recovered.identities.isEmpty())
        assertTrue(recovered.collectedIdentityKeys.isEmpty())
    }

    @Test
    fun malformedDurablePrivateStateIsNotAcceptedAsAnEmptyList() {
        val decoded =
            PracticalShoppingHomeSessionStoreCodec.decodePrivateIdentityState(
                privateIdentities = "v1:not-valid",
                privateBasketCollectedIdentityKeys = null
            )

        assertTrue(!decoded.valid)
        assertNull(decoded.state)
        assertNull(
            PracticalShoppingHomeSessionStoreCodec.recoverPrivateIdentityState(
                savedStateComplete = false,
                savedPrivateIdentities = null,
                savedPrivateBasketCollectedIdentityKeys = null,
                durableSnapshot = null,
                durableStateTrusted = false
            )
        )
    }
}
