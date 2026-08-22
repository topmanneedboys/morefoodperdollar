package com.valuepilot.app

import com.valuepilot.core.NoSemanticEnricher
import com.valuepilot.core.SemanticEnricher
import java.util.Locale
import kotlin.math.PI
import kotlin.math.pow

data class Quantity(
    val kind: Kind,
    val amountBase: Double,
    val display: String,
    val confidence: Double = 1.0,
    val diameterIn: Double? = null,
    val packCount: Int? = null
) {
    enum class Kind { MASS_G, VOLUME_ML, COUNT, PIZZA_AREA_SQIN }
}

data class PortionEstimate(
    val points: Double,
    val confidence: Double,
    val basis: String,
    val source: String = "explicit"
)

data class Promotion(
    val type: String = "none",
    val label: String = "",
    val receivedMultiplier: Double = 1.0,
    val minPaidUnits: Int = 1,
    val minimumSpend: Double? = null,
    val effectivePrice: Double? = null
)

data class PriceOffer(
    val currentPrice: Double,
    val currency: String,
    val regularPrice: Double? = null,
    val previousPrice: Double? = null,
    val salePrice: Double? = null,
    val memberPrice: Double? = null,
    val membershipRequired: Boolean = false
)

data class ValueItem(
    val name: String,
    val rawText: String,
    val price: Double,
    val currency: String,
    val quantity: Quantity?,
    val calories: Double?,
    val portion: PortionEstimate?,
    val promotion: Promotion,
    val confidence: Double,
    val ai: LocalAiPrediction = LocalAiPrediction(),
    val sourcePackage: String? = null,
    val offer: PriceOffer = PriceOffer(price, currency),
    val availability: String? = null,
    val searchSessionId: String? = null,
    val cardFingerprint: String? = null,
    val locator: ItemLocator? = null
) {
    val stableId: Long
        get() = StableIds.long(IncrementalProductStore.itemIdentity(this))

    fun applicablePrice(useMemberPrice: Boolean): Double =
        if (useMemberPrice) offer.memberPrice ?: offer.currentPrice else offer.currentPrice

    val pricePerKg: Double?
        get() = quantity?.takeIf { it.kind == Quantity.Kind.MASS_G && it.amountBase > 0 && price > 0 }
            ?.let { price / ((it.amountBase * promotion.receivedMultiplier) / 1000.0) }
    val pricePerL: Double?
        get() = quantity?.takeIf { it.kind == Quantity.Kind.VOLUME_ML && it.amountBase > 0 && price > 0 }
            ?.let { price / ((it.amountBase * promotion.receivedMultiplier) / 1000.0) }
    val pricePerUnit: Double?
        get() = quantity?.takeIf { it.kind == Quantity.Kind.COUNT && it.amountBase > 0 && price > 0 }
            ?.let { price / (it.amountBase * promotion.receivedMultiplier) }
    val caloriesPerDollar: Double?
        get() = calories?.takeIf { it > 0 && price > 0 }?.let { it * promotion.receivedMultiplier / price }
    val pizzaAreaPerDollar: Double?
        get() = quantity?.takeIf { it.kind == Quantity.Kind.PIZZA_AREA_SQIN && it.amountBase > 0 && price > 0 }
            ?.let { it.amountBase * promotion.receivedMultiplier / price }
    val portionPointsPerDollar: Double?
        get() = portion?.takeIf { it.points > 0 && price > 0 }?.let { it.points * promotion.receivedMultiplier / price }
    val meatPointsPerDollar: Double?
        get() = portion?.takeIf { it.points > 0 && price > 0 && ai.meatRatio > 0.08 && ai.confidence >= 0.30 }
            ?.let { it.points * ai.meatRatio * promotion.receivedMultiplier / price }
}

data class RankedItem(
    val item: ValueItem,
    val rank: Int,
    val mode: RankMode,
    val metricLabel: String,
    val exactnessLabel: String = "Calculated"
) {
    val stableId: Long get() = item.stableId
}

enum class RankMode { SMART, MASS, VOLUME, CALORIE, PIZZA, UNIT, PORTION, MEAT }

object ValueEngine {
    private const val PRICE_NUMBER_SOURCE = "(?:\\d{1,3}(?:[ ,.]\\d{3})+|\\d{1,6})(?:[.,]\\d{1,2})?"
    private val priceRegex = Regex(
        "(?:\\b(?:CA\\$|C\\$|US\\$|A\\$)|[$€£₹৳])\\s*$PRICE_NUMBER_SOURCE|\\b$PRICE_NUMBER_SOURCE\\s*(?:CAD|USD|EUR|GBP|INR|BDT|AUD)\\b",
        RegexOption.IGNORE_CASE
    )
    private val priceNumberRegex = Regex(PRICE_NUMBER_SOURCE)
    private val calorieRegex = Regex("\\b(\\d{2,5}(?:[.,]\\d+)?)\\s*(?:k?cal(?:ories?)?)\\b", RegexOption.IGNORE_CASE)

