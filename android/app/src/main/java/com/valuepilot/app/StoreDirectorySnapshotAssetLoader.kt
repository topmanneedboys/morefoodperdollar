package com.valuepilot.app

import com.valuepilot.core.OfflineCatalogIntegrityAssessment
import com.valuepilot.core.OfflineCatalogIntegrityState
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.json.JSONObject

/** Why a bundled directory is or is not safe to expose as a location list. */
enum class StoreDirectoryAdmissionState {
    ACCEPTED,
    INVALID_INTEGRITY,
    FUTURE_DATED,
    EXPIRED
}

/** Small signed-manifest summary used by Data Status without parsing every row. */
data class StoreDirectorySummary(
    val snapshotId: String,
    val generatedAtEpochMillis: Long,
    val observedAtEpochMillis: Long,
    val totalRecordCount: Int,
    val regionRecordCounts: Map<String, Int>,
    val sourceDisplayName: String,
    val licenseId: String,
    val attribution: String,
    val admissionState: StoreDirectoryAdmissionState
) {
    val accepted: Boolean
        get() = admissionState == StoreDirectoryAdmissionState.ACCEPTED
}

data class StoreDirectoryAddress(
    val houseNumber: String? = null,
    val unit: String? = null,
    val street: String? = null,
    val city: String? = null,
    val postcode: String? = null
)

/** One source-listed location; this model intentionally has no offer fields. */
data class StoreDirectoryRecord(
    val recordId: String,
    val sourceElementId: String,
    val sourceElementType: String,
    val providerId: String,
    val datasetNamespaceId: String,
    val regionId: String,
    val name: String,
    val brand: String?,
    val operator: String?,
    val address: StoreDirectoryAddress?,
    val storeType: String,
    val latitudeE7: Int,
    val longitudeE7: Int,
    val sourceSnapshotId: String,
    val licenseId: String,
    val observedAtEpochMillis: Long,
    val confidence: String,
    val status: String,
    val brandWikidataId: String?,
    val operatorWikidataId: String?
)

data class StoreDirectoryAssetLoadResult(
    val summary: StoreDirectorySummary,
    val records: List<StoreDirectoryRecord>
) {
    init {
        require(summary.accepted || records.isEmpty()) {
            "Blocked store-directory admissions must not expose records"
        }
    }
}

/**
 * Decode and validate the signed, source-isolated store directory emitted by
 * tools/build_store_directory_snapshot.py.  It performs no network or clock
 * reads; callers supply evaluation time and the freshness policy.
 */
object StoreDirectorySnapshotAssetLoader {

    private const val SCHEMA_VERSION = 1
    private const val SNAPSHOT_ROLE = "STORE_DIRECTORY"
    private const val MAX_RECORDS = 50_000
    private const val GTA = "ca-gta"
    private const val METRO_VANCOUVER = "ca-metro-vancouver"
    private val REQUIRED_REGIONS = setOf(GTA, METRO_VANCOUVER)
    private val RECORD_FIELDS =
        setOf(
            "recordId",
            "sourceElementId",
            "sourceElementType",
            "providerId",
            "datasetNamespaceId",
            "regionId",
            "name",
            "storeType",
            "latitudeE7",
            "longitudeE7",
            "sourceSnapshotId",
            "licenseId",
            "observedAtEpochMillis",
            "confidence",
            "status"
        )
    private val OPTIONAL_RECORD_FIELDS =
        setOf("brand", "operator", "address", "brandWikidataId", "operatorWikidataId")
    private val FORBIDDEN_RECORD_FIELDS =
        setOf(
            "price",
            "currentPrice",
            "salePrice",
            "currency",
            "offer",
            "offerId",
            "promotion",
            "stock",
            "availability",
            "quantity",
            "packageQuantity",
            "unitPrice",
            "validFrom",
            "validTo"
        )
    private val SOURCE_ELEMENT_ID = Regex("(node|way|relation)/[1-9][0-9]*")
    private val RECORD_ID = Regex("osm:(node|way|relation):[1-9][0-9]*")
    private val WIKIDATA_ID = Regex("Q[1-9][0-9]{0,18}")

