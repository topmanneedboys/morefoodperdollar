package com.valuepilot.app

/**
 * The bounded in-memory bridge between exact Search identities and one
 * provider-authoritative shop plan.  Identity and current-price evidence are
 * deliberately supplied separately: durable Home state never stores prices.
 */
internal data class ArgentinaPrivateBasketRequestBuild(
    val request: ArgentinaBackendContract.ShopRequest?,
    val includedIdentityKeys: List<String>,
    val unresolvedIdentityKeys: List<String>
) {
    init {
        require(includedIdentityKeys.distinct().size == includedIdentityKeys.size)
        require(unresolvedIdentityKeys.distinct().size == unresolvedIdentityKeys.size)
        require(includedIdentityKeys.intersect(unresolvedIdentityKeys.toSet()).isEmpty())
        require(includedIdentityKeys.size <= ArgentinaBackendContract.MAX_LINES)
        require(unresolvedIdentityKeys.size <= ArgentinaBackendContract.MAX_LINES)
        require(request == null || request.items.size == includedIdentityKeys.size)
        require(request == null || request.items.all { it.productEvidenceKey != null })
    }
}

internal object ArgentinaPrivateBasketRequestBuilder {
    fun build(
        identities: List<PracticalShoppingPrivateIdentity>,
        offersByIdentity: Map<String, List<ArgentinaBackendContract.SearchOffer>>,
        area: ArgentinaQualifiedLaunchArea
    ): ArgentinaPrivateBasketRequestBuild {
        val unique = identities
            .distinctBy(PracticalShoppingPrivateIdentity::identityKey)
            .take(ArgentinaBackendContract.MAX_LINES)
        val included = mutableListOf<PracticalShoppingPrivateIdentity>()
        val unresolved = mutableListOf<String>()
        unique.forEach { identity ->
            val offer = offersByIdentity[identity.identityKey]
                .orEmpty()
                .asSequence()
                .filter { it.productEvidenceKey == identity.identityKey }
                .filter { it.freshnessStatus == ArgentinaBackendContract.Freshness.FRESH }
                .filter { it.quantity != null }
                .sortedWith(
                    compareBy<ArgentinaBackendContract.SearchOffer>(
                        { it.listPrice.decimal },
                        { it.storeKey },
                        { it.offerKey }
                    )
                )
                .firstOrNull()
            if (offer == null) unresolved += identity.identityKey else included += identity
        }

        val items = included.mapIndexed { index, identity ->
            val offer = requireNotNull(
                offersByIdentity[identity.identityKey]
                    .orEmpty()
                    .filter { it.productEvidenceKey == identity.identityKey }
                    .filter { it.freshnessStatus == ArgentinaBackendContract.Freshness.FRESH }
                    .filter { it.quantity != null }
                    .sortedWith(
                        compareBy<ArgentinaBackendContract.SearchOffer>(
                            { it.listPrice.decimal },
                            { it.storeKey },
                            { it.offerKey }
                        )
                    )
                    .firstOrNull()
            )
            val quantity = requireNotNull(offer.quantity)
            ArgentinaBackendContract.RequestItem(
                lineId = stableLineId(index, identity.identityKey),
                query = identity.displayName,
                amount = quantity.amount,
                unit = quantity.unit,
                productEvidenceKey = identity.identityKey
            )
        }
        return ArgentinaPrivateBasketRequestBuild(
            request = items.takeIf { it.isNotEmpty() }?.let {
                ArgentinaBackendContract.ShopRequest(
                    latitude = area.latitude,
                    longitude = area.longitude,
                    radiusKm = area.radiusKm,
                    items = it
                )
            },
            includedIdentityKeys = included.map(PracticalShoppingPrivateIdentity::identityKey),
            unresolvedIdentityKeys = unresolved
        )
    }

    private fun stableLineId(index: Int, identityKey: String): String =
        "private-$index-" + identityKey.toByteArray(Charsets.UTF_8)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
            .take(64)
}

enum class ArgentinaPrivateBasketPlanStatus {
    NO_CURRENT_PLAN,
    CURRENT_PARTIAL,
    CURRENT_COMPLETE
}

data class ArgentinaPrivateBasketLineProjection(
    val identityKey: String,
    val productName: String,
    val packageQuantityText: String,
    val packageCount: Int?,
    val lineTotalText: String?,
    val storeKey: String?,
    val storeName: String?,
    val availabilityText: String,
    val missing: Boolean
) {
    init {
        require(identityKey.isNotBlank())
        require(productName.isNotBlank())
        require(packageQuantityText.isNotBlank())
        require(packageCount == null || packageCount > 0)
        require(lineTotalText == null || lineTotalText.isNotBlank())
        require(storeKey == null || storeKey.isNotBlank())
        require(storeName == null || storeName.isNotBlank())
        require(availabilityText.isNotBlank())
        require((storeKey == null) == missing)
    }
}

data class ArgentinaPrivateBasketStoreProjection(
    val storeKey: String,
    val storeName: String,
    val latitude: String,
    val longitude: String,
    val addressLabel: String?
) {
    init {
        require(storeKey.isNotBlank())
        require(storeName.isNotBlank())
        require(latitude.isNotBlank())
        require(longitude.isNotBlank())
        require(addressLabel == null || addressLabel.isNotBlank())
    }
}

