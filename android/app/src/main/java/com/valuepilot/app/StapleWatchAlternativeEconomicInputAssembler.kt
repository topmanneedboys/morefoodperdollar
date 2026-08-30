package com.valuepilot.app

import com.valuepilot.core.EvidenceFreshness
import com.valuepilot.core.ShoppingPlanEvidenceSummary
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.StapleWatchBasketAlternativeCandidate
import com.valuepilot.core.StapleWatchBasketCandidate

enum class StapleWatchAlternativeEconomicInputAssemblyBlocker {
    BASELINE_INPUT_NOT_ASSEMBLED
}

enum class StapleWatchAlternativeEconomicInputBlocker {
    PRICE_COVERAGE_INCOMPLETE,
    CURRENTNESS_INCOMPLETE,
    MIXED_MONEY_SPEC,
    MONEY_SPEC_DIFFERS_FROM_BASELINE,
    BASKET_TOTAL_OVERFLOW
}

/** One stable alternative-store outcome; a candidate and blocker can never coexist. */
class StapleWatchAlternativeEconomicInputOutcome private constructor(
    val storeKey: ShoppingStoreKey,
    val candidate: StapleWatchBasketAlternativeCandidate?,
    val blocker: StapleWatchAlternativeEconomicInputBlocker?
) {
    init {
        require((candidate != null) == (blocker == null)) {
            "Staple-watch alternative economic input must be either assembled or blocked"
        }
        candidate?.let { assembled ->
            require(assembled.basket.storeKey == storeKey) {
                "Staple-watch alternative candidate must match its logical store key"
            }
        }
    }

    val assembled: Boolean
        get() = candidate != null

    companion object {
        internal fun assembled(
            storeKey: ShoppingStoreKey,
            candidate: StapleWatchBasketAlternativeCandidate
        ): StapleWatchAlternativeEconomicInputOutcome =
            StapleWatchAlternativeEconomicInputOutcome(
                storeKey = storeKey,
                candidate = candidate,
                blocker = null
            )

        internal fun blocked(
            storeKey: ShoppingStoreKey,
            blocker: StapleWatchAlternativeEconomicInputBlocker
        ): StapleWatchAlternativeEconomicInputOutcome =
            StapleWatchAlternativeEconomicInputOutcome(
                storeKey = storeKey,
                candidate = null,
                blocker = blocker
            )
    }
}

/**
 * Canonical alternative-store Watch economic inputs bound to one exact verified baseline assembly.
 *
 * Construction is private. When the baseline input is unavailable, the whole alternative assembly
 * is blocked and no per-store economic candidates are exposed. Otherwise there is exactly one
 * outcome per resolved alternative identity in stable identity order. A blocked store is preserved
 * as a typed outcome rather than silently dropped.
 *
 * This boundary sums exact basket money, preserves FRESH/AGING evidence, and attaches only the
 * already-resolved additional travel delta. It does not rank stores, calculate savings, apply route
 * caps, invoke Watch economics, choose providers, own a clock/network, persist state, schedule work,
 * or authorize notification delivery.
 */
class StapleWatchAlternativeEconomicInputAssembly private constructor(
    val preconditions: StapleWatchEconomicEvidencePreconditions,
    val baselineAssembly: StapleWatchUsualStoreEconomicInputAssembly,
    val blocker: StapleWatchAlternativeEconomicInputAssemblyBlocker?,
    val outcomes: List<StapleWatchAlternativeEconomicInputOutcome>
) {
    init {
        require(baselineAssembly.preconditions === preconditions) {
            "Staple-watch alternative assembly must retain the exact baseline preconditions"
        }
        val expectedStoreKeys = preconditions.identityFacts.alternativeStoreKeys
        if (blocker == null) {
            require(outcomes.map { outcome -> outcome.storeKey } == expectedStoreKeys) {
                "Staple-watch alternative economic outcomes must preserve exact stable identity order"
            }
        } else {
            require(outcomes.isEmpty()) {
                "Blocked staple-watch alternative assembly cannot expose per-store outcomes"
            }
        }
    }

    val assembledCandidates: List<StapleWatchBasketAlternativeCandidate>
        get() = outcomes.mapNotNull { outcome -> outcome.candidate }

    companion object {
        internal fun blocked(
            preconditions: StapleWatchEconomicEvidencePreconditions,
            baselineAssembly: StapleWatchUsualStoreEconomicInputAssembly,
            blocker: StapleWatchAlternativeEconomicInputAssemblyBlocker
        ): StapleWatchAlternativeEconomicInputAssembly =
            StapleWatchAlternativeEconomicInputAssembly(
                preconditions = preconditions,
                baselineAssembly = baselineAssembly,
                blocker = blocker,
                outcomes = emptyList()
            )

        internal fun assembled(
            preconditions: StapleWatchEconomicEvidencePreconditions,
            baselineAssembly: StapleWatchUsualStoreEconomicInputAssembly,
            outcomes: List<StapleWatchAlternativeEconomicInputOutcome>
        ): StapleWatchAlternativeEconomicInputAssembly =
            StapleWatchAlternativeEconomicInputAssembly(
                preconditions = preconditions,
                baselineAssembly = baselineAssembly,
                blocker = null,
                outcomes = outcomes
            )
    }
}

/** Pure per-store assembler for alternatives consumed later by Watch economics. */
object StapleWatchAlternativeEconomicInputAssembler {

