package com.valuepilot.app

import com.valuepilot.core.Money
import com.valuepilot.core.PracticalShoppingPlanner
import com.valuepilot.core.PracticalShoppingPolicy
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingPlanEvidenceSummary
import com.valuepilot.core.ShoppingRequest
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.ShoppingTravel
import com.valuepilot.core.SingleStorePlanCandidate
import com.valuepilot.core.TwoStorePlanCandidate

private const val MAX_DEMO_QUERY_LENGTH = 240
private const val MAX_STORED_DEMO_QUERY_LENGTH = MAX_DEMO_QUERY_LENGTH + 1
private const val MAX_DEMO_INTENTS = 32

/**
 * Tiny offline fixture used only to prove the Practical Shopping consumer flow.
 *
 * Every store, product price and route below is fictional. This object must never
 * be treated as a production provider, retailer feed, current-price source or
 * evidence of real-world availability.
 */
object LocalSamplePracticalShoppingDemo {

    private data class SampleItem(
        val key: ShoppingItemKey,
        val displayName: String,
        val detail: String,
        val aliases: List<List<String>>
    )

    private data class SampleStore(
        val key: ShoppingStoreKey,
        val displayName: String,
        val travelFromUser: ShoppingTravel,
        val prices: Map<ShoppingItemKey, Money>
    )

    private data class SamplePairTravel(
        val base: ShoppingStoreKey,
        val added: ShoppingStoreKey,
        val additionalTravel: ShoppingTravel
    )

    enum class ChickenChoice(val label: String) {
        BREAST("Breast"),
        THIGHS("Thighs"),
        DRUMSTICKS("Drumsticks"),
        WHOLE("Whole chicken"),
        GROUND("Ground chicken")
    }

    enum class Status {
        IDLE,
        QUERY_TOO_LONG,
        NEEDS_REFINEMENT,
        RESULT
    }

    sealed interface Intent {
        data class QueryChanged(val query: String) : Intent
        data object Submit : Intent
        data class ChooseChicken(val choice: ChickenChoice) : Intent
    }

    data class ResolvedItemUiState(
        val name: String,
        val detail: String
    ) {
        init {
            require(name.isNotBlank())
            require(detail.isNotBlank())
        }
    }

    data class ChickenClarificationUiState(
        val prompt: String,
        val choices: List<ChickenChoice>
    ) {
        init {
            require(prompt.isNotBlank())
            require(choices.isNotEmpty())
        }
    }

    data class UiState(
        val query: String,
        val status: Status,
        val items: List<ResolvedItemUiState>,
        val chickenClarification: ChickenClarificationUiState?,
        val unknownItems: List<String>,
        val result: PracticalShoppingUiState?,
        val message: String?,
        val sampleNotice: String
    ) {
        init {
            require(query.length <= MAX_STORED_DEMO_QUERY_LENGTH)
            require(items.size + unknownItems.size <= MAX_DEMO_INTENTS)
            require(message == null || message.isNotBlank())
            require(sampleNotice.isNotBlank())
        }
    }

    data class Model internal constructor(
        val ui: UiState,
        internal val selectedChicken: ChickenChoice?
    )

    private data class Resolution(
        val items: List<SampleItem>,
        val needsChickenChoice: Boolean,
        val unknownItems: List<String>
    )

    private val eggs =
        SampleItem(
            key = ShoppingItemKey("sample-eggs-large-12"),
            displayName = "Eggs",
            detail = "12 large · sample default",
            aliases = listOf(listOf("egg"), listOf("eggs"))
        )

    private val milk =
        SampleItem(
            key = ShoppingItemKey("sample-milk-2pct-4l"),
            displayName = "Milk",
            detail = "2% · 4 L · sample default",
            aliases = listOf(listOf("milk"))
        )

    private val bananas =
        SampleItem(
            key = ShoppingItemKey("sample-bananas-1kg"),
            displayName = "Bananas",
            detail = "Approx. 1 kg · sample target",
            aliases = listOf(listOf("banana"), listOf("bananas"))
        )

    private val bread =
        SampleItem(
            key = ShoppingItemKey("sample-bread-675g"),
            displayName = "Bread",
            detail = "675 g loaf · sample default",
            aliases = listOf(listOf("bread"))
        )

    private val rice =
        SampleItem(
            key = ShoppingItemKey("sample-basmati-rice-2kg"),
            displayName = "Basmati rice",
            detail = "2 kg · sample default",
            aliases = listOf(listOf("rice"), listOf("basmati", "rice"))
        )

    private val coffee =
        SampleItem(
            key = ShoppingItemKey("sample-ground-coffee-340g"),
            displayName = "Coffee",
            detail = "340 g ground coffee · fictional sample target",
            aliases = listOf(listOf("coffee"))
        )

