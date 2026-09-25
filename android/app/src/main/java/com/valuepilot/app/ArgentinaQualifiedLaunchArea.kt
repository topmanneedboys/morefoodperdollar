package com.valuepilot.app

import java.math.BigDecimal

/**
 * A deliberately explicit, source-backed Search area.  This is not device
 * location and must never be replaced by an implicit default coordinate.
 */
internal data class ArgentinaQualifiedLaunchArea(
    val id: String,
    val label: String,
    val latitude: String,
    val longitude: String,
    val radiusKm: String,
    val sourceProvider: String,
    val releaseDate: String,
    val regionId: String,
    val manifestSha256: String,
    val storesSha256: String,
    val derivation: String
) {
    init {
        require(id.isNotBlank())
        require(label.isNotBlank())
        require(sourceProvider.isNotBlank())
        require(releaseDate.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))
        require(regionId.isNotBlank())
        require(manifestSha256.matches(SHA256_PATTERN))
        require(storesSha256.matches(SHA256_PATTERN))
        ArgentinaBackendContract.SearchRequest(
            latitude = latitude,
            longitude = longitude,
            radiusKm = radiusKm,
            query = "area"
        )
        require(radiusKm.toBigDecimal() <= MAX_RADIUS_KM)
        require(derivation.isNotBlank())
    }

    fun context(): ArgentinaSearchContext =
        ArgentinaSearchContext(
            latitude = latitude,
            longitude = longitude,
            radiusKm = radiusKm,
            source = ArgentinaSearchContextSource.QUALIFIED_LAUNCH_AREA
        )

    private companion object {
        val MAX_RADIUS_KM = BigDecimal("50")
        val SHA256_PATTERN = Regex("[0-9a-f]{64}")
    }
}

internal object ArgentinaQualifiedLaunchAreas {
    /**
     * CABA median of the 973 source-marked VALID store coordinates in the
     * qualified ar-caba snapshot.  A 25 km radius covers the deterministic
     * p95 distance while excluding source outliers and remains <= 50 km.
     */
    val BuenosAiresCaba = ArgentinaQualifiedLaunchArea(
        id = "ar-caba-buenos-aires",
        label = "Buenos Aires (CABA)",
        latitude = "-34.605506",
        longitude = "-58.442562",
        radiusKm = "25",
        sourceProvider = "ARGENTINA_SEPA_PRECIOS_CLAROS",
        releaseDate = "2026-09-06",
        regionId = "ar-caba",
        manifestSha256 = "49c88fb9fc9b745788562bf292352649692196e3c0f68a01737ddc779850eda3",
        storesSha256 = "de38f7c8e2715cca07a2ba734cc5c7b4d65ba7a3ed187e710c2bfb795ebf26ad",
        derivation =
            "Median latitude/longitude of 973 geoStatus=VALID CABA stores from the qualified release; 25 km is the deterministic p95 coverage radius."
    )
}
