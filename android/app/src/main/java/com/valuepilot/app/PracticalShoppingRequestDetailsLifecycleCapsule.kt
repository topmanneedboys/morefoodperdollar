package com.valuepilot.app

import com.valuepilot.core.ShoppingRequest
import com.valuepilot.core.ShoppingRequestDetails
import com.valuepilot.core.ShoppingRequestDetailsCodec

/**
 * Dormant app-lifecycle holder for typed Practical Shopping request details.
 *
 * The encoded payload is self-identifying because [ShoppingRequestDetails] contains the exact
 * bounded [ShoppingRequest] it belongs to. Restored details are released only when a caller
 * presents an exactly equal request; query text, presentation labels, partial item overlap, and
 * reordered items never establish continuity.
 *
 * This boundary does not reconcile requests, infer shopper intent, interpret quantity/package
 * fields, resolve products, attach price/evidence, rank stores, or authorize network/UI behavior.
 * Malformed, oversized, or otherwise rejected payloads fail closed to an empty capsule.
 */
class PracticalShoppingRequestDetailsLifecycleCapsule private constructor(
    private val restoredDetails: ShoppingRequestDetails?
) {

    /** Returns details only for the exact request identity carried by the saved payload. */
    fun detailsForExactRequest(request: ShoppingRequest): ShoppingRequestDetails? =
        restoredDetails?.takeIf { details -> details.request == request }

    /**
     * Emits a fresh canonical payload for Android saved-state storage.
     *
     * A valid in-memory request can still be outside the codec's stricter byte limits. In that
     * case persistence is refused rather than truncating or weakening the identity boundary.
     */
    fun encodedOrNull(): ByteArray? =
        restoredDetails?.let { details -> ShoppingRequestDetailsCodec.encode(details).bytes }

    companion object {
        fun empty(): PracticalShoppingRequestDetailsLifecycleCapsule =
            PracticalShoppingRequestDetailsLifecycleCapsule(restoredDetails = null)

        fun fromDetails(
            details: ShoppingRequestDetails
        ): PracticalShoppingRequestDetailsLifecycleCapsule =
            PracticalShoppingRequestDetailsLifecycleCapsule(restoredDetails = details)

        /**
         * Restores only a payload accepted by the shared-core codec.
         *
         * Input bytes are decoded immediately and are not retained, so later mutation of a
         * Bundle-provided byte array cannot change the restored shopper intent.
         */
        fun restore(
            encoded: ByteArray?
        ): PracticalShoppingRequestDetailsLifecycleCapsule {
            if (encoded == null || encoded.isEmpty()) return empty()

            val decoded = ShoppingRequestDetailsCodec.decode(encoded).details ?: return empty()
            return PracticalShoppingRequestDetailsLifecycleCapsule(restoredDetails = decoded)
        }
    }
}
