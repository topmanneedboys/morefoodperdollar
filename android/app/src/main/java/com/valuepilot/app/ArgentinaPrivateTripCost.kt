package com.valuepilot.app

import java.math.BigDecimal
import java.math.RoundingMode

/** Explicit, user-entered trip assumptions; no route or device location is inferred. */
data class ArgentinaPrivateTripCostInput(
    val oneWayDrivingKm: BigDecimal,
    val fuelConsumptionLitresPer100Km: BigDecimal,
    val fuelPricePerLitre: BigDecimal,
    val tolls: BigDecimal = BigDecimal.ZERO,
    val parking: BigDecimal = BigDecimal.ZERO
) {
    init {
        require(oneWayDrivingKm >= BigDecimal.ZERO)
        require(fuelConsumptionLitresPer100Km > BigDecimal.ZERO)
        require(fuelPricePerLitre >= BigDecimal.ZERO)
        require(tolls >= BigDecimal.ZERO)
        require(parking >= BigDecimal.ZERO)
        listOf(
            oneWayDrivingKm,
            fuelConsumptionLitresPer100Km,
            fuelPricePerLitre,
            tolls,
            parking
        ).forEach { require(it.scale() <= 6) }
    }
}

data class ArgentinaPrivateTripCostResult(
    val roundTripKm: BigDecimal,
    val fuelCost: BigDecimal,
    val tolls: BigDecimal,
    val parking: BigDecimal,
    val total: BigDecimal
) {
    init {
        require(roundTripKm >= BigDecimal.ZERO)
        listOf(fuelCost, tolls, parking, total).forEach { require(it >= BigDecimal.ZERO) }
        require(total == fuelCost + tolls + parking)
    }
}

object ArgentinaPrivateTripCostCalculator {
    fun calculate(input: ArgentinaPrivateTripCostInput): ArgentinaPrivateTripCostResult {
        val roundTrip = input.oneWayDrivingKm.multiply(BigDecimal("2"))
        val fuelLitres = roundTrip
            .multiply(input.fuelConsumptionLitresPer100Km)
            .divide(BigDecimal("100"), 12, RoundingMode.HALF_UP)
        val fuel = fuelLitres
            .multiply(input.fuelPricePerLitre)
            .setScale(2, RoundingMode.HALF_UP)
        val tolls = input.tolls.setScale(2, RoundingMode.HALF_UP)
        val parking = input.parking.setScale(2, RoundingMode.HALF_UP)
        return ArgentinaPrivateTripCostResult(
            roundTripKm = roundTrip.stripTrailingZeros(),
            fuelCost = fuel,
            tolls = tolls,
            parking = parking,
            total = fuel.add(tolls).add(parking).setScale(2, RoundingMode.UNNECESSARY)
        )
    }

    fun parseDecimal(raw: String, label: String): BigDecimal =
        raw.trim().takeIf { it.isNotBlank() }?.let {
            runCatching { BigDecimal(it) }.getOrNull()
        }?.also { require(it.scale() <= 6) }
            ?: error("Enter a valid $label")
}

fun ArgentinaPrivateTripCostResult.displayText(): String =
    "Trip cost · ARS ${total.toPlainString()} (fuel ARS ${fuelCost.toPlainString()} · " +
        "tolls ARS ${tolls.toPlainString()} · parking ARS ${parking.toPlainString()})"
