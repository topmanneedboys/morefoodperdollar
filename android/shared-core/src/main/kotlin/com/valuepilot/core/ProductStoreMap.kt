package com.valuepilot.core

import java.math.BigDecimal
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A provider-neutral, bounded relationship between one exact product identity
 * and one physical store.  This is deliberately not a product catalog and it
 * does not infer stock from a published price.
 */
data class ProductStoreMapCoordinate(
    val latitudeE7: Int,
    val longitudeE7: Int
) {
    init {
        require(latitudeE7 in -900_000_000..900_000_000)
        require(longitudeE7 in -1_800_000_000..1_800_000_000)
    }

    val latitudeDegrees: Double
        get() = latitudeE7 / COORDINATE_SCALE

    val longitudeDegrees: Double
        get() = longitudeE7 / COORDINATE_SCALE

    companion object {
        private const val COORDINATE_SCALE = 10_000_000.0

        /** Parse only an exact decimal representation; never silently round. */
        fun parse(latitude: String, longitude: String): ProductStoreMapCoordinate? =
            runCatching {
                val lat = BigDecimal(latitude.trim()).movePointRight(7).intValueExact()
                val lon = BigDecimal(longitude.trim()).movePointRight(7).intValueExact()
                ProductStoreMapCoordinate(lat, lon)
            }.getOrNull()
    }
}

enum class ProductStoreMapFreshness {
    FRESH,
    STALE,
    UNKNOWN
}

enum class ProductStoreMapResultStatus {
    READY,
    NO_EXACT_RELATIONSHIP,
    NO_VALID_COORDINATES
}

/** One immutable evidence row at the provider-neutral boundary. */
data class ProductStoreMapEvidence(
    val relationshipKey: String,
    val productEvidenceKey: String,
    val gtin: String?,
    val storeKey: ShoppingStoreKey,
    val storeName: String,
    val addressLabel: String?,
    val coordinate: ProductStoreMapCoordinate?,
    val releaseDate: String?,
    val freshness: ProductStoreMapFreshness,
    val price: Money?,
    val sourceLabel: String,
    val availabilityUnknown: Boolean = true
) {
    init {
        require(relationshipKey.isNotBlank() && relationshipKey.length <= 320)
        require(productEvidenceKey.isNotBlank() && productEvidenceKey.length <= 320)
        require(gtin == null || gtin.matches(Regex("[0-9]{8}|[0-9]{12}|[0-9]{13}|[0-9]{14}")))
        require(storeName.isNotBlank() && storeName.length <= 240)
        require(addressLabel == null || (addressLabel.isNotBlank() && addressLabel.length <= 512))
        require(releaseDate == null || releaseDate.matches(ISO_DATE))
        require(sourceLabel.isNotBlank() && sourceLabel.length <= 240)
        require(availabilityUnknown) { "Map evidence must not infer inventory from price publication" }
        price?.let { require(it.minorUnits >= 0L) }
    }

    private companion object {
        val ISO_DATE = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
    }
}

data class ProductStoreMapQuery(
    val productEvidenceKey: String,
    val gtin: String? = null,
    val origin: ProductStoreMapCoordinate? = null
) {
    init {
        require(productEvidenceKey.isNotBlank() && productEvidenceKey.length <= 320)
        require(gtin == null || gtin.matches(Regex("[0-9]{8}|[0-9]{12}|[0-9]{13}|[0-9]{14}")))
    }
}

data class ProductStoreMapPin(
    val relationshipKey: String,
    val productEvidenceKey: String,
    val storeKey: ShoppingStoreKey,
    val storeName: String,
    val addressLabel: String?,
    val coordinate: ProductStoreMapCoordinate,
    val releaseDate: String?,
    val freshness: ProductStoreMapFreshness,
    val price: Money?,
    val priceConflict: Boolean,
    val approximateDistanceMetres: Long?,
    val sourceLabel: String,
    val availabilityUnknown: Boolean = true
) {
    init {
        require(availabilityUnknown)
        require(approximateDistanceMetres == null || approximateDistanceMetres >= 0L)
    }
}

data class ProductStoreMapResult(
    val query: ProductStoreMapQuery,
    val status: ProductStoreMapResultStatus,
    val pins: List<ProductStoreMapPin>,
    val relationshipCount: Int,
    val excludedCoordinateCount: Int,
    val duplicateStoreCount: Int
) {
    init {
        require(pins.size <= ProductStoreMapIndex.MAX_PINS)
        require(relationshipCount >= 0)
        require(excludedCoordinateCount >= 0)
        require(duplicateStoreCount >= 0)
    }
}

/**
 * Exact-key map index.  It owns no clock, network, geocoder, renderer, or tile
 * provider.  Records are bounded before indexing, and a product key is never
 * fuzzy-matched to another package, brand, or GTIN.
 */
