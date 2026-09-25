package com.valuepilot.core

/**
 * Exact factual scope used to resolve competing assertions.
 *
 * Claims with different domains or scopes are deliberately never placed in the
 * same conflict set. Thus a physical-store observation, a web offer, and a
 * market benchmark remain independent facts even when they share a GTIN.
 */
data class EvidenceFactKey(
    val domain: EvidenceClaimDomain,
    val scope: EvidenceClaimScope
)

enum class EvidenceFactResolutionStatus {
    RESOLVED,
    UNRESOLVED_CONFLICT
}

data class EvidenceFactResolution(
    val key: EvidenceFactKey,
    val status: EvidenceFactResolutionStatus,
    val selectedValueFingerprint: String?,
    val supportingClaims: List<IndexedEvidenceClaim>,
    val conflictingClaims: List<IndexedEvidenceClaim>
) {
    init {
        when (status) {
            EvidenceFactResolutionStatus.RESOLVED -> {
                require(!selectedValueFingerprint.isNullOrBlank())
                require(supportingClaims.isNotEmpty())
                require(conflictingClaims.isEmpty())
                require(
                    supportingClaims.all {
                        it.claim.valueFingerprint == selectedValueFingerprint
                    }
                )
            }

            EvidenceFactResolutionStatus.UNRESOLVED_CONFLICT -> {
                require(selectedValueFingerprint == null)
                require(supportingClaims.isEmpty())
                require(conflictingClaims.size >= 2)
                require(
                    conflictingClaims
                        .map { it.claim.valueFingerprint }
                        .toSet()
                        .size >= 2
                )
            }
        }
    }

    val blocksRanking: Boolean
        get() = status == EvidenceFactResolutionStatus.UNRESOLVED_CONFLICT
}

/**
 * Deterministic N-source conflict resolver.
 *
 * Pairwise EvidenceConflictPolicy decisions are used only within an exact fact
 * key. A claim that loses to any stronger/newer claim is dominated. If all
 * undominated claims agree on one canonical value, that factual value resolves.
 * If multiple different values remain undominated, ValuePilot fails closed and
 * exposes an unresolved conflict instead of averaging, voting, or guessing.
 *
 * This matters for 3+ sources: a conflict between two weak sources does not
 * block a third authoritative source that deterministically defeats both.
 */
object EvidenceFactResolver {

    fun resolve(
        claims: Collection<IndexedEvidenceClaim>
    ): List<EvidenceFactResolution> =
        claims
            .groupBy {
                EvidenceFactKey(
                    domain = it.claim.domain,
                    scope = it.claim.scope
                )
            }
            .entries
            .sortedBy { stableKey(it.key) }
            .map { (key, groupedClaims) ->
                resolveGroup(key, groupedClaims)
            }

    private fun resolveGroup(
        key: EvidenceFactKey,
        claims: List<IndexedEvidenceClaim>
    ): EvidenceFactResolution {
        require(claims.isNotEmpty())

        if (claims.size == 1) {
            return resolved(
                key = key,
                value = claims.single().claim.valueFingerprint,
                claims = claims
            )
        }

        val ordered = claims.sortedWith(indexedClaimComparator)
        val dominated = mutableSetOf<Int>()
        var structuralConflict = false

        for (leftIndex in ordered.indices) {
            for (rightIndex in (leftIndex + 1) until ordered.size) {
                val left = ordered[leftIndex]
                val right = ordered[rightIndex]

                if (left.claim.valueFingerprint == right.claim.valueFingerprint) {
                    continue
                }

                when (
                    EvidenceConflictPolicy.resolve(
                        left = left.claim,
                        right = right.claim
                    ).relationship
                ) {
                    EvidenceConflictRelationship.PREFER_LEFT ->
                        dominated += rightIndex

                    EvidenceConflictRelationship.PREFER_RIGHT ->
                        dominated += leftIndex

                    EvidenceConflictRelationship.UNRESOLVED_CONFLICT ->
                        Unit

                    EvidenceConflictRelationship.SAME_CLAIM ->
                        Unit

                    EvidenceConflictRelationship.COEXISTS,
                    EvidenceConflictRelationship.INCOMPARABLE ->
                        structuralConflict = true
                }
            }
        }

        val undominated = ordered.filterIndexed { index, _ -> index !in dominated }
        val remainingValues = undominated.map { it.claim.valueFingerprint }.toSet()

        if (!structuralConflict && remainingValues.size == 1) {
            val selectedValue = remainingValues.single()
            val supporters = ordered.filter {
                it.claim.valueFingerprint == selectedValue
            }
            return resolved(
                key = key,
                value = selectedValue,
                claims = supporters
            )
        }

        val conflicts =
            if (remainingValues.size >= 2) {
                undominated
            } else {
                ordered
            }

        return EvidenceFactResolution(
            key = key,
            status = EvidenceFactResolutionStatus.UNRESOLVED_CONFLICT,
            selectedValueFingerprint = null,
            supportingClaims = emptyList(),
            conflictingClaims = conflicts
        )
    }

    private fun resolved(
        key: EvidenceFactKey,
        value: String,
        claims: List<IndexedEvidenceClaim>
    ) =
        EvidenceFactResolution(
            key = key,
            status = EvidenceFactResolutionStatus.RESOLVED,
            selectedValueFingerprint = value,
            supportingClaims = claims.sortedWith(indexedClaimComparator),
            conflictingClaims = emptyList()
        )

    private val indexedClaimComparator =
        compareBy<IndexedEvidenceClaim>(
            { it.namespace.id },
            { it.claim.claimId },
            { it.claim.valueFingerprint },
            { it.claim.observedAtEpochMillis }
        )

    private fun stableKey(key: EvidenceFactKey): String =
        listOf(
            key.domain.name,
            key.scope.productKey,
            key.scope.merchantKey.orEmpty(),
            key.scope.locationKey.orEmpty(),
            key.scope.commerceChannelKey.orEmpty(),
            key.scope.currencyCode.orEmpty()
        ).joinToString("|")
}
