package com.valuepilot.app

import com.valuepilot.core.ProductObservation
import com.valuepilot.core.ProductObservationId

/**
 * Small, deterministic sample catalog used only to validate the permanent
 * consumer Search experience before live provider integrations exist.
 *
 * These fixtures are deliberately fictional. They are not claims about live
 * retailer prices, inventory, promotions, or availability.
 *
 * The provider returns observations only. Relevance, parsing and ranking remain
 * owned by the permanent ValuePilot application/core layers.
 */
object LocalSampleProductSearchProvider : ProductSearchProvider {

    private data class Fixture(
        val sourceId: String,
        val rawText: String
    )

    private val fixtures =
        listOf(
            Fixture("Sample Market A", "Large Eggs\n12 ct\n$5.49"),
            Fixture("Sample Market B", "Large Eggs Value Pack\n18 ct\n$7.49"),
            Fixture("Sample Market C", "Family Pack Eggs\n30 ct\n$11.99"),
            Fixture("Sample Market A", "Whole Milk\n2 L\n$5.49"),
            Fixture("Sample Market B", "Whole Milk Family Jug\n4 L\n$8.99"),
            Fixture("Sample Market C", "Oat Milk\n1.75 L\n$5.99"),
            Fixture("Sample Market A", "Chicken Breast Small Pack\n600 g\n$10.49"),
            Fixture("Sample Market B", "Chicken Breast\n900 g\n$13.49"),
            Fixture("Sample Market C", "Chicken Breast Family Pack\n1.6 kg\n$20.99"),
            Fixture("Sample Market A", "Basmati Rice\n2 kg\n$8.99"),
            Fixture("Sample Market B", "Basmati Rice Family Bag\n5 kg\n$17.99"),
            Fixture("Sample Market C", "Jasmine Rice\n4 kg\n$15.49"),
            Fixture("Sample Pizza A", "Pepperoni Pizza\n12 in\n$14.99"),
            Fixture("Sample Pizza B", "Pepperoni Pizza Large\n16 in\n$21.99"),
            Fixture("Sample Pizza C", "Cheese Pizza\n14 in\n$17.49"),
            Fixture("Sample Market A", "Honeycrisp Apples\n3 lb\n$5.99"),
            Fixture("Sample Market B", "Gala Apples\n1.5 kg\n$4.49"),
            Fixture("Sample Market C", "Bananas\n1 kg\n$1.99")
        )

    override fun search(
        request: ProductSearchRequest
    ): ProductSearchBatch {
        val observations =
            fixtures
                .take(request.maxObservations)
                .mapIndexed { index, fixture ->
                    ProductObservation(
                        id = ProductObservationId("sample-${index + 1}"),
                        sourceId = fixture.sourceId,
                        rawText = fixture.rawText,
                        observedAtEpochMillis = 0L
                    )
                }

        return ProductSearchBatch(
            requestId = request.requestId,
            observations = observations
        )
    }

    const val SAMPLE_PRODUCT_COUNT = 18
}