    fun loadSummary(
        manifestJson: String,
        sourceBytes: ByteArray,
        integrity: OfflineCatalogIntegrityAssessment,
        evaluatedAtEpochMillis: Long,
        maximumSnapshotAgeMillis: Long
    ): StoreDirectorySummary {
        require(manifestJson.isNotBlank()) { "Store-directory manifest must not be blank" }
        require(evaluatedAtEpochMillis > 0L) { "Store-directory evaluation time must be positive" }
        require(maximumSnapshotAgeMillis > 0L) { "Store-directory maximum age must be positive" }
        val root = JSONObject(manifestJson)
        require(root.getInt("schemaVersion") == SCHEMA_VERSION) { "Unsupported store-directory schema" }
        require(root.getString("snapshotRole") == SNAPSHOT_ROLE) { "Snapshot role must be STORE_DIRECTORY" }
        val snapshotId = root.getString("snapshotId").also { require(it.isNotBlank()) }
        val generatedAt = root.getLong("generatedAtEpochMillis").also { require(it > 0L) }

        val source = root.getJSONObject("source")
        val providerId = source.getString("providerId").also { require(it.isNotBlank()) }
        require(providerId == "openstreetmap") { "Unexpected store-directory provider" }
        val namespaceId = source.getString("datasetNamespaceId").also { require(it.isNotBlank()) }
        require(namespaceId == "openstreetmap-places") { "Unexpected store-directory namespace" }
        val sourceDisplayName = source.getString("displayName").also { require(it.isNotBlank()) }
        val licenseId = source.getString("licenseId").also { require(it.isNotBlank()) }
        val attribution = source.getString("attribution").also { require(it.isNotBlank()) }
        val observedAt = source.getLong("observedAtEpochMillis").also { require(it > 0L && it <= generatedAt) }
        require(source.getLong("acquiredAtEpochMillis") in 1L..generatedAt) { "Invalid store-directory acquisition time" }
        require(source.getString("storageBoundary") == "OPEN_SHARE_ALIKE") { "Store-directory source boundary is invalid" }
        require(source.getString("sourceSnapshotId").isNotBlank()) { "Store-directory source snapshot is missing" }
        require(source.getString("sourceUrl").isNotBlank()) { "Store-directory source URL is missing" }
        require(source.getString("licenseUrl").isNotBlank()) { "Store-directory license URL is missing" }
        val allowedUses = source.getJSONArray("allowedUses").let { uses ->
            (0 until uses.length()).map { index -> uses.getString(index) }.toSet()
        }
        require(
            allowedUses.containsAll(setOf("access", "cache", "comparison", "display", "index", "mobile_app", "retention_deletion"))
        ) { "Store-directory source uses are incomplete" }
        val authorization = source.getJSONObject("authorization")
        require(authorization.getString("providerId") == providerId)
        require(authorization.getString("datasetNamespaceId") == namespaceId)
        val gates = authorization.getJSONArray("gates")
        val gateNames = mutableSetOf<String>()
        for (index in 0 until gates.length()) {
            val gate = gates.getJSONObject(index)
            val name = gate.getString("gate")
            require(gateNames.add(name)) { "Store-directory source authorization contains duplicate gates" }
            require(gate.getString("state") == "SATISFIED") { "Store-directory source authorization is incomplete" }
            require(gate.getString("basisId").isNotBlank())
        }
        require(
            gateNames.containsAll(
                setOf(
                "DATA_ACCESS_AUTHORIZED",
                "CACHE_AUTHORIZED",
                "INDEX_AUTHORIZED",
                "CONSUMER_DISPLAY_AUTHORIZED",
                "MOBILE_APP_AUTHORIZED",
                "RETENTION_DELETION_POLICY_DEFINED",
                "GEOGRAPHY_SCOPED",
                "COMMERCIAL_USE_REVIEWED"
                )
            )
        ) { "Store-directory source authorization is missing a gate" }

        val regionObjects = root.getJSONArray("regions")
        require(regionObjects.length() == REQUIRED_REGIONS.size) { "Store-directory region count is invalid" }
        val regionCounts = linkedMapOf<String, Int>()
        for (index in 0 until regionObjects.length()) {
            val region = regionObjects.getJSONObject(index)
            val regionId = region.getString("regionId")
            require(regionId in REQUIRED_REGIONS && regionId !in regionCounts) { "Unsupported or duplicate store-directory region" }
            region.getString("displayName").also { require(it.isNotBlank()) }
            val bounds = region.getJSONObject("boundingBoxE7")
            val expectedBounds =
                when (regionId) {
                    GTA -> mapOf("minLatitude" to 433_500_000, "maxLatitude" to 443_500_000, "minLongitude" to -802_000_000, "maxLongitude" to -784_500_000)
                    METRO_VANCOUVER -> mapOf("minLatitude" to 489_500_000, "maxLatitude" to 495_500_000, "minLongitude" to -1_235_500_000, "maxLongitude" to -1_222_000_000)
                    else -> emptyMap()
                }
            expectedBounds.forEach { (key, value) -> require(bounds.getInt(key) == value) { "Store-directory region bounds are invalid" } }
            val count = region.getInt("recordCount")
            require(count in 0..MAX_RECORDS) { "Store-directory region count is outside bounds" }
            regionCounts[regionId] = count
        }
        require(regionCounts.keys == REQUIRED_REGIONS) { "Store-directory regions are incomplete" }
        require(regionCounts.keys.toList() == regionCounts.keys.toList().sorted()) { "Store-directory regions are not canonical" }

        val coverage = root.getJSONObject("coverage")
        val totalCount = coverage.getInt("storeRecordCount")
        require(totalCount in 0..MAX_RECORDS) { "Store-directory total count is outside bounds" }
        require(totalCount == regionCounts.values.sum()) { "Store-directory region counts do not sum" }
        require(coverage.getInt("currentOfferRecordCount") == 0) { "Store directory cannot contain current offers" }
        require(coverage.getString("currentOfferCoverage") == "NOT_INCLUDED")
        require(coverage.getString("priceCoverage") == "NOT_INCLUDED")
        require(coverage.getString("stockCoverage") == "NOT_INCLUDED")
        require(coverage.getString("availabilityCoverage") == "NOT_INCLUDED")
        val coverageRegions = coverage.getJSONObject("regionRecordCounts")
        require(coverageRegions.keys().asSequence().toSet() == REQUIRED_REGIONS)
        REQUIRED_REGIONS.forEach { region -> require(coverageRegions.getInt(region) == regionCounts.getValue(region)) }

        val content = root.getJSONObject("content")
        require(content.getString("path") == "sources/$namespaceId.jsonl")
        val contentHash = content.getString("sha256")
        require(contentHash.matches(Regex("[0-9a-f]{64}")))
        require(content.getInt("recordCount") == totalCount)
        require(sourceBytes.isNotEmpty() && sourceBytes.last() == '\n'.code.toByte()) { "Store-directory source must be newline terminated" }
        require(contentHash == sha256(sourceBytes)) { "Store-directory source content hash mismatch" }

        val integrityVerified =
            integrity.manifestHash == OfflineCatalogIntegrityState.VERIFIED &&
                integrity.signature == OfflineCatalogIntegrityState.VERIFIED
        val admissionState =
            when {
                !integrityVerified -> StoreDirectoryAdmissionState.INVALID_INTEGRITY
                generatedAt > evaluatedAtEpochMillis -> StoreDirectoryAdmissionState.FUTURE_DATED
                evaluatedAtEpochMillis - generatedAt > maximumSnapshotAgeMillis -> StoreDirectoryAdmissionState.EXPIRED
                else -> StoreDirectoryAdmissionState.ACCEPTED
            }
        return StoreDirectorySummary(
            snapshotId = snapshotId,
            generatedAtEpochMillis = generatedAt,
            observedAtEpochMillis = observedAt,
            totalRecordCount = totalCount,
            regionRecordCounts = regionCounts.toMap(),
            sourceDisplayName = sourceDisplayName,
            licenseId = licenseId,
            attribution = attribution,
            admissionState = admissionState
        )
    }

