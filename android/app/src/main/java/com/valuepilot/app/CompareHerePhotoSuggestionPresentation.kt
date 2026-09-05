package com.valuepilot.app

/**
 * Display-only signals for one OCR suggestion in the photo review dialog.
 *
 * The deterministic parser is reused only to explain what the current text might contain. The
 * returned values never become comparison observations and never bypass the editable-entry,
 * like-for-like, currency, quantity, or price checks. [editorPrefill] is an optional ordinary-text
 * draft offered only after an explicit shopper action; every label and draft remains unconfirmed.
 */
internal data class CompareHerePhotoSuggestionPresentation(
    val displayLabel: String,
    val nameSuggestion: String?,
    val priceSignal: String?,
    val quantitySignal: String?,
    val editorPrefill: String?,
    val reviewNotice: String
) {
    init {
        require(displayLabel.isNotBlank())
        require(displayLabel.length <= MAX_DISPLAY_LABEL_CHARS)
        require(nameSuggestion == null || nameSuggestion.length <= MAX_NAME_CHARS)
        require(priceSignal == null || priceSignal.length <= MAX_PRICE_SIGNAL_CHARS)
        require(quantitySignal == null || quantitySignal.length <= MAX_QUANTITY_SIGNAL_CHARS)
        require(
            editorPrefill == null ||
                editorPrefill.length <= CompareHereManualProductDraft.MAX_BLOCK_CHARS
        )
        require(editorPrefill == null || editorPrefill.isNotBlank())
        require(editorPrefill == null || editorPrefill.none { it.isISOControl() && it !in "\r\n\t" })
        require(reviewNotice.isNotBlank())
        require(reviewNotice.startsWith(REVIEW_PREFIX))
    }

    companion object {
        internal const val MAX_DISPLAY_LABEL_CHARS = 320
        private const val MAX_NAME_CHARS = 120
        private const val MAX_PRICE_SIGNAL_CHARS = 120
        private const val MAX_QUANTITY_SIGNAL_CHARS = 80
        private const val REVIEW_PREFIX = "Review only —"
    }
}

/**
 * Bounded, deterministic projection of parser signals for a shopper's explicit OCR review step.
 */
internal object CompareHerePhotoSuggestionPresentationFactory {
    private val ISO_CURRENCY = Regex("[A-Z]{3}")

    fun forCandidate(rawText: String): CompareHerePhotoSuggestionPresentation {
        val normalized = ValueEngine.normalize(rawText)
        val displaySource = safeDisplaySource(normalized)
        val prices = ValueEngine.prices(normalized)
        val currencies = prices.map { it.currency }.toSet()
        val concreteCurrency = currencies.singleOrNull()?.takeIf { ISO_CURRENCY.matches(it) }
        val nameSuggestion =
            safeDisplaySource(ValueEngine.name(normalized))
                .takeIf { it.isNotBlank() && !it.equals("Unnamed item", ignoreCase = true) }
                ?.take(120)
        val priceSignal = priceSignal(prices, concreteCurrency)
        val quantity = ValueEngine.quantity(normalized)
        val exactQuantity = quantity?.takeIf(::isExactQuantity)
        val quantitySignal =
            quantity?.let { parsed ->
                if (isExactQuantity(parsed)) {
                    parsed.display.take(80)
                } else {
                    "${parsed.display.take(64)} — verify"
                }
            }

        val exactPrice =
            prices
                .singleOrNull()
                ?.takeIf { concreteCurrency != null }
        val editorPrefill =
            buildEditorPrefill(
                name = nameSuggestion,
                price = exactPrice,
                quantity = exactQuantity,
                promotion = exactPrice?.let { ValueEngine.promotion(normalized, it.amount) }
            )

        val reviewReasons = mutableListOf<String>()
        if (nameSuggestion == null) reviewReasons += "product name"
        if (prices.size != 1 || concreteCurrency == null) {
            reviewReasons += "current price and currency"
        }
        if (exactQuantity == null) reviewReasons += "package size"
        val reviewNotice =
            if (reviewReasons.isEmpty()) {
                "Review only — confirm every detected detail before comparing"
            } else {
                "Review only — confirm ${reviewReasons.joinToString(", ")}"
            }

        val signals = mutableListOf<String>()
        nameSuggestion?.let { signals += "name: $it" }
        priceSignal?.let { signals += "price text: $it" }
        quantitySignal?.let { signals += "package: $it" }
        val signalText =
            signals.takeIf { it.isNotEmpty() }?.joinToString(" · ")
                ?: "No structured details recognized"
        val displayLabel =
            truncate(
                "$reviewNotice\n$signalText\nOCR text: ${displaySource.ifBlank { "(empty)" }}",
                CompareHerePhotoSuggestionPresentation.MAX_DISPLAY_LABEL_CHARS
            )

        return CompareHerePhotoSuggestionPresentation(
            displayLabel = displayLabel,
            nameSuggestion = nameSuggestion,
            priceSignal = priceSignal,
            quantitySignal = quantitySignal,
            editorPrefill = editorPrefill,
            reviewNotice = reviewNotice
        )
    }

    /**
     * Builds an explicitly user-selected editable draft only from a complete, unambiguous set
     * of parser signals. The result is still ordinary text: the exact Compare Here adapter must
     * validate it after the shopper reviews it. Unsupported promotion shapes intentionally stay
     * on the raw-OCR path so a prefill cannot erase price-affecting conditions.
     */
    private fun buildEditorPrefill(
        name: String?,
        price: ValueEngine.Price?,
        quantity: Quantity?,
        promotion: Promotion?
    ): String? {
        if (name == null || price == null || quantity == null) return null

        val promotionText =
            when (promotion?.type) {
                null, "none" -> null
                "percent-off-shown", "free-delivery" ->
                    promotion.label.takeIf(String::isNotBlank)?.let(::safeDisplaySource)
                // The parser's exact BOGO grammar accepts words, while its display label uses
                // numerals. Keep the canonical input form that the existing adapter can replay.
                "bogo" -> "Buy one get one free"
                else -> return null
            }

        return listOfNotNull(
                name,
                safeDisplaySource(price.raw),
                safeDisplaySource(quantity.display),
                promotionText
            )
            .joinToString("\n")
            .takeIf {
                it.isNotBlank() &&
                    it.length <= CompareHereManualProductDraft.MAX_BLOCK_CHARS
            }
    }

    private fun priceSignal(
        prices: List<ValueEngine.Price>,
        concreteCurrency: String?
    ): String? {
        if (prices.isEmpty()) return null
        val bounded = prices.take(3).joinToString(" / ") { it.raw }
        return when {
            prices.size == 1 && concreteCurrency != null -> bounded
            prices.size == 1 -> "$bounded — currency needs review"
            prices.size > 3 -> "$bounded / … — choose current/member price"
            else -> "$bounded — choose current/member price"
        }.take(120)
    }

    private fun isExactQuantity(quantity: Quantity): Boolean =
        quantity.kind != Quantity.Kind.PIZZA_AREA_SQIN &&
        quantity.confidence >= 0.9 &&
            !quantity.display.contains(" avg", ignoreCase = true)

    private fun safeDisplaySource(value: String): String =
        value
            .replace(Regex("\\s+"), " ")
            .filterNot { it.isISOControl() }
            .trim()

    private fun truncate(value: String, maxChars: Int): String {
        if (value.length <= maxChars) return value
        return value.take(maxChars - 1).trimEnd().plus("…")
    }
}