    private val mass = mapOf(
        "mcg" to .000001, "µg" to .000001, "μg" to .000001,
        "mg" to .001, "g" to 1.0, "gram" to 1.0, "grams" to 1.0,
        "kg" to 1000.0, "kilogram" to 1000.0, "kilograms" to 1000.0,
        "oz" to 28.349523125, "ounce" to 28.349523125, "ounces" to 28.349523125,
        "lb" to 453.59237, "lbs" to 453.59237, "pound" to 453.59237, "pounds" to 453.59237
    )
    private val volume = mapOf(
        "ml" to 1.0, "milliliter" to 1.0, "milliliters" to 1.0, "millilitre" to 1.0, "millilitres" to 1.0,
        "l" to 1000.0, "liter" to 1000.0, "liters" to 1000.0, "litre" to 1000.0, "litres" to 1000.0,
        "cl" to 10.0, "dl" to 100.0,
        "fl oz" to 29.5735295625, "floz" to 29.5735295625,
        "cup" to 236.5882365, "cups" to 236.5882365,
        "pt" to 473.176473, "pint" to 473.176473, "pints" to 473.176473,
        "qt" to 946.352946, "quart" to 946.352946, "quarts" to 946.352946,
        "gal" to 3785.411784, "gallon" to 3785.411784, "gallons" to 3785.411784
    )
    private const val MASS_UNITS = "mcg|[µμ]g|mg|grams?|g|kilograms?|kg|ounces?|oz|pounds?|lbs?|lb"
    private const val VOLUME_UNITS = "millilit(?:er|re)s?|ml|lit(?:er|re)s?|l|cl|dl|fl\\s*oz|floz|fluid\\s*ounces?|cups?|pints?|pt|quarts?|qt|gallons?|gal"
    private const val QUANTITY_UNITS = "$VOLUME_UNITS|$MASS_UNITS"