class ProductStoreMapIndex private constructor(
    private val byProduct: Map<String, List<ProductStoreMapEvidence>>
) {
    fun query(query: ProductStoreMapQuery): ProductStoreMapResult {
        val relationships = byProduct[query.productEvidenceKey].orEmpty()
            .filter { query.gtin == null || it.gtin == query.gtin }
        if (relationships.isEmpty()) {
            return ProductStoreMapResult(
                query = query,
                status = ProductStoreMapResultStatus.NO_EXACT_RELATIONSHIP,
                pins = emptyList(),
                relationshipCount = 0,
                excludedCoordinateCount = 0,
                duplicateStoreCount = 0
            )
        }

        val valid = relationships.filter { it.coordinate != null }
        val excluded = relationships.size - valid.size
        if (valid.isEmpty()) {
            return ProductStoreMapResult(
                query = query,
                status = ProductStoreMapResultStatus.NO_VALID_COORDINATES,
                pins = emptyList(),
                relationshipCount = relationships.size,
                excludedCoordinateCount = excluded,
                duplicateStoreCount = 0
            )
        }

        val grouped = valid.groupBy { it.storeKey }
        // A stable store identity must not silently move between coordinates.
        // Conflicting coordinates are excluded rather than selecting whichever
        // row happened to arrive first.
        val consistentGroups = grouped.values.filter { sameStore ->
            sameStore.mapNotNull { it.coordinate }.distinct().size <= 1
        }
        val pins = consistentGroups.map { sameStore -> canonicalPin(sameStore, query.origin) }
            .sortedWith(
                compareBy<ProductStoreMapPin>(
                    { it.approximateDistanceMetres ?: Long.MAX_VALUE },
                    { it.storeKey.value },
                    { it.relationshipKey }
                )
            )
            .take(MAX_PINS)
        return ProductStoreMapResult(
            query = query,
            status = if (pins.isEmpty()) ProductStoreMapResultStatus.NO_VALID_COORDINATES else ProductStoreMapResultStatus.READY,
            pins = pins,
            relationshipCount = relationships.size,
            excludedCoordinateCount = excluded + (grouped.size - consistentGroups.size),
            duplicateStoreCount = (valid.size - grouped.size).coerceAtLeast(0)
        )
    }

    private fun canonicalPin(
        values: List<ProductStoreMapEvidence>,
        origin: ProductStoreMapCoordinate?
    ): ProductStoreMapPin {
        val ordered = values.sortedWith(
            compareBy<ProductStoreMapEvidence>(
                { freshnessRank(it.freshness) },
                { it.relationshipKey }
            )
        )
        val first = ordered.first()
        val prices = values.mapNotNull { it.price }.distinct()
        val price = prices.singleOrNull()
        val conflict = prices.size > 1
        val coordinate = requireNotNull(first.coordinate)
        return ProductStoreMapPin(
            relationshipKey = first.relationshipKey,
            productEvidenceKey = first.productEvidenceKey,
            storeKey = first.storeKey,
            storeName = first.storeName,
            addressLabel = first.addressLabel,
            coordinate = coordinate,
            releaseDate = first.releaseDate,
            freshness = if (values.any { it.freshness == ProductStoreMapFreshness.FRESH }) {
                ProductStoreMapFreshness.FRESH
            } else if (values.any { it.freshness == ProductStoreMapFreshness.STALE }) {
                ProductStoreMapFreshness.STALE
            } else {
                ProductStoreMapFreshness.UNKNOWN
            },
            price = price,
            priceConflict = conflict,
            approximateDistanceMetres = origin?.let { haversineMetres(it, coordinate) },
            sourceLabel = first.sourceLabel
        )
    }

    companion object {
        const val MAX_RECORDS = 2_048
        const val MAX_PINS = 64

        fun build(records: List<ProductStoreMapEvidence>): ProductStoreMapIndex {
            require(records.size <= MAX_RECORDS) { "product-store map input exceeds the bounded record limit" }
            val unique = linkedMapOf<String, ProductStoreMapEvidence>()
            records.sortedBy { it.relationshipKey }.forEach { record ->
                val previous = unique[record.relationshipKey]
                if (previous == null) {
                    unique[record.relationshipKey] = record
                } else {
                    require(previous == record) { "conflicting duplicate product-store relationship" }
                }
            }
            return ProductStoreMapIndex(
                unique.values.groupBy { it.productEvidenceKey }
                    .mapValues { (_, values) -> values.sortedBy { it.relationshipKey } }
            )
        }

        private fun freshnessRank(value: ProductStoreMapFreshness): Int =
            when (value) {
                ProductStoreMapFreshness.FRESH -> 0
                ProductStoreMapFreshness.STALE -> 1
                ProductStoreMapFreshness.UNKNOWN -> 2
            }

        private fun haversineMetres(
            origin: ProductStoreMapCoordinate,
            destination: ProductStoreMapCoordinate
        ): Long {
            val originLat = Math.toRadians(origin.latitudeDegrees)
            val destinationLat = Math.toRadians(destination.latitudeDegrees)
            val deltaLat = Math.toRadians(destination.latitudeDegrees - origin.latitudeDegrees)
            val deltaLon = Math.toRadians(destination.longitudeDegrees - origin.longitudeDegrees)
            val a = min(
                1.0,
                max(
                    0.0,
                    sin(deltaLat / 2.0) * sin(deltaLat / 2.0) +
                        cos(originLat) * cos(destinationLat) *
                            sin(deltaLon / 2.0) * sin(deltaLon / 2.0)
                )
            )
            return (2.0 * EARTH_RADIUS_METRES * asin(sqrt(a))).roundToLong().coerceAtLeast(0L)
        }

        private const val EARTH_RADIUS_METRES = 6_371_000.0
    }
}
