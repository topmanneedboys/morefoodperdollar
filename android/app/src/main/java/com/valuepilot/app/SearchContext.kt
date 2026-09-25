package com.valuepilot.app

import java.util.Locale

data class ContextSignal(
    val text: String,
    val contentDescription: String = "",
    val viewId: String? = null,
    val className: String = "",
    val editable: Boolean = false,
    val focused: Boolean = false,
    val heading: Boolean = false,
    val depth: Int = 0
)

data class ContextObservation(
    val packageName: String,
    val platform: String,
    val query: String?,
    val storeIdentity: String?,
    val pageHint: String?,
    val observedAtMillis: Long
)

data class SearchContext(
    val platform: String,
    val packageName: String,
    val storeIdentity: String?,
    val query: String?,
    val queryFingerprint: String,
    val pageFingerprint: String,
    val sessionId: String,
    val startedAtMillis: Long
) {
    val displayQuery: String
        get() = query?.trim()?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            ?: storeIdentity
            ?: platform
}

data class ContextTransition(
    val context: SearchContext,
    val changed: Boolean,
    val reason: String
)

class SearchSessionManager {
    private var current: SearchContext? = null
    private var sequence = 0L

    fun current(): SearchContext? = current

    fun observe(observation: ContextObservation): ContextTransition {
        val previous = current
        val query = SearchContextDetector.normalizeQuery(observation.query)
        val queryFingerprint = SearchContextDetector.fingerprint(query)
        val store = SearchContextDetector.cleanStore(observation.storeIdentity)
        val observedPageFingerprint = SearchContextDetector.fingerprint(
            listOf(observation.packageName, store, query, observation.pageHint)
                .filterNotNull()
                .joinToString("|")
        )
        val pageFingerprint = if (
            previous != null &&
            previous.packageName == observation.packageName &&
            observation.pageHint == null
        ) {
            previous.pageFingerprint
        } else {
            observedPageFingerprint
        }

        val reason = when {
            previous == null -> "first context"
            previous.packageName != observation.packageName -> "platform changed"
            SearchContextDetector.fingerprint(previous.storeIdentity) != SearchContextDetector.fingerprint(store) &&
                previous.storeIdentity != null && store != null -> "store changed"
            previous.queryFingerprint != queryFingerprint && (previous.query != null || query != null) -> "query changed"
            previous.query == null && query == null && previous.pageFingerprint != pageFingerprint &&
                observation.pageHint != null -> "page changed"
            else -> "same context"
        }
        val changed = reason != "same context"

        if (changed) {
            sequence++
            val started = observation.observedAtMillis
            current = SearchContext(
                platform = observation.platform,
                packageName = observation.packageName,
                storeIdentity = store,
                query = query,
                queryFingerprint = queryFingerprint,
                pageFingerprint = pageFingerprint,
                sessionId = StableIds.text("${observation.packageName}|$store|$queryFingerprint|$pageFingerprint|$started|$sequence"),
                startedAtMillis = started
            )
        } else if (previous != null && (previous.query == null && query != null || previous.storeIdentity == null && store != null)) {
            current = previous.copy(
                query = query ?: previous.query,
                queryFingerprint = if (query != null) queryFingerprint else previous.queryFingerprint,
                storeIdentity = store ?: previous.storeIdentity,
                pageFingerprint = pageFingerprint
            )
        }

        return ContextTransition(requireNotNull(current), changed, reason)
    }

    fun observeExplicitQuery(
        packageName: String,
        query: String?,
        observedAtMillis: Long,
        platform: String = SearchContextDetector.platformFor(packageName)
    ): ContextTransition {
        val previous = current
        return observe(
            ContextObservation(
                packageName = packageName,
                platform = platform,
                query = query,
                storeIdentity = previous?.takeIf { it.packageName == packageName }?.storeIdentity,
                pageHint = "search",
                observedAtMillis = observedAtMillis
            )
        )
    }

    fun isCurrent(sessionId: String?): Boolean = sessionId != null && current?.sessionId == sessionId

    fun clear() {
        current = null
    }
}

