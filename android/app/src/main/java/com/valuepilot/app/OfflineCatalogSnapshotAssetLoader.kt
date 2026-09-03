package com.valuepilot.app

import android.content.Context
import com.valuepilot.core.EvidenceDatasetNamespace
import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.EvidenceStorageBoundary
import com.valuepilot.core.OfflineCatalogAdmissionDecision
import com.valuepilot.core.OfflineCatalogAdmissionEvaluator
import com.valuepilot.core.OfflineCatalogAdmissionRequest
import com.valuepilot.core.OfflineCatalogDiscoveryEngine
import com.valuepilot.core.OfflineCatalogDiscoveryIndex
import com.valuepilot.core.OfflineCatalogDiscoveryRequest
import com.valuepilot.core.OfflineCatalogDiscoveryResult
import com.valuepilot.core.OfflineCatalogIntegrityAssessment
import com.valuepilot.core.OfflineCatalogProduct
import com.valuepilot.core.OfflineCatalogSnapshotManifest
import com.valuepilot.core.OfflineCatalogSnapshotSource
import com.valuepilot.core.ProductionAuthorizationGate
import com.valuepilot.core.ProductionAuthorizationState
import com.valuepilot.core.ProductionDatasetSnapshotRef
import com.valuepilot.core.ProductionGateAssessment
import com.valuepilot.core.ProviderProductionAuthorizationAssessment
import com.valuepilot.core.SourceProductIdentity
import com.valuepilot.core.TextCanonicalizer
import java.io.InputStream
import java.security.MessageDigest
import org.json.JSONObject

/**
 * Result of loading one explicit local catalog artifact.
 *
 * A blocked admission is still returned for diagnostics, but its products are
 * always empty so callers cannot accidentally use an unsigned, stale, or
 * unauthorized snapshot. This loader reads only caller-supplied asset text;
 * it performs no network or provider work.
 */
data class OfflineCatalogAssetLoadResult(
    val manifest: OfflineCatalogSnapshotManifest,
    val admission: OfflineCatalogAdmissionDecision,
    val products: List<OfflineCatalogProduct>
) {
    private val discoveryProducts = products.toList()
    private val discoveryIndex: OfflineCatalogDiscoveryIndex? by lazy {
        discoveryProducts.takeIf { admission.accepted }?.let(OfflineCatalogDiscoveryIndex::build)
    }

    init {
        require(admission.accepted || products.isEmpty()) {
            "Blocked catalog admissions must not expose products"
        }
    }

    /**
     * Discover identity-only products from this admitted snapshot.
     *
     * Blocked snapshots intentionally behave like an empty catalog. This
     * keeps consumers from branching around the admission boundary and avoids
     * ever turning an unverified asset into a visible product suggestion.
     */
    fun discover(
        rawQuery: String,
        canonicalizer: TextCanonicalizer,
        maxResults: Int = OfflineCatalogDiscoveryRequest.MAX_RESULTS
    ): OfflineCatalogDiscoveryResult {
        val index = discoveryIndex
        if (index != null) {
            return index.discover(rawQuery, canonicalizer, maxResults)
        }
        return OfflineCatalogDiscoveryEngine.discover(
            request =
                OfflineCatalogDiscoveryRequest(
                    rawQuery = rawQuery,
                    candidates = emptyList(),
                    maxResults = maxResults
                ),
            canonicalizer = canonicalizer
        )
    }
}

/**
 * Decode and admit the canonical JSON/JSONL contract emitted by
 * [tools/build_offline_catalog_snapshot.py]. Android owns the local text I/O;
 * shared-core remains the authority for scope, rights, freshness and rollback.
 */
object OfflineCatalogSnapshotAssetLoader {

    private val PRODUCT_FIELDS =
        setOf(
            "recordId",
            "providerId",
            "datasetNamespaceId",
            "sourceIdentity",
            "displayName",
            "brand",
            "canonicalSearchName",
            "canonicalSearchBrand",
            "canonicalSearchAliases"
        )
    private val IDENTITY_FIELDS = setOf("providerItemId", "sku", "gtin")

