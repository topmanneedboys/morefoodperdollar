package com.valuepilot.app

sealed interface CompareHereManualScreenContent {
    data class Message(
        val title: String,
        val guidance: String,
        val rejectedProductCount: Int = 0
    ) : CompareHereManualScreenContent {
        init {
            require(title.isNotBlank())
            require(guidance.isNotBlank())
            require(rejectedProductCount >= 0)
        }
    }

    data class Comparison(
        val state: CompareHereUiState
    ) : CompareHereManualScreenContent
}

/** Replaceable physical target for the manual Compare Here screen. */
fun interface CompareHereManualScreenRenderer {
    fun render(content: CompareHereManualScreenContent)
}

/**
 * Narrows route mechanics into consumer-safe physical screen content.
 *
 * The renderer receives either a generic route message or the already-projected immutable
 * Compare Here state. Route statuses, observation ids, adapter issues and exact-core objects stay
 * outside the physical View.
 */
class CompareHereManualScreenPresenter(
    private val renderer: CompareHereManualScreenRenderer
) {
    fun render(routeState: CompareHereManualRouteState) {
        val content =
            routeState.comparisonState?.let(CompareHereManualScreenContent::Comparison)
                ?: CompareHereManualScreenContent.Message(
                    title = routeState.title,
                    guidance = routeState.guidance,
                    rejectedProductCount = routeState.rejectedProductCount
                )
        renderer.render(content)
    }
}
