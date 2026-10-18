package com.valuepilot.app

/**
 * Builds the existing provider-neutral shop request from one exact Search
 * identity.  The backend/shared planner remains authoritative for store
 * choice and totals; this helper only carries the selected identity and an
 * explicitly supplied package quantity across the boundary.
 */
internal object ArgentinaQualifiedOfferPlan {
    fun request(
        match: PracticalShoppingSearchIdentityPresentation.Match,
        area: ArgentinaQualifiedLaunchArea
    ): ArgentinaBackendContract.ShopRequest? {
        val offer = match.qualifiedOffers.firstOrNull() ?: return null
        val quantity = offer.quantity ?: return null
        return ArgentinaBackendContract.ShopRequest(
            latitude = area.latitude,
            longitude = area.longitude,
            radiusKm = area.radiusKm,
            items = listOf(
                ArgentinaBackendContract.RequestItem(
                    lineId = "search-${stableLineSuffix(match.identityKey)}",
                    query = match.displayName,
                    amount = quantity.amount,
                    unit = quantity.unit,
                    productEvidenceKey = match.identityKey
                )
            )
        )
    }

    /**
     * Chooses a safe Search-dialog plan without confusing policy selection
     * with the provider's complete exact-price alternatives.  A direct
     * one-item Search request has no Best Value policy, so the backend may
     * leave bestSensibleChoice unresolved while still returning a complete
     * single-store plan.
     */
    fun presentationPlan(
        decision: ArgentinaBackendContract.ShoppingDecision
    ): ArgentinaBackendContract.Plan? {
        decision.bestSensibleChoice.selectedPlan?.let { return it }
        return decision.cheapestSingleStore?.takeIf {
            it.completePriceEvidence && it.missingLineIds.isEmpty()
        }
    }

    private fun stableLineSuffix(identityKey: String): String =
        identityKey.toByteArray(Charsets.UTF_8)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
            .take(24)
            .ifBlank { "identity" }
}