    fun load(
        manifestJson: String,
        sourceJsonByNamespace: Map<String, String>,
        integrity: OfflineCatalogIntegrityAssessment,
        evaluatedAtEpochMillis: Long,
        maximumSnapshotAgeMillis: Long,
        lastKnownGoodGeneratedAtEpochMillis: Long? = null
    ): OfflineCatalogAssetLoadResult {
        require(manifestJson.isNotBlank()) { "Catalog manifest must not be blank" }
        val root = JSONObject(manifestJson)
        val sourceObjects = root.getJSONArray("sources")
        val expectedNamespaces = buildSet {
            for (index in 0 until sourceObjects.length()) {
                add(sourceObjects.getJSONObject(index).getJSONObject("namespace").getString("id"))
            }
        }
        require(sourceJsonByNamespace.keys == expectedNamespaces) {
            "Catalog source assets must exactly match manifest namespaces"
        }

        val sources =
            (0 until sourceObjects.length())
                .map { index -> parseSource(sourceObjects.getJSONObject(index), sourceJsonByNamespace) }
                .sortedBy { it.namespace.id }
        val manifest =
            OfflineCatalogSnapshotManifest(
                schemaVersion = root.getInt("schemaVersion"),
                snapshotId = root.getString("snapshotId"),
                regionId = root.getString("regionId"),
                generatedAtEpochMillis = root.getLong("generatedAtEpochMillis"),
                sources = sources
            )

        root.optJSONObject("coverage")?.let { coverage ->
            require(coverage.getInt("catalogRecordCount") == manifest.totalRecordCount) {
                "Catalog coverage count does not match source records"
            }
        }

        val admission =
            OfflineCatalogAdmissionEvaluator.evaluate(
                OfflineCatalogAdmissionRequest(
                    manifest = manifest,
                    integrity = integrity,
                    evaluatedAtEpochMillis = evaluatedAtEpochMillis,
                    maximumSnapshotAgeMillis = maximumSnapshotAgeMillis,
                    lastKnownGoodGeneratedAtEpochMillis = lastKnownGoodGeneratedAtEpochMillis
                )
            )

        val products =
            if (admission.accepted) {
                sources
                    .flatMap { source -> parseProducts(source, sourceJsonByNamespace.getValue(source.namespace.id)) }
                    .sortedWith(compareBy<OfflineCatalogProduct>({ it.dataset.id }, { it.recordId }))
                    .also { loaded ->
                        require(loaded.map { it.recordId }.distinct().size == loaded.size) {
                            "Catalog record ids must be unique across source namespaces"
                        }
                    }
            } else {
                emptyList()
            }

        return OfflineCatalogAssetLoadResult(
            manifest = manifest,
            admission = admission,
            products = products
        )
    }

    /** Read one manifest asset and explicit source assets from the APK bundle. */
    fun loadFromAssets(
        context: Context,
        manifestAssetPath: String,
        sourceAssetPathByNamespace: Map<String, String>,
        integrity: OfflineCatalogIntegrityAssessment,
        evaluatedAtEpochMillis: Long,
        maximumSnapshotAgeMillis: Long,
        lastKnownGoodGeneratedAtEpochMillis: Long? = null
    ): OfflineCatalogAssetLoadResult {
        val manifestJson = context.assets.open(manifestAssetPath).readUtf8()
        val sources =
            sourceAssetPathByNamespace.mapValues { (_, path) ->
                context.assets.open(path).readUtf8()
            }
        return load(
            manifestJson = manifestJson,
            sourceJsonByNamespace = sources,
            integrity = integrity,
            evaluatedAtEpochMillis = evaluatedAtEpochMillis,
            maximumSnapshotAgeMillis = maximumSnapshotAgeMillis,
            lastKnownGoodGeneratedAtEpochMillis = lastKnownGoodGeneratedAtEpochMillis
        )
    }

