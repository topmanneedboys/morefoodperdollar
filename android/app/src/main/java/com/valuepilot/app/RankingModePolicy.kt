package com.valuepilot.app

/** Business rules for which ranking modes are meaningful in the active result set. */
object RankingModePolicy {
    private val meatQuery = Regex("meat|beef|chicken|pork|steak")

    fun availableModes(items: Sequence<ValueItem>, query: String?): Set<RankMode> {
        val normalizedQuery = query.orEmpty().lowercase()
        var hasMass = false
        var hasVolume = false
        var hasCount = false
        var hasCalories = false
        var hasPortion = false
        var hasMeat = false
        var hasPizza = false
        for (item in items) {
            when (item.quantity?.kind) {
                Quantity.Kind.MASS_G -> hasMass = true
                Quantity.Kind.VOLUME_ML -> hasVolume = true
                Quantity.Kind.COUNT -> hasCount = true
                Quantity.Kind.PIZZA_AREA_SQIN -> hasPizza = true
                null -> Unit
            }
            if (item.calories != null) hasCalories = true
            if (item.portion != null) hasPortion = true
            if (item.meatPointsPerDollar != null) hasMeat = true
        }
        return buildSet {
            add(RankMode.SMART)
            if (hasMass || meatQuery.containsMatchIn(normalizedQuery)) add(RankMode.MASS)
            if (hasVolume || normalizedQuery.contains("milk")) add(RankMode.VOLUME)
            if (hasCount || normalizedQuery.contains("egg")) add(RankMode.UNIT)
            if (hasCalories) add(RankMode.CALORIE)
            if (hasPortion) add(RankMode.PORTION)
            if (hasMeat) add(RankMode.MEAT)
            if (hasPizza || normalizedQuery.contains("pizza")) add(RankMode.PIZZA)
        }
    }

    fun resolveRequested(requested: RankMode, items: Collection<ValueItem>, query: String?): RankMode =
        if (requested == RankMode.SMART || requested in availableModes(items.asSequence(), query)) requested else RankMode.SMART
}
