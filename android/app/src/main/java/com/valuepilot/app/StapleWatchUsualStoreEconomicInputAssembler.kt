package com.valuepilot.app

import com.valuepilot.core.EvidenceFreshness
import com.valuepilot.core.ShoppingPlanEvidenceSummary
import com.valuepilot.core.StapleWatchBasketCandidate

enum class StapleWatchUsualStoreEconomicInputBlocker {
    EVIDENCE_PRECONDITIONS_NOT_SATISFIED,
    MIXED_MONEY_SPEC,
    BASKET_TOTAL_OVERFLOW
}

/**
 * Exact usual-store Watch basket input assembled from already-authoritative evidence preconditions.
 *
 * Construction is private so callers cannot mint a detached candidate/blocker pairing. A candidate
 * exists only when the supplied preconditions establish complete current usual-store evidence, every
 * exact item price shares one money specification, and exact basket summation succeeds.
 *
 * This boundary does not inspect alternative-store values or travel, rank stores, evaluate savings,
 * own a clock/network/provider, persist state, schedule work, or authorize notification delivery.
 */
class StapleWatchUsualStoreEconomicInputAssembly private constructor(
    val preconditions: StapleWatchEconomicEvidencePreconditions,
    val candidate: StapleWatchBasketCandidate?,
    val blocker: StapleWatchUsualStoreEconomicInputBlocker?
) {
    init {
        require((candidate != null) == (blocker == null)) {
            "Staple-watch usual-store economic input must be either assembled or blocked"
        }
        candidate?.let { assembled ->
            require(assembled.storeKey == preconditions.intent.usualStoreKey) {
                "Staple-watch usual-store candidate must match the exact fact-check intent"
            }
            require(assembled.coveredItemKeys == preconditions.intent.request.itemKeySet) {
                "Staple-watch usual-store candidate must cover the exact watched basket"
            }
        }
    }

    val assembled: Boolean
        get() = candidate != null

    companion object {
        internal fun assembled(
            preconditions: StapleWatchEconomicEvidencePreconditions,
            candidate: StapleWatchBasketCandidate
        ): StapleWatchUsualStoreEconomicInputAssembly =
            StapleWatchUsualStoreEconomicInputAssembly(
                preconditions = preconditions,
                candidate = candidate,
                blocker = null
            )

        internal fun blocked(
            preconditions: StapleWatchEconomicEvidencePreconditions,
            blocker: StapleWatchUsualStoreEconomicInputBlocker
        ): StapleWatchUsualStoreEconomicInputAssembly =
            StapleWatchUsualStoreEconomicInputAssembly(
                preconditions = preconditions,
                candidate = null,
                blocker = blocker
            )
    }
}

/** Pure exact assembler for the normal-store basket consumed later by Watch economics. */
object StapleWatchUsualStoreEconomicInputAssembler {

    fun assemble(
        preconditions: StapleWatchEconomicEvidencePreconditions
    ): StapleWatchUsualStoreEconomicInputAssembly {
        if (!preconditions.satisfied) {
            return StapleWatchUsualStoreEconomicInputAssembly.blocked(
                preconditions = preconditions,
                blocker =
                    StapleWatchUsualStoreEconomicInputBlocker
                        .EVIDENCE_PRECONDITIONS_NOT_SATISFIED
            )
        }

        val itemPrices =
            preconditions.usualStorePriceFacts.itemPrices.map { fact ->
                require(fact.state == StapleWatchBasketItemPriceState.USABLE_EXACT_PRICE) {
                    "Satisfied staple-watch preconditions must retain usable exact usual-store prices"
                }
                requireNotNull(fact.exactPrice) {
                    "Usable staple-watch usual-store price is missing its exact amount"
                }
            }
        val moneySpecs =
            itemPrices
                .map { price -> price.currencyCode to price.fractionDigits }
                .toSet()
        if (moneySpecs.size != 1) {
            return StapleWatchUsualStoreEconomicInputAssembly.blocked(
                preconditions = preconditions,
                blocker = StapleWatchUsualStoreEconomicInputBlocker.MIXED_MONEY_SPEC
            )
        }

        val basketTotal =
            try {
                itemPrices.drop(1).fold(itemPrices.first()) { total, price ->
                    total + price
                }
            } catch (_: ArithmeticException) {
                return StapleWatchUsualStoreEconomicInputAssembly.blocked(
                    preconditions = preconditions,
                    blocker = StapleWatchUsualStoreEconomicInputBlocker.BASKET_TOTAL_OVERFLOW
                )
            }

        val freshness =
            preconditions.currentnessFacts.usualStore.itemCurrentness.map { fact ->
                require(
                    fact.status == StapleWatchEvidenceCurrentnessStatus.CURRENTNESS_ESTABLISHED
                ) {
                    "Satisfied staple-watch preconditions must retain established usual-store currentness"
                }
                requireNotNull(fact.freshness) {
                    "Established staple-watch usual-store currentness is missing freshness"
                }.also { value ->
                    require(
                        value == EvidenceFreshness.FRESH || value == EvidenceFreshness.AGING
                    ) {
                        "Watch economic input accepts only FRESH or AGING established evidence"
                    }
                }
            }
        val evidence =
            ShoppingPlanEvidenceSummary(
                freshItemCount = freshness.count { value -> value == EvidenceFreshness.FRESH },
                agingItemCount = freshness.count { value -> value == EvidenceFreshness.AGING },
                staleItemCount = 0,
                unknownFreshnessItemCount = 0
            )
        val candidate =
            StapleWatchBasketCandidate(
                storeKey = preconditions.intent.usualStoreKey,
                coveredItemKeys = preconditions.intent.request.itemKeySet,
                knownBasketCost = basketTotal,
                evidence = evidence
            )

        return StapleWatchUsualStoreEconomicInputAssembly.assembled(
            preconditions = preconditions,
            candidate = candidate
        )
    }
}
