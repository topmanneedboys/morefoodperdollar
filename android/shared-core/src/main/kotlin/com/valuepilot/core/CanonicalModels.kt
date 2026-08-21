package com.valuepilot.core

/** Exact monetary amount. Currency and fraction digits are explicit; no binary floating point. */
data class Money(
    val minorUnits: Long,
    val currencyCode: String,
    val fractionDigits: Int = 2
) {
    init {
        require(currencyCode.matches(Regex("[A-Z]{3}"))) { "Use an ISO-style uppercase currency code" }
        require(fractionDigits in 0..6)
    }

    companion object {
        fun parse(decimal: String, currencyCode: String, fractionDigits: Int = 2): Money {
            require(fractionDigits in 0..6)
            val value = decimal.trim().replace(',', '.')
            require(value.matches(Regex("-?\\d+(?:\\.\\d+)?"))) { "Invalid decimal money" }
            val negative = value.startsWith('-')
            val unsigned = value.removePrefix("-")
            val parts = unsigned.split('.', limit = 2)
            val whole = parts[0].toLong()
            val fraction = parts.getOrElse(1) { "" }
            require(fraction.length <= fractionDigits) { "Too many fractional digits" }
            val scale = powerOfTen(fractionDigits)
            val minor = Math.addExact(Math.multiplyExact(whole, scale), fraction.padEnd(fractionDigits, '0').ifEmpty { "0" }.toLong())
            return Money(if (negative) -minor else minor, currencyCode, fractionDigits)
        }

        internal fun powerOfTen(exponent: Int): Long {
            var value = 1L
            repeat(exponent) { value = Math.multiplyExact(value, 10L) }
            return value
        }
    }
}

enum class BaseUnit { GRAM, MILLILITRE, COUNT, SQUARE_INCH }

/** Amount is millionths of [unit], allowing exact in-process normalization without Double. */
data class NormalizedQuantity(val amountMicros: Long, val unit: BaseUnit) {
    init { require(amountMicros > 0) }
}

object QuantityNormalization {
    private const val MICROS = 1_000_000L
    fun count(value: Long): NormalizedQuantity = NormalizedQuantity(Math.multiplyExact(value, MICROS), BaseUnit.COUNT)
    fun grams(value: Long): NormalizedQuantity = NormalizedQuantity(Math.multiplyExact(value, MICROS), BaseUnit.GRAM)
    fun millilitres(value: Long): NormalizedQuantity = NormalizedQuantity(Math.multiplyExact(value, MICROS), BaseUnit.MILLILITRE)
    fun litres(value: Long): NormalizedQuantity = millilitres(Math.multiplyExact(value, 1_000L))
    fun pounds(value: Long): NormalizedQuantity = NormalizedQuantity(Math.multiplyExact(value, 453_592_370L), BaseUnit.GRAM)
    fun multipack(packages: Long, each: NormalizedQuantity): NormalizedQuantity =
        NormalizedQuantity(Math.multiplyExact(packages, each.amountMicros), each.unit)
}

data class PromotionTerms(
    val label: String = "",
    val receivedUnits: Long = 1,
    val paidUnits: Long = 1
) {
    init { require(receivedUnits > 0 && paidUnits > 0) }
}

data class Offer(
    val current: Money,
    val member: Money? = null,
    val previous: Money? = null,
    val promotion: PromotionTerms = PromotionTerms()
) {
    init {
        listOfNotNull(member, previous).forEach {
            require(it.currencyCode == current.currencyCode && it.fractionDigits == current.fractionDigits)
        }
    }
}

@JvmInline value class ProductObservationId(val value: String)
@JvmInline value class SearchSessionId(val value: String)
@JvmInline value class ProductResultId(val value: Long)

data class ProductObservation(
    val id: ProductObservationId,
    val sourceId: String,
    val rawText: String,
    val observedAtEpochMillis: Long
)

data class CanonicalProduct(
    val canonicalName: String,
    val quantity: NormalizedQuantity?,
    val offer: Offer,
    val observationId: ProductObservationId
)

enum class RateUnit { KILOGRAM, LITRE, ITEM, SQUARE_INCH }

data class UnitRate(val currencyCode: String, val currencyMicrosPerUnit: Long, val unit: RateUnit)

object DeterministicValueMath {
    /** Rounded half-up to millionths of a currency unit per normalized base unit. */
    fun pricePerBaseUnit(offer: Offer, quantity: NormalizedQuantity, useMemberPrice: Boolean = false): UnitRate {
        val money = if (useMemberPrice) offer.member ?: offer.current else offer.current
        val currencyMicros = Math.multiplyExact(money.minorUnits, Money.powerOfTen(6 - money.fractionDigits))
        val promotion = offer.promotion
        val displayUnitMicros = when (quantity.unit) {
            BaseUnit.GRAM, BaseUnit.MILLILITRE -> 1_000_000_000L
            BaseUnit.COUNT, BaseUnit.SQUARE_INCH -> 1_000_000L
        }
        val numerator = Math.multiplyExact(Math.multiplyExact(currencyMicros, displayUnitMicros), promotion.paidUnits)
        val denominator = Math.multiplyExact(quantity.amountMicros, promotion.receivedUnits)
        val rounded = Math.addExact(numerator, denominator / 2) / denominator
        val rateUnit = when (quantity.unit) {
            BaseUnit.GRAM -> RateUnit.KILOGRAM
            BaseUnit.MILLILITRE -> RateUnit.LITRE
            BaseUnit.COUNT -> RateUnit.ITEM
            BaseUnit.SQUARE_INCH -> RateUnit.SQUARE_INCH
        }
        return UnitRate(money.currencyCode, rounded, rateUnit)
    }
}