    fun load(
        manifestJson: String,
        sourceJsonl: String,
        integrity: OfflineCatalogIntegrityAssessment,
        evaluatedAtEpochMillis: Long,
        maximumSnapshotAgeMillis: Long
    ): StoreDirectoryAssetLoadResult {
        val sourceBytes = sourceJsonl.toByteArray(StandardCharsets.UTF_8)
        val summary = loadSummary(manifestJson, sourceBytes, integrity, evaluatedAtEpochMillis, maximumSnapshotAgeMillis)
        if (!summary.accepted) return StoreDirectoryAssetLoadResult(summary, emptyList())
        val lines = sourceJsonl.removeSuffix("\n").split("\n")
        require(lines.size == summary.totalRecordCount) { "Store-directory source record count mismatch" }
        val records = lines.mapIndexed { index, line -> parseRecord(JSONObject(line), index + 1) }
        require(records.map { it.recordId }.distinct().size == records.size) { "Store-directory record ids must be unique" }
        val loadedRegionCounts =
            REQUIRED_REGIONS.associateWith { region -> records.count { it.regionId == region } }
        require(loadedRegionCounts == summary.regionRecordCounts) { "Store-directory region rows do not match manifest" }
        return StoreDirectoryAssetLoadResult(summary, records)
    }