    private fun parseSource(
        sourceJson: JSONObject,
        sourceJsonByNamespace: Map<String, String>
    ): OfflineCatalogSnapshotSource {
        val namespaceJson = sourceJson.getJSONObject("namespace")
        val namespace =
            EvidenceDatasetNamespace(
                id = namespaceJson.getString("id"),
                displayName = namespaceJson.getString("displayName"),
                licenseId = namespaceJson.getString("licenseId"),
                storageBoundary =
                    EvidenceStorageBoundary.valueOf(namespaceJson.getString("storageBoundary"))
            )
        val snapshotJson = sourceJson.getJSONObject("snapshot")
        val snapshot =
            ProductionDatasetSnapshotRef(
                providerId = EvidenceProviderId(snapshotJson.getString("providerId")),
                datasetNamespaceId = snapshotJson.getString("datasetNamespaceId"),
                snapshotId = snapshotJson.getString("snapshotId")
            )
        require(snapshot.datasetNamespaceId == namespace.id) {
            "Catalog source snapshot namespace mismatch"
        }
        val authorizationJson = sourceJson.getJSONObject("authorization")
        val gatesJson = authorizationJson.getJSONArray("gates")
        val gates =
            (0 until gatesJson.length()).map { index ->
                val gateJson = gatesJson.getJSONObject(index)
                ProductionGateAssessment(
                    gate = ProductionAuthorizationGate.valueOf(gateJson.getString("gate")),
                    state = ProductionAuthorizationState.valueOf(gateJson.getString("state")),
                    basisId = gateJson.optStringOrNull("basisId")
                )
            }
        val authorization =
            ProviderProductionAuthorizationAssessment(
                providerId = EvidenceProviderId(authorizationJson.getString("providerId")),
                datasetNamespaceId = authorizationJson.getString("datasetNamespaceId"),
                gates = gates
            )
        require(authorization.providerId == snapshot.providerId) {
            "Catalog source authorization provider mismatch"
        }
        require(authorization.datasetNamespaceId == namespace.id) {
            "Catalog source authorization namespace mismatch"
        }
        val namespaceId = namespace.id
        val sourceText = sourceJsonByNamespace.getValue(namespaceId)
        require(sourceText.isNotEmpty() && sourceText.endsWith("\n")) {
            "Catalog source $namespaceId must be newline-terminated JSONL"
        }
        val expectedHash = sourceJson.getString("contentSha256")
        require(expectedHash == sha256(sourceText.toByteArray(Charsets.UTF_8))) {
            "Catalog source content hash mismatch: $namespaceId"
        }

        return OfflineCatalogSnapshotSource(
            namespace = namespace,
            snapshot = snapshot,
            authorization = authorization,
            recordCount = sourceJson.getInt("recordCount"),
            contentSha256 = expectedHash,
            acquiredAtEpochMillis = sourceJson.getLong("acquiredAtEpochMillis"),
            sourcePublishedAtEpochMillis = sourceJson.optLongOrNull("sourcePublishedAtEpochMillis")
        )
    }

    private fun parseProducts(
        source: OfflineCatalogSnapshotSource,
        sourceText: String
    ): List<OfflineCatalogProduct> {
        val lines = sourceText.removeSuffix("\n").split("\n")
        require(lines.size == source.recordCount) {
            "Catalog source record count mismatch: ${source.namespace.id}"
        }
        return lines.mapIndexed { index, line ->
            require(line.isNotBlank()) { "Catalog source contains a blank line at ${index + 1}" }
            val json = JSONObject(line)
            require(json.keys().asSequence().toSet() == PRODUCT_FIELDS) {
                "Catalog records must contain only catalog identity/search fields"
            }
            require(json.getString("providerId") == source.snapshot.providerId.value)
            require(json.getString("datasetNamespaceId") == source.namespace.id)
            val displayName = json.getString("displayName")
            val canonicalSearchName = json.getString("canonicalSearchName")
            require(canonicalSearchName == JvmTextCanonicalizer.search(displayName)) {
                "Catalog record search name is not deterministic"
            }
            val identityJson = json.getJSONObject("sourceIdentity")
            require(
                IDENTITY_FIELDS.containsAll(
                    identityJson.keys().asSequence().toSet()
                )
            )
            val identity =
                SourceProductIdentity(
                    providerItemId = identityJson.optStringOrNull("providerItemId"),
                    sku = identityJson.optStringOrNull("sku"),
                    gtin = identityJson.optStringOrNull("gtin")
                )
            val brand = json.optStringOrNull("brand")
            val canonicalSearchBrand = json.optStringOrNull("canonicalSearchBrand")
            require(
                canonicalSearchBrand == brand?.let(JvmTextCanonicalizer::search)
            ) {
                "Catalog record brand search text is not deterministic"
            }
            val aliasesJson = json.getJSONArray("canonicalSearchAliases")
            val aliases = (0 until aliasesJson.length()).map { aliasesJson.getString(it) }
            require(aliases == aliases.distinct().sorted()) {
                "Catalog record aliases must be canonical and sorted"
            }
            require(aliases.all { alias -> alias == JvmTextCanonicalizer.search(alias) }) {
                "Catalog record aliases must be deterministic search tokens"
            }
            OfflineCatalogProduct(
                recordId = json.getString("recordId"),
                providerId = source.snapshot.providerId,
                dataset = source.namespace,
                sourceIdentity = identity,
                displayName = displayName,
                brand = brand,
                canonicalSearchName = canonicalSearchName,
                canonicalSearchBrand = canonicalSearchBrand,
                canonicalSearchAliases = aliases
            )
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (!has(name) || isNull(name)) null else getString(name)

    private fun JSONObject.optLongOrNull(name: String): Long? =
        if (!has(name) || isNull(name)) null else getLong(name)

    private fun InputStream.readUtf8(): String =
        bufferedReader(Charsets.UTF_8).use { it.readText() }
}
