package com.valuepilot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingTravelResolverTest {

    private val north = ShoppingStoreKey("north")
    private val west = ShoppingStoreKey("west")
    private val userToNorth = PracticalShoppingTravelLeg(null, north)
    private val northToWest = PracticalShoppingTravelLeg(north, west)
    private val context = PracticalShoppingTravelContext("origin-session-a", "DRIVING")
    private val freshnessPolicy =
        EvidenceFreshnessPolicy(
            freshForMillis = 5_000L,
            staleAfterMillis = 20_000L,
            futureToleranceMillis = 100L
        )

    @Test
    fun `fresh provider route for exact context becomes automatic travel`() {
        val travel = ShoppingTravel(2_400L, 420L)
        val candidate =
            providerCandidate(
                id = "route-a",
                leg = userToNorth,
                context = context,
                travel = travel,
                observedAt = 9_000L
            )

        val result =
            resolve(
                legs = listOf(userToNorth),
                candidates = listOf(candidate),
                evaluatedAt = 10_000L
            )

        val resolution = result.legResolutions.single()
        assertEquals(PracticalShoppingTravelResolutionStatus.AUTO_USABLE, resolution.status)
        assertEquals(travel, resolution.selectedTravel)
        assertEquals(listOf("route-a"), resolution.supportingCandidateIds)
        assertEquals(travel, result.automaticTravel[userToNorth])
        assertEquals(EvidenceFreshness.FRESH, result.candidateEvaluations.single().freshness)
    }

    @Test
    fun `aging route remains usable but stale route does not`() {
        val aging =
            providerCandidate(
                id = "aging",
                leg = userToNorth,
                context = context,
                travel = ShoppingTravel(2_000L, 360L),
                observedAt = 2_000L
            )
        val stale =
            providerCandidate(
                id = "stale",
                leg = northToWest,
                context = context,
                travel = ShoppingTravel(1_000L, 180L),
                observedAt = 1_000L
            )

        val agingResult =
            resolve(
                legs = listOf(userToNorth),
                candidates = listOf(aging),
                evaluatedAt = 10_000L
            )
        val staleResult =
            resolve(
                legs = listOf(northToWest),
                candidates = listOf(stale),
                evaluatedAt = 30_000L
            )

        assertEquals(EvidenceFreshness.AGING, agingResult.candidateEvaluations.single().freshness)
        assertEquals(PracticalShoppingTravelResolutionStatus.AUTO_USABLE, agingResult.legResolutions.single().status)
        val staleEvaluation = staleResult.candidateEvaluations.single()
        assertEquals(EvidenceFreshness.STALE, staleEvaluation.freshness)
        assertEquals(setOf(PracticalShoppingTravelCandidateBlocker.STALE), staleEvaluation.blockers)
        assertEquals(PracticalShoppingTravelResolutionStatus.UNRESOLVED, staleResult.legResolutions.single().status)
    }

    @Test
    fun `future dated route is blocked`() {
        val future =
            providerCandidate(
                id = "future",
                leg = userToNorth,
                context = context,
                travel = ShoppingTravel(2_000L, 360L),
                observedAt = 11_000L
            )

        val result = resolve(listOf(userToNorth), listOf(future), evaluatedAt = 10_000L)

        val evaluation = result.candidateEvaluations.single()
        assertEquals(EvidenceFreshness.FUTURE_DATED, evaluation.freshness)
        assertEquals(setOf(PracticalShoppingTravelCandidateBlocker.FUTURE_DATED), evaluation.blockers)
        assertEquals(PracticalShoppingTravelResolutionStatus.UNRESOLVED, result.legResolutions.single().status)
    }

    @Test
    fun `route from different origin or travel mode context cannot be reused`() {
        val oldContext = PracticalShoppingTravelContext("origin-session-old", "WALKING")
        val candidate =
            providerCandidate(
                id = "old-route",
                leg = userToNorth,
                context = oldContext,
                travel = ShoppingTravel(1_500L, 900L),
                observedAt = 9_500L
            )

        val result = resolve(listOf(userToNorth), listOf(candidate), evaluatedAt = 10_000L)

        val evaluation = result.candidateEvaluations.single()
        assertEquals(setOf(PracticalShoppingTravelCandidateBlocker.CONTEXT_MISMATCH), evaluation.blockers)
        assertFalse(userToNorth in result.automaticTravel)
    }

    @Test
    fun `straight line approximation stays a suggestion even when fresh`() {
        val suggestion =
            PracticalShoppingTravelCandidate(
                candidateId = "straight-line",
                leg = userToNorth,
                context = context,
                travel = ShoppingTravel(1_000L, 60L),
                relationship = PracticalShoppingTravelRelationship.APPROXIMATE_SUGGESTION,
                observedAtEpochMillis = 9_900L,
                basisId = "straight-line-distance",
                providerId = EvidenceProviderId("map-metadata")
            )

        val result = resolve(listOf(userToNorth), listOf(suggestion), evaluatedAt = 10_000L)

        val resolution = result.legResolutions.single()
        assertEquals(PracticalShoppingTravelResolutionStatus.NEEDS_EXPLICIT_SELECTION, resolution.status)
        assertNull(resolution.selectedTravel)
        assertEquals(listOf("straight-line"), resolution.suggestionCandidateIds)
        assertTrue(result.automaticTravel.isEmpty())
    }

    @Test
    fun `matching route estimates may corroborate one travel value`() {
        val travel = ShoppingTravel(2_200L, 400L)
        val first =
            providerCandidate("first", userToNorth, context, travel, observedAt = 9_000L, provider = "router-a")
        val second =
            providerCandidate("second", userToNorth, context, travel, observedAt = 8_000L, provider = "router-b")

        val result = resolve(listOf(userToNorth), listOf(second, first), evaluatedAt = 10_000L)

        val resolution = result.legResolutions.single()
        assertEquals(PracticalShoppingTravelResolutionStatus.AUTO_USABLE, resolution.status)
        assertEquals(travel, resolution.selectedTravel)
        assertEquals(listOf("first", "second"), resolution.supportingCandidateIds)
    }

    @Test
    fun `conflicting fresh route estimates require explicit selection rather than shortest winner`() {
        val fast =
            providerCandidate(
                id = "fast",
                leg = userToNorth,
                context = context,
                travel = ShoppingTravel(2_000L, 300L),
                observedAt = 9_000L,
                provider = "router-a"
            )
        val slow =
            providerCandidate(
                id = "slow",
                leg = userToNorth,
                context = context,
                travel = ShoppingTravel(1_800L, 420L),
                observedAt = 9_500L,
                provider = "router-b"
            )

        val result = resolve(listOf(userToNorth), listOf(fast, slow), evaluatedAt = 10_000L)

        val resolution = result.legResolutions.single()
        assertEquals(PracticalShoppingTravelResolutionStatus.NEEDS_EXPLICIT_SELECTION, resolution.status)
        assertNull(resolution.selectedTravel)
        assertEquals(listOf("fast", "slow"), resolution.supportingCandidateIds)
        assertFalse(userToNorth in result.automaticTravel)
    }

    @Test
    fun `one exact route is not displaced by approximate suggestion`() {
        val exactTravel = ShoppingTravel(2_000L, 360L)
        val exact = providerCandidate("route", userToNorth, context, exactTravel, 9_000L)
        val suggestion =
            PracticalShoppingTravelCandidate(
                candidateId = "approx",
                leg = userToNorth,
                context = context,
                travel = ShoppingTravel(1_700L, 300L),
                relationship = PracticalShoppingTravelRelationship.APPROXIMATE_SUGGESTION,
                observedAtEpochMillis = 9_500L,
                basisId = "approximation"
            )

        val result = resolve(listOf(userToNorth), listOf(suggestion, exact), evaluatedAt = 10_000L)

        val resolution = result.legResolutions.single()
        assertEquals(PracticalShoppingTravelResolutionStatus.AUTO_USABLE, resolution.status)
        assertEquals(exactTravel, resolution.selectedTravel)
        assertEquals(listOf("route"), resolution.supportingCandidateIds)
        assertEquals(listOf("approx"), resolution.suggestionCandidateIds)
    }

    @Test
    fun `candidate for undeclared leg is blocked`() {
        val candidate =
            providerCandidate(
                id = "wrong-leg",
                leg = northToWest,
                context = context,
                travel = ShoppingTravel(1_000L, 180L),
                observedAt = 9_000L
            )

        val result = resolve(listOf(userToNorth), listOf(candidate), evaluatedAt = 10_000L)

        assertEquals(
            setOf(PracticalShoppingTravelCandidateBlocker.LEG_NOT_REQUESTED),
            result.candidateEvaluations.single().blockers
        )
        assertEquals(PracticalShoppingTravelResolutionStatus.UNRESOLVED, result.legResolutions.single().status)
    }

    @Test
    fun `routing provider estimate requires provider provenance`() {
        assertThrows(IllegalArgumentException::class.java) {
            PracticalShoppingTravelCandidate(
                candidateId = "missing-provider",
                leg = userToNorth,
                context = context,
                travel = ShoppingTravel(1_000L, 180L),
                relationship = PracticalShoppingTravelRelationship.ROUTING_PROVIDER_ESTIMATE,
                observedAtEpochMillis = 9_000L,
                basisId = "provider-route",
                providerId = null
            )
        }
    }

    @Test
    fun `per leg candidate set is bounded`() {
        val candidates =
            (0..16).map { index ->
                providerCandidate(
                    id = "candidate-$index",
                    leg = userToNorth,
                    context = context,
                    travel = ShoppingTravel(1_000L + index, 180L + index),
                    observedAt = 9_000L,
                    provider = "provider-$index"
                )
            }

        assertThrows(IllegalArgumentException::class.java) {
            resolve(listOf(userToNorth), candidates, evaluatedAt = 10_000L)
        }
    }

    private fun resolve(
        legs: List<PracticalShoppingTravelLeg>,
        candidates: List<PracticalShoppingTravelCandidate>,
        evaluatedAt: Long
    ): PracticalShoppingTravelResolutionResult =
        PracticalShoppingTravelResolver.resolve(
            context = context,
            legs = legs,
            candidates = candidates,
            evaluatedAtEpochMillis = evaluatedAt,
            freshnessPolicy = freshnessPolicy
        )

    private fun providerCandidate(
        id: String,
        leg: PracticalShoppingTravelLeg,
        context: PracticalShoppingTravelContext,
        travel: ShoppingTravel,
        observedAt: Long,
        provider: String = "router"
    ): PracticalShoppingTravelCandidate =
        PracticalShoppingTravelCandidate(
            candidateId = id,
            leg = leg,
            context = context,
            travel = travel,
            relationship = PracticalShoppingTravelRelationship.ROUTING_PROVIDER_ESTIMATE,
            observedAtEpochMillis = observedAt,
            basisId = "basis-$id",
            providerId = EvidenceProviderId(provider)
        )
}