object SearchContextDetector {
    private val placeholder = Regex(
        "^(?:search|search here|search for items?|search products?|search stores?|what are you looking for\\??)$",
        RegexOption.IGNORE_CASE
    )
    private val queryHeading = Regex(
        "(?:search\\s+results?(?:\\s+for)?|results?\\s+for|showing\\s+results?\\s+for)\\s*[\"'“”:]?\\s*([^\\n\"'“”]{1,80})",
        RegexOption.IGNORE_CASE
    )
    fun detect(packageName: String, signals: Collection<ContextSignal>, observedAtMillis: Long): ContextObservation {
        data class Candidate(val value: String, val score: Int)

        val queryCandidates = mutableListOf<Candidate>()
        val storeCandidates = mutableListOf<Candidate>()
        val pageCandidates = mutableListOf<Candidate>()

        for (signal in signals) {
            val text = signal.text.trim()
            val description = signal.contentDescription.trim()
            val id = signal.viewId.orEmpty().lowercase(Locale.ROOT)
            val className = signal.className.lowercase(Locale.ROOT)
            val searchIdentity = looksLikeSearchInput(id, className, description, signal.editable)
            val focusedSearchField = signal.editable && signal.focused && signal.depth <= 5 &&
                className.contains("edittext")

            if (signal.depth <= 6) queryHeading.find(text)?.groupValues?.getOrNull(1)?.let { raw ->
                normalizeQuery(raw)?.let { queryCandidates += Candidate(it, 12 - signal.depth.coerceAtMost(5)) }
            }
            if (searchIdentity || focusedSearchField) {
                normalizeQuery(text)?.let {
                    var score = 7
                    if (signal.editable) score += 3
                    if (signal.focused) score += 3
                    if (id.contains("search") || id.contains("query")) score += 3
                    queryCandidates += Candidate(it, score)
                }
            }

            if ((id.contains("store") || id.contains("merchant") || id.contains("restaurant")) && text.length in 2..80) {
                cleanStore(text)?.let { storeCandidates += Candidate(it, 10 - signal.depth.coerceAtMost(4)) }
            }
            val highLevelHeading = signal.heading && signal.depth <= 3
            val highLevelTitle = signal.depth <= 4 &&
                (id.contains("toolbar") || id.contains("page_title") || id.contains("screen_title"))
            if ((highLevelHeading || highLevelTitle) && text.length in 2..80 && !priceLike(text) && !placeholder.matches(text)) {
                pageCandidates += Candidate(text, 8 - signal.depth.coerceAtMost(3))
            }
        }

        val query = queryCandidates.maxWithOrNull(compareBy<Candidate> { it.score }.thenBy { it.value.length })?.value
        val store = storeCandidates.maxWithOrNull(compareBy<Candidate> { it.score }.thenBy { it.value.length })?.value
        val page = if (query != null) {
            "search"
        } else {
            pageCandidates.maxWithOrNull(compareBy<Candidate> { it.score }.thenBy { it.value.length })?.value
        }

        return ContextObservation(
            packageName = packageName,
            platform = platformFor(packageName),
            query = query,
            storeIdentity = store,
            pageHint = page,
            observedAtMillis = observedAtMillis
        )
    }

    fun queryFromEvent(text: Collection<CharSequence?>, contentDescription: CharSequence?): String? {
        val candidates = buildList {
            text.mapNotNullTo(this) { it?.toString() }
            contentDescription?.toString()?.let(::add)
        }
        return candidates.asSequence().mapNotNull(::normalizeQuery).lastOrNull()
    }

    fun looksLikeSearchInput(
        viewId: String?,
        className: String?,
        contentDescription: String?,
        editable: Boolean
    ): Boolean {
        val id = viewId.orEmpty().lowercase(Locale.ROOT)
        val klass = className.orEmpty().lowercase(Locale.ROOT)
        val description = contentDescription.orEmpty().trim()
        return id.contains("search") || id.contains("query") || klass.contains("searchview") ||
            description.startsWith("search", true) ||
            (editable && id.contains("search_input"))
    }

    fun normalizeQuery(value: String?): String? {
        var query = value.orEmpty().replace('\u00a0', ' ').trim()
        query = queryHeading.find(query)?.groupValues?.getOrNull(1) ?: query
        query = query.trim(' ', '\t', '\n', '\r', '"', '\'', '“', '”', ':')
            .replace(Regex("\\s+"), " ")
            .replace(
                Regex("\\s*(?:[·|]|[-–—]|\\()?\\s*\\d{1,5}\\s+(?:matches?|results?|items?)\\)?\\s*$", RegexOption.IGNORE_CASE),
                ""
            )
            .trim()
        if (query.length !in 1..80 || placeholder.matches(query) || priceLike(query)) return null
        if (!Regex("\\p{L}").containsMatchIn(query)) return null
        return query
    }

    fun cleanStore(value: String?): String? {
        val clean = value.orEmpty().replace('\u00a0', ' ').replace(Regex("\\s+"), " ").trim()
        if (clean.length !in 2..80 || priceLike(clean) || placeholder.matches(clean)) return null
        return clean
    }

    fun platformFor(packageName: String): String = when {
        packageName.contains("uber", true) && packageName.contains("eat", true) -> "Uber Eats"
        packageName.contains("doordash", true) -> "DoorDash"
        packageName.contains("instacart", true) -> "Instacart"
        packageName.contains("skipthedishes", true) || packageName.contains("justeat", true) -> "Skip"
        packageName.contains("walmart", true) -> "Walmart"
        else -> packageName.substringAfterLast('.').replaceFirstChar { it.titlecase() }
    }

    fun fingerprint(value: String?): String {
        val normalized = JvmTextCanonicalizer.identity(value)
        return if (normalized.isBlank()) "none" else StableIds.text(normalized)
    }

    private fun priceLike(value: String): Boolean = Regex("[$€£₹৳]\\s*\\d|\\b\\d+[.,]\\d{2}\\b").containsMatchIn(value)
}

object StableIds {
    fun long(value: String): Long {
        var hash = -0x340d631b7bdddcdbL
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            hash = hash xor (byte.toLong() and 0xff)
            hash *= 0x100000001b3L
        }
        return hash
    }

    fun text(value: String): String = java.lang.Long.toUnsignedString(long(value), 36)
}
