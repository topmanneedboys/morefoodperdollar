package com.valuepilot.app

/**
 * A deliberately bounded, privacy-safe text card for an explicitly shared Home plan.
 *
 * The card contains only already-projected plan facts and the owning surface's explicit
 * disclosure. It omits item names and private price history by default, and it never turns a
 * partial or fictional result into a live-retailer claim.
 */
internal data class PracticalShoppingHomeShareCard(
    val title: String,
    val text: String,
    val preview: String
) {
    init {
        require(title.isNotBlank())
        require(text.isNotBlank())
        require(preview.isNotBlank())
        require(title.length <= MAX_SHARE_CARD_CHARS)
        require(text.length <= MAX_SHARE_CARD_CHARS)
        require(preview.length <= MAX_SHARE_CARD_CHARS)
        require(title.none { Character.isISOControl(it.code) })
        require(text.none { Character.isISOControl(it.code) })
        require(preview.none { Character.isISOControl(it.code) })
    }

    companion object {
        private const val MAX_SHARE_CARD_CHARS = 1_200
    }
}

/**
 * Projects one already-rendered Home result into a user-triggered share card.
 *
 * This is formatting only. The shared planner/projector has already decided the store, totals,
 * coverage, freshness and optional stop. A no-coverage result cannot produce a share card because
 * there is no store plan to describe.
 */
internal object PracticalShoppingHomeShareCardProjector {

    fun project(
        state: PracticalShoppingUiState,
        sampleNotice: String
    ): PracticalShoppingHomeShareCard? {
        val primary = state.primary ?: return null
        val safeSampleNotice = safeFact(sampleNotice) ?: return null
        // Every required primary fact must survive the same display bound. Do not create a
        // truncated share that could hide whether the basket is complete or fresh.
        val requiredPrimaryFacts =
            requiredFacts(
                primary.basketCostText,
                primary.storeName,
                primary.coverageText,
                primary.travelText,
                primary.evidenceText,
                primary.whyText
            ) ?: return null
        val optionalPrimaryFacts =
            optionalFacts(primary.notice, primary.freshnessNotice) ?: return null
        val primaryFacts = requiredPrimaryFacts + optionalPrimaryFacts.filterNotNull()

        val secondStopFacts =
            state.secondStop?.let { secondStop ->
                requiredFacts(
                    secondStop.storeName,
                    secondStop.savingsText,
                    secondStop.additionalTravelText,
                    secondStop.evidenceText
                )
            }
        if (state.secondStop != null && secondStopFacts == null) return null

        val primaryLine =
            "${primaryFacts[0]} at ${primaryFacts[1]}. " +
                primaryFacts.drop(2).joinToString(". ") + "."
        val secondStopLine =
            secondStopFacts?.let { facts ->
                " Optional second stop at ${facts[0]}. ${facts[1]}. " +
                    "${facts[2]}. ${facts[3]}."
            }.orEmpty()
        val disclosure =
            "${safeSampleNotice} This summary contains no item names or private price history."
        val text =
            "ValuePilot shopping plan: $primaryLine$secondStopLine $disclosure"
        val preview =
            "$primaryLine$secondStopLine $disclosure"

        return runCatching {
            PracticalShoppingHomeShareCard(
                title = "ValuePilot shopping plan",
                text = text,
                preview = preview
            )
        }.getOrNull()
    }

    private fun safeFact(value: String): String? {
        // Check the original value before trimming: String.trim() would otherwise discard a
        // trailing NUL/control character and make an unsafe fact look valid.
        if (value.any { character -> Character.isISOControl(character.code) }) return null
        val trimmed = value.trim()
        return trimmed.takeIf {
            it.isNotBlank() &&
                it.length <= 240 &&
                it.isNotEmpty()
        }
    }

    private fun requiredFacts(vararg values: String): List<String>? =
        values.map { value -> safeFact(value) ?: return null }

    /** Preserve the distinction between an omitted optional fact and an unsafe one. */
    private fun optionalFacts(vararg values: String?): List<String?>? =
        values.map { value ->
            if (value == null) {
                null
            } else {
                safeFact(value) ?: return null
            }
        }
}