    private val chickenBreast = chickenItem(
        suffix = "breast",
        displayName = "Chicken breast",
        aliases = listOf(listOf("chicken", "breast"), listOf("chicken", "breasts"))
    )

    private val chickenThighs = chickenItem(
        suffix = "thighs",
        displayName = "Chicken thighs",
        aliases = listOf(listOf("chicken", "thigh"), listOf("chicken", "thighs"))
    )

    private val chickenDrumsticks = chickenItem(
        suffix = "drumsticks",
        displayName = "Chicken drumsticks",
        aliases = listOf(listOf("chicken", "drumstick"), listOf("chicken", "drumsticks"))
    )

    private val wholeChicken = chickenItem(
        suffix = "whole",
        displayName = "Whole chicken",
        aliases = listOf(listOf("whole", "chicken"))
    )

    private val groundChicken = chickenItem(
        suffix = "ground",
        displayName = "Ground chicken",
        aliases = listOf(listOf("ground", "chicken"))
    )

    private val chickenByChoice = mapOf(
        ChickenChoice.BREAST to chickenBreast,
        ChickenChoice.THIGHS to chickenThighs,
        ChickenChoice.DRUMSTICKS to chickenDrumsticks,
        ChickenChoice.WHOLE to wholeChicken,
        ChickenChoice.GROUND to groundChicken
    )

    private val resolvableItems =
        listOf(
            chickenBreast,
            chickenThighs,
            chickenDrumsticks,
            wholeChicken,
            groundChicken,
            coffee,
            rice,
            eggs,
            milk,
            bananas,
            bread
        )

    private val marketA = ShoppingStoreKey("sample-practical-market-a")
    private val marketB = ShoppingStoreKey("sample-practical-market-b")
    private val marketC = ShoppingStoreKey("sample-practical-market-c")

    private val stores =
        listOf(
            SampleStore(
                key = marketA,
                displayName = "Sample Market North",
                travelFromUser = ShoppingTravel(2_200L, 360L),
                prices =
                    prices(
                        eggs to "4.49",
                        milk to "6.49",
                        bananas to "2.19",
                        bread to "3.49",
                        rice to "15.99",
                        chickenBreast to "15.49",
                        chickenThighs to "11.49",
                        chickenDrumsticks to "9.49",
                        wholeChicken to "14.99",
                        groundChicken to "10.99"
                    )
            ),
            SampleStore(
                key = marketB,
                displayName = "Sample Market West",
                travelFromUser = ShoppingTravel(4_000L, 540L),
                prices =
                    prices(
                        eggs to "3.99",
                        milk to "6.29",
                        bananas to "1.99",
                        bread to "3.29",
                        rice to "14.99",
                        chickenBreast to "14.99",
                        chickenThighs to "10.99",
                        chickenDrumsticks to "8.99",
                        wholeChicken to "13.99",
                        groundChicken to "10.49"
                    )
            ),
            SampleStore(
                key = marketC,
                displayName = "Example Grocer East",
                travelFromUser = ShoppingTravel(3_100L, 480L),
                prices =
                    prices(
                        eggs to "4.29",
                        milk to "5.99",
                        bread to "3.09",
                        rice to "13.99",
                        chickenBreast to "13.99",
                        chickenThighs to "10.49",
                        chickenDrumsticks to "8.49",
                        wholeChicken to "14.49",
                        groundChicken to "9.99"
                        // Bananas intentionally absent to exercise incomplete coverage.
                    )
            )
        )

    private val pairTravel =
        listOf(
            SamplePairTravel(marketA, marketB, ShoppingTravel(2_000L, 300L)),
            SamplePairTravel(marketA, marketC, ShoppingTravel(3_500L, 480L)),
            SamplePairTravel(marketB, marketA, ShoppingTravel(1_800L, 240L)),
            SamplePairTravel(marketB, marketC, ShoppingTravel(2_600L, 360L)),
            SamplePairTravel(marketC, marketA, ShoppingTravel(3_100L, 420L)),
            SamplePairTravel(marketC, marketB, ShoppingTravel(2_400L, 300L))
        )

    private val policy =
        PracticalShoppingPolicy(
            minimumSecondStopSavings = Money.parse("15.00", "CAD"),
            maxAdditionalTravelSeconds = 600L,
            maxAdditionalDistanceMetres = 5_000L
        )

    private val sampleNotice =
        "Fictional sample data only — not live retailer prices or availability."

    fun initialModel(): Model =
        Model(
            ui =
                UiState(
                    query = "",
                    status = Status.IDLE,
                    items = emptyList(),
                    chickenClarification = null,
                    unknownItems = emptyList(),
                    result = null,
                    message = "Type a few groceries to preview the shopping planner.",
                    sampleNotice = sampleNotice
                ),
            selectedChicken = null
        )

