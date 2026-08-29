package com.valuepilot.app

import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey

private const val MAX_SAVED_PRODUCT_DISPLAY_METADATA = 128
private const val MAX_SAVED_STORE_DISPLAY_METADATA = 64
private const val MAX_SAVED_DISPLAY_METADATA_INPUT_LENGTH = 500
private const val MAX_SAVED_CONSUMER_LABEL_LENGTH = 160

/**
 * Separately supplied consumer-facing names for already persisted exact preferences.
 *
 * Persisted identity records intentionally contain no trusted consumer labels. This
 * boundary therefore keeps display metadata separate and keyed only by the same stable
 * Practical Shopping item/store keys. Extra metadata cannot create a saved row.
 */
data class PracticalShoppingSavedExactPreferenceDisplayMetadata(
    val productDisplayNames: Map<ShoppingItemKey, String> = emptyMap(),
    val storeDisplayNames: Map<ShoppingStoreKey, String> = emptyMap()
) {
    init {
        require(productDisplayNames.size <= MAX_SAVED_PRODUCT_DISPLAY_METADATA)
        require(storeDisplayNames.size <= MAX_SAVED_STORE_DISPLAY_METADATA)
        productDisplayNames.values.forEach { value ->
            require(value.length <= MAX_SAVED_DISPLAY_METADATA_INPUT_LENGTH)
        }
        storeDisplayNames.values.forEach { value ->
            require(value.length <= MAX_SAVED_DISPLAY_METADATA_INPUT_LENGTH)
        }
    }
}

/** Typed actions emitted by a future Saved renderer. Views never parse display text. */
sealed interface PracticalShoppingSavedExactPreferenceUiAction {
    data class DeleteProduct(
        val itemKey: ShoppingItemKey
    ) : PracticalShoppingSavedExactPreferenceUiAction

    data class DeleteStore(
        val storeKey: ShoppingStoreKey
    ) : PracticalShoppingSavedExactPreferenceUiAction

    data object ClearAll : PracticalShoppingSavedExactPreferenceUiAction
}

data class PracticalShoppingSavedProductUiRow(
    val title: String,
    val supportingText: String,
    val action: PracticalShoppingSavedExactPreferenceUiAction.DeleteProduct
) {
    init {
        require(title.isNotBlank())
        require(title.length <= MAX_SAVED_CONSUMER_LABEL_LENGTH)
        require(supportingText.isNotBlank())
    }
}

data class PracticalShoppingSavedStoreUiRow(
    val title: String,
    val supportingText: String,
    val action: PracticalShoppingSavedExactPreferenceUiAction.DeleteStore
) {
    init {
        require(title.isNotBlank())
        require(title.length <= MAX_SAVED_CONSUMER_LABEL_LENGTH)
        require(supportingText.isNotBlank())
    }
}

/** Immutable consumer-ready state. No raw product/store/provider/source identifiers are strings here. */
data class PracticalShoppingSavedExactPreferenceUiState(
    val headline: String,
    val productRows: List<PracticalShoppingSavedProductUiRow>,
    val storeRows: List<PracticalShoppingSavedStoreUiRow>,
    val unresolvedDisplayNameCount: Int,
    val notice: String?,
    val emptyMessage: String?,
    val clearAllAction: PracticalShoppingSavedExactPreferenceUiAction.ClearAll?
) {
    init {
        require(headline.isNotBlank())
        require(productRows.size <= MAX_SAVED_PRODUCT_DISPLAY_METADATA)
        require(storeRows.size <= MAX_SAVED_STORE_DISPLAY_METADATA)
        require(unresolvedDisplayNameCount >= 0)
        require(notice == null || notice.isNotBlank())
        require(emptyMessage == null || emptyMessage.isNotBlank())
        require((clearAllAction != null) == (emptyMessage == null))
    }
}

/**
 * Consumer state plus exact unresolved keys retained outside normal UI strings.
 *
 * A future metadata adapter may use these exact keys to refresh names. The renderer
 * receives [state] and never reconstructs identity from a label.
 */
data class PracticalShoppingSavedExactPreferenceUiProjection(
    val state: PracticalShoppingSavedExactPreferenceUiState,
    val unresolvedProductKeys: List<ShoppingItemKey>,
    val unresolvedStoreKeys: List<ShoppingStoreKey>
) {
    init {
        require(unresolvedProductKeys.distinct().size == unresolvedProductKeys.size)
        require(unresolvedStoreKeys.distinct().size == unresolvedStoreKeys.size)
        require(
            state.unresolvedDisplayNameCount ==
                unresolvedProductKeys.size + unresolvedStoreKeys.size
        )
    }
}

