package com.valuepilot.app

import com.valuepilot.core.EvidenceChannel
import com.valuepilot.core.EvidenceClaimKind
import com.valuepilot.core.EvidenceEnvironment
import com.valuepilot.core.EvidenceProvider
import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.ProductObservation
import com.valuepilot.core.ProductObservationId
import com.valuepilot.core.ShoppingEvidence
import com.valuepilot.core.ShoppingSource
import com.valuepilot.core.ShoppingSourceId
import com.valuepilot.core.SourceProductIdentity

/**
 * Small deterministic catalogue used only to validate the permanent consumer
 * Search experience before live provider integrations exist.
 *
 * Every result is structurally SAMPLE + FIXTURE evidence. It cannot be mistaken
 * for a live retailer offer merely because presentation text changes.
 *
 * Relevance, parsing and ranking remain owned by ValuePilot.
 */
object LocalSampleProductSearchProvider :
    ProductSearchProvider {

    private data class Fixture(
        val sourceId: String,
        val sourceName: String,
        val rawText: String
    )

    private val provider =
        EvidenceProvider(
            id =
                EvidenceProviderId(
                    "valuepilot-sample-catalog"
                ),
            displayName =
                "ValuePilot Sample Catalog"
        )

    private val fixtures =
        listOf(
            Fixture(
                "sample-market-a",
                "Sample Market A",
                "Large Eggs\n12 ct\n$5.49"
            ),
            Fixture(
                "sample-market-b",
                "Sample Market B",
                "Large Eggs Value Pack\n18 ct\n$7.49"
            ),
            Fixture(
                "sample-market-c",
                "Sample Market C",
                "Family Pack Eggs\n30 ct\n$11.99"
            ),
            Fixture(
                "sample-market-a",
                "Sample Market A",
                "Whole Milk\n2 L\n$5.49"
            ),
            Fixture(
                "sample-market-b",
                "Sample Market B",
                "Whole Milk Family Jug\n4 L\n$8.99"
            ),
            Fixture(
                "sample-market-c",
                "Sample Market C",
                "Oat Milk\n1.75 L\n$5.99"
            ),
            Fixture(
                "sample-market-a",
                "Sample Market A",
                "Chicken Breast Small Pack\n600 g\n$10.49"
            ),
            Fixture(
                "sample-market-b",
                "Sample Market B",
                "Chicken Breast\n900 g\n$13.49"
            ),
            Fixture(
                "sample-market-c",
                "Sample Market C",
                "Chicken Breast Family Pack\n1.6 kg\n$20.99"
            ),
            Fixture(
                "sample-market-a",
                "Sample Market A",
                "Basmati Rice\n2 kg\n$8.99"
            ),
            Fixture(
                "sample-market-b",
                "Sample Market B",
                "Basmati Rice Family Bag\n5 kg\n$17.99"
            ),
            Fixture(
                "sample-market-c",
                "Sample Market C",
                "Jasmine Rice\n4 kg\n$15.49"
            ),
            Fixture(
                "sample-pizza-a",
                "Sample Pizza A",
                "Pepperoni Pizza\n12 in\n$14.99"
            ),
            Fixture(
                "sample-pizza-b",
                "Sample Pizza B",
                "Pepperoni Pizza Large\n16 in\n$21.99"
            ),
            Fixture(
                "sample-pizza-c",
                "Sample Pizza C",
                "Cheese Pizza\n14 in\n$17.49"
            ),
            Fixture(
                "sample-market-a",
                "Sample Market A",
                "Honeycrisp Apples\n3 lb\n$5.99"
            ),
            Fixture(
                "sample-market-b",
                "Sample Market B",
                "Gala Apples\n1.5 kg\n$4.49"
            ),
            Fixture(
                "sample-market-c",
                "Sample Market C",
                "Bananas\n1 kg\n$1.99"
            )
        )

    override fun search(
        request: ProductSearchRequest
    ): ProductSearchBatch {

        val evidence =
            fixtures
                .take(request.maxObservations)
                .mapIndexed { index, fixture ->

                    val itemId =
                        "sample-${index + 1}"

                    ShoppingEvidence(
                        observation =
                            ProductObservation(
                                id =
                                    ProductObservationId(
                                        itemId
                                    ),
                                sourceId =
                                    fixture.sourceId,
                                rawText =
                                    fixture.rawText,
                                observedAtEpochMillis =
                                    0L
                            ),
                        provider =
                            provider,
                        source =
                            ShoppingSource(
                                id =
                                    ShoppingSourceId(
                                        fixture.sourceId
                                    ),
                                displayName =
                                    fixture.sourceName
                            ),
                        environment =
                            EvidenceEnvironment.SAMPLE,
                        channel =
                            EvidenceChannel.FIXTURE,
                        observationClaimKind =
                            EvidenceClaimKind
                                .SOURCE_ASSERTED,
                        sourceProductIdentity =
                            SourceProductIdentity(
                                providerItemId =
                                    itemId
                            )
                    )
                }

        return ProductSearchBatch(
            requestId =
                request.requestId,
            evidence =
                evidence
        )
    }

    const val SAMPLE_PRODUCT_COUNT = 18
}
