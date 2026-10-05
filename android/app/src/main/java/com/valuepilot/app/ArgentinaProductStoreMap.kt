package com.valuepilot.app

import com.valuepilot.core.Money
import com.valuepilot.core.ProductStoreMapCoordinate
import com.valuepilot.core.ProductStoreMapEvidence
import com.valuepilot.core.ProductStoreMapFreshness
import com.valuepilot.core.ProductStoreMapIndex
import com.valuepilot.core.ProductStoreMapQuery
import com.valuepilot.core.ProductStoreMapResult
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest

/**
 * Projects the existing exact-key Argentina planner response into the shared
 * product/store map index.  No lexical matching or store discovery happens
 * here; a line is accepted only when its product key is the selected Search
 * identity and its store is declared by the same qualified plan.
 */
internal object ArgentinaProductStoreMapAdapter {
    private const val SOURCE_FALLBACK = "Argentina SEPA / Precios Claros"

    // A SEPA store relationship is only eligible for this Argentina map when
    // its coordinate falls inside a broad national envelope.  This is a
    // fail-closed sanity check, not geocoding and not a claim that every point
    // inside the envelope is a valid store location.
    private const val ARGENTINA_MIN_LATITUDE_E7 = -551_000_000
    private const val ARGENTINA_MAX_LATITUDE_E7 = -217_000_000
    private const val ARGENTINA_MIN_LONGITUDE_E7 = -736_000_000
    private const val ARGENTINA_MAX_LONGITUDE_E7 = -536_000_000

    fun canQuery(selected: PracticalShoppingSearchIdentityPresentation.Match): Boolean =
        selected.sourceLabel == SOURCE_FALLBACK &&
            selected.identityKey.isNotBlank() &&
            explicitPackageQuantity(selected.packageQuantityLabel) != null

    fun indexFor(
        response: ArgentinaBackendContract.ShopResponse,
        selected: PracticalShoppingSearchIdentityPresentation.Match
    ): ProductStoreMapIndex {
        val plans = buildList {
            add(response.result.cheapestSingleStore)
            add(response.result.closestCompletePriceEvidenceStore)
            add(response.result.cheapestTwoStoreCombination)
            add(response.result.cheapestUpToTwoStores)
            add(response.result.cheapestPerLineUnboundedStores)
            add(response.result.bestSensibleChoice.selectedPlan)
            addAll(response.result.decisionFrontier)
        }.filterNotNull()

        val evidence = plans
            .flatMap { plan ->
                val stores = plan.stores.associateBy { it.storeKey }
                plan.lines.mapNotNull { line ->
                    if (line.productEvidenceKey != selected.identityKey) return@mapNotNull null
                    val store = stores[line.storeKey] ?: return@mapNotNull null
                    val coordinate = ProductStoreMapCoordinate.parse(store.latitude, store.longitude)
                        ?.takeIf(::isBroadlyArgentinian)
                    ProductStoreMapEvidence(
                        relationshipKey = line.offerKey,
                        productEvidenceKey = line.productEvidenceKey,
                        gtin = selected.gtin,
                        storeKey = com.valuepilot.core.ShoppingStoreKey(store.storeKey),
                        storeName = store.storeName,
                        addressLabel = addressLabel(store.address),
                        coordinate = coordinate,
                        releaseDate = line.provenance.releaseDate,
                        freshness = line.freshnessStatus.toMapFreshness(),
                        price = moneyOrNull(line.packagePrice),
                        sourceLabel = line.provenance.provider.ifBlank { SOURCE_FALLBACK }
                    )
                }
            }
            // Let the bounded index reject an oversized candidate set instead
            // of silently dropping relationships that could contain a
            // conflicting duplicate or a later exact store.
            .take(ProductStoreMapIndex.MAX_RECORDS + 1)

        return ProductStoreMapIndex.build(evidence)
    }

    fun query(
        response: ArgentinaBackendContract.ShopResponse,
        selected: PracticalShoppingSearchIdentityPresentation.Match,
        origin: ProductStoreMapCoordinate?
    ): ProductStoreMapResult =
        indexFor(response, selected).query(
            ProductStoreMapQuery(
                productEvidenceKey = selected.identityKey,
                gtin = selected.gtin,
                origin = origin
            )
        )

    fun deterministicLineId(identityKey: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(identityKey.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return "map-$digest"
    }

    fun explicitPackageQuantity(label: String?): Pair<String, String>? {
        val match = label?.trim()?.matchRegex(Regex("^([0-9]+(?:\\.[0-9]+)?)\\s+([A-Za-z]+)$")) ?: return null
        val amount = match.groupValues[1]
        val unit = match.groupValues[2]
        return runCatching {
            ArgentinaBackendContract.RequestItem(
                lineId = "quantity",
                query = "quantity",
                amount = amount,
                unit = unit,
                productEvidenceKey = null
            )
        }.getOrNull()?.let { amount to unit }
    }

    private fun String.matchRegex(pattern: Regex): MatchResult? = pattern.matchEntire(this)

    private fun ArgentinaBackendContract.Freshness.toMapFreshness(): ProductStoreMapFreshness =
        when (this) {
            ArgentinaBackendContract.Freshness.FRESH -> ProductStoreMapFreshness.FRESH
            ArgentinaBackendContract.Freshness.STALE -> ProductStoreMapFreshness.STALE
            ArgentinaBackendContract.Freshness.UNKNOWN -> ProductStoreMapFreshness.UNKNOWN
        }

    private fun moneyOrNull(value: ArgentinaBackendContract.Money): Money? =
        runCatching {
            val scale = maxOf(2, value.decimal.scale())
            require(scale <= 6)
            val minor = value.decimal.setScale(scale, RoundingMode.UNNECESSARY)
                .movePointRight(scale)
                .longValueExact()
            Money(minor, value.currency, scale)
        }.getOrNull()

    private fun addressLabel(address: Map<String, String>): String? =
        address.toSortedMap().entries
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
            .joinToString(", ") { (key, value) -> "$key: $value" }
            .takeIf { it.isNotBlank() }

    private fun isBroadlyArgentinian(coordinate: ProductStoreMapCoordinate): Boolean =
        coordinate.latitudeE7 in ARGENTINA_MIN_LATITUDE_E7..ARGENTINA_MAX_LATITUDE_E7 &&
            coordinate.longitudeE7 in ARGENTINA_MIN_LONGITUDE_E7..ARGENTINA_MAX_LONGITUDE_E7
}
