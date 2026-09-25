package com.valuepilot.core

/**
 * Provenance basis for a provider dataset's offer-country scope.
 *
 * Currency and general advertiser context are deliberately weak. ValuePilot may
 * preserve those hints, but they cannot establish that an offer is intended for
 * a country. Only explicit/documented market evidence can validate country scope.
 */
enum class ImportedOfferCountryBasis {
    EXPLICIT_OFFER_COUNTRY,
    EXPLICIT_DATASET_COUNTRY,
    DOCUMENTED_DATASET_MARKET,
    CURRENCY_ONLY,
    ADVERTISER_CONTEXT_ONLY,
    INFERRED,
    UNKNOWN
}

/**
 * Provider-neutral country-scope evidence for one isolated provider dataset.
 *
 * countryCode uses an ISO-style two-letter uppercase representation. basisId is
 * an opaque non-secret reference to the source/document that supports the basis.
 * This type performs no geolocation, network access, currency inference, or
 * retailer-specific logic.
 */
data class ProviderDatasetOfferGeography(
    val providerId: EvidenceProviderId,
    val datasetNamespaceId: String,
    val countryCode: String? = null,
    val basis: ImportedOfferCountryBasis = ImportedOfferCountryBasis.UNKNOWN,
    val basisId: String? = null
) {
    init {
        require(datasetNamespaceId.matches(Regex("[a-z0-9][a-z0-9._-]{0,95}")))
        countryCode?.let {
            require(it.matches(Regex("[A-Z]{2}"))) {
                "Country code must be an ISO-style uppercase two-letter code"
            }
        }
        basisId?.let {
            require(it.isNotBlank())
            require(it.length <= 240)
        }

        if (basis == ImportedOfferCountryBasis.UNKNOWN) {
            require(countryCode == null) {
                "Unknown country basis cannot carry a trusted country code"
            }
        }

        if (supportsCountryValidation) {
            require(countryCode != null) {
                "Strong country evidence requires a country code"
            }
            require(!basisId.isNullOrBlank()) {
                "Strong country evidence requires an auditable basis"
            }
        }
    }

    val supportsCountryValidation: Boolean
        get() =
            basis == ImportedOfferCountryBasis.EXPLICIT_OFFER_COUNTRY ||
                basis == ImportedOfferCountryBasis.EXPLICIT_DATASET_COUNTRY ||
                basis == ImportedOfferCountryBasis.DOCUMENTED_DATASET_MARKET
}

enum class ProviderDatasetCountryMatchStatus {
    MATCH,
    MISMATCH,
    UNRESOLVED
}

data class ProviderDatasetCountryAssessment(
    val targetCountryCode: String,
    val suppliedCountryCode: String?,
    val basis: ImportedOfferCountryBasis,
    val basisId: String?,
    val status: ProviderDatasetCountryMatchStatus
) {
    init {
        require(targetCountryCode.matches(Regex("[A-Z]{2}")))
    }

    val validated: Boolean
        get() = status == ProviderDatasetCountryMatchStatus.MATCH

    /**
     * Bridge into the fail-closed production activation gate.
     *
     * MATCH is satisfied, a strong explicit mismatch is denied, and every weak
     * or absent basis remains unknown. No currency/context inference is upgraded.
     */
    fun toProductionGateAssessment(): ProductionGateAssessment =
        when (status) {
            ProviderDatasetCountryMatchStatus.MATCH ->
                ProductionGateAssessment(
                    gate = ProductionAuthorizationGate.OFFER_GEOGRAPHY_VALIDATED,
                    state = ProductionAuthorizationState.SATISFIED,
                    basisId = requireNotNull(basisId)
                )

            ProviderDatasetCountryMatchStatus.MISMATCH ->
                ProductionGateAssessment(
                    gate = ProductionAuthorizationGate.OFFER_GEOGRAPHY_VALIDATED,
                    state = ProductionAuthorizationState.DENIED,
                    basisId = requireNotNull(basisId)
                )

            ProviderDatasetCountryMatchStatus.UNRESOLVED ->
                ProductionGateAssessment(
                    gate = ProductionAuthorizationGate.OFFER_GEOGRAPHY_VALIDATED,
                    state = ProductionAuthorizationState.UNKNOWN,
                    basisId = basisId
                )
        }
}

/**
 * Deterministic country-scope evaluator.
 *
 * A two-letter country value is not enough by itself: the provenance basis must
 * also be strong. Therefore a "CA" value derived only from CAD remains unresolved.
 */
object ProviderDatasetOfferGeographyEvaluator {

    fun evaluate(
        geography: ProviderDatasetOfferGeography,
        targetCountryCode: String
    ): ProviderDatasetCountryAssessment {
        require(targetCountryCode.matches(Regex("[A-Z]{2}"))) {
            "Target country code must be an ISO-style uppercase two-letter code"
        }

        val status =
            when {
                !geography.supportsCountryValidation ->
                    ProviderDatasetCountryMatchStatus.UNRESOLVED

                geography.countryCode == targetCountryCode ->
                    ProviderDatasetCountryMatchStatus.MATCH

                else ->
                    ProviderDatasetCountryMatchStatus.MISMATCH
            }

        return ProviderDatasetCountryAssessment(
            targetCountryCode = targetCountryCode,
            suppliedCountryCode = geography.countryCode,
            basis = geography.basis,
            basisId = geography.basisId,
            status = status
        )
    }
}