    private fun parseRecord(json: JSONObject, lineNumber: Int): StoreDirectoryRecord {
        val keys = json.keys().asSequence().toSet()
        require(keys.intersect(FORBIDDEN_RECORD_FIELDS).isEmpty()) { "Store-directory line $lineNumber contains offer fields" }
        require(keys.containsAll(RECORD_FIELDS) && keys.all { it in RECORD_FIELDS || it in OPTIONAL_RECORD_FIELDS }) { "Store-directory line $lineNumber has a non-canonical schema" }
        val recordId = json.getString("recordId")
        require(RECORD_ID.matches(recordId))
        val sourceElementId = json.getString("sourceElementId")
        require(SOURCE_ELEMENT_ID.matches(sourceElementId))
        require(recordId == "osm:" + sourceElementId.replace('/', ':'))
        val sourceElementType = json.getString("sourceElementType")
        require(sourceElementId.startsWith("$sourceElementType/"))
        val providerId = json.getString("providerId")
        val datasetNamespaceId = json.getString("datasetNamespaceId")
        require(providerId == "openstreetmap" && datasetNamespaceId == "openstreetmap-places")
        val regionId = json.getString("regionId")
        require(regionId in REQUIRED_REGIONS)
        val name = canonicalText(json.getString("name"), 240)
        val brand = json.optStringOrNull("brand")?.let { canonicalText(it, 240) }
        val operator = json.optStringOrNull("operator")?.let { canonicalText(it, 240) }
        val address = parseAddress(json.optJSONObjectOrNull("address"), lineNumber)
        val storeType = json.getString("storeType").also { require(it.isNotBlank()) }
        val latitudeE7 = json.getInt("latitudeE7")
        val longitudeE7 = json.getInt("longitudeE7")
        require(inBounds(regionId, latitudeE7, longitudeE7)) { "Store-directory line $lineNumber coordinate is outside its region" }
        val sourceSnapshotId = json.getString("sourceSnapshotId").also { require(it.isNotBlank()) }
        val licenseId = json.getString("licenseId").also { require(it.isNotBlank()) }
        val observedAt = json.getLong("observedAtEpochMillis").also { require(it > 0L) }
        require(json.getString("confidence") == "SOURCE_LISTED")
        require(json.getString("status") == "LOCATION_ONLY")
        val brandWikidataId = json.optStringOrNull("brandWikidataId")?.also { require(WIKIDATA_ID.matches(it)) }
        val operatorWikidataId = json.optStringOrNull("operatorWikidataId")?.also { require(WIKIDATA_ID.matches(it)) }
        return StoreDirectoryRecord(
            recordId,
            sourceElementId,
            sourceElementType,
            providerId,
            datasetNamespaceId,
            regionId,
            name,
            brand,
            operator,
            address,
            storeType,
            latitudeE7,
            longitudeE7,
            sourceSnapshotId,
            licenseId,
            observedAt,
            "SOURCE_LISTED",
            "LOCATION_ONLY",
            brandWikidataId,
            operatorWikidataId
        )
    }

    private fun parseAddress(json: JSONObject?, lineNumber: Int): StoreDirectoryAddress? {
        if (json == null) return null
        val allowed = setOf("housenumber", "unit", "street", "city", "postcode")
        require(json.keys().asSequence().toSet().all { it in allowed }) { "Store-directory line $lineNumber address has unsupported fields" }
        return StoreDirectoryAddress(
            houseNumber = json.optStringOrNull("housenumber")?.let { canonicalText(it, 160) },
            unit = json.optStringOrNull("unit")?.let { canonicalText(it, 160) },
            street = json.optStringOrNull("street")?.let { canonicalText(it, 160) },
            city = json.optStringOrNull("city")?.let { canonicalText(it, 160) },
            postcode = json.optStringOrNull("postcode")?.let { canonicalText(it, 160) }
        )
    }

    private fun canonicalText(value: String, maxLength: Int): String {
        val result = value.trim().split(Regex("\\s+")).joinToString(" ")
        require(result.isNotBlank() && result.length <= maxLength)
        return result
    }

    private fun inBounds(regionId: String, latitudeE7: Int, longitudeE7: Int): Boolean =
        when (regionId) {
            GTA -> latitudeE7 in 433_500_000..443_500_000 && longitudeE7 in -802_000_000..-784_500_000
            METRO_VANCOUVER -> latitudeE7 in 489_500_000..495_500_000 && longitudeE7 in -1_235_500_000..-1_222_000_000
            else -> false
        }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte -> "%02x".format(byte) }

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (!has(name) || isNull(name)) null else getString(name)

    private fun JSONObject.optJSONObjectOrNull(name: String): JSONObject? =
        if (!has(name) || isNull(name)) null else getJSONObject(name)
}
