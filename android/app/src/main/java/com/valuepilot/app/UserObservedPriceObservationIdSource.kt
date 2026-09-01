package com.valuepilot.app

import java.util.UUID

/**
 * Supplies opaque local record identities for fresh user-observed-price observation lifecycles.
 *
 * The identifier distinguishes one local observation record from another; it carries no shopping
 * fact, proof, time, store, price, confirmation, evidence, or current-price meaning. Callers own
 * when a new observation lifecycle begins. This source owns only the concrete opaque-ID mechanism.
 */
internal fun interface UserObservedPriceObservationIdSource {
    fun nextObservationId(): String
}

/** Production local source for collision-resistant opaque observation record identities. */
internal object LocalUserObservedPriceObservationIdSource : UserObservedPriceObservationIdSource {
    override fun nextObservationId(): String = "observation-${UUID.randomUUID()}"
}
