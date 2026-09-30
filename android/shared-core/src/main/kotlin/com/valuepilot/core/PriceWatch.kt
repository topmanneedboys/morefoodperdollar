package com.valuepilot.core

/**
 * Source classes that a price observation may carry.
 *
 * PERSONAL_ENTRY is the only source activated by the current Android milestone. The authorized
 * retailer class is deliberately a future data boundary: having a type for it does not authorize
 * an adapter, network request, or production promotion.
 */
enum class PriceObservationSourceKind {
    PERSONAL_ENTRY,
    AUTHORIZED_RETAILER_OFFER
}

/** Explicit permission scope retained with every observation provenance record. */
enum class PriceObservationPermission {
    PRIVATE_DEVICE_ONLY,
    AUTHORIZED_CURRENT_PRICE
}

/** Freshness is supplied by an upstream source policy; it is never inferred from wall-clock time. */
enum class PriceObservationFreshness {
    UNKNOWN,
    FRESH,
    STALE,
    EXPIRED
}

private const val PERSONAL_PRICE_OBSERVATION_SOURCE_ID = "shopper-entered-v1"

data class PriceObservationProvenance(
    val sourceId: String,
    val sourceKind: PriceObservationSourceKind,
    val rightsRecordId: String? = null,
    val permission: PriceObservationPermission
) {
    init {
        require(sourceId.isNotBlank() && sourceId.length <= 160)
        require(rightsRecordId == null || rightsRecordId.isNotBlank())
        when (sourceKind) {
            PriceObservationSourceKind.PERSONAL_ENTRY -> {
                require(permission == PriceObservationPermission.PRIVATE_DEVICE_ONLY)
                require(rightsRecordId == null)
                require(sourceId == PERSONAL_PRICE_OBSERVATION_SOURCE_ID)
            }

            PriceObservationSourceKind.AUTHORIZED_RETAILER_OFFER -> {
                // No authorized current-price source is qualified in this milestone. Keeping the
                // future type explicit prevents a caller from treating an arbitrary rights label
                // as permission to create a live offer.
                require(false) { "No authorized current-price source is enabled" }
            }
        }
    }
}

/**
 * One exact personal or future-authorized price observation.
 *
 * This is not a confirmed purchase and not automatically a current offer. Product identity is an
 * exact stable key; no name, image, price similarity, or fuzzy matching is allowed to join records.
 */
data class PriceObservation(
    val observationId: String,
    val productIdentityKey: String,
    val packageQuantity: NormalizedQuantity? = null,
    val price: Money,
    val observedDateIso: String? = null,
    val store: ConfirmedPurchaseStore? = null,
    val freshness: PriceObservationFreshness = PriceObservationFreshness.UNKNOWN,
    val provenance: PriceObservationProvenance
) {
    init {
        require(singleLine(observationId, MAX_OBSERVATION_ID_LENGTH) != null)
        require(singleLine(productIdentityKey, MAX_PRODUCT_IDENTITY_KEY_LENGTH) != null)
        require(price.minorUnits > 0L)
        require(observedDateIso == null || ConfirmedPurchase.isValidIsoDate(observedDateIso))
        require(observedDateIso != null || freshness == PriceObservationFreshness.UNKNOWN)
    }

    fun matchesExactProduct(productIdentityKey: String): Boolean =
        this.productIdentityKey == productIdentityKey

    companion object {
        const val MAX_OBSERVATION_ID_LENGTH = 160
        const val MAX_PRODUCT_IDENTITY_KEY_LENGTH = 256

        private fun singleLine(value: String, maxLength: Int): String? {
            val trimmed = value.trim()
            return trimmed.takeIf {
                it.isNotBlank() &&
                    it.length <= maxLength &&
                    it.none { character -> character == '\n' || character == '\r' }
            }
        }

        /** Current Android entry point for a shopper-entered private observation. */
        fun personal(
            observationId: String,
            productIdentityKey: String,
            packageQuantity: NormalizedQuantity? = null,
            price: Money,
            observedDateIso: String? = null,
            store: ConfirmedPurchaseStore? = null
        ): PriceObservation {
            val normalizedDate = observedDateIso?.trim()?.takeIf(String::isNotBlank)
            return PriceObservation(
                observationId = observationId.trim(),
                productIdentityKey = productIdentityKey.trim(),
                packageQuantity = packageQuantity,
                price = price,
                observedDateIso = normalizedDate,
                store = store,
                // A shopper-entered date is historical context, not a freshness proof. No wall
                // clock or hidden policy is allowed to turn it into a current-price claim.
                freshness = PriceObservationFreshness.UNKNOWN,
                provenance =
                    PriceObservationProvenance(
                        sourceId = "shopper-entered-v1",
                        sourceKind = PriceObservationSourceKind.PERSONAL_ENTRY,
                        permission = PriceObservationPermission.PRIVATE_DEVICE_ONLY
                    )
            )
        }
    }
}

/** One stable watch record. The product identity key is its deduplication key. */
data class PriceWatch(
    val productIdentityKey: String,
    val displayName: String,
    val brand: String? = null,
    val packageQuantityLabel: String? = null,
    val targetPrice: Money? = null
) {
    init {
        require(productIdentityKey.isNotBlank() && productIdentityKey.length <= 256)
        require(displayName.isNotBlank() && displayName.length <= 512)
        require(brand == null || brand.isNotBlank())
        require(packageQuantityLabel == null || packageQuantityLabel.isNotBlank())
        require(targetPrice == null || targetPrice.minorUnits >= 0L)
    }
}

/** Bounded provider-neutral state for local watches and shopper-entered observations. */
data class PriceWatchState(
    val watches: List<PriceWatch> = emptyList(),
    val observations: List<PriceObservation> = emptyList()
) {
    init {
        require(watches.size <= MAX_PRICE_WATCHES)
        require(observations.size <= MAX_PRICE_OBSERVATIONS)
        require(watches.map { it.productIdentityKey }.distinct().size == watches.size)
        require(observations.map { it.observationId }.distinct().size == observations.size)
    }

    companion object {
        const val MAX_PRICE_WATCHES = 128
        const val MAX_PRICE_OBSERVATIONS = 512

        fun empty(): PriceWatchState = PriceWatchState()
    }
}
