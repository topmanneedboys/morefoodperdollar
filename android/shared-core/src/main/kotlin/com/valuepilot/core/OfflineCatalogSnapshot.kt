package com.valuepilot.core

/**
 * One source-isolated contribution to an offline catalog snapshot.
 *
 * The content hash identifies the exact source artifact admitted by the host.
 * This record contains no price, availability or ranking fields. Authorization
 * is evaluated for product discovery only; offer evidence must independently
 * pass the existing production offer gates.
 */
data class OfflineCatalogSnapshotSource(
    val namespace: EvidenceDatasetNamespace,
    val snapshot: ProductionDatasetSnapshotRef,
    val authorization: ProviderProductionAuthorizationAssessment,
    val recordCount: Int,
    val contentSha256: String,
    val acquiredAtEpochMillis: Long,
    val sourcePublishedAtEpochMillis: Long? = null
) {
    init {
        require(snapshot.datasetNamespaceId == namespace.id) {
            "Snapshot dataset scope must match its isolated namespace"
        }
        require(authorization.providerId == snapshot.providerId) {
            "Authorization provider scope must match the snapshot"
        }
        require(authorization.datasetNamespaceId == namespace.id) {
            "Authorization dataset scope must match its isolated namespace"
        }
        require(recordCount in 1..MAX_SOURCE_RECORDS)
        require(contentSha256.matches(SHA_256_PATTERN)) {
            "Content hash must be lowercase SHA-256 hex"
        }
        require(acquiredAtEpochMillis > 0L)
        sourcePublishedAtEpochMillis?.let { publishedAt ->
            require(publishedAt > 0L)
            require(publishedAt <= acquiredAtEpochMillis) {
                "Source publication cannot be later than acquisition"
            }
        }
    }

    companion object {
        const val MAX_SOURCE_RECORDS = 50_000
        private val SHA_256_PATTERN = Regex("[0-9a-f]{64}")
    }
}

/**
 * Explicit role of an offline catalog snapshot.
 *
 * The only currently admissible role contains product identity/search metadata
 * and never current offers.  A future offer snapshot would need a separate
 * contract and independently verified authority rather than reusing this role.
 */
enum class OfflineCatalogRole {
    IDENTITY_ONLY
}

/**
 * Deterministic manifest metadata for one bounded regional offline catalog.
 *
 * Sources remain independent entries so attribution, withdrawal and licence
 * handling never require flattening incompatible datasets into one authority.
 */
data class OfflineCatalogSnapshotManifest(
    val schemaVersion: Int,
    val catalogRole: OfflineCatalogRole = OfflineCatalogRole.IDENTITY_ONLY,
    val snapshotId: String,
    val regionId: String,
    val generatedAtEpochMillis: Long,
    val sources: List<OfflineCatalogSnapshotSource>
) {
    init {
        require(schemaVersion > 0)
        require(catalogRole == OfflineCatalogRole.IDENTITY_ONLY)
        require(snapshotId.matches(ID_PATTERN))
        require(regionId.matches(ID_PATTERN))
        require(generatedAtEpochMillis > 0L)
        require(sources.isNotEmpty())
        require(sources.size <= MAX_SOURCES)
        require(sources.all { it.acquiredAtEpochMillis <= generatedAtEpochMillis }) {
            "Catalog sources must be acquired before the manifest is generated"
        }

        val namespaceIds = sources.map { it.namespace.id }
        require(namespaceIds.size == namespaceIds.toSet().size) {
            "A dataset namespace may appear at most once per catalog snapshot"
        }

        val snapshotRefs = sources.map { it.snapshot }
        require(snapshotRefs.size == snapshotRefs.toSet().size) {
            "A source snapshot may appear at most once per catalog snapshot"
        }

        require(totalRecordCount <= MAX_TOTAL_RECORDS) {
            "Catalog snapshot exceeds the bounded record limit"
        }
    }

    val totalRecordCount: Int
        get() = sources.sumOf { it.recordCount }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val MAX_SOURCES = 32
        const val MAX_TOTAL_RECORDS = 50_000
        private val ID_PATTERN = Regex("[a-z0-9][a-z0-9._-]{0,127}")
    }
}

enum class OfflineCatalogIntegrityState {
    VERIFIED,
    FAILED,
    UNKNOWN
}

/**
 * Host-supplied cryptographic verification result for the exact manifest and
 * its source artifacts. Shared core owns no filesystem, key store or crypto I/O.
 */
