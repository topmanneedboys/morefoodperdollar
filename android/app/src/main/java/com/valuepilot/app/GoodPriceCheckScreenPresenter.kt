package com.valuepilot.app

internal sealed interface GoodPriceCheckScreenContent {
    data class Message(
        val title: String,
        val guidance: String
    ) : GoodPriceCheckScreenContent {
        init {
            require(title.isNotBlank())
            require(guidance.isNotBlank())
        }
    }

    data class Result(
        val state: GoodPriceCheckUiState
    ) : GoodPriceCheckScreenContent
}

/** Replaceable physical target for the first-class good-price screen. */
internal fun interface GoodPriceCheckScreenRenderer {
    fun render(content: GoodPriceCheckScreenContent)
}

/** Hands only immutable, already-projected good-price content to the physical renderer. */
internal class GoodPriceCheckScreenPresenter(
    private val renderer: GoodPriceCheckScreenRenderer
) {
    fun render(routeState: GoodPriceCheckRouteState) {
        val content =
            routeState.result?.let(GoodPriceCheckScreenContent::Result)
                ?: GoodPriceCheckScreenContent.Message(
                    title = routeState.title,
                    guidance = routeState.guidance
                )
        renderer.render(content)
    }
}