    fun assemble(
        preconditions: StapleWatchEconomicEvidencePreconditions,
        baselineAssembly: StapleWatchUsualStoreEconomicInputAssembly
    ): StapleWatchAlternativeEconomicInputAssembly {
        require(baselineAssembly.preconditions === preconditions) {
            "Staple-watch alternative input must use the exact baseline preconditions"
        }
        val baseline =
            baselineAssembly.candidate
                ?: return StapleWatchAlternativeEconomicInputAssembly.blocked(
                    preconditions = preconditions,
                    baselineAssembly = baselineAssembly,
                    blocker =
                        StapleWatchAlternativeEconomicInputAssemblyBlocker
                            .BASELINE_INPUT_NOT_ASSEMBLED
                )

        val pricesByStore =
            preconditions.alternativeStorePriceFacts.alternatives.associateBy { fact -> fact.storeKey }
        val currentnessByStore =
            preconditions.currentnessFacts.alternatives.associateBy { fact -> fact.storeKey }
        val travelByStore =
            preconditions.additionalTravelFacts.alternatives.associateBy { fact -> fact.storeKey }
        val evidenceReadyStoreKeys =
            preconditions.priceAndCurrentnessReadyAlternativeStoreKeys.toSet()

        val outcomes =
            preconditions.identityFacts.alternativeStoreKeys.map { storeKey ->
                val prices = requireNotNull(pricesByStore[storeKey])
                val currentness = requireNotNull(currentnessByStore[storeKey])
                val travel = requireNotNull(travelByStore[storeKey])
                val priceCoverageComplete =
                    prices.itemPrices.all { fact ->
                        fact.state == StapleWatchBasketItemPriceState.USABLE_EXACT_PRICE
                    }
                val currentnessComplete =
                    currentness.itemCurrentness.all { fact ->
                        fact.status == StapleWatchEvidenceCurrentnessStatus.CURRENTNESS_ESTABLISHED
                    }
                require(
                    (storeKey in evidenceReadyStoreKeys) ==
                        (priceCoverageComplete && currentnessComplete)
                ) {
                    "Staple-watch evidence-ready alternative set drifted from its authoritative facts"
                }

                if (!priceCoverageComplete) {
                    return@map StapleWatchAlternativeEconomicInputOutcome.blocked(
                        storeKey = storeKey,
                        blocker =
                            StapleWatchAlternativeEconomicInputBlocker.PRICE_COVERAGE_INCOMPLETE
                    )
                }
                if (!currentnessComplete) {
                    return@map StapleWatchAlternativeEconomicInputOutcome.blocked(
                        storeKey = storeKey,
                        blocker = StapleWatchAlternativeEconomicInputBlocker.CURRENTNESS_INCOMPLETE
                    )
                }

                val itemPrices =
                    prices.itemPrices.map { fact ->
                        requireNotNull(fact.exactPrice) {
                            "Usable staple-watch alternative price is missing its exact amount"
                        }
                    }
                val moneySpecs =
                    itemPrices
                        .map { price -> price.currencyCode to price.fractionDigits }
                        .toSet()
                if (moneySpecs.size != 1) {
                    return@map StapleWatchAlternativeEconomicInputOutcome.blocked(
                        storeKey = storeKey,
                        blocker = StapleWatchAlternativeEconomicInputBlocker.MIXED_MONEY_SPEC
                    )
                }

                val firstPrice = requireNotNull(itemPrices.firstOrNull())
                if (
                    firstPrice.currencyCode != baseline.knownBasketCost.currencyCode ||
                    firstPrice.fractionDigits != baseline.knownBasketCost.fractionDigits
                ) {
                    return@map StapleWatchAlternativeEconomicInputOutcome.blocked(
                        storeKey = storeKey,
                        blocker =
                            StapleWatchAlternativeEconomicInputBlocker
                                .MONEY_SPEC_DIFFERS_FROM_BASELINE
                    )
                }

                val basketTotal =
                    try {
                        itemPrices.drop(1).fold(firstPrice) { total, price -> total + price }
                    } catch (_: ArithmeticException) {
                        return@map StapleWatchAlternativeEconomicInputOutcome.blocked(
                            storeKey = storeKey,
                            blocker = StapleWatchAlternativeEconomicInputBlocker.BASKET_TOTAL_OVERFLOW
                        )
                    }

                val freshness =
                    currentness.itemCurrentness.map { fact ->
                        requireNotNull(fact.freshness) {
                            "Established staple-watch alternative currentness is missing freshness"
                        }.also { value ->
                            require(
                                value == EvidenceFreshness.FRESH || value == EvidenceFreshness.AGING
                            ) {
                                "Watch alternative economic input accepts only FRESH or AGING evidence"
                            }
                        }
                    }
                val evidence =
                    ShoppingPlanEvidenceSummary(
                        freshItemCount =
                            freshness.count { value -> value == EvidenceFreshness.FRESH },
                        agingItemCount =
                            freshness.count { value -> value == EvidenceFreshness.AGING },
                        staleItemCount = 0,
                        unknownFreshnessItemCount = 0
                    )
                val basket =
                    StapleWatchBasketCandidate(
                        storeKey = storeKey,
                        coveredItemKeys = preconditions.intent.request.itemKeys.toSet(),
                        knownBasketCost = basketTotal,
                        evidence = evidence
                    )
                StapleWatchAlternativeEconomicInputOutcome.assembled(
                    storeKey = storeKey,
                    candidate =
                        StapleWatchBasketAlternativeCandidate(
                            basket = basket,
                            additionalTravel = travel.additionalTravel
                        )
                )
            }

        return StapleWatchAlternativeEconomicInputAssembly.assembled(
            preconditions = preconditions,
            baselineAssembly = baselineAssembly,
            outcomes = outcomes
        )
    }
}