data class ArgentinaPrivateBasketPlanProjection(
    val status: ArgentinaPrivateBasketPlanStatus,
    val headline: String,
    val totalText: String?,
    val coverageText: String,
    val lines: List<ArgentinaPrivateBasketLineProjection>,
    val missingIdentityNames: List<String>,
    val stores: List<ArgentinaPrivateBasketStoreProjection>,
    val notice: String,
    val releaseDate: String
) {
    init {
        require(headline.isNotBlank())
        require(totalText == null || totalText.isNotBlank())
        require(coverageText.isNotBlank())
        require(lines.size <= ArgentinaBackendContract.MAX_LINES)
        require(missingIdentityNames.none(String::isBlank))
        require(stores.size <= ArgentinaBackendContract.MAX_STORES)
        require(notice.isNotBlank())
        require(releaseDate.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))
    }
}

internal object ArgentinaPrivateBasketPlanProjector {
    private const val SOURCE = "Argentina SEPA / Precios Claros"

    fun project(
        response: ArgentinaBackendContract.ShopResponse,
        identities: List<PracticalShoppingPrivateIdentity>
    ): ArgentinaPrivateBasketPlanProjection {
        val requestNames = response.result.request.items.associate { it.lineId to it.query }
        val selected = response.result.bestSensibleChoice.selectedPlan
            ?: response.result.cheapestUpToTwoStores
            ?: response.result.cheapestSingleStore
            ?: response.result.cheapestPerLineUnboundedStores
        if (selected == null) {
            return ArgentinaPrivateBasketPlanProjection(
                status = ArgentinaPrivateBasketPlanStatus.NO_CURRENT_PLAN,
                headline = "No current price plan found",
                totalText = null,
                coverageText = "No exact current price evidence was returned.",
                lines = identities.map { identity ->
                    missingLine(identity)
                },
                missingIdentityNames = identities.map(PracticalShoppingPrivateIdentity::displayName),
                stores = emptyList(),
                notice = "$SOURCE release ${response.common.releaseDate}; unknown prices remain unknown.",
                releaseDate = response.common.releaseDate
            )
        }

        val selectedByKey = selected.lines.associateBy { it.productEvidenceKey }
        val lines = identities.map { identity ->
            val line = selectedByKey[identity.identityKey]
            if (line == null) {
                missingLine(identity)
            } else {
                ArgentinaPrivateBasketLineProjection(
                    identityKey = identity.identityKey,
                    productName = line.productName.ifBlank {
                        requestNames[line.lineId] ?: identity.displayName
                    },
                    packageQuantityText = "${line.packageQuantity.amount} ${line.packageQuantity.unit}",
                    packageCount = line.packageCount,
                    lineTotalText = "${line.lineTotal.currency} ${line.lineTotal.amount}",
                    storeKey = line.storeKey,
                    storeName = line.storeName,
                    availabilityText = "Availability unknown",
                    missing = false
                )
            }
        }
        val missing = lines.filter { it.missing }.map { it.productName }
        val complete = selected.completePriceEvidence && selected.missingLineIds.isEmpty() && missing.isEmpty()
        val pricedCount = lines.count { !it.missing }
        val stores = selected.stores
            .distinctBy { it.storeKey }
            .take(ArgentinaBackendContract.MAX_STORES)
            .map { store ->
                ArgentinaPrivateBasketStoreProjection(
                    storeKey = store.storeKey,
                    storeName = store.storeName,
                    latitude = store.latitude,
                    longitude = store.longitude,
                    addressLabel = store.address.toSortedMap().entries
                        .filter { (key, value) -> key.isNotBlank() && value.isNotBlank() }
                        .joinToString(", ") { (key, value) -> "$key: $value" }
                        .takeIf(String::isNotBlank)
                )
            }
        return ArgentinaPrivateBasketPlanProjection(
            status = if (complete) {
                ArgentinaPrivateBasketPlanStatus.CURRENT_COMPLETE
            } else {
                ArgentinaPrivateBasketPlanStatus.CURRENT_PARTIAL
            },
            headline = if (complete) "Current listed-price basket" else "Basket price coverage is incomplete",
            totalText = if (complete) {
                "Listed total · ${selected.total.currency} ${selected.total.amount}"
            } else {
                "Known subtotal · ${selected.total.currency} ${selected.total.amount}"
            },
            coverageText = "$pricedCount of ${identities.size} selected items have exact price evidence",
            lines = lines,
            missingIdentityNames = missing,
            stores = stores,
            notice = buildString {
                append("$SOURCE release ")
                append(response.common.releaseDate)
                append(". Published prices do not establish live shelf inventory; availability remains unknown.")
                if (missing.isNotEmpty()) append(" Missing: ").append(missing.joinToString()).append('.')
            },
            releaseDate = response.common.releaseDate
        )
    }

    private fun missingLine(identity: PracticalShoppingPrivateIdentity) =
        ArgentinaPrivateBasketLineProjection(
            identityKey = identity.identityKey,
            productName = identity.displayName,
            packageQuantityText = identity.packageQuantityLabel ?: "package quantity unknown",
            packageCount = null,
            lineTotalText = null,
            storeKey = null,
            storeName = null,
            availabilityText = "Price evidence unknown",
            missing = true
        )
}
