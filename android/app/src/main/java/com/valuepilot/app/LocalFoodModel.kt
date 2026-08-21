package com.valuepilot.app

import android.content.Context
import com.valuepilot.core.SemanticEnricher
import com.valuepilot.core.SemanticSignals
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Tiny, deterministic, on-device semantic model shared with the browser extension.
 * It classifies food categories and supplies bounded relative signals only; exact
 * weight, volume, count, calorie, and promotion math always remains authoritative.
 */
typealias LocalAiPrediction = SemanticSignals

object LocalModelSemanticEnricher : SemanticEnricher {
    override fun enrich(rawText: String): SemanticSignals = LocalFoodModel.predict(rawText)
}

object LocalFoodModel {
    private data class ClassConfig(
        val label: String,
        val food: Boolean,
        val portionEligible: Boolean,
        val basePortionPoints: Double,
        val meatRatio: Double,
        val logPrior: Double,
        val unknownLogProbability: Double,
        val tokens: Map<String, Double>
    )

    private data class Model(
        val version: String,
        val temperature: Double,
        val vocabulary: Set<String>,
        val classes: Map<String, ClassConfig>
    )

    private val stopWords = setOf("and", "with", "the", "for", "from", "style", "fresh", "classic", "large", "small", "medium")
    @Volatile private var model: Model? = null

    @Synchronized
    fun initialize(context: Context) {
        if (model != null) return
        runCatching {
            context.applicationContext.assets.open("local_ai_model.json").bufferedReader(Charsets.UTF_8).use { load(it.readText()) }
        }
    }

    @Synchronized
    internal fun initializeFromJson(json: String) {
        load(json)
    }

    private fun load(json: String) {
        val root = JSONObject(json)
        val vocabularyJson = root.getJSONArray("vocabulary")
        val vocabulary = buildSet {
            for (index in 0 until vocabularyJson.length()) add(vocabularyJson.getString(index))
        }
        val classesJson = root.getJSONObject("classes")
        val classes = linkedMapOf<String, ClassConfig>()
        val classNames = classesJson.keys().asSequence().toList().sorted()
        for (name in classNames) {
            val raw = classesJson.getJSONObject(name)
            val tokenJson = raw.getJSONObject("tokens")
            val tokens = linkedMapOf<String, Double>()
            val tokenNames = tokenJson.keys().asSequence().toList().sorted()
            for (token in tokenNames) tokens[token] = tokenJson.getDouble(token)
            classes[name] = ClassConfig(
                label = raw.getString("label"),
                food = raw.getBoolean("food"),
                portionEligible = raw.getBoolean("portionEligible"),
                basePortionPoints = raw.optDouble("basePortionPoints", 0.0),
                meatRatio = raw.optDouble("meatRatio", 0.0),
                logPrior = raw.getDouble("logPrior"),
                unknownLogProbability = raw.getDouble("unknownLogProbability"),
                tokens = tokens
            )
        }
        model = Model(
            version = root.getString("modelVersion"),
            temperature = root.optDouble("temperature", 1.0).takeIf { it > 0 } ?: 1.0,
            vocabulary = vocabulary,
            classes = classes
        )
    }

    fun predict(value: String?): LocalAiPrediction {
        val active = model ?: return LocalAiPrediction()
        val features = tokenize(value).filter(active.vocabulary::contains)
        if (features.isEmpty()) return LocalAiPrediction(available = true, modelVersion = active.version)

        data class Row(val name: String, val config: ClassConfig, val score: Double, var probability: Double = 0.0)
        val rows = active.classes.map { (name, config) ->
            var score = config.logPrior
            for (token in features) score += config.tokens[token] ?: config.unknownLogProbability
            Row(name, config, score)
        }
        val maxScore = rows.maxOf { it.score / active.temperature }
        var total = 0.0
        for (row in rows) {
            row.probability = exp(row.score / active.temperature - maxScore)
            total += row.probability
        }
        for (row in rows) row.probability /= total.takeIf { it > 0 } ?: 1.0
        val sorted = rows.sortedWith(compareByDescending<Row> { it.probability }.thenBy { it.name })
        val top = sorted.first()
        val evidenceStrength = (0.52 + features.size * 0.16).coerceAtMost(1.0)
        val calibrated = top.probability * evidenceStrength
        val strongestFood = sorted.filter { it.config.food }.maxOfOrNull { it.probability } ?: 0.0
        val nonFood = sorted.firstOrNull { it.name == "nonfood" }?.probability ?: 0.0
        val foodProbability = strongestFood / (strongestFood + nonFood).coerceAtLeast(1e-9) * evidenceStrength
        val porkProbability = (sorted.firstOrNull { it.name == "pork" }?.probability ?: 0.0) * evidenceStrength
        val meatRatio = if (top.name == "nonfood") 0.0 else top.config.meatRatio * (0.55 + calibrated).coerceAtMost(1.0)
        val evidence = features
            .filter(top.config.tokens::containsKey)
            .sortedByDescending { (top.config.tokens[it] ?: top.config.unknownLogProbability) - top.config.unknownLogProbability }
            .take(4)
            .map { it.drop(2).replace('_', ' ') }
        val portionEligible = top.config.portionEligible && calibrated >= 0.26

        return LocalAiPrediction(
            available = true,
            modelVersion = active.version,
            category = top.name,
            label = top.config.label,
            confidence = rounded(calibrated),
            foodConfidence = rounded(foodProbability),
            porkConfidence = rounded(porkProbability),
            meatRatio = rounded(meatRatio),
            portionEligible = portionEligible,
            basePortionPoints = top.config.basePortionPoints.takeIf { portionEligible },
            evidence = evidence
        )
    }

    internal fun tokenize(value: String?): List<String> {
        val normalized = Normalizer.normalize(value.orEmpty(), Normalizer.Form.NFKD)
            .replace(Regex("[\\u0300-\\u036f]"), "")
            .lowercase(Locale.ROOT)
            .replace("&", " and ")
        val words = Regex("[a-z][a-z'-]{1,30}").findAll(normalized)
            .map { it.value.trim('\'') }
            .filter { it.length > 1 && it !in stopWords }
            .toList()
        val features = linkedSetOf<String>()
        words.forEach { features += "u:$it" }
        for (index in 0 until words.lastIndex) features += "b:${words[index]}_${words[index + 1]}"
        return features.toList()
    }

    private fun rounded(value: Double): Double = (value * 10_000.0).roundToInt() / 10_000.0
}
