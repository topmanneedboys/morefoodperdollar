package com.valuepilot.app

import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.GtinValidation
import com.valuepilot.core.PracticalShoppingProductIdentityCandidate
import com.valuepilot.core.PracticalShoppingProductIntentRelationship
import com.valuepilot.core.ProductionProductEvidenceKeyResolver
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.SourceProductIdentity

enum class PracticalShoppingExactProductConfirmationFailure {
    INVALID_GTIN,
    ITEM_MISMATCH,
    PRODUCT_IDENTITY_UNAVAILABLE
}

data class PracticalShoppingExactProductConfirmationResult(
    val candidate: PracticalShoppingProductIdentityCandidate?,
    val failures: Set<PracticalShoppingExactProductConfirmationFailure>
) {
    init {
        require((candidate != null) == failures.isEmpty())
    }

    val accepted: Boolean
        get() = candidate != null
}

/**
 * Network-free boundary for explicit exact-product intent confirmation.
 *
 * This adapter does not search a catalog, infer an item from text/image/price,
 * or decide whether a product is available at a store. It only translates an
 * explicit exact-product action into the existing production product-identity
 * candidate contract. [com.valuepilot.core.PracticalShoppingProductIdentityResolver]
 * remains the sole layer that turns candidates into automatic bindings.
 */
object PracticalShoppingExactProductConfirmationAdapter {

    private val LOCAL_BARCODE_PROVIDER = EvidenceProviderId("local-barcode-capture")

    /**
     * A barcode captured because the user is requesting this exact packaged product.
     *
     * The GTIN is checksum validated and preserved as supplied (apart from surrounding
     * transport whitespace). Invalid values are never repaired. A valid barcode proves
     * only the exact requested identity; it grants no product metadata, price, merchant,
     * availability, quantity, or production-price authority.
     */
    fun exactBarcodeRequest(
        itemKey: ShoppingItemKey,
        rawGtin: String,
        candidateId: String
    ): PracticalShoppingExactProductConfirmationResult {
        val gtin = rawGtin.trim()
        if (!GtinValidation.isValid(gtin)) {
            return PracticalShoppingExactProductConfirmationResult(
                candidate = null,
                failures = setOf(PracticalShoppingExactProductConfirmationFailure.INVALID_GTIN)
            )
        }

        return PracticalShoppingExactProductConfirmationResult(
            candidate =
                PracticalShoppingProductIdentityCandidate(
                    candidateId = candidateId,
                    itemKey = itemKey,
                    providerId = LOCAL_BARCODE_PROVIDER,
                    sourceIdentity = SourceProductIdentity(gtin = gtin),
                    relationship = PracticalShoppingProductIntentRelationship.EXACT_PRODUCT_REQUEST
                ),
            failures = emptySet()
        )
    }

    /**
     * Converts one explicitly selected product candidate into an exact user-confirmed
     * intent relationship while preserving its original product identity and provenance.
     *
     * Selection does not upgrade source metadata authority. It only establishes that the
     * user means this exact identity for [itemKey]. The selected identity must already be
     * capable of producing the existing stable production product key.
     */
    fun confirmSelection(
        itemKey: ShoppingItemKey,
        selectedCandidate: PracticalShoppingProductIdentityCandidate,
        candidateId: String
    ): PracticalShoppingExactProductConfirmationResult {
        if (selectedCandidate.itemKey != itemKey) {
            return PracticalShoppingExactProductConfirmationResult(
                candidate = null,
                failures = setOf(PracticalShoppingExactProductConfirmationFailure.ITEM_MISMATCH)
            )
        }

        val productKey =
            ProductionProductEvidenceKeyResolver.resolve(
                providerId = selectedCandidate.providerId,
                identity = selectedCandidate.sourceIdentity
            )
        if (productKey == null) {
            return PracticalShoppingExactProductConfirmationResult(
                candidate = null,
                failures =
                    setOf(PracticalShoppingExactProductConfirmationFailure.PRODUCT_IDENTITY_UNAVAILABLE)
            )
        }

        return PracticalShoppingExactProductConfirmationResult(
            candidate =
                PracticalShoppingProductIdentityCandidate(
                    candidateId = candidateId,
                    itemKey = itemKey,
                    providerId = selectedCandidate.providerId,
                    sourceIdentity = selectedCandidate.sourceIdentity,
                    relationship =
                        PracticalShoppingProductIntentRelationship.USER_CONFIRMED_EXACT_PRODUCT,
                    dataset = selectedCandidate.dataset
                ),
            failures = emptySet()
        )
    }
}