    fun normalize(text: String?): String = text.orEmpty()
        .replace('\u00A0', ' ')
        .replace(Regex("[\\t\\r ]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    fun parseNumber(value: String?): Double? {
        val raw = value.orEmpty().trim().replace(Regex("[ '\\u00A0]"), "")
        if (raw.isBlank()) return null
        val comma = raw.lastIndexOf(',')
        val dot = raw.lastIndexOf('.')
        val normalized = when {
            comma >= 0 && dot >= 0 && comma > dot -> raw.replace(".", "").replace(',', '.')
            comma >= 0 && dot >= 0 -> raw.replace(",", "")
            comma >= 0 && Regex("^\\d{1,3}(?:,\\d{3})+$").matches(raw) -> raw.replace(",", "")
            comma >= 0 -> raw.replace(',', '.')
            dot >= 0 && Regex("^\\d{1,3}(?:\\.\\d{3})+$").matches(raw) -> raw.replace(".", "")
            else -> raw
        }
        return normalized.toDoubleOrNull()
    }

    data class Price(
        val amount: Double,
        val currency: String,
        val raw: String,
        val index: Int = -1,
        val source: String = "match"
    )

    fun prices(text: String): List<Price> = priceRegex.findAll(normalize(text)).mapNotNull { match ->
        val number = priceNumberRegex.find(match.value)?.value?.let(::parseNumber) ?: return@mapNotNull null
        if (number <= 0) return@mapNotNull null
        val raw = match.value
        val currency = when {
            raw.contains("CAD", true) || raw.contains("CA$") || raw.contains("C$") -> "CAD"
            raw.contains("USD", true) || raw.contains("US$") -> "USD"
            raw.contains("AUD", true) || raw.contains("A$") -> "AUD"
            raw.contains("EUR", true) || raw.contains("€") -> "EUR"
            raw.contains("GBP", true) || raw.contains("£") -> "GBP"
            raw.contains("INR", true) || raw.contains("₹") -> "INR"
            raw.contains("BDT", true) || raw.contains("৳") -> "BDT"
            else -> "USD/CAD"
        }
        Price(number, currency, raw, match.range.first)
    }.toList()

    private data class PriceLabels(
        val member: Boolean,
        val previous: Boolean,
        val regular: Boolean,
        val sale: Boolean,
        val savings: Boolean,
        val unitRate: Boolean
    )

    private fun labelsFor(text: String, price: Price): PriceLabels {
        val start = price.index.coerceAtLeast(0)
        val prefix = text.substring(maxOf(0, start - 48), start).lowercase(Locale.ROOT)
        val suffixStart = (start + price.raw.length).coerceAtMost(text.length)
        val suffix = text.substring(suffixStart, minOf(text.length, suffixStart + 32)).lowercase(Locale.ROOT)
        val around = "$prefix ${price.raw.lowercase(Locale.ROOT)} $suffix"
        val memberPrefix = Regex(
            "(?:member(?:s)?(?:hip)?(?:\\s+price)?|loyalty(?:\\s+price)?|club\\s+price)\\s*[:\\-]?$",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(prefix.takeLast(40))
        val memberSuffix = Regex("^\\s*(?:for\\s+members?|members?\\s+only)\\b", RegexOption.IGNORE_CASE)
            .containsMatchIn(suffix.take(28))
        return PriceLabels(
            member = memberPrefix || memberSuffix,
            previous = Regex("(?:previous\\s+price(?:\\s+was)?|formerly|was)\\s*[:\\-]?$", RegexOption.IGNORE_CASE)
                .containsMatchIn(prefix.takeLast(34)),
            regular = Regex("(?:regular(?:\\s+price)?|reg\\.?)\\s*[:\\-]?$", RegexOption.IGNORE_CASE)
                .containsMatchIn(prefix.takeLast(30)),
            sale = Regex("(?:now|sale(?:\\s+price)?|current(?:\\s+price)?|deal(?:\\s+price)?)\\s*[:\\-]?$", RegexOption.IGNORE_CASE)
                .containsMatchIn(prefix.takeLast(32)),
            savings = Regex("(?:save|saving|discount|off)\\s*$", RegexOption.IGNORE_CASE)
                .containsMatchIn(prefix.takeLast(20)) || Regex("\\boff\\b", RegexOption.IGNORE_CASE).containsMatchIn(around.takeLast(24)),
            unitRate = Regex("(?:unit\\s+price|price\\s+per)\\s*[:\\-]?$", RegexOption.IGNORE_CASE)
                .containsMatchIn(prefix.takeLast(28)) ||
                Regex(
                    "^\\s*(?:/|per\\s+)(?:item|each|ea|unit|count|ct|kg|g|lb|oz|l|ml)\\b",
                    RegexOption.IGNORE_CASE
                ).containsMatchIn(suffix.take(24))
        )
    }

    private fun choosePrice(text: String, all: List<Price>): Price? {
        if (all.isEmpty()) return null
        val currencyAmount = "(?:CA\\$|C\\$|US\\$|A\\$|[$€£₹৳])\\s*$PRICE_NUMBER_SOURCE"
        Regex("\\b(\\d{1,2})\\s*(?:for|/|at)\\s*($currencyAmount)", RegexOption.IGNORE_CASE).find(text)?.let { match ->
            prices(match.groupValues[2]).firstOrNull()?.let { return it.copy(source = "bundle") }
        }
        val eligible = all.map { it to labelsFor(text, it) }.filterNot { it.second.unitRate }
        if (eligible.isEmpty()) return null
        val labeled = eligible.filterNot { it.second.savings }.ifEmpty { eligible }
        labeled.firstOrNull { (_, labels) -> labels.sale && !labels.member }?.let { return it.first.copy(source = "explicit-current") }
        labeled.firstOrNull { (_, labels) -> !labels.member && !labels.previous && !labels.regular }?.let { return it.first.copy(source = "current") }
        labeled.firstOrNull { (_, labels) -> labels.regular }?.let { return it.first.copy(source = "regular") }
        labeled.firstOrNull { (_, labels) -> labels.previous }?.let { return it.first.copy(source = "previous-fallback") }
        labeled.firstOrNull { (_, labels) -> labels.member }?.let { return it.first.copy(source = "member-only") }
        return all.first().copy(source = "first")
    }

    private fun offer(text: String, all: List<Price>, selected: Price): PriceOffer {
        val eligible = all.map { it to labelsFor(text, it) }.filterNot { it.second.unitRate }
        val labeled = eligible.filterNot { it.second.savings }.ifEmpty { eligible }
        val member = labeled.firstOrNull { (_, labels) -> labels.member && !labels.previous }?.first?.amount
        val previous = labeled.firstOrNull { (_, labels) -> labels.previous }?.first?.amount
        val regular = labeled.firstOrNull { (_, labels) -> labels.regular }?.first?.amount
        val sale = labeled.firstOrNull { (_, labels) -> labels.sale && !labels.member }?.first?.amount
        return PriceOffer(
            currentPrice = selected.amount,
            currency = selected.currency,
            regularPrice = regular,
            previousPrice = previous,
            salePrice = sale,
            memberPrice = member,
            membershipRequired = member != null
        )
    }

    fun promotion(text: String, price: Double): Promotion {
        val value = normalize(text).lowercase(Locale.ROOT)
        fun percentPromotion(label: String, pctWhole: Double): Promotion {
            val pct = (pctWhole.coerceIn(0.0, 99.0)) / 100.0
            val paidForTwo = 2.0 - pct
            val multiplier = 2.0 / paidForTwo
            return Promotion("bogo-percent", label, multiplier, 2, price * paidForTwo, price / multiplier)
        }

        Regex("\\b(?:bogo|buy\\s*one\\s*(?:,|&|and)?\\s*get\\s*(?:one|1))\\s*(\\d{1,2})\\s*%\\s*off\\b", RegexOption.IGNORE_CASE).find(value)?.let {
            val pct = it.groupValues[1].toDoubleOrNull() ?: 0.0
            return percentPromotion("Buy 1, get 1 ${pct.toInt()}% off", pct)
        }
        Regex("\\b(?:second|2nd)\\s+(?:item\\s+)?(?:is\\s+)?(\\d{1,2})\\s*%\\s*off\\b", RegexOption.IGNORE_CASE).find(value)?.let {
            val pct = it.groupValues[1].toDoubleOrNull() ?: 0.0
            return percentPromotion("2nd item ${pct.toInt()}% off", pct)
        }
        if (Regex("\\b(?:bogo(?!\\s*\\d+\\s*%\\s*off)|buy\\s*one\\s*(?:,|&|and)?\\s*get\\s*(?:one|1)(?:\\s*free)?(?!\\s*\\d+\\s*%\\s*off)|2\\s*for\\s*1|two\\s*for\\s*one)\\b", RegexOption.IGNORE_CASE).containsMatchIn(value)) {
            return Promotion("bogo", "Buy 1, get 1", 2.0, 1, price, price / 2.0)
        }
        Regex("\\bbuy\\s+(\\d+)\\s+(?:and\\s+)?get\\s+(\\d+)\\s+(?:free|at\\s+no\\s+cost)\\b", RegexOption.IGNORE_CASE).find(value)?.let {
            val buy = it.groupValues[1].toIntOrNull() ?: 0
            val get = it.groupValues[2].toIntOrNull() ?: 0
            if (buy > 0 && get > 0) {
                val multiplier = (buy + get).toDouble() / buy
                return Promotion("buy-x-get-y", "Buy $buy, get $get", multiplier, buy, price * buy, price / multiplier)
            }
        }
        Regex("\\b(\\d{1,2})\\s*(?:for|/)\\s*(?:ca\\$|c\\$|us\\$|a\\$|[$€£₹৳])\\s*($PRICE_NUMBER_SOURCE)", RegexOption.IGNORE_CASE).find(value)?.let {
            val units = it.groupValues[1].toIntOrNull() ?: 0
            val total = parseNumber(it.groupValues[2]) ?: 0.0
            if (units > 1 && total > 0) return Promotion("bundle", "$units for $total", units.toDouble(), units, total, total / units)
        }
        Regex("\\b(\\d{1,2})\\s*%\\s*off\\b", RegexOption.IGNORE_CASE).find(value)?.let {
            return Promotion("percent-off-shown", "${it.groupValues[1]}% off", 1.0, 1, price, price)
        }
        if (Regex("\\bfree\\s+delivery\\b", RegexOption.IGNORE_CASE).containsMatchIn(value)) {
            return Promotion("free-delivery", "Free delivery", 1.0, 1, price, price)
        }
        return Promotion(minimumSpend = price, effectivePrice = price)
    }

    private fun normalizeUnit(unit: String): String {
        val normalized = unit.lowercase(Locale.ROOT).replace(".", "").replace(Regex("\\s+"), " ").trim()
        return when {
            Regex("^fluid ounces?$").matches(normalized) -> "fl oz"
            normalized == "floz" -> "fl oz"
            else -> normalized
        }
    }

    fun quantity(text: String): Quantity? {
        val value = normalize(text).lowercase(Locale.ROOT)
        val candidates = mutableListOf<Quantity>()

        Regex("\\b(\\d{1,3})\\s*[x×]\\s*(\\d+(?:[.,]\\d+)?)\\s*($QUANTITY_UNITS)\\b", RegexOption.IGNORE_CASE).findAll(value).forEach { match ->
            val count = match.groupValues[1].toIntOrNull() ?: return@forEach
            val each = parseNumber(match.groupValues[2]) ?: return@forEach
            val unit = normalizeUnit(match.groupValues[3])
            mass[unit]?.let { candidates += Quantity(Quantity.Kind.MASS_G, count * each * it, "$count × ${match.groupValues[2]} ${match.groupValues[3]}", 1.0, packCount = count) }
            volume[unit]?.let { candidates += Quantity(Quantity.Kind.VOLUME_ML, count * each * it, "$count × ${match.groupValues[2]} ${match.groupValues[3]}", 1.0, packCount = count) }
        }

        Regex("\\b(?:pack|case|box)\\s+of\\s+(\\d{1,3}).{0,40}?(\\d+(?:[.,]\\d+)?)\\s*($QUANTITY_UNITS)\\s*(?:each|ea)?\\b", RegexOption.IGNORE_CASE).find(value)?.let { match ->
            val count = match.groupValues[1].toIntOrNull() ?: 0
            val each = parseNumber(match.groupValues[2]) ?: 0.0
            val unit = normalizeUnit(match.groupValues[3])
            if (count > 0 && each > 0) {
                mass[unit]?.let { candidates += Quantity(Quantity.Kind.MASS_G, count * each * it, "$count × ${match.groupValues[2]} ${match.groupValues[3]}", .95, packCount = count) }
                volume[unit]?.let { candidates += Quantity(Quantity.Kind.VOLUME_ML, count * each * it, "$count × ${match.groupValues[2]} ${match.groupValues[3]}", .95, packCount = count) }
            }
        }

        val rangeQuantityRegex = Regex(
            "\\b(\\d+(?:[.,]\\d+)?)\\s*[-–]\\s*(\\d+(?:[.,]\\d+)?)\\s*($QUANTITY_UNITS)\\b",
            RegexOption.IGNORE_CASE
        )
        val rangeMatches = rangeQuantityRegex.findAll(value).toList()
        rangeMatches.forEach { match ->
            val a = parseNumber(match.groupValues[1]) ?: return@forEach
            val b = parseNumber(match.groupValues[2]) ?: return@forEach
            val midpoint = (a + b) / 2.0
            val unit = normalizeUnit(match.groupValues[3])
            mass[unit]?.let { candidates += Quantity(Quantity.Kind.MASS_G, midpoint * it, "${match.groupValues[1]}–${match.groupValues[2]} ${match.groupValues[3]} avg", .7) }
            volume[unit]?.let { candidates += Quantity(Quantity.Kind.VOLUME_ML, midpoint * it, "${match.groupValues[1]}–${match.groupValues[2]} ${match.groupValues[3]} avg", .7) }
        }

        Regex("\\b(\\d+(?:[.,]\\d+)?)\\s*($QUANTITY_UNITS)\\b", RegexOption.IGNORE_CASE).findAll(value).forEach { match ->
            val prefix = value.substring(maxOf(0, match.range.first - 8), match.range.first)
            if (Regex("\\d+\\s*[x×]\\s*$").containsMatchIn(prefix)) return@forEach
            if (rangeMatches.any { range -> match.range.first >= range.range.first && match.range.last <= range.range.last }) return@forEach
            val amount = parseNumber(match.groupValues[1]) ?: return@forEach
            val unit = normalizeUnit(match.groupValues[2])
            mass[unit]?.let { candidates += Quantity(Quantity.Kind.MASS_G, amount * it, "${match.groupValues[1]} ${match.groupValues[2]}", .98) }
            volume[unit]?.let { candidates += Quantity(Quantity.Kind.VOLUME_ML, amount * it, "${match.groupValues[1]} ${match.groupValues[2]}", .98) }
        }

        listOf(
            Regex("\\b(\\d{1,4})\\s*(?:count|ct|pieces?|pcs|pack|pk|units?|ea)\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(?:pack|box|case|set)\\s+of\\s+(\\d{1,4})\\b", RegexOption.IGNORE_CASE)
        ).forEach { regex ->
            regex.find(value)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.takeIf { it > 0 }?.let {
                candidates += Quantity(Quantity.Kind.COUNT, it, "${it.toInt()} count", .9)
            }
        }
        Regex("\\b(half[ -]?)?dozen\\b", RegexOption.IGNORE_CASE).find(value)?.let {
            val half = it.groupValues[1].isNotBlank()
            candidates += Quantity(Quantity.Kind.COUNT, if (half) 6.0 else 12.0, if (half) "half-dozen" else "dozen", .92)
        }

        if (Regex("\\b(pizza|pie|flatbread)\\b", RegexOption.IGNORE_CASE).containsMatchIn(value)) {
            val inches = Regex("\\b(\\d{1,2}(?:[.,]\\d+)?)\\s*(?:in(?:ch(?:es)?)?|\")\\b", RegexOption.IGNORE_CASE)
                .find(value)?.groupValues?.get(1)?.let(::parseNumber)
            val centimeters = Regex("\\b(\\d{2,3}(?:[.,]\\d+)?)\\s*cm\\b", RegexOption.IGNORE_CASE)
                .find(value)?.groupValues?.get(1)?.let(::parseNumber)
            val diameter = inches ?: centimeters?.div(2.54)
            if (diameter != null && diameter in 5.0..30.0) {
                candidates += Quantity(Quantity.Kind.PIZZA_AREA_SQIN, PI * (diameter / 2.0).pow(2), "${fmt(diameter, 1)}\" pizza", .9, diameter)
            }
        }

        fun score(quantity: Quantity): Double {
            val kind = when (quantity.kind) {
                Quantity.Kind.MASS_G -> 4.0
                Quantity.Kind.VOLUME_ML -> 3.0
                Quantity.Kind.PIZZA_AREA_SQIN -> 2.0
                Quantity.Kind.COUNT -> 1.0
            }
            return kind + quantity.confidence + if (quantity.packCount != null) 2.0 else 0.0
        }
        return candidates.maxByOrNull(::score)
    }

    fun calories(text: String): Double? = calorieRegex.find(normalize(text))?.groupValues?.get(1)?.let(::parseNumber)?.takeIf { it > 0 }

    internal fun sanitizeNameLine(value: String): String {
        var clean = priceRegex.replace(normalize(value), " ")
        val leadingNoise = Regex(
            "^\\s*(?:(?:previous\\s+price(?:\\s+was)?|regular(?:\\s+price)?|reg\\.?(?=\\s|:|-|\\z)|member(?:s|ship)?(?:\\s+price)?|loyalty(?:\\s+price)?|sale(?:\\s+price)?|current\\s+price|now|was)\\s*[:\\-]?|for\\s+members?\\b)\\s*",
            RegexOption.IGNORE_CASE
        )
        repeat(4) { clean = leadingNoise.replace(clean, "") }
        clean = clean
            .replace(Regex("\\b(?:previous\\s+price(?:\\s+was)?|regular\\s+price|member(?:s|ship)?\\s+price)\\b", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\bfor\\s+members?\\s*$", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', ':', '-', '·', '|')
        return clean
    }

    fun name(text: String): String {
        val lines = text.split(Regex("\\n+")).map(::normalize).filter { it.isNotBlank() }.take(40)
        val bad = Regex("^(?:add|customize|choose|select|popular|sponsored|deal|sale|save|free delivery|buy one|get one|from\\s+[$€£₹৳]|[$€£₹৳]\\s*\\d|view cart|checkout|order now|in stock|out of stock|members? only)\\b", RegexOption.IGNORE_CASE)
        val ratingOrTime = Regex("^(?:\\d(?:[.,]\\d)?\\s*(?:stars?|★)|\\d+\\s*(?:min|mins|minutes?|hours?|reviews?|ratings?))\\b", RegexOption.IGNORE_CASE)
        val quantityOnly = Regex("^\\d+(?:[.,]\\d+)?\\s*(?:$QUANTITY_UNITS|cal|kcal|calories?|ct|count|pieces?|pcs|pack|pk|units?|ea)$", RegexOption.IGNORE_CASE)
        val scored = lines.mapIndexedNotNull { index, line ->
            val clean = sanitizeNameLine(line)
            if (clean.length !in 2..160) return@mapIndexedNotNull null
            var score = 0.0
            if (Regex("\\p{L}").containsMatchIn(clean)) score += 4
            if (clean.length in 3..80) score += 3
            if (!priceRegex.containsMatchIn(line)) score += 1
            if (bad.containsMatchIn(clean)) score -= 9
            if (ratingOrTime.containsMatchIn(clean)) score -= 8
            if (quantityOnly.matches(clean)) score -= 10
            if (Regex("\\b(?:subtotal|total|delivery fee|service fee|tax|tip|add to cart)\\b", RegexOption.IGNORE_CASE).containsMatchIn(clean)) score -= 7
            clean to (score - index * .05)
        }
        val best = scored.maxByOrNull { it.second }
        if (best != null && best.second > -1) return best.first.take(120)
        return sanitizeNameLine(normalize(text)).take(120).ifBlank { "Unnamed item" }
    }

    fun estimatePortion(text: String, ai: LocalAiPrediction = LocalAiPrediction()): PortionEstimate? {
        val value = normalize(text).lowercase(Locale.ROOT)
        var points: Double? = null
        var confidence = .45
        var basis = ""
        var source = "explicit"

        Regex("\\b(\\d{1,3})\\s*(?:piece|pieces|pc|pcs|wings?|nuggets?|tenders?|patties|tacos?|burgers?|sandwiches?|slices?)\\b", RegexOption.IGNORE_CASE).find(value)?.let {
            val count = it.groupValues[1].toDoubleOrNull()
            if (count != null && count in 1.0..100.0) {
                points = count; confidence = .72; basis = "${count.toInt()} food units"
            }
        }
        if (points == null) {
            when {
                Regex("\\btriple\\b").containsMatchIn(value) -> { points = 3.0; confidence = .66; basis = "triple" }
                Regex("\\bdouble\\b").containsMatchIn(value) -> { points = 2.0; confidence = .64; basis = "double" }
                Regex("\\bsingle\\b").containsMatchIn(value) -> { points = 1.0; confidence = .60; basis = "single" }
            }
        }
        if (points == null && ai.portionEligible && (ai.basePortionPoints ?: 0.0) > 0 && ai.foodConfidence >= .34) {
            points = ai.basePortionPoints
            confidence = minOf(.62, .28 + ai.confidence * .5)
            basis = "local AI: ${ai.label}"
            source = "local-ai"
        }

        val size = when {
            Regex("\\b(?:party|feast)\\b").containsMatchIn(value) -> 2.2 to "party"
            Regex("\\b(?:family|sharing)\\b").containsMatchIn(value) -> 1.8 to "family"
            Regex("\\b(?:extra[ -]?large|x[- ]?large|xl)\\b").containsMatchIn(value) -> 1.55 to "extra large"
            Regex("\\blarge\\b").containsMatchIn(value) -> 1.3 to "large"
            Regex("\\bmedium\\b").containsMatchIn(value) -> 1.0 to "medium"
            Regex("\\bsmall\\b").containsMatchIn(value) -> .8 to "small"
            Regex("\\b(?:kid|kids|junior)\\b").containsMatchIn(value) -> .65 to "kids/junior"
            else -> null
        }
        if (size != null) {
            points = (points ?: 1.0) * size.first
            confidence = maxOf(confidence, .55)
            basis = if (basis.isBlank()) size.second else "$basis + ${size.second}"
        }
        if (Regex("\\b(?:combo|meal)\\b").containsMatchIn(value)) {
            points = (points ?: 1.0) * 1.35
            confidence = maxOf(confidence, .50)
            basis = if (basis.isBlank()) "meal" else "$basis + meal"
        }
        return points?.let { PortionEstimate(it, confidence, basis, source) }
    }

    fun analyze(
        text: String,
        sourcePackage: String? = null,
        semanticEnricher: SemanticEnricher = NoSemanticEnricher
    ): ValueItem? {
        val normalized = normalize(text)
        val allPrices = prices(normalized)
        val selected = choosePrice(normalized, allPrices) ?: return null
        val offer = offer(normalized, allPrices, selected)
        val promo = promotion(normalized, selected.amount)
        val quantity = quantity(normalized)
        val calories = calories(normalized)
        val ai = semanticEnricher.enrich(normalized)
        val portion = estimatePortion(normalized, ai)
        var confidence = .55
        confidence += if (selected.source == "first" || selected.source == "bundle") .12 else .16
        if (quantity != null) confidence += .18 * quantity.confidence
        if (calories != null) confidence += .08
        if (promo.type != "none") confidence += .03
        if (ai.foodConfidence >= .5) confidence += minOf(.04, ai.confidence * .04)
        val availability = when {
            Regex("\\bout of stock|sold out|unavailable\\b", RegexOption.IGNORE_CASE).containsMatchIn(normalized) -> "Out of stock"
            Regex("\\bin stock|available\\b", RegexOption.IGNORE_CASE).containsMatchIn(normalized) -> "In stock"
            else -> null
        }
        return ValueItem(
            name = name(normalized), rawText = normalized, price = selected.amount, currency = selected.currency,
            quantity = quantity, calories = calories, portion = portion, promotion = promo,
            confidence = confidence.coerceAtMost(.99), ai = ai, sourcePackage = sourcePackage,
            offer = offer, availability = availability
        )
    }

    fun canonicalName(name: String): String {
        val normalized = JvmTextCanonicalizer.identity(normalize(name))
        return priceRegex.replace(normalized, " ")
            .replace(Regex("\\b\\d+(?:[.,]\\d+)?\\s*(?:mg|g|grams?|kg|kilograms?|oz|ounces?|lb|lbs|pounds?|ml|milliliters?|l|litres?|liters?|fl\\s*oz|cal|kcal|calories?|ct|count|pieces?|pcs|pack|pk|units?|ea)\\b", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\b(?:buy\\s+one\\s+get\\s+one(?:\\s+free)?|bogo|sale|deal|save\\s+\\d+%|\\d+%\\s*off|add to cart|customize)\\b", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\b(?:previous\\s+price(?:\\s+was)?|regular\\s+price|member(?:s|ship)?\\s+price|for\\s+members?)\\b", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .replace(Regex("\\s+"), " ").trim()
            .ifBlank { normalize(name).lowercase(Locale.ROOT).take(80) }
    }

    fun dedupe(items: Collection<ValueItem>): List<ValueItem> {
        val map = linkedMapOf<String, ValueItem>()
        for (item in items) {
            val key = DomainIdentity.productKey(item, includeSessionScope = false).stableText()
            val old = map[key]
            if (old == null || item.confidence > old.confidence || item.rawText.length > old.rawText.length) map[key] = item
        }
        return map.values.toList()
    }

    fun minimumSpend(item: ValueItem, useMemberPrice: Boolean = false): Double {
        val applicable = item.applicablePrice(useMemberPrice)
        val ratio = (item.promotion.minimumSpend ?: item.price) / item.price.coerceAtLeast(.000001)
        return applicable * ratio
    }

    fun filterItems(
        items: Collection<ValueItem>,
        maxPrice: Double? = null,
        foodOnly: Boolean = true,
        excludePork: Boolean = false,
        query: String? = null,
        useMemberPrice: Boolean = false
    ): List<ValueItem> = items.filter { item ->
        if (maxPrice != null && maxPrice > 0 && minimumSpend(item, useMemberPrice) > maxPrice) return@filter false
        if (excludePork && (item.ai.category == "pork" || item.ai.porkConfidence >= .36)) return@filter false
        if (foodOnly && item.ai.available && item.ai.category == "nonfood" && item.ai.foodConfidence < .22) return@filter false
        if (!SearchRelevance.matches(query, item)) return@filter false
        true
    }

    fun rank(items: Collection<ValueItem>, requested: RankMode, useMemberPrice: Boolean = false): List<RankedItem> {
        val clean = dedupe(items)
        val mode = if (requested == RankMode.SMART) smartMode(clean) else requested
        data class Metric(
            val item: ValueItem,
            val value: Double?,
            val lower: Boolean,
            val label: String,
            val exactness: String,
            val index: Int
        )
        val rows = clean.mapIndexed { index, item ->
            val price = item.applicablePrice(useMemberPrice)
            val received = item.promotion.receivedMultiplier
            val massValue = item.quantity?.takeIf { it.kind == Quantity.Kind.MASS_G && it.amountBase > 0 && price > 0 }
                ?.let { price / ((it.amountBase * received) / 1000.0) }
            val volumeValue = item.quantity?.takeIf { it.kind == Quantity.Kind.VOLUME_ML && it.amountBase > 0 && price > 0 }
                ?.let { price / ((it.amountBase * received) / 1000.0) }
            val unitValue = item.quantity?.takeIf { it.kind == Quantity.Kind.COUNT && it.amountBase > 0 && price > 0 }
                ?.let { price / (it.amountBase * received) }
            val calorieValue = item.calories?.takeIf { it > 0 && price > 0 }?.let { it * received / price }
            val pizzaValue = item.quantity?.takeIf { it.kind == Quantity.Kind.PIZZA_AREA_SQIN && it.amountBase > 0 && price > 0 }
                ?.let { it.amountBase * received / price }
            val portionValue = item.portion?.takeIf { it.points > 0 && price > 0 }?.let { it.points * received / price }
            val meatValue = item.portion?.takeIf { it.points > 0 && price > 0 && item.ai.meatRatio > .08 && item.ai.confidence >= .30 }
                ?.let { it.points * item.ai.meatRatio * received / price }
            fun measuredExactness(value: Double?): String = when {
                value == null -> "Shown price"
                (item.quantity?.confidence ?: 1.0) < .9 -> "Estimate"
                else -> "Calculated"
            }
            when (mode) {
                RankMode.MASS -> Metric(item, massValue, true, massValue?.let { "${money(it, item.currency)}/kg" } ?: "Price only", measuredExactness(massValue), index)
                RankMode.VOLUME -> Metric(item, volumeValue, true, volumeValue?.let { "${money(it, item.currency)}/L" } ?: "Price only", measuredExactness(volumeValue), index)
                RankMode.CALORIE -> Metric(item, calorieValue, false, calorieValue?.let { "${fmt(it, 1)} cal/$" } ?: "Price only", if (calorieValue == null) "Shown price" else "Calculated", index)
                RankMode.PIZZA -> Metric(item, pizzaValue, false, pizzaValue?.let { "${fmt(it, 1)} in²/$" } ?: "Price only", measuredExactness(pizzaValue), index)
                RankMode.UNIT -> Metric(item, unitValue, true, unitValue?.let { "${money(it, item.currency)}/item" } ?: "Price only", measuredExactness(unitValue), index)
                RankMode.PORTION -> Metric(item, portionValue, false, portionValue?.let { "${fmt(it, 2)} food amount/$" } ?: "Price only", "Estimate", index)
                RankMode.MEAT -> Metric(item, meatValue, false, meatValue?.let { "${fmt(it, 2)} meat value/$" } ?: "Price only", "Estimate", index)
                RankMode.SMART -> error("resolved above")
            }
        }.sortedWith(Comparator { a, b ->
            when {
                a.value != null && b.value == null -> -1
                a.value == null && b.value != null -> 1
                a.value == null && b.value == null -> a.item.price.compareTo(b.item.price).takeIf { it != 0 } ?: a.index.compareTo(b.index)
                else -> {
                    val comparison = if (a.lower) a.value!!.compareTo(b.value!!) else b.value!!.compareTo(a.value!!)
                    comparison.takeIf { it != 0 }
                        ?: b.item.confidence.compareTo(a.item.confidence).takeIf { it != 0 }
                        ?: a.item.price.compareTo(b.item.price).takeIf { it != 0 }
                        ?: a.index.compareTo(b.index)
                }
            }
        })
        return rows.mapIndexed { index, metric -> RankedItem(metric.item, index + 1, mode, metric.label, metric.exactness) }
    }

    fun smartMode(items: Collection<ValueItem>): RankMode {
        val counts = mapOf(
            RankMode.MASS to items.count { it.pricePerKg != null },
            RankMode.VOLUME to items.count { it.pricePerL != null },
            RankMode.CALORIE to items.count { it.caloriesPerDollar != null },
            RankMode.PIZZA to items.count { it.pizzaAreaPerDollar != null },
            RankMode.UNIT to items.count { it.pricePerUnit != null },
            RankMode.PORTION to items.count { it.portionPointsPerDollar != null }
        )
        val order = listOf(RankMode.MASS, RankMode.VOLUME, RankMode.CALORIE, RankMode.PIZZA, RankMode.UNIT, RankMode.PORTION)
        return order.filter { (counts[it] ?: 0) >= 2 }
            .maxWithOrNull(compareBy<RankMode> { counts[it] ?: 0 }.thenByDescending { -order.indexOf(it) })
            ?: order.maxByOrNull { counts[it] ?: 0 }
            ?: RankMode.CALORIE
    }

    fun money(value: Double, currency: String): String {
        val symbol = when (currency) { "EUR" -> "€"; "GBP" -> "£"; "INR" -> "₹"; "BDT" -> "৳"; else -> "$" }
        return symbol + String.format(Locale.US, "%.2f", value)
    }

    fun fmt(value: Double, digits: Int): String = String.format(Locale.US, "%.${digits}f", value)
}
