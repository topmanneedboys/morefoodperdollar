package com.valuepilot.app

import java.util.Locale
import kotlin.math.PI
import kotlin.math.pow

data class Quantity(
    val kind: Kind,
    val amountBase: Double,
    val display: String,
    val confidence: Double = 1.0,
    val diameterIn: Double? = null
) {
    enum class Kind { MASS_G, VOLUME_ML, COUNT, PIZZA_AREA_SQIN }
}

data class PortionEstimate(val points: Double, val confidence: Double, val basis: String)

data class Promotion(
    val type: String = "none",
    val label: String = "",
    val receivedMultiplier: Double = 1.0,
    val minPaidUnits: Int = 1,
    val effectivePrice: Double? = null
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
    val sourcePackage: String? = null
) {
    val pricePerKg: Double?
        get() = quantity?.takeIf { it.kind == Quantity.Kind.MASS_G && it.amountBase > 0 }
            ?.let { price / ((it.amountBase * promotion.receivedMultiplier) / 1000.0) }
    val pricePerL: Double?
        get() = quantity?.takeIf { it.kind == Quantity.Kind.VOLUME_ML && it.amountBase > 0 }
            ?.let { price / ((it.amountBase * promotion.receivedMultiplier) / 1000.0) }
    val pricePerUnit: Double?
        get() = quantity?.takeIf { it.kind == Quantity.Kind.COUNT && it.amountBase > 0 }
            ?.let { price / (it.amountBase * promotion.receivedMultiplier) }
    val caloriesPerDollar: Double?
        get() = calories?.takeIf { it > 0 }?.let { it * promotion.receivedMultiplier / price }
    val pizzaAreaPerDollar: Double?
        get() = quantity?.takeIf { it.kind == Quantity.Kind.PIZZA_AREA_SQIN && it.amountBase > 0 }
            ?.let { it.amountBase * promotion.receivedMultiplier / price }
    val portionPointsPerDollar: Double?
        get() = portion?.takeIf { it.points > 0 }?.let { it.points * promotion.receivedMultiplier / price }
}

data class RankedItem(val item: ValueItem, val rank: Int, val mode: RankMode, val metricLabel: String)

enum class RankMode { SMART, MASS, VOLUME, CALORIE, PIZZA, UNIT, PORTION }

object ValueEngine {
    private val priceRegex = Regex("(?:\\b(?:CA\\$|C\\$|US\\$|A\\$)|[$€£₹৳])\\s*\\d{1,5}(?:[.,]\\d{1,2})?|\\b\\d{1,5}(?:[.,]\\d{1,2})?\\s*(?:CAD|USD|EUR|GBP|INR|BDT)\\b", RegexOption.IGNORE_CASE)
    private val calorieRegex = Regex("\\b(\\d{2,5}(?:[.,]\\d+)?)\\s*(?:k?cal(?:ories?)?)\\b", RegexOption.IGNORE_CASE)

    private val mass = mapOf(
        "mg" to .001, "g" to 1.0, "gram" to 1.0, "grams" to 1.0,
        "kg" to 1000.0, "kilogram" to 1000.0, "kilograms" to 1000.0,
        "oz" to 28.349523125, "ounce" to 28.349523125, "ounces" to 28.349523125,
        "lb" to 453.59237, "lbs" to 453.59237, "pound" to 453.59237, "pounds" to 453.59237
    )
    private val volume = mapOf(
        "ml" to 1.0, "milliliter" to 1.0, "milliliters" to 1.0,
        "l" to 1000.0, "liter" to 1000.0, "liters" to 1000.0, "litre" to 1000.0, "litres" to 1000.0,
        "fl oz" to 29.5735295625
    )

    fun normalize(text: String?): String = (text ?: "")
        .replace('\u00A0', ' ')
        .replace(Regex("[\\t\\r ]+"), " ")
        .replace(Regex("\\n{3,}"), "\\n\\n")
        .trim()

    private fun n(s: String): Double? {
        val raw = s.trim()
        val normalized = if (Regex("^\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?$").matches(raw)) {
            raw.replace(",", "")
        } else raw.replace(',', '.')
        return normalized.toDoubleOrNull()
    }

    data class Price(val amount: Double, val currency: String, val raw: String, val index: Int = -1)

