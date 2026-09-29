package com.valuepilot.core

/**
 * The origin of the identity a shopper chose to record as purchased.
 *
 * This is provenance for a private fact, not a ranking, provider, or offer signal.
 */
enum class ConfirmedPurchaseIdentityOrigin {
    HOME_BASKET,
    SEARCH_IDENTITY,
    SAVED_IDENTITY,
    MANUAL,
    UNKNOWN
}

/** Optional shopper-confirmed store context. Missing values stay unknown. */
data class ConfirmedPurchaseStore(
    val displayName: String? = null,
    val identityScope: PracticalShoppingStoreIdentityScope? = null
) {
    init {
        require(displayName == null || singleLine(displayName, MAX_STORE_DISPLAY_NAME_LENGTH) != null)
        require(displayName != null || identityScope != null) {
            "A purchase store must contain a display name or exact identity scope"
        }
    }

    companion object {
        private const val MAX_STORE_DISPLAY_NAME_LENGTH = 240

        private fun singleLine(value: String, maxLength: Int): String? {
            val trimmed = value.trim()
            return trimmed.takeIf {
                it.isNotBlank() &&
                    it.length <= maxLength &&
                    it.none { character -> character == '\n' || character == '\r' }
            }
        }
    }
}

/**
 * One user-confirmed purchase fact.
 *
 * Nullable fields are intentionally first-class: a shopper may know the item was purchased while
 * not knowing the exact quantity, price, date, or store. This record is never an official offer,
 * a saved identity, or an observed-price proof; downstream consumers must keep those authorities
 * separate.
 */
data class ConfirmedPurchase(
    val purchaseId: String,
    val productIdentityKey: String,
    val identityOrigin: ConfirmedPurchaseIdentityOrigin,
    val displayName: String,
    val brand: String? = null,
    val packageQuantityLabel: String? = null,
    val quantity: NormalizedQuantity? = null,
    val amountPaid: Money? = null,
    val purchaseDateIso: String? = null,
    val store: ConfirmedPurchaseStore? = null
) {
    init {
        require(singleLine(purchaseId, MAX_PURCHASE_ID_LENGTH) != null)
        require(singleLine(productIdentityKey, MAX_PRODUCT_IDENTITY_KEY_LENGTH) != null)
        require(singleLine(displayName, MAX_DISPLAY_NAME_LENGTH) != null)
        require(brand == null || singleLine(brand, MAX_BRAND_LENGTH) != null)
        require(packageQuantityLabel == null || singleLine(packageQuantityLabel, MAX_PACKAGE_QUANTITY_LENGTH) != null)
        require(amountPaid == null || amountPaid.minorUnits >= 0L)
        require(purchaseDateIso == null || isValidIsoDate(purchaseDateIso))
    }

    /** Stable product identity comparison; repeated purchases are intentionally allowed. */
    fun hasSameProductIdentity(other: ConfirmedPurchase): Boolean =
        productIdentityKey == other.productIdentityKey

    companion object {
        const val MAX_PURCHASE_ID_LENGTH = 160
        const val MAX_PRODUCT_IDENTITY_KEY_LENGTH = 256
        const val MAX_DISPLAY_NAME_LENGTH = 512
        const val MAX_BRAND_LENGTH = 160
        const val MAX_PACKAGE_QUANTITY_LENGTH = 96

        private fun singleLine(value: String, maxLength: Int): String? {
            val trimmed = value.trim()
            return trimmed.takeIf {
                it.isNotBlank() &&
                    it.length <= maxLength &&
                    it.none { character -> character == '\n' || character == '\r' }
            }
        }

        /** Strict, clock-free ISO calendar-date validation for durable records. */
        fun isValidIsoDate(value: String): Boolean {
            if (!Regex("\\d{4}-\\d{2}-\\d{2}").matches(value)) return false
            val year = value.substring(0, 4).toIntOrNull() ?: return false
            val month = value.substring(5, 7).toIntOrNull() ?: return false
            val day = value.substring(8, 10).toIntOrNull() ?: return false
            if (year !in 1..9999 || month !in 1..12 || day < 1) return false
            val leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
            val days = when (month) {
                2 -> if (leap) 29 else 28
                4, 6, 9, 11 -> 30
                else -> 31
            }
            return day <= days
        }
    }
}

/** Complete input submitted only after the shopper explicitly confirms the purchase. */
data class ConfirmedPurchaseConfirmationInput(
    val purchaseId: String,
    val productIdentityKey: String,
    val identityOrigin: ConfirmedPurchaseIdentityOrigin,
    val displayName: String,
    val brand: String? = null,
    val packageQuantityLabel: String? = null,
    val quantity: NormalizedQuantity? = null,
    val amountPaid: Money? = null,
    val purchaseDateIso: String? = null,
    val store: ConfirmedPurchaseStore? = null,
    val explicitlyConfirmed: Boolean
)

enum class ConfirmedPurchaseConfirmationFailure {
    EXPLICIT_CONFIRMATION_REQUIRED,
    INVALID_RECORD
}

data class ConfirmedPurchaseConfirmationResult(
    val purchase: ConfirmedPurchase?,
    val failures: Set<ConfirmedPurchaseConfirmationFailure>
) {
    init {
        require((purchase != null) == failures.isEmpty())
    }

    val accepted: Boolean
        get() = purchase != null
}

/** Sole shared-core boundary that can mint a confirmed-purchase record. */
object ConfirmedPurchaseConfirmation {
    fun confirm(input: ConfirmedPurchaseConfirmationInput): ConfirmedPurchaseConfirmationResult {
        if (!input.explicitlyConfirmed) {
            return ConfirmedPurchaseConfirmationResult(
                purchase = null,
                failures = setOf(ConfirmedPurchaseConfirmationFailure.EXPLICIT_CONFIRMATION_REQUIRED)
            )
        }
        return try {
            ConfirmedPurchaseConfirmationResult(
                purchase = ConfirmedPurchase(
                    purchaseId = input.purchaseId.trim(),
                    productIdentityKey = input.productIdentityKey.trim(),
                    identityOrigin = input.identityOrigin,
                    displayName = input.displayName.trim(),
                    brand = input.brand?.trim()?.takeIf(String::isNotBlank),
                    packageQuantityLabel = input.packageQuantityLabel?.trim()?.takeIf(String::isNotBlank),
                    quantity = input.quantity,
                    amountPaid = input.amountPaid,
                    purchaseDateIso = input.purchaseDateIso?.trim()?.takeIf(String::isNotBlank),
                    store = input.store
                ),
                failures = emptySet()
            )
        } catch (_: IllegalArgumentException) {
            ConfirmedPurchaseConfirmationResult(
                purchase = null,
                failures = setOf(ConfirmedPurchaseConfirmationFailure.INVALID_RECORD)
            )
        }
    }
}
