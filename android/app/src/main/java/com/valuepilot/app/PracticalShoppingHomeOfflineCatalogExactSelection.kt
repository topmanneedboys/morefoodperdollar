package com.valuepilot.app

import com.valuepilot.core.OfflineCatalogDiscoveryResult
import com.valuepilot.core.ShoppingItemKey

/**
 * Result of an explicit Home catalog choice.
 *
 * The catalog is an identity suggestion rail, so this boundary does not infer an exact
 * product from a name. It turns one user-selected result into the existing source-revalidated
 * exact-choice projection and returns the already-typed Remember request only after that
 * projection accepts the choice.
 */
internal enum class PracticalShoppingHomeOfflineCatalogExactSelectionIssue {
    STALE_OR_UNKNOWN_MATCH,
    SOURCE_IDENTITY_UNAVAILABLE,
    CONFIRMATION_REJECTED
}

internal data class PracticalShoppingHomeOfflineCatalogExactSelectionResult(
    val selection: PracticalShoppingConfirmedProductChoice? = null,
    val issue: PracticalShoppingHomeOfflineCatalogExactSelectionIssue? = null,
    val confirmationFailures: Set<PracticalShoppingExactProductConfirmationFailure> = emptySet()
) {
    init {
        require((selection != null) == (issue == null))
        require(
            issue == PracticalShoppingHomeOfflineCatalogExactSelectionIssue.CONFIRMATION_REJECTED ||
                confirmationFailures.isEmpty()
        )
        require(
            issue != PracticalShoppingHomeOfflineCatalogExactSelectionIssue.CONFIRMATION_REJECTED ||
                confirmationFailures.isNotEmpty()
        )
    }
}

/**
 * Home-specific adapter over the existing exact-choice projector.
 *
 * This intentionally creates a one-option projection for the selected result. That preserves
 * the opaque action binding and source revalidation without reconstructing product identity from
 * a display label or relying on a result-list index as identity.
 */
internal object PracticalShoppingHomeOfflineCatalogExactSelection {

    fun confirm(
        itemKey: ShoppingItemKey,
        result: OfflineCatalogDiscoveryResult,
        matchIndex: Int,
        presentationGeneration: Long,
        confirmedCandidateId: String
    ): PracticalShoppingHomeOfflineCatalogExactSelectionResult {
        val match = result.matches.getOrNull(matchIndex)
            ?: return rejected(PracticalShoppingHomeOfflineCatalogExactSelectionIssue.STALE_OR_UNKNOWN_MATCH)

        val gtin = match.product.canonicalGtin
            ?: return rejected(
                PracticalShoppingHomeOfflineCatalogExactSelectionIssue.SOURCE_IDENTITY_UNAVAILABLE
            )

        val row =
            OpenFoodFactsImportedProduct(
                code = gtin,
                productName = match.product.displayName,
                brands = match.product.brand,
                productQuantity = null,
                productQuantityUnit = null
            )
        val candidate =
            OpenFoodFactsPracticalShoppingIdentityAdapter
                .catalogSuggestion(
                    itemKey = itemKey,
                    row = row,
                    candidateId = "home-offline-${match.product.recordId}"
                )
                .candidate
                ?: return rejected(
                    PracticalShoppingHomeOfflineCatalogExactSelectionIssue.SOURCE_IDENTITY_UNAVAILABLE
                )

        val projection =
            runCatching {
                PracticalShoppingExactChoiceConfirmationProjector.projectOpenFoodFactsProducts(
                    presentationGeneration = presentationGeneration,
                    itemKey = itemKey,
                    options =
                        listOf(
                            PracticalShoppingOpenFoodFactsConfirmationOption(
                                candidate = candidate,
                                row = row
                            )
                        )
                )
            }.getOrNull()
                ?: return rejected(
                    PracticalShoppingHomeOfflineCatalogExactSelectionIssue.CONFIRMATION_REJECTED
                )

        val action = projection.state.rows.singleOrNull()?.action
            ?: return rejected(
                PracticalShoppingHomeOfflineCatalogExactSelectionIssue.CONFIRMATION_REJECTED
            )
        val confirmed = projection.confirm(action, confirmedCandidateId)
        return confirmed.selection?.let { selection ->
            PracticalShoppingHomeOfflineCatalogExactSelectionResult(selection = selection)
        } ?: PracticalShoppingHomeOfflineCatalogExactSelectionResult(
            issue = PracticalShoppingHomeOfflineCatalogExactSelectionIssue.CONFIRMATION_REJECTED,
            confirmationFailures = confirmed.confirmationFailures
                .ifEmpty {
                    setOf(
                        PracticalShoppingExactProductConfirmationFailure
                            .PRODUCT_IDENTITY_UNAVAILABLE
                    )
                }
        )
    }

    private fun rejected(
        issue: PracticalShoppingHomeOfflineCatalogExactSelectionIssue
    ): PracticalShoppingHomeOfflineCatalogExactSelectionResult =
        PracticalShoppingHomeOfflineCatalogExactSelectionResult(
            issue = issue,
            confirmationFailures =
                if (issue == PracticalShoppingHomeOfflineCatalogExactSelectionIssue.CONFIRMATION_REJECTED) {
                    setOf(PracticalShoppingExactProductConfirmationFailure.PRODUCT_IDENTITY_UNAVAILABLE)
                } else {
                    emptySet()
                }
        )
}
