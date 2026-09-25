package com.valuepilot.core

import kotlin.math.roundToLong

object ExactScale {
    fun fromDouble(value: Double, fractionDigits: Int): Long {
        require(value.isFinite())
        require(fractionDigits in 0..6)
        return (value * Money.powerOfTen(fractionDigits)).roundToLong()
    }
}

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

    operator fun plus(other: Money): Money {
        require(currencyCode == other.currencyCode && fractionDigits == other.fractionDigits)
        return copy(minorUnits = Math.addExact(minorUnits, other.minorUnits))
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

        /** Compatibility boundary for legacy Double prices; exact thereafter. */
        fun fromMajorUnits(value: Double, currencyCode: String, fractionDigits: Int = 2): Money {
            require(value.isFinite())
            return Money(ExactScale.fromDouble(value, fractionDigits), currencyCode, fractionDigits)
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

data class SemanticSignals(
    val available: Boolean = false,
    val modelVersion: String = "unavailable",
    val category: String = "unknown",
    val label: String = "unknown",
    val confidence: Double = 0.0,
    val foodConfidence: Double = 0.0,
    val porkConfidence: Double = 0.0,
    val meatRatio: Double = 0.0,
    val portionEligible: Boolean = false,
    val basePortionPoints: Double? = null,
    val evidence: List<String> = emptyList()
)

fun interface SemanticEnricher {
    fun enrich(rawText: String): SemanticSignals
}

object NoSemanticEnricher : SemanticEnricher {
    override fun enrich(rawText: String): SemanticSignals = SemanticSignals()
}

/** Platform adapter boundary for invariant internal text; never presentation localization. */
interface TextCanonicalizer {
    fun identity(value: String?): String
    fun search(value: String?): String
}

data class ProductIdentityKey(
    val canonicalName: String,
    val currentPriceMinor: Long,
    val memberPriceMinor: Long?,
    val quantityUnit: String?,
    val quantityMicros: Long?,
    val promotionCode: String,
    val sourceId: String? = null,
    val sessionId: String? = null
) {
    init { require(canonicalName.isNotBlank()) }

    fun stableText(): String = listOf(
        canonicalName, currentPriceMinor.toString(), memberPriceMinor?.toString().orEmpty(),
        quantityUnit.orEmpty(), quantityMicros?.toString().orEmpty(), promotionCode,
        sourceId.orEmpty(), sessionId.orEmpty()
    ).joinToString("|")
}

data class ProductMatchEvidence(
    val canonicalName: String,
    val currentPriceMinor: Long,
    val memberPriceMinor: Long?,
    val quantityUnit: String?,
    val quantityMicros: Long?
)

data class ProductEquivalence(val matches: Boolean, val score: Double, val reason: String)

object ProductMatching {
    fun compare(target: ProductMatchEvidence, candidate: ProductMatchEvidence): ProductEquivalence {
        if (target.currentPriceMinor != candidate.currentPriceMinor) return ProductEquivalence(false, 0.0, "current price differs")
        if (target.memberPriceMinor != null && target.memberPriceMinor != candidate.memberPriceMinor) {
            return ProductEquivalence(false, 0.0, "member price differs")
        }
        if (target.quantityUnit != null && (target.quantityUnit != candidate.quantityUnit || target.quantityMicros != candidate.quantityMicros)) {
            return ProductEquivalence(false, 0.0, "quantity differs")
        }
        val nameScore = tokenSimilarity(target.canonicalName, candidate.canonicalName)
        if (nameScore < .72) return ProductEquivalence(false, nameScore, "name differs")
        val evidence = 2 + (if (target.quantityUnit != null) 1 else 0) + (if (target.memberPriceMinor != null) 1 else 0)
        return ProductEquivalence(true, (nameScore * .6 + evidence * .1).coerceAtMost(1.0), "canonical evidence matches")
    }

    private fun tokenSimilarity(left: String, right: String): Double {
        if (left == right && left.isNotBlank()) return 1.0
        val a = left.split(' ').filter(String::isNotBlank).toSet()
        val b = right.split(' ').filter(String::isNotBlank).toSet()
        if (a.isEmpty() || b.isEmpty()) return 0.0
        return 2.0 * a.intersect(b).size / (a.size + b.size).toDouble()
    }
}

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
