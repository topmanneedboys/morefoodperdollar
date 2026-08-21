package com.valuepilot.app

import java.text.Normalizer
import java.util.Locale

data class RelevanceDecision(
    val include: Boolean,
    val score: Double,
    val reason: String
)

object SearchRelevance {
    private enum class Family { EGGS, MILK, BANANA, CUCUMBER, BREAD, MEAT, PIZZA, UNKNOWN }

    private val aliases = mapOf(
        Family.EGGS to setOf("egg", "eggs", "eggwhite", "eggwhites"),
        Family.MILK to setOf("milk", "nextmilk", "buttermilk", "oatmilk", "soymilk", "almondmilk", "cashewmilk"),
        Family.BANANA to setOf("banana", "bananas", "plantain", "plantains"),
        Family.CUCUMBER to setOf("cucumber", "cucumbers", "gherkin", "gherkins"),
        Family.BREAD to setOf("bread", "loaf", "loaves", "baguette", "baguettes"),
        Family.MEAT to setOf("meat", "beef", "chicken", "pork", "turkey", "lamb", "steak"),
        Family.PIZZA to setOf("pizza", "pizzas", "flatbread", "pie")
    )
    private val stopWords = setOf("a", "an", "and", "the", "for", "with", "fresh", "large", "small", "medium", "pack")
    private val plantMilkWords = setOf("oat", "soy", "almond", "cashew", "coconut", "plant", "dairy", "lactose", "nextmilk")

    fun evaluate(query: String?, productName: String, ai: LocalAiPrediction = LocalAiPrediction()): RelevanceDecision {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return RelevanceDecision(true, 1.0, "no query")

        val normalizedName = normalize(productName)
        if (normalizedName.isBlank()) return RelevanceDecision(false, 0.0, "missing product name")
        val queryTokens = tokens(normalizedQuery)
        val productTokens = tokens(normalizedName)
        if (queryTokens.isEmpty()) return RelevanceDecision(true, 1.0, "no meaningful query token")

        val queryFamily = family(queryTokens, normalizedQuery)
        val productFamily = family(productTokens, normalizedName)
        if (queryFamily != Family.UNKNOWN && productFamily != Family.UNKNOWN && queryFamily != productFamily) {
            return RelevanceDecision(false, 0.0, "category mismatch")
        }

        val exactHits = queryTokens.count { queryToken -> productTokens.any { equivalent(queryToken, it) } }
        val coverage = exactHits.toDouble() / queryTokens.size.coerceAtLeast(1)
        if (coverage >= 0.999) return RelevanceDecision(true, 1.0, "query tokens matched")

        if (queryFamily != Family.UNKNOWN) {
            if (queryFamily == productFamily) return RelevanceDecision(true, maxOf(.78, coverage), "category matched")
            if (queryFamily == Family.MILK && (
                    productTokens.any(plantMilkWords::contains) &&
                        productTokens.any { it in setOf("beverage", "drink", "alternative") }
                    )
            ) {
                return RelevanceDecision(true, .72, "milk alternative")
            }
            if (queryFamily == Family.MILK && ai.available && ai.label.contains("dairy", true) && ai.confidence >= .4) {
                return RelevanceDecision(true, .68, "local category evidence")
            }
            return RelevanceDecision(false, coverage, "required category absent")
        }

        val union = (queryTokens + productTokens).toSet().size.coerceAtLeast(1)
        val intersection = queryTokens.count { queryToken -> productTokens.any { equivalent(queryToken, it) } }
        val jaccard = intersection.toDouble() / union
        val containsPhrase = normalizedName.contains(normalizedQuery) || normalizedQuery.contains(normalizedName)
        val score = maxOf(coverage * .8, jaccard, if (containsPhrase) .82 else 0.0)
        return RelevanceDecision(score >= .42, score, if (score >= .42) "lexical match" else "insufficient query match")
    }

    fun matches(query: String?, item: ValueItem): Boolean = evaluate(query, item.name, item.ai).include

    private fun family(tokens: Set<String>, normalized: String): Family {
        for ((family, words) in aliases) {
            if (tokens.any { token -> words.any { equivalent(token, it) } }) return family
            val compact = normalized.replace(" ", "")
            if (words.any(compact::contains)) return family
        }
        return Family.UNKNOWN
    }

    private fun tokens(value: String): Set<String> = value.split(' ')
        .asSequence()
        .map(::stem)
        .filter { it.length > 1 && it !in stopWords }
        .toCollection(linkedSetOf())

    private fun equivalent(left: String, right: String): Boolean {
        val a = stem(left)
        val b = stem(right)
        return a == b || (a.length >= 5 && b.length >= 5 && (a.startsWith(b) || b.startsWith(a)))
    }

    private fun stem(value: String): String = when {
        value.endsWith("ies") && value.length > 4 -> value.dropLast(3) + "y"
        value.endsWith("es") && value.length > 4 -> value.dropLast(2)
        value.endsWith('s') && value.length > 3 -> value.dropLast(1)
        else -> value
    }

    private fun normalize(value: String?): String = Normalizer.normalize(value.orEmpty(), Normalizer.Form.NFKD)
        .replace(Regex("[\\u0300-\\u036f]"), "")
        .lowercase(Locale.ROOT)
        .replace('&', ' ')
        .replace(Regex("[^a-z0-9]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
