package com.valuepilot.core

/**
 * Storage/legal boundary metadata for one evidence dataset.
 *
 * This is an engineering classification, not a legal conclusion. Keeping
 * independent namespaces makes deletion, attribution, debugging, and later
 * licence review possible without flattening every provider into one database.
 */
enum class EvidenceStorageBoundary {
    OPEN_SHARE_ALIKE,
    OPEN_GOVERNMENT,
    PROPRIETARY_RESTRICTED,
    USER_CONTROLLED,
    UNKNOWN
}

data class EvidenceDatasetNamespace(
    val id: String,
    val displayName: String,
    val licenseId: String,
    val storageBoundary: EvidenceStorageBoundary
) {
    init {
        require(id.matches(Regex("[a-z0-9][a-z0-9._-]{0,95}")))
        require(displayName.isNotBlank() && displayName.length <= 160)
        require(licenseId.isNotBlank() && licenseId.length <= 120)
    }
}

data class IndexedEvidenceClaim(
    val namespace: EvidenceDatasetNamespace,
    val claim: EvidenceClaim
)

enum class EvidenceIndexInsertResult {
    ADDED,
    DUPLICATE,
    REJECTED_CLAIM_ID_COLLISION
}

/**
 * Deterministic in-memory index that preserves source-dataset isolation.
 *
 * The index never ranks products. It stores claims under their original
 * dataset namespace, allows bounded lookup by stable product key, and delegates
 * factual conflict resolution to EvidenceFactResolver without flattening the
 * underlying provenance.
 */
class SourceIsolatedEvidenceIndex {
    private val namespaces =
        linkedMapOf<String, EvidenceDatasetNamespace>()
    private val claimsByNamespace =
        linkedMapOf<String, LinkedHashMap<String, EvidenceClaim>>()

    fun register(namespace: EvidenceDatasetNamespace) {
        val existing = namespaces[namespace.id]
        require(existing == null || existing == namespace) {
            "Dataset namespace id already registered with different metadata"
        }
        if (existing == null) {
            namespaces[namespace.id] = namespace
            claimsByNamespace[namespace.id] = linkedMapOf()
        }
    }

    fun insert(
        namespace: EvidenceDatasetNamespace,
        claim: EvidenceClaim
    ): EvidenceIndexInsertResult {
        register(namespace)
        val claims = requireNotNull(claimsByNamespace[namespace.id])
        val existing = claims[claim.claimId]

        if (existing == null) {
            claims[claim.claimId] = claim
            return EvidenceIndexInsertResult.ADDED
        }

        return if (existing == claim) {
            EvidenceIndexInsertResult.DUPLICATE
        } else {
            EvidenceIndexInsertResult.REJECTED_CLAIM_ID_COLLISION
        }
    }

    fun claimsForProduct(
        productKey: String,
        domain: EvidenceClaimDomain? = null
    ): List<IndexedEvidenceClaim> {
        require(productKey.isNotBlank())

        return namespaces.keys
            .sorted()
            .flatMap { namespaceId ->
                val namespace = requireNotNull(namespaces[namespaceId])
                claimsByNamespace[namespaceId]
                    .orEmpty()
                    .values
                    .asSequence()
                    .filter { it.scope.productKey == productKey }
                    .filter { domain == null || it.domain == domain }
                    .sortedBy { it.claimId }
                    .map { IndexedEvidenceClaim(namespace, it) }
                    .toList()
            }
    }

    /**
     * Resolve all exact facts for one product while retaining every selected
     * value's supporting source claims. Different retailer/location/channel
     * scopes remain separate resolutions; unresolved same-scope disagreements
     * remain explicitly blocking.
     */
    fun resolveFactsForProduct(
        productKey: String,
        domain: EvidenceClaimDomain? = null
    ): List<EvidenceFactResolution> =
        EvidenceFactResolver.resolve(
            claimsForProduct(
                productKey = productKey,
                domain = domain
            )
        )

    fun claimsInNamespace(
        namespaceId: String
    ): List<EvidenceClaim> =
        claimsByNamespace[namespaceId]
            .orEmpty()
            .values
            .sortedBy { it.claimId }

    fun registeredNamespaces(): List<EvidenceDatasetNamespace> =
        namespaces.values.sortedBy { it.id }

    /**
     * Remove exactly one dataset namespace and all of its claims without
     * touching any other provider. This is intentionally coarse-grained for
     * source withdrawal, licence changes, rebuilds, and correction workflows.
     */
    fun removeNamespace(namespaceId: String): Int {
        namespaces.remove(namespaceId) ?: return 0
        return claimsByNamespace.remove(namespaceId)?.size ?: 0
    }

    fun size(): Int = claimsByNamespace.values.sumOf { it.size }
}