/**
 * Pure projection from validated persisted preference state + separately supplied names.
 *
 * There is no storage, UI, network, product matching, store discovery, price, travel,
 * ranking or hidden source policy here. Missing/unsafe names fail closed per row and are
 * counted explicitly instead of falling back to GTIN, SKU, provider, dataset, merchant,
 * location, commerce-channel or stable-key strings.
 */
object PracticalShoppingSavedExactPreferenceUiProjector {

    fun project(
        savedState: PracticalShoppingSavedExactPreferenceState,
        metadata: PracticalShoppingSavedExactPreferenceDisplayMetadata
    ): PracticalShoppingSavedExactPreferenceUiProjection {
        val unresolvedProducts = mutableListOf<ShoppingItemKey>()
        val unresolvedStores = mutableListOf<ShoppingStoreKey>()

        val productRows =
            savedState.productPreferences
                .sortedBy { preference -> preference.itemKey.value }
                .mapNotNull { preference ->
                    val label =
                        safeLabel(
                            raw = metadata.productDisplayNames[preference.itemKey],
                            forbiddenIdentifiers =
                                listOfNotNull(
                                    preference.providerId.value,
                                    preference.sourceIdentity.providerItemId,
                                    preference.sourceIdentity.sku,
                                    preference.sourceIdentity.gtin,
                                    preference.dataset?.id
                                )
                        )
                    if (label == null) {
                        unresolvedProducts += preference.itemKey
                        null
                    } else {
                        PracticalShoppingSavedProductUiRow(
                            title = label,
                            supportingText = "Exact product choice",
                            action =
                                PracticalShoppingSavedExactPreferenceUiAction.DeleteProduct(
                                    preference.itemKey
                                )
                        )
                    }
                }

        val storeRows =
            savedState.storePreferences
                .sortedBy { preference -> preference.storeKey.value }
                .mapNotNull { preference ->
                    val label =
                        safeLabel(
                            raw = metadata.storeDisplayNames[preference.storeKey],
                            forbiddenIdentifiers =
                                listOfNotNull(
                                    preference.scope.merchantKey,
                                    preference.scope.locationKey,
                                    preference.scope.commerceChannelKey,
                                    preference.providerId?.value,
                                    preference.dataset?.id
                                ) +
                                prefixedIdentitySuffixes(
                                    preference.scope.merchantKey,
                                    preference.scope.locationKey
                                )
                        )
                    if (label == null) {
                        unresolvedStores += preference.storeKey
                        null
                    } else {
                        PracticalShoppingSavedStoreUiRow(
                            title = label,
                            supportingText = "Exact store choice",
                            action =
                                PracticalShoppingSavedExactPreferenceUiAction.DeleteStore(
                                    preference.storeKey
                                )
                        )
                    }
                }

        val totalSaved = savedState.productPreferences.size + savedState.storePreferences.size
        val unresolvedCount = unresolvedProducts.size + unresolvedStores.size
        val state =
            PracticalShoppingSavedExactPreferenceUiState(
                headline = "Saved choices",
                productRows = productRows,
                storeRows = storeRows,
                unresolvedDisplayNameCount = unresolvedCount,
                notice = unresolvedNotice(unresolvedCount),
                emptyMessage =
                    if (totalSaved == 0) {
                        "No saved choices yet."
                    } else {
                        null
                    },
                clearAllAction =
                    if (totalSaved == 0) {
                        null
                    } else {
                        PracticalShoppingSavedExactPreferenceUiAction.ClearAll
                    }
            )

        return PracticalShoppingSavedExactPreferenceUiProjection(
            state = state,
            unresolvedProductKeys = unresolvedProducts.toList(),
            unresolvedStoreKeys = unresolvedStores.toList()
        )
    }

    private fun unresolvedNotice(count: Int): String? =
        when (count) {
            0 -> null
            1 -> "1 saved choice needs a display name before it can be shown."
            else -> "$count saved choices need display names before they can be shown."
        }

    private fun safeLabel(
        raw: String?,
        forbiddenIdentifiers: List<String>
    ): String? {
        val value = raw?.trim() ?: return null
        if (value.isBlank() || value.length > MAX_SAVED_CONSUMER_LABEL_LENGTH) {
            return null
        }
        if (value.any { character -> Character.isISOControl(character.code) }) {
            return null
        }

        val forbidden =
            forbiddenIdentifiers
                .map { identifier -> identifier.trim() }
                .filter { identifier -> identifier.isNotBlank() }
                .distinct()

        if (
            forbidden.any { identifier ->
                value.equals(identifier, ignoreCase = true) ||
                    (identifier.length >= 6 && value.contains(identifier, ignoreCase = true))
            }
        ) {
            return null
        }

        return value
    }

    private fun prefixedIdentitySuffixes(vararg values: String?): List<String> =
        values
            .filterNotNull()
            .mapNotNull { value ->
                value.substringAfterLast(':', missingDelimiterValue = "")
                    .takeIf { suffix -> suffix.length >= 6 }
            }
}
