package com.valuepilot.app

/**
 * Verified baseline money specification available to a future explicit Staple Watch policy draft.
 *
 * Instances can only be minted from an already-assembled usual-store economic input. Retaining that
 * exact assembly prevents device locale, presentation copy, or detached price data from becoming
 * currency authority for the savings threshold.
 */
class StapleWatchPolicyBaselineMoneySpec private constructor(
    val baselineAssembly: StapleWatchUsualStoreEconomicInputAssembly,
    val currencyCode: String,
    val fractionDigits: Int
) {
    init {
        val baseline = requireNotNull(baselineAssembly.candidate) {
            "Staple-watch policy money spec requires an assembled baseline"
        }
        require(baseline.knownBasketCost.currencyCode == currencyCode) {
            "Staple-watch policy currency must match the exact baseline basket"
        }
        require(baseline.knownBasketCost.fractionDigits == fractionDigits) {
            "Staple-watch policy money precision must match the exact baseline basket"
        }
    }

    companion object {
        internal fun from(
            baselineAssembly: StapleWatchUsualStoreEconomicInputAssembly
        ): StapleWatchPolicyBaselineMoneySpec {
            val baseline = requireNotNull(baselineAssembly.candidate) {
                "Staple-watch policy money spec cannot be minted from a blocked baseline"
            }
            return StapleWatchPolicyBaselineMoneySpec(
                baselineAssembly = baselineAssembly,
                currencyCode = baseline.knownBasketCost.currencyCode,
                fractionDigits = baseline.knownBasketCost.fractionDigits
            )
        }
    }
}

/**
 * Fail-closed result of resolving policy money authority from completed Watch evidence.
 *
 * The retained baseline assembly owns the typed reason when resolution is blocked. This result does
 * not choose a savings threshold, construct policy, inspect alternatives/travel, persist anything,
 * render UI, schedule work, or authorize delivery.
 */
class StapleWatchPolicyBaselineMoneySpecResolution private constructor(
    val preconditions: StapleWatchEconomicEvidencePreconditions,
    val baselineAssembly: StapleWatchUsualStoreEconomicInputAssembly,
    val moneySpec: StapleWatchPolicyBaselineMoneySpec?
) {
    init {
        require(baselineAssembly.preconditions === preconditions) {
            "Staple-watch policy money resolution must retain the exact evidence preconditions"
        }
        require((moneySpec != null) == baselineAssembly.assembled) {
            "Staple-watch policy money spec must exist exactly when the baseline is assembled"
        }
        moneySpec?.let { resolved ->
            require(resolved.baselineAssembly === baselineAssembly) {
                "Staple-watch policy money spec must retain the exact baseline assembly"
            }
        }
    }

    val resolved: Boolean
        get() = moneySpec != null

    companion object {
        internal fun create(
            preconditions: StapleWatchEconomicEvidencePreconditions,
            baselineAssembly: StapleWatchUsualStoreEconomicInputAssembly
        ): StapleWatchPolicyBaselineMoneySpecResolution =
            StapleWatchPolicyBaselineMoneySpecResolution(
                preconditions = preconditions,
                baselineAssembly = baselineAssembly,
                moneySpec =
                    if (baselineAssembly.assembled) {
                        StapleWatchPolicyBaselineMoneySpec.from(baselineAssembly)
                    } else {
                        null
                    }
            )
    }
}

/** Pure resolver for the baseline money specification a later explicit policy draft may use. */
object StapleWatchPolicyBaselineMoneySpecResolver {

    fun resolve(
        preconditions: StapleWatchEconomicEvidencePreconditions
    ): StapleWatchPolicyBaselineMoneySpecResolution {
        val baselineAssembly = StapleWatchUsualStoreEconomicInputAssembler.assemble(preconditions)
        return StapleWatchPolicyBaselineMoneySpecResolution.create(
            preconditions = preconditions,
            baselineAssembly = baselineAssembly
        )
    }
}
