package com.valuepilot.core

import java.math.BigInteger

/** How an explicitly supplied driving distance should be applied to the trip. */
enum class TripDistanceBasis {
    /** The entered route is one-way, so the calculator applies it in both directions. */
    ROUND_TRIP,

    /** The entered value is already the additional driving distance for the scenario. */
    ADDITIONAL_DRIVING
}

/** The provenance of a trip origin; neither variant implies continuous tracking. */
enum class TripOriginKind {
    MANUAL,
    FOREGROUND_LOCATION
}

/** A bounded, user-visible origin reference without retaining raw coordinates. */
data class TripOriginReference(
    val kind: TripOriginKind,
    val label: String,
    val contextId: String? = null
) {
    init {
        require(label.isNotBlank() && label.length <= 240)
        require(label.none { it == '\n' || it == '\r' })
        require(contextId == null || (contextId.isNotBlank() && contextId.length <= 240))
        require(kind != TripOriginKind.FOREGROUND_LOCATION || contextId != null)
    }
}

/** Exact driving/fuel assumptions entered for one trip calculation. */
data class TripCostInput(
    val drivingDistanceMetres: Long,
    val distanceBasis: TripDistanceBasis,
    /** Whole millilitres consumed per 100 kilometres. */
    val fuelConsumptionMilliLitresPer100Km: Long,
    /** Explicit fuel price for one litre. */
    val fuelPricePerLitre: Money,
    val tolls: Money? = null,
    val parking: Money? = null
) {
    init {
        require(drivingDistanceMetres >= 0L)
        require(fuelConsumptionMilliLitresPer100Km > 0L)
        require(fuelPricePerLitre.minorUnits >= 0L)
        tolls?.let { require(it.minorUnits >= 0L) }
        parking?.let { require(it.minorUnits >= 0L) }
        listOfNotNull(tolls, parking).forEach { cost ->
            requireSameMoneySpec(fuelPricePerLitre, cost)
        }
    }

    val effectiveDrivingDistanceMetres: Long
        get() =
            when (distanceBasis) {
                TripDistanceBasis.ROUND_TRIP -> Math.multiplyExact(drivingDistanceMetres, 2L)
                TripDistanceBasis.ADDITIONAL_DRIVING -> drivingDistanceMetres
            }

    private companion object {
        fun requireSameMoneySpec(left: Money, right: Money) {
            require(
                left.currencyCode == right.currencyCode &&
                    left.fractionDigits == right.fractionDigits
            ) {
                "Trip costs must use one explicit currency and precision"
            }
        }
    }
}

/** Exact breakdown of one trip-cost scenario. */
data class TripCostResult(
    val effectiveDrivingDistanceMetres: Long,
    val fuelCost: Money,
    val tolls: Money,
    val parking: Money,
    val total: Money
) {
    init {
        require(effectiveDrivingDistanceMetres >= 0L)
        require(fuelCost.minorUnits >= 0L)
        require(tolls.minorUnits >= 0L)
        require(parking.minorUnits >= 0L)
        requireSameMoneySpec(fuelCost, tolls)
        requireSameMoneySpec(fuelCost, parking)
        requireSameMoneySpec(fuelCost, total)
        require(total == fuelCost + tolls + parking)
    }

    private companion object {
        fun requireSameMoneySpec(left: Money, right: Money) {
            require(
                left.currencyCode == right.currencyCode &&
                    left.fractionDigits == right.fractionDigits
            )
        }
    }
}

/**
 * Deterministic trip-cost calculation shared by future offer comparisons and the Android
 * manual-input flow. It uses integer/rational arithmetic only; no route is inferred here.
 */
object TripCostCalculator {
    // metres × (milli-litres / 100 km) gives milli-litres × 100,000.
    private val FUEL_DISTANCE_DENOMINATOR = BigInteger.valueOf(100_000L)
    // One litre is 1,000 milli-litres; round the resulting money to one minor unit.
    private val FUEL_COST_DENOMINATOR = FUEL_DISTANCE_DENOMINATOR.multiply(BigInteger.valueOf(1_000L))

    fun calculate(input: TripCostInput): TripCostResult {
        val distance = input.effectiveDrivingDistanceMetres
        val numerator =
            BigInteger.valueOf(distance)
                .multiply(BigInteger.valueOf(input.fuelConsumptionMilliLitresPer100Km))
                .multiply(BigInteger.valueOf(input.fuelPricePerLitre.minorUnits))
        val fuelMinor = roundHalfUp(numerator, FUEL_COST_DENOMINATOR)
        val zero = Money(0L, input.fuelPricePerLitre.currencyCode, input.fuelPricePerLitre.fractionDigits)
        val fuel = input.fuelPricePerLitre.copy(minorUnits = fuelMinor)
        val tolls = input.tolls ?: zero
        val parking = input.parking ?: zero
        return TripCostResult(
            effectiveDrivingDistanceMetres = distance,
            fuelCost = fuel,
            tolls = tolls,
            parking = parking,
            total = fuel + tolls + parking
        )
    }

    private fun roundHalfUp(numerator: BigInteger, denominator: BigInteger): Long {
        require(numerator.signum() >= 0)
        val (quotient, remainder) = numerator.divideAndRemainder(denominator)
        val rounded =
            if (remainder.shiftLeft(1) >= denominator) {
                quotient + BigInteger.ONE
            } else {
                quotient
            }
        return rounded.longValueExact()
    }
}

/** Current basket economics are intentionally unknown until an authorized offer source exists. */
enum class TripBasketSavingsStatus {
    UNKNOWN_NO_AUTHORIZED_CURRENT_OFFERS,
    KNOWN_COMPARABLE_OFFERS
}

data class TripBasketEconomics(
    val status: TripBasketSavingsStatus = TripBasketSavingsStatus.UNKNOWN_NO_AUTHORIZED_CURRENT_OFFERS,
    val basketSavings: Money? = null,
    val netSavingsAfterTripCost: Money? = null
) {
    init {
        require(
            (status == TripBasketSavingsStatus.KNOWN_COMPARABLE_OFFERS) ==
                (basketSavings != null && netSavingsAfterTripCost != null)
        )
        if (basketSavings != null && netSavingsAfterTripCost != null) {
            require(
                basketSavings.currencyCode == netSavingsAfterTripCost.currencyCode &&
                    basketSavings.fractionDigits == netSavingsAfterTripCost.fractionDigits
            )
        }
    }
}
