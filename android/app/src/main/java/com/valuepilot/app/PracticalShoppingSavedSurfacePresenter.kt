package com.valuepilot.app

/** Narrow target for any replaceable physical Saved renderer. */
fun interface PracticalShoppingSavedSurfaceRenderer {
    fun render(state: PracticalShoppingSavedSurfaceState)
}

/**
 * Adapts verified lifecycle state to the pure physical-Saved presentation contract.
 *
 * This presenter owns no Android classes, persistence, identity, clock, network, provider,
 * price, travel, or ranking logic. It is the only mapping needed between a Saved lifecycle
 * host/session and a replaceable physical renderer.
 */
class PracticalShoppingSavedSurfacePresenter(
    private val renderer: PracticalShoppingSavedSurfaceRenderer,
    private val watchedProductIdentityKeys: () -> Set<String> = { emptySet() },
    private val onProductCount: (Int) -> Unit = {}
) : PracticalShoppingSavedLifecycleRenderer {
    override fun render(state: PracticalShoppingSavedLifecycleState) {
        val projection = PracticalShoppingSavedSurfaceProjector.project(
            lifecycle = state,
            watchedProductIdentityKeys = watchedProductIdentityKeys()
        )
        onProductCount(projection.productRows.size)
        renderer.render(projection)
    }
}