    fun reduce(model: Model, intent: Intent): Model =
        when (intent) {
            is Intent.QueryChanged -> onQueryChanged(intent.query)
            Intent.Submit -> evaluate(model.ui.query, model.selectedChicken)
            is Intent.ChooseChicken -> evaluate(model.ui.query, intent.choice)
        }

    private fun onQueryChanged(query: String): Model {
        val boundedQuery = query.take(MAX_STORED_DEMO_QUERY_LENGTH)
        if (query.length > MAX_DEMO_QUERY_LENGTH) {
            return Model(
                ui =
                    UiState(
                        query = boundedQuery,
                        status = Status.QUERY_TOO_LONG,
                        items = emptyList(),
                        chickenClarification = null,
                        unknownItems = emptyList(),
                        result = null,
                        message = "Keep the sample shopping list to $MAX_DEMO_QUERY_LENGTH characters or fewer.",
                        sampleNotice = sampleNotice
                    ),
                selectedChicken = null
            )
        }

        return Model(
            ui =
                UiState(
                    query = boundedQuery,
                    status = Status.IDLE,
                    items = emptyList(),
                    chickenClarification = null,
                    unknownItems = emptyList(),
                    result = null,
                    message =
                        if (boundedQuery.isBlank()) {
                            "Type a few groceries to preview the shopping planner."
                        } else {
                            "Ready to plan this sample list."
                        },
                    sampleNotice = sampleNotice
                ),
            selectedChicken = null
        )
    }

    private fun evaluate(rawQuery: String, chickenChoice: ChickenChoice?): Model {
        if (rawQuery.length > MAX_DEMO_QUERY_LENGTH) {
            return onQueryChanged(rawQuery)
        }

        val resolution = resolve(rawQuery, chickenChoice)
        val rows = resolution.items.map { ResolvedItemUiState(it.displayName, it.detail) }

        if (rawQuery.isBlank()) {
            return initialModel()
        }

        if (resolution.items.size + resolution.unknownItems.size > MAX_DEMO_INTENTS) {
            val boundedRows = rows.take(MAX_DEMO_INTENTS)
            val remainingUnknownSlots = MAX_DEMO_INTENTS - boundedRows.size
            return Model(
                ui =
                    UiState(
                        query = rawQuery,
                        status = Status.NEEDS_REFINEMENT,
                        items = boundedRows,
                        chickenClarification = null,
                        unknownItems = resolution.unknownItems.take(remainingUnknownSlots),
                        result = null,
                        message = "Keep this sample list to $MAX_DEMO_INTENTS distinct items or fewer.",
                        sampleNotice = sampleNotice
                    ),
                selectedChicken = chickenChoice
            )
        }

        if (resolution.needsChickenChoice || resolution.unknownItems.isNotEmpty()) {
            val message = when {
                resolution.needsChickenChoice && resolution.unknownItems.isNotEmpty() ->
                    "Choose the chicken type and fix the items this sample does not recognize."

                resolution.needsChickenChoice ->
                    "Which chicken do you want?"

                else ->
                    "Fix the items this small sample does not recognize."
            }

            return Model(
                ui =
                    UiState(
                        query = rawQuery,
                        status = Status.NEEDS_REFINEMENT,
                        items = rows,
                        chickenClarification =
                            if (resolution.needsChickenChoice) {
                                ChickenClarificationUiState(
                                    prompt = "Chicken",
                                    choices = ChickenChoice.entries
                                )
                            } else {
                                null
                            },
                        unknownItems = resolution.unknownItems,
                        result = null,
                        message = message,
                        sampleNotice = sampleNotice
                    ),
                selectedChicken = chickenChoice
            )
        }

        if (resolution.items.isEmpty()) {
            return Model(
                ui =
                    UiState(
                        query = rawQuery,
                        status = Status.NEEDS_REFINEMENT,
                        items = emptyList(),
                        chickenClarification = null,
                        unknownItems = emptyList(),
                        result = null,
                        message = "Add at least one recognized sample grocery.",
                        sampleNotice = sampleNotice
                    ),
                selectedChicken = chickenChoice
            )
        }

        val request = ShoppingRequest(resolution.items.map { it.key })
        val singleCandidates = stores.map { singleCandidate(it, request) }
        val twoStoreCandidates = pairTravel.mapNotNull { pairCandidate(it, request) }
        val decision =
            PracticalShoppingPlanner.evaluate(
                request = request,
                singleStoreCandidates = singleCandidates,
                twoStoreCandidates = twoStoreCandidates,
                policy = policy
            )
        val projection =
            PracticalShoppingUiProjector.project(
                request = request,
                decision = decision,
                storeDisplayNames = stores.associate { it.key to it.displayName },
                itemDisplayNames = resolution.items.associate { it.key to it.displayName }
            )

        return Model(
            ui =
                UiState(
                    query = rawQuery,
                    status = Status.RESULT,
                    items = rows,
                    chickenClarification = null,
                    unknownItems = emptyList(),
                    result = projection.state,
                    message = null,
                    sampleNotice = sampleNotice
                ),
            selectedChicken = chickenChoice
        )
    }