data class OfflineCatalogIntegrityAssessment(
    val manifestHash: OfflineCatalogIntegrityState,
    val signature: OfflineCatalogIntegrityState,
    val basisId: String? = null
) {
    init {
        basisId?.let {
            require(it.isNotBlank())
            require(it.length <= 240)
        }
        if (
            manifestHash == OfflineCatalogIntegrityState.VERIFIED &&
            signature == OfflineCatalogIntegrityState.VERIFIED
        ) {
            require(!basisId.isNullOrBlank()) {
                "Verified catalog integrity requires an auditable basis"
            }
        }
    }
}

data class OfflineCatalogAdmissionRequest(
    val manifest: OfflineCatalogSnapshotManifest,
    val integrity: OfflineCatalogIntegrityAssessment,
    val evaluatedAtEpochMillis: Long,
    val maximumSnapshotAgeMillis: Long,
    val lastKnownGoodGeneratedAtEpochMillis: Long? = null
) {
    init {
        require(evaluatedAtEpochMillis > 0L)
        require(maximumSnapshotAgeMillis > 0L)
        lastKnownGoodGeneratedAtEpochMillis?.let { require(it > 0L) }
    }
}

enum class OfflineCatalogAdmissionBlocker {
    UNSUPPORTED_SCHEMA_VERSION,
    MANIFEST_HASH_NOT_VERIFIED,
    SIGNATURE_NOT_VERIFIED,
    FUTURE_DATED_SNAPSHOT,
    EXPIRED_SNAPSHOT,
    OLDER_THAN_LAST_KNOWN_GOOD,
    SOURCE_NOT_AUTHORIZED_FOR_PRODUCT_DISCOVERY
}

data class OfflineCatalogAdmissionDecision(
    val accepted: Boolean,
    val blockers: Set<OfflineCatalogAdmissionBlocker>,
    val sourceAuthorization: Map<String, ProductionActivationDecision>
) {
    init {
        require(accepted == blockers.isEmpty())
    }
}

/**
 * Fail-closed admission for a signed, bounded offline catalog snapshot.
 *
 * Product-discovery authorization is deliberately weaker than offer authority
 * but still requires explicit access, display, cache, index, mobile-app and
 * retention/deletion permission for every isolated source. This evaluator never
 * promotes catalog data into price, availability or exact shopping intent.
 */
object OfflineCatalogAdmissionEvaluator {

    fun evaluate(request: OfflineCatalogAdmissionRequest): OfflineCatalogAdmissionDecision {
        val blockers = linkedSetOf<OfflineCatalogAdmissionBlocker>()
        val manifest = request.manifest

        if (manifest.schemaVersion != OfflineCatalogSnapshotManifest.CURRENT_SCHEMA_VERSION) {
            blockers += OfflineCatalogAdmissionBlocker.UNSUPPORTED_SCHEMA_VERSION
        }
        if (request.integrity.manifestHash != OfflineCatalogIntegrityState.VERIFIED) {
            blockers += OfflineCatalogAdmissionBlocker.MANIFEST_HASH_NOT_VERIFIED
        }
        if (request.integrity.signature != OfflineCatalogIntegrityState.VERIFIED) {
            blockers += OfflineCatalogAdmissionBlocker.SIGNATURE_NOT_VERIFIED
        }

        if (manifest.generatedAtEpochMillis > request.evaluatedAtEpochMillis) {
            blockers += OfflineCatalogAdmissionBlocker.FUTURE_DATED_SNAPSHOT
        } else if (
            request.evaluatedAtEpochMillis - manifest.generatedAtEpochMillis >
                request.maximumSnapshotAgeMillis
        ) {
            blockers += OfflineCatalogAdmissionBlocker.EXPIRED_SNAPSHOT
        }

        if (
            request.lastKnownGoodGeneratedAtEpochMillis != null &&
            manifest.generatedAtEpochMillis < request.lastKnownGoodGeneratedAtEpochMillis
        ) {
            blockers += OfflineCatalogAdmissionBlocker.OLDER_THAN_LAST_KNOWN_GOOD
        }

        val sourceAuthorization =
            manifest.sources
                .sortedBy { it.namespace.id }
                .associate { source ->
                    source.namespace.id to
                        ProductionAuthorizationEvaluator.evaluate(
                            assessment = source.authorization,
                            profile =
                                ProductionActivationProfiles
                                    .CONSUMER_MOBILE_PRODUCT_DISCOVERY
                        )
                }

        if (sourceAuthorization.values.any { !it.authorized }) {
            blockers +=
                OfflineCatalogAdmissionBlocker
                    .SOURCE_NOT_AUTHORIZED_FOR_PRODUCT_DISCOVERY
        }

        return OfflineCatalogAdmissionDecision(
            accepted = blockers.isEmpty(),
            blockers = blockers,
            sourceAuthorization = sourceAuthorization
        )
    }
}
