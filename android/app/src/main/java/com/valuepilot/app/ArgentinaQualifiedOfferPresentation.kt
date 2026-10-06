package com.valuepilot.app

/**
 * Small, deterministic presentation helpers for the qualified Search offer
 * preview.  This is deliberately display-only: it does not rank offers,
 * infer stock, or turn a listed price into an availability claim.
 */
internal object ArgentinaQualifiedOfferPresentation {
    fun summary(offer: ArgentinaBackendContract.SearchOffer): String = buildString {
        append(offer.listPrice.currency).append(' ').append(offer.listPrice.amount)
        append(" at ").append(offer.storeName)
        if (offer.quantity != null) offer.unitPrice?.let { unit ->
            append(" · ").append(unit.currency).append(' ').append(unit.amount)
            offer.unitPriceBasis?.takeIf { it.isNotBlank() }?.let { append('/').append(it) }
        }
        append(" · stock unknown")
    }

    fun details(offer: ArgentinaBackendContract.SearchOffer): String = buildString {
        append(summary(offer)).append('\n')
        offer.quantity?.let {
            append("Package quantity: ").append(it.amount).append(' ').append(it.unit).append('\n')
        } ?: append("Package quantity: unknown\n")
        append("Freshness: ").append(offer.freshnessStatus.name).append('\n')
        append("Availability: UNKNOWN\n")
        append("Release: ").append(offer.releaseDate).append('\n')
        val address = offer.storeAddress.toSortedMap().entries
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
            .joinToString(", ") { (key, value) -> "$key: $value" }
        if (address.isNotBlank()) append("Address: ").append(address).append('\n')
        append("Source: ").append(offer.provenance.provider)
        offer.provenance.source?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
        append("\nA published price is not proof of stock, pickup, delivery or a Best Value ranking.")
    }
}