    fun prices(text: String): List<Price> = priceRegex.findAll(normalize(text)).mapNotNull { m ->
        val number = Regex("\\d{1,5}(?:[.,]\\d{1,2})?").find(m.value)?.value?.let(::n) ?: return@mapNotNull null
        if (number <= 0) return@mapNotNull null
        val r = m.value
        val currency = when {
            r.contains("CAD", true) || r.contains("C$") || r.contains("CA$") -> "CAD"
            r.contains("USD", true) || r.contains("US$") -> "USD"
            r.contains("EUR", true) || r.contains("€") -> "EUR"
            r.contains("GBP", true) || r.contains("£") -> "GBP"
            r.contains("INR", true) || r.contains("₹") -> "INR"
            r.contains("BDT", true) || r.contains("৳") -> "BDT"
            else -> "USD/CAD"
        }
        Price(number, currency, r, m.range.first)
    }.toList()

    private fun choosePrice(text: String, all: List<Price>): Price? {
        if (all.isEmpty()) return null
        val bundle = Regex("\\b(\\d{1,2})\\s*(?:for|/)\\s*((?:CA\\$|C\\$|US\\$|A\\$|[$€£₹৳])\\s*\\d+(?:[.,]\\d{1,2})?)", RegexOption.IGNORE_CASE).find(text)
        if (bundle != null) prices(bundle.groupValues[2]).firstOrNull()?.let { return it }

        if (all.size > 1) {
            val current = Regex("\\b(?:now|sale(?:\\s+price)?|member(?:\\s+price)?|deal(?:\\s+price)?)\\s*[:-]?\\s*((?:CA\\$|C\\$|US\\$|A\\$|[$€£₹৳])\\s*\\d+(?:[.,]\\d{1,2})?)", RegexOption.IGNORE_CASE).find(text)
            if (current != null) prices(current.groupValues[1]).firstOrNull()?.let { return it }

            val usable = all.filter { p ->
                val startAt = kotlin.math.max(0, p.index - 14)
                val prefix = if (p.index >= 0 && p.index <= text.length) text.substring(startAt, p.index) else ""
                !Regex("\\b(?:save|saving|discount|off)\\s*$", RegexOption.IGNORE_CASE).containsMatchIn(prefix)
            }
            val saleHints = Regex("\\b(sale|now|deal|member|promo|offer|discount|save|regular|reg\\.?|was)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
            if (saleHints && usable.isNotEmpty()) return usable.minByOrNull { it.amount }
        }
        return all.first()
    }

    fun promotion(text: String, price: Double): Promotion {
        val t = normalize(text).lowercase(Locale.ROOT)

        Regex("\\b(?:bogo|buy\\s*one\\s*(?:,|&|and)?\\s*get\\s*(?:one|1))\\s*(\\d{1,2})\\s*%\\s*off\\b", RegexOption.IGNORE_CASE).find(t)?.let {
            val pct = ((it.groupValues[1].toDoubleOrNull() ?: 0.0).coerceIn(0.0, 99.0)) / 100.0
            val mult = 2.0 / (2.0 - pct)
            return Promotion("bogo-percent", "Buy 1, get 1 ${(pct * 100).toInt()}% off", mult, 2, price / mult)
        }

        if (Regex("\\b(?:bogo(?!\\s*\\d+\\s*%\\s*off)|buy\\s*one\\s*(?:,|&|and)?\\s*get\\s*(?:one|1)(?:\\s*free)?(?!\\s*\\d+\\s*%\\s*off)|2\\s*for\\s*1|two\\s*for\\s*one)\\b", RegexOption.IGNORE_CASE).containsMatchIn(t)) {
            return Promotion("bogo", "Buy 1, get 1", 2.0, 1, price / 2.0)
        }

        Regex("\\bbuy\\s+(\\d+)\\s+(?:and\\s+)?get\\s+(\\d+)\\s+(?:free|at\\s+no\\s+cost)\\b", RegexOption.IGNORE_CASE).find(t)?.let {
            val buy = it.groupValues[1].toIntOrNull() ?: 0
            val get = it.groupValues[2].toIntOrNull() ?: 0
            if (buy > 0 && get > 0) {
                val mult = (buy + get).toDouble() / buy
                return Promotion("buy-x-get-y", "Buy $buy, get $get", mult, buy, price / mult)
            }
        }

        Regex("\\b(\\d{1,2})\\s*(?:for|/)\\s*(?:ca\\$|c\\$|us\\$|a\\$|[$€£₹৳])\\s*(\\d+(?:[.,]\\d{1,2})?)", RegexOption.IGNORE_CASE).find(t)?.let {
            val units = it.groupValues[1].toIntOrNull() ?: 0
            val total = n(it.groupValues[2]) ?: 0.0
            if (units > 1 && total > 0) return Promotion("bundle", "$units for $total", units.toDouble(), units, total / units)
        }

        Regex("\\b(\\d{1,2})\\s*%\\s*off\\b", RegexOption.IGNORE_CASE).find(t)?.let {
            return Promotion("percent-off-shown", "${it.groupValues[1]}% off", 1.0, 1, price)
        }
        return Promotion(effectivePrice = price)
    }

    fun quantity(text: String): Quantity? {
        val t = normalize(text).lowercase(Locale.ROOT)
        val candidates = mutableListOf<Quantity>()

        Regex("\\b(\\d{1,3})\\s*[x×]\\s*(\\d+(?:[.,]\\d+)?)\\s*(mg|g|grams?|kg|kilograms?|oz|ounces?|lb|lbs|pounds?|ml|l|litres?|liters?|fl\\s*oz)\\b", RegexOption.IGNORE_CASE)
            .findAll(t).forEach { m ->
                val count = m.groupValues[1].toDoubleOrNull() ?: return@forEach
                val each = n(m.groupValues[2]) ?: return@forEach
                val unit = m.groupValues[3].replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
                mass[unit]?.let { candidates += Quantity(Quantity.Kind.MASS_G, count * each * it, "${m.groupValues[1]} × ${m.groupValues[2]} ${m.groupValues[3]}", 1.0) }
                volume[unit]?.let { candidates += Quantity(Quantity.Kind.VOLUME_ML, count * each * it, "${m.groupValues[1]} × ${m.groupValues[2]} ${m.groupValues[3]}", 1.0) }
            }

        Regex("\\b(\\d+(?:[.,]\\d+)?)\\s*[-–]\\s*(\\d+(?:[.,]\\d+)?)\\s*(g|kg|oz|lb|lbs|ml|l)\\b", RegexOption.IGNORE_CASE)
            .findAll(t).forEach { m ->
                val a = n(m.groupValues[1]) ?: return@forEach
                val b = n(m.groupValues[2]) ?: return@forEach
                val mid = (a + b) / 2.0
                val unit = m.groupValues[3].lowercase(Locale.ROOT)
                mass[unit]?.let { candidates += Quantity(Quantity.Kind.MASS_G, mid * it, "${m.groupValues[1]}–${m.groupValues[2]} ${m.groupValues[3]} avg", .7) }
                volume[unit]?.let { candidates += Quantity(Quantity.Kind.VOLUME_ML, mid * it, "${m.groupValues[1]}–${m.groupValues[2]} ${m.groupValues[3]} avg", .7) }
            }

        Regex("\\b(\\d+(?:[.,]\\d+)?)\\s*(mg|g|grams?|kg|kilograms?|oz|ounces?|lb|lbs|pounds?|ml|milliliters?|l|litres?|liters?|fl\\s*oz)\\b", RegexOption.IGNORE_CASE)
            .findAll(t).forEach { m ->
                val prefix = t.substring(kotlin.math.max(0, m.range.first - 8), m.range.first)
                if (Regex("\\d+\\s*[x×]\\s*$").containsMatchIn(prefix)) return@forEach
                val value = n(m.groupValues[1]) ?: return@forEach
                val unit = m.groupValues[2].replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
                mass[unit]?.let { candidates += Quantity(Quantity.Kind.MASS_G, value * it, "${m.groupValues[1]} ${m.groupValues[2]}", .98) }
                volume[unit]?.let { candidates += Quantity(Quantity.Kind.VOLUME_ML, value * it, "${m.groupValues[1]} ${m.groupValues[2]}", .98) }
            }

        listOf(
            Regex("\\b(\\d{1,4})\\s*(?:count|ct|pieces?|pcs|pack|pk|units?|ea)\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(?:pack|box|case|set)\\s+of\\s+(\\d{1,4})\\b", RegexOption.IGNORE_CASE)
        ).forEach { re ->
            re.find(t)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.takeIf { it > 0 }?.let {
                candidates += Quantity(Quantity.Kind.COUNT, it, "${it.toInt()} count", .9)
            }
        }

        if (Regex("\\b(pizza|pie|flatbread)\\b", RegexOption.IGNORE_CASE).containsMatchIn(t)) {
            val inch = Regex("\\b(\\d{1,2}(?:[.,]\\d+)?)\\s*(?:in(?:ch(?:es)?)?|\")\\b", RegexOption.IGNORE_CASE).find(t)?.groupValues?.get(1)?.let(::n)
            val cm = Regex("\\b(\\d{2,3}(?:[.,]\\d+)?)\\s*cm\\b", RegexOption.IGNORE_CASE).find(t)?.groupValues?.get(1)?.let(::n)
            val d = inch ?: cm?.div(2.54)
            if (d != null && d in 5.0..30.0) {
                candidates += Quantity(Quantity.Kind.PIZZA_AREA_SQIN, PI * (d / 2.0).pow(2), "${"%.1f".format(Locale.US, d)}\" pizza", .9, d)
            }
        }

        return candidates.maxWithOrNull(compareBy<Quantity> {
            when (it.kind) {
                Quantity.Kind.MASS_G -> 4
                Quantity.Kind.VOLUME_ML -> 3
                Quantity.Kind.PIZZA_AREA_SQIN -> 2
                Quantity.Kind.COUNT -> 1
            }
        }.thenBy { it.confidence })
    }

    fun estimatePortion(text: String): PortionEstimate? {
        val t = normalize(text).lowercase(Locale.ROOT)
        var points: Double? = null
        var confidence = .45
        var basis = ""

        Regex("\\b(\\d{1,3})\\s*(?:piece|pieces|pc|pcs|wings?|nuggets?|tenders?|patties|tacos?|burgers?|sandwiches?|slices?)\\b", RegexOption.IGNORE_CASE).find(t)?.let {
            val count = it.groupValues[1].toDoubleOrNull()
            if (count != null && count in 1.0..100.0) {
                points = count; confidence = .72; basis = "${count.toInt()} food units"
            }
        }

        if (points == null) {
            when {
                Regex("\\btriple\\b").containsMatchIn(t) -> { points = 3.0; confidence = .66; basis = "triple" }
                Regex("\\bdouble\\b").containsMatchIn(t) -> { points = 2.0; confidence = .64; basis = "double" }
                Regex("\\bsingle\\b").containsMatchIn(t) -> { points = 1.0; confidence = .60; basis = "single" }
            }
        }

        val size = when {
            Regex("\\b(?:party|feast)\\b").containsMatchIn(t) -> 2.2 to "party"
            Regex("\\b(?:family|sharing)\\b").containsMatchIn(t) -> 1.8 to "family"
            Regex("\\b(?:extra[ -]?large|x[- ]?large|xl)\\b").containsMatchIn(t) -> 1.55 to "extra large"
            Regex("\\blarge\\b").containsMatchIn(t) -> 1.3 to "large"
            Regex("\\bmedium\\b").containsMatchIn(t) -> 1.0 to "medium"
            Regex("\\bsmall\\b").containsMatchIn(t) -> .8 to "small"
            Regex("\\b(?:kid|kids|junior)\\b").containsMatchIn(t) -> .65 to "kids/junior"
            else -> null
        }
        if (size != null) {
            points = (points ?: 1.0) * size.first
            confidence = maxOf(confidence, .55)
            basis = if (basis.isBlank()) size.second else "$basis + ${size.second}"
        }

        if (Regex("\\b(?:combo|meal)\\b").containsMatchIn(t)) {
            points = (points ?: 1.0) * 1.35
            confidence = maxOf(confidence, .50)
            basis = if (basis.isBlank()) "meal" else "$basis + meal"
        }
        return points?.let { PortionEstimate(it, confidence, basis) }
    }

    fun calories(text: String): Double? = calorieRegex.find(normalize(text))?.groupValues?.get(1)?.let(::n)?.takeIf { it > 0 }

    fun name(text: String): String {
        val lines = text.split(Regex("\\n+")).map(::normalize).filter { it.isNotBlank() }
        val bad = Regex("^(?:add|customize|popular|sponsored|deal|sale|save|free delivery|buy one|get one|from\\s+[$€£₹৳]|[$€£₹৳]\\s*\\d)", RegexOption.IGNORE_CASE)
        for (line in lines) {
            val noPrice = priceRegex.replace(line, "").trim()
            if (noPrice.length >= 2 && !bad.containsMatchIn(noPrice)) return noPrice.take(120)
        }
        return priceRegex.replace(normalize(text), "").trim().take(120).ifBlank { "Unnamed item" }
    }

    fun analyze(text: String, sourcePackage: String? = null): ValueItem? {
        val t = normalize(text)
        val allPrices = prices(t)
        val p = choosePrice(t, allPrices) ?: return null
        val promo = promotion(t, p.amount)
        val q = quantity(t)
        val cal = calories(t)
        val portionEstimate = estimatePortion(t)
        var conf = .67
        if (q != null) conf += .18 * q.confidence
        if (cal != null) conf += .08
        if (promo.type != "none") conf += .03
        return ValueItem(name(t), t, p.amount, p.currency, q, cal, portionEstimate, promo, conf.coerceAtMost(.99), sourcePackage)
    }

    fun canonicalName(name: String): String = priceRegex.replace(normalize(name).lowercase(Locale.ROOT), " ")
        .replace(Regex("\\b\\d+(?:[.,]\\d+)?\\s*(?:mg|g|grams?|kg|kilograms?|oz|ounces?|lb|lbs|pounds?|ml|milliliters?|l|litres?|liters?|fl\\s*oz|cal|kcal|calories?|ct|count|pieces?|pcs|pack|pk|units?|ea)\\b", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("\\b(?:bogo|sale|deal|add to cart|customize)\\b", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("[^a-z0-9]+"), " ")
        .replace(Regex("\\s+"), " ").trim()

    fun dedupe(items: Collection<ValueItem>): List<ValueItem> {
        val map = linkedMapOf<String, ValueItem>()
        for (item in items) {
            val q = item.quantity
            val qKey = q?.amountBase?.toInt() ?: 0
            val key = "${canonicalName(item.name)}|${"%.2f".format(Locale.US, item.price)}|${q?.kind}|$qKey|${item.promotion.type}"
            val old = map[key]
            if (old == null || item.confidence > old.confidence || item.rawText.length > old.rawText.length) map[key] = item
        }
        return map.values.toList()
    }

    fun rank(items: Collection<ValueItem>, requested: RankMode): List<RankedItem> {
        val clean = dedupe(items)
        val mode = if (requested == RankMode.SMART) smartMode(clean) else requested
        data class M(val item: ValueItem, val value: Double?, val lower: Boolean, val label: String)
        val rows = clean.map { item ->
            when (mode) {
                RankMode.MASS -> M(item, item.pricePerKg, true, item.pricePerKg?.let { "${money(it, item.currency)}/kg" } ?: "price only")
                RankMode.VOLUME -> M(item, item.pricePerL, true, item.pricePerL?.let { "${money(it, item.currency)}/L" } ?: "price only")
                RankMode.CALORIE -> M(item, item.caloriesPerDollar, false, item.caloriesPerDollar?.let { "${fmt(it, 1)} cal/$" } ?: "price only")
                RankMode.PIZZA -> M(item, item.pizzaAreaPerDollar, false, item.pizzaAreaPerDollar?.let { "${fmt(it, 1)} in²/$" } ?: "price only")
                RankMode.UNIT -> M(item, item.pricePerUnit, true, item.pricePerUnit?.let { "${money(it, item.currency)}/unit" } ?: "price only")
                RankMode.PORTION -> M(item, item.portionPointsPerDollar, false, item.portionPointsPerDollar?.let { "${fmt(it, 2)} est. portion/$" } ?: "price only")
                RankMode.SMART -> error("resolved above")
            }
        }.sortedWith { a, b ->
            when {
                a.value != null && b.value == null -> -1
                a.value == null && b.value != null -> 1
                a.value == null && b.value == null -> a.item.price.compareTo(b.item.price)
                else -> if (a.lower) a.value!!.compareTo(b.value!!) else b.value!!.compareTo(a.value!!)
            }
        }
        return rows.mapIndexed { i, m -> RankedItem(m.item, i + 1, mode, m.label) }
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
        return order.filter { (counts[it] ?: 0) >= 2 }.maxByOrNull { counts[it] ?: 0 }
            ?: order.maxByOrNull { counts[it] ?: 0 } ?: RankMode.CALORIE
    }

    fun money(value: Double, currency: String): String {
        val symbol = when (currency) { "EUR" -> "€"; "GBP" -> "£"; "INR" -> "₹"; "BDT" -> "৳"; else -> "$" }
        return symbol + String.format(Locale.US, "%.2f", value)
    }

    fun fmt(value: Double, digits: Int): String = String.format(Locale.US, "%.${digits}f", value)
}
