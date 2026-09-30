package com.valuepilot.app

import com.valuepilot.core.Money
import com.valuepilot.core.TripCostInput
import com.valuepilot.core.TripDistanceBasis
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

internal data class TripPlannerAssumptionInput(
    val distanceKmText: String,
    val distanceBasis: TripDistanceBasis,
    val consumptionText: String,
    val fuelPriceText: String,
    val currencyText: String,
    val tollText: String,
    val parkingText: String
)

internal enum class TripPlannerAssumptionField {
    DISTANCE,
    CONSUMPTION,
    FUEL_PRICE,
    CURRENCY,
    TOLLS,
    PARKING
}

internal data class TripPlannerAssumptionFailure(
    val field: TripPlannerAssumptionField,
    val message: String
)

internal data class TripPlannerAssumptionParseResult(
    val value: TripCostInput? = null,
    val failure: TripPlannerAssumptionFailure? = null
) {
    init {
        require((value != null) != (failure != null))
    }

    val accepted: Boolean
        get() = value != null
}

/** Exact, clock-free trip-input parsing kept outside the Android route owner. */
internal object TripPlannerInputParser {
    fun parse(input: TripPlannerAssumptionInput): TripPlannerAssumptionParseResult {
        val distance = parseMetres(input.distanceKmText)
            ?: return failure(TripPlannerAssumptionField.DISTANCE, "Enter a non-negative distance with at most 3 decimals")
        val consumption = input.consumptionText.trim().toLongOrNull()
            ?.takeIf { it > 0L }
            ?: return failure(TripPlannerAssumptionField.CONSUMPTION, "Enter a positive whole number of millilitres per 100 km")
        val currency = input.currencyText.trim().uppercase(Locale.ROOT)
        if (!currency.matches(Regex("[A-Z]{3}"))) {
            return failure(TripPlannerAssumptionField.CURRENCY, "Use a three-letter currency code")
        }
        val fuel = parseMoney(input.fuelPriceText, currency)
            ?: return failure(TripPlannerAssumptionField.FUEL_PRICE, "Enter a non-negative exact fuel price")
        val tolls =
            if (input.tollText.trim().isBlank()) null
            else parseMoney(input.tollText, currency)
                ?: return failure(TripPlannerAssumptionField.TOLLS, "Enter a non-negative exact toll amount or leave it blank")
        val parking =
            if (input.parkingText.trim().isBlank()) null
            else parseMoney(input.parkingText, currency)
                ?: return failure(TripPlannerAssumptionField.PARKING, "Enter a non-negative exact parking amount or leave it blank")
        return runCatching {
            TripPlannerAssumptionParseResult(
                value = TripCostInput(
                    drivingDistanceMetres = distance,
                    distanceBasis = input.distanceBasis,
                    fuelConsumptionMilliLitresPer100Km = consumption,
                    fuelPricePerLitre = fuel,
                    tolls = tolls,
                    parking = parking
                )
            )
        }.getOrElse {
            failure(TripPlannerAssumptionField.FUEL_PRICE, "Use one currency and exact non-negative values")
        }
    }

    private fun parseMetres(raw: String): Long? =
        runCatching {
            val kilometres = raw.trim().replace(',', '.')
            if (!kilometres.matches(Regex("\\d+(?:\\.\\d{1,3})?"))) return null
            BigDecimal(kilometres)
                .movePointRight(3)
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact()
        }.getOrNull()

    private fun parseMoney(raw: String, currency: String): Money? =
        runCatching { Money.parse(raw.trim().replace(',', '.'), currency).also { require(it.minorUnits >= 0L) } }.getOrNull()

    private fun failure(field: TripPlannerAssumptionField, message: String) =
        TripPlannerAssumptionParseResult(failure = TripPlannerAssumptionFailure(field, message))
}

internal object TripPlannerOriginParser {
    fun manual(raw: String): String? =
        raw.trim().replace(Regex("\\s+"), " ").takeIf { it.isNotBlank() && it.length <= 240 && it.none { c -> c == '\n' || c == '\r' } }
}
