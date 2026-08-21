package com.valuepilot.app

import java.util.Locale

data class ProductStoreStats(
    val itemCount: Int,
    val knownCardCount: Int,
    val pendingCardCount: Int,
    val ignoredUnchangedCards: Long,
    val appliedChanges: Long,
    val sessionResets: Long
)

data class StoreApplyResult(
    val changed: Boolean,
    val insertedOrUpdated: Int,
    val removed: Int,
    val total: Int
)

class IncrementalProductStore(private val maxItems: Int = 1_000) : ProductRepository {
    private val items = linkedMapOf<String, ValueItem>()
    private val cardVersions = linkedMapOf<String, String>()
    private val pendingVersions = mutableMapOf<String, String>()
    private val cardToItemKey = mutableMapOf<String, String>()
    private var sessionId: String? = null
    private var ignoredUnchangedCards = 0L
    private var appliedChanges = 0L
    private var sessionResets = 0L

    override fun beginContext(context: SearchContext): Boolean {
        if (sessionId == context.sessionId) return false
        if (sessionId != null) sessionResets++
        sessionId = context.sessionId
        items.clear()
        cardVersions.clear()
        pendingVersions.clear()
        cardToItemKey.clear()
        return true
    }

    override fun reserveChanged(observations: Collection<ProductCardSnapshot>): List<ProductCardSnapshot> {
        val cards = observations
        val selected = ArrayList<ProductCardSnapshot>(cards.size)
        val seenInBatch = mutableSetOf<String>()
        for (card in cards) {
            val pendingKey = "${card.cardFingerprint}|${card.contentFingerprint}"
            if (!seenInBatch.add(pendingKey)) continue
            val known = cardVersions[card.cardFingerprint]
            val pending = pendingVersions[card.cardFingerprint]
            if (known == card.contentFingerprint || pending == card.contentFingerprint) {
                ignoredUnchangedCards++
                continue
            }
            pendingVersions[card.cardFingerprint] = card.contentFingerprint
            selected += card
        }
        return selected
    }

    override fun apply(parsedObservations: Collection<ParsedCard>): StoreApplyResult {
        val parsedCards = parsedObservations
        var inserted = 0
        var removed = 0
        for (parsed in parsedCards) {
            if (pendingVersions[parsed.cardFingerprint] != parsed.contentFingerprint) continue
            pendingVersions.remove(parsed.cardFingerprint)
            cardVersions[parsed.cardFingerprint] = parsed.contentFingerprint

            val previousKey = cardToItemKey.remove(parsed.cardFingerprint)
            if (previousKey != null && items.remove(previousKey) != null) removed++
            val item = parsed.item ?: continue
            val key = itemIdentity(item)
            val existing = items[key]
            if (existing == null || item.confidence > existing.confidence || item.rawText.length > existing.rawText.length) {
                items[key] = item
                cardToItemKey[parsed.cardFingerprint] = key
                inserted++
                appliedChanges++
            } else {
                cardToItemKey[parsed.cardFingerprint] = key
            }
        }
        trimToLimit()
        return StoreApplyResult(inserted > 0 || removed > 0, inserted, removed, items.size)
    }

    override fun release(observations: Collection<ProductCardSnapshot>) {
        val cards = observations
        for (card in cards) {
            if (pendingVersions[card.cardFingerprint] == card.contentFingerprint) pendingVersions.remove(card.cardFingerprint)
        }
    }

    override fun clear() {
        items.clear()
        cardVersions.clear()
        pendingVersions.clear()
        cardToItemKey.clear()
    }

    override fun snapshot(): List<ValueItem> = items.values.toList()

    override fun size(): Int = items.size

    override fun stats(): ProductStoreStats = ProductStoreStats(
        itemCount = items.size,
        knownCardCount = cardVersions.size,
        pendingCardCount = pendingVersions.size,
        ignoredUnchangedCards = ignoredUnchangedCards,
        appliedChanges = appliedChanges,
        sessionResets = sessionResets
    )

    private fun trimToLimit() {
        while (items.size > maxItems) {
            val oldestKey = items.keys.firstOrNull() ?: break
            items.remove(oldestKey)
            val affectedCards = cardToItemKey.filterValues { it == oldestKey }.keys
            for (card in affectedCards) {
                cardToItemKey.remove(card)
                cardVersions.remove(card)
            }
        }
    }

    companion object {
        fun itemIdentity(item: ValueItem): String {
            val quantity = item.quantity
            val amount = quantity?.amountBase?.let { String.format(Locale.US, "%.3f", it) } ?: "none"
            return listOf(
                ValueEngine.canonicalName(item.name),
                String.format(Locale.US, "%.2f", item.offer.currentPrice),
                item.offer.memberPrice?.let { String.format(Locale.US, "%.2f", it) } ?: "none",
                quantity?.kind?.name ?: "none",
                amount,
                item.promotion.type,
                item.sourcePackage.orEmpty(),
                item.searchSessionId.orEmpty()
            ).joinToString("|")
        }
    }
}