    private fun resolve(rawQuery: String, chickenChoice: ChickenChoice?): Resolution {
        val tokens = tokenize(rawQuery)
        if (tokens.isEmpty()) {
            return Resolution(emptyList(), false, emptyList())
        }

        val resolved = LinkedHashMap<ShoppingItemKey, SampleItem>()
        val unknown = LinkedHashSet<String>()
        var needsChicken = false
        var index = 0

        while (index < tokens.size) {
            val aliasMatch = findAlias(tokens, index)
            if (aliasMatch != null) {
                if (aliasMatch.first.key !in resolved) {
                    resolved[aliasMatch.first.key] = aliasMatch.first
                }
                index += aliasMatch.second
                continue
            }

            val token = tokens[index]
            if (token == "chicken") {
                val selected = chickenChoice?.let(chickenByChoice::get)
                if (selected == null) {
                    needsChicken = true
                } else if (selected.key !in resolved) {
                    resolved[selected.key] = selected
                }
            } else {
                unknown.add(token)
            }
            index += 1
        }

        return Resolution(
            items = resolved.values.toList(),
            needsChickenChoice = needsChicken,
            unknownItems = unknown.toList()
        )
    }

    private fun findAlias(tokens: List<String>, start: Int): Pair<SampleItem, Int>? {
        for (item in resolvableItems) {
            for (alias in item.aliases.sortedByDescending { it.size }) {
                if (start + alias.size > tokens.size) continue
                if (tokens.subList(start, start + alias.size) == alias) {
                    return item to alias.size
                }
            }
        }
        return null
    }

    private fun tokenize(raw: String): List<String> =
        raw.lowercase()
            .replace(Regex("[,;\\n\\r\\t]+"), " ")
            .trim()
            .split(Regex("\\s+"))
            .map { it.trim('.', ':', '!', '?', '(', ')', '[', ']', '{', '}') }
            .filter(String::isNotBlank)

    private fun singleCandidate(
        store: SampleStore,
        request: ShoppingRequest
    ): SingleStorePlanCandidate {
        val covered = request.itemKeys.filterTo(linkedSetOf()) { store.prices.containsKey(it) }
        val total = exactTotal(request.itemKeys.mapNotNull(store.prices::get))

        return SingleStorePlanCandidate(
            storeKey = store.key,
            coveredItemKeys = covered,
            knownBasketCost = total,
            travel = store.travelFromUser,
            evidence = unknownEvidence(covered.size)
        )
    }

    private fun pairCandidate(
        pair: SamplePairTravel,
        request: ShoppingRequest
    ): TwoStorePlanCandidate? {
        val base = stores.first { it.key == pair.base }
        val added = stores.first { it.key == pair.added }
        val selectedPrices = mutableListOf<Money>()
        val covered = linkedSetOf<ShoppingItemKey>()

        for (itemKey in request.itemKeys) {
            val prices = listOfNotNull(base.prices[itemKey], added.prices[itemKey])
            if (prices.isEmpty()) continue
            covered += itemKey
            selectedPrices += prices.minBy { it.minorUnits }
        }

        if (covered != request.itemKeys.toSet()) return null

        return TwoStorePlanCandidate(
            baseStoreKey = pair.base,
            addedStoreKey = pair.added,
            coveredItemKeys = covered,
            knownCombinedBasketCost = exactTotal(selectedPrices),
            additionalTravel = pair.additionalTravel,
            evidence = unknownEvidence(covered.size)
        )
    }

    private fun exactTotal(prices: List<Money>): Money =
        prices.fold(Money(0L, "CAD")) { total, price -> total + price }

    private fun unknownEvidence(itemCount: Int): ShoppingPlanEvidenceSummary =
        ShoppingPlanEvidenceSummary(
            freshItemCount = 0,
            staleItemCount = 0,
            unknownFreshnessItemCount = itemCount
        )

    private fun prices(vararg entries: Pair<SampleItem, String>): Map<ShoppingItemKey, Money> =
        entries.associate { (item, decimal) -> item.key to Money.parse(decimal, "CAD") }

    private fun chickenItem(
        suffix: String,
        displayName: String,
        aliases: List<List<String>>
    ): SampleItem =
        SampleItem(
            key = ShoppingItemKey("sample-chicken-$suffix-1kg"),
            displayName = displayName,
            detail = "Approx. 1 kg · fictional sample target",
            aliases = aliases
        )
}
