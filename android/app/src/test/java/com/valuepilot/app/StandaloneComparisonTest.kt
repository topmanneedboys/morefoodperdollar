package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StandaloneComparisonTest {

    private val applesInput = """
        Honeycrisp apples
        3 lb
        ${'$'}5.99

        Gala apples
        1.5 kg
        ${'$'}4.49
    """.trimIndent()

    @Test
    fun captureThreeProducts() {
        val input = """
            Honeycrisp apples
            3 lb
            ${'$'}5.99

            Gala apples
            1.5 kg
            ${'$'}4.49

            Fuji apples
            2 lb
            ${'$'}3.99
        """.trimIndent()

        val result = ManualProductObservationAdapter.capture(
            rawInput = input,
            observedAtEpochMillis = 123L
        )

        assertTrue(result is ManualCaptureResult.Success)
        val observations = (result as ManualCaptureResult.Success).observations

        assertEquals(3, observations.size)
    }

    @Test
    fun observationMetadataIsDeterministic() {
        val input = """
            First product
            1 kg
            ${'$'}3.00


            Second product
            2 kg
            ${'$'}5.00
        """.trimIndent()

        val suppliedTime = 987654321L

        val result = ManualProductObservationAdapter.capture(
            rawInput = input,
            observedAtEpochMillis = suppliedTime
        )

        assertTrue(result is ManualCaptureResult.Success)
        val observations = (result as ManualCaptureResult.Success).observations

        assertEquals(2, observations.size)

        assertEquals("manual-1", observations[0].id.value)
        assertEquals("manual-2", observations[1].id.value)

        assertEquals("manual", observations[0].sourceId)
        assertEquals("manual", observations[1].sourceId)

        assertEquals(suppliedTime, observations[0].observedAtEpochMillis)
        assertEquals(suppliedTime, observations[1].observedAtEpochMillis)

        assertEquals(
            "First product`n1 kg`n${'$'}3.00".replace("`n", "\n"),
            observations[0].rawText
        )
        assertEquals(
            "Second product`n2 kg`n${'$'}5.00".replace("`n", "\n"),
            observations[1].rawText
        )
    }

    @Test
    fun groceryComparisonRanksGalaFirst() {
        val controller = StandaloneComparisonController()

        val state = controller.reduce(
            controller.initialState(),
            StandaloneComparisonIntent.Compare(
                rawInput = applesInput,
                observedAtEpochMillis = 1L
            )
        )

        assertTrue(state.comparisonSucceeded)
        assertEquals(StandaloneComparisonStatus.READY, state.status)
        assertEquals(2, state.parsedCount)
        assertEquals(0, state.rejectedCount)
        assertTrue(state.results.isNotEmpty())
        assertEquals("Gala apples", state.results.first().name)
        assertEquals(1, state.results.first().rank)
        assertTrue(state.results.first().best)
    }

    @Test
    fun invalidBlockDoesNotCrashBatch() {
        val input = """
            Honeycrisp apples
            3 lb
            ${'$'}5.99

            this block has no price

            Gala apples
            1.5 kg
            ${'$'}4.49
        """.trimIndent()

        val controller = StandaloneComparisonController()

        val state = controller.reduce(
            controller.initialState(),
            StandaloneComparisonIntent.Compare(input, 2L)
        )

        assertEquals(3, state.submittedCount)
        assertEquals(2, state.parsedCount)
        assertEquals(1, state.rejectedCount)
        assertTrue(state.comparisonSucceeded)
        assertEquals(2, state.results.size)
    }

    @Test
    fun fewerThanTwoValidProducts() {
        val input = """
            Honeycrisp apples
            3 lb
            ${'$'}5.99

            invalid block without a price
        """.trimIndent()

        val controller = StandaloneComparisonController()

        val state = controller.reduce(
            controller.initialState(),
            StandaloneComparisonIntent.Compare(input, 3L)
        )

        assertFalse(state.comparisonSucceeded)
        assertEquals(
            StandaloneComparisonStatus.NOT_ENOUGH_VALID_PRODUCTS,
            state.status
        )
        assertEquals(2, state.submittedCount)
        assertEquals(1, state.parsedCount)
        assertEquals(1, state.rejectedCount)
        assertTrue(state.results.isEmpty())
    }

    @Test
    fun mixedCurrenciesAreRejected() {
        val input = """
            Rice
            5 kg
            C${'$'}12.49

            Rice
            5 kg
            A${'$'}13.49
        """.trimIndent()

        val controller = StandaloneComparisonController()

        val state = controller.reduce(
            controller.initialState(),
            StandaloneComparisonIntent.Compare(input, 4L)
        )

        assertFalse(state.comparisonSucceeded)
        assertEquals(
            StandaloneComparisonStatus.MIXED_CURRENCIES,
            state.status
        )
        assertEquals(2, state.parsedCount)
        assertTrue(state.results.isEmpty())
    }

    @Test
    fun maxInputCharsEnforced() {
        val input = "x".repeat(
            ManualProductObservationAdapter.MAX_INPUT_CHARS + 1
        )

        val result = ManualProductObservationAdapter.capture(input, 5L)

        assertTrue(result is ManualCaptureResult.Failure)
        assertEquals(
            ManualCaptureFailure.INPUT_TOO_LONG,
            (result as ManualCaptureResult.Failure).reason
        )
    }

    @Test
    fun maxProductBlocksEnforced() {
        val input = (1..101).joinToString("\n\n") { index ->
            "Product $index"
        }

        val result = ManualProductObservationAdapter.capture(input, 6L)

        assertTrue(result is ManualCaptureResult.Failure)
        assertEquals(
            ManualCaptureFailure.TOO_MANY_BLOCKS,
            (result as ManualCaptureResult.Failure).reason
        )
    }

    @Test
    fun maxBlockCharsEnforced() {
        val input = "x".repeat(
            ManualProductObservationAdapter.MAX_BLOCK_CHARS + 1
        )

        val result = ManualProductObservationAdapter.capture(input, 7L)

        assertTrue(result is ManualCaptureResult.Failure)
        assertEquals(
            ManualCaptureFailure.BLOCK_TOO_LONG,
            (result as ManualCaptureResult.Failure).reason
        )
    }

    @Test
    fun clearReturnsInitialState() {
        val controller = StandaloneComparisonController()
        val initial = controller.initialState()

        val compared = controller.reduce(
            initial,
            StandaloneComparisonIntent.Compare(applesInput, 8L)
        )

        assertTrue(compared.comparisonSucceeded)

        val cleared = controller.reduce(
            compared,
            StandaloneComparisonIntent.Clear
        )

        assertEquals(initial, cleared)
    }

    @Test
    fun deterministicPathDoesNotRequireSemanticModel() {
        val controller = StandaloneComparisonController()

        val state = controller.reduce(
            controller.initialState(),
            StandaloneComparisonIntent.Compare(applesInput, 9L)
        )

        assertTrue(state.comparisonSucceeded)
        assertEquals(StandaloneComparisonStatus.READY, state.status)
        assertEquals(2, state.results.size)

        state.results.forEach { row ->
            assertTrue(row.name.isNotBlank())
            assertTrue(row.priceSummary.isNotBlank())
        }
    }

    @Test
    fun parserCalledExactlyOncePerObservation() {
        var calls = 0

        val countingParser = ProductParser { rawText, sourceId ->
            calls++
            DeterministicProductParser.parse(rawText, sourceId)
        }

        val controller = StandaloneComparisonController(
            parser = countingParser
        )

        val input = """
            Honeycrisp apples
            3 lb
            ${'$'}5.99

            Gala apples
            1.5 kg
            ${'$'}4.49

            Fuji apples
            2 lb
            ${'$'}3.99
        """.trimIndent()

        val state = controller.reduce(
            controller.initialState(),
            StandaloneComparisonIntent.Compare(input, 10L)
        )

        assertTrue(state.comparisonSucceeded)
        assertEquals(3, calls)
    }

    @Test
    fun explicitBlocksPreserveInternalBlankLines() {
        val blocks = listOf(
            "Honeycrisp apples\n\n3 lb\n\n${'$'}5.99",
            "Gala apples\n\n1.5 kg\n\n${'$'}4.49"
        )

        val result = ManualProductObservationAdapter.captureBlocks(
            rawBlocks = blocks,
            observedAtEpochMillis = 100L
        )

        assertTrue(result is ManualCaptureResult.Success)

        val observations =
            (result as ManualCaptureResult.Success).observations

        assertEquals(2, observations.size)

        assertEquals(
            "Honeycrisp apples\n\n3 lb\n\n${'$'}5.99",
            observations[0].rawText
        )

        assertEquals(
            "Gala apples\n\n1.5 kg\n\n${'$'}4.49",
            observations[1].rawText
        )
    }

    @Test
    fun explicitBlockComparisonHandlesInternalBlankLines() {
        val controller = StandaloneComparisonController()

        val state = controller.reduce(
            controller.initialState(),
            StandaloneComparisonIntent.CompareBlocks(
                productBlocks = listOf(
                    "Honeycrisp apples\n\n3 lb\n\n${'$'}5.99",
                    "Gala apples\n\n1.5 kg\n\n${'$'}4.49"
                ),
                observedAtEpochMillis = 101L
            )
        )

        assertTrue(state.comparisonSucceeded)
        assertEquals(
            StandaloneComparisonStatus.READY,
            state.status
        )
        assertEquals(2, state.parsedCount)
        assertEquals(2, state.results.size)

        assertEquals(
            "Gala apples",
            state.results[0].name
        )
        assertEquals(
            "1.5 kg",
            state.results[0].quantity
        )

        assertEquals(
            "Honeycrisp apples",
            state.results[1].name
        )
        assertEquals(
            "3 lb",
            state.results[1].quantity
        )
    }
    @Test
    fun rankingEngineIsInjectedAndCalledOnce() {
        var calls = 0

        val countingRanker = RankingEngine { request ->
            calls++
            DeterministicRankingEngine.rank(request)
        }

        val controller = StandaloneComparisonController(
            rankingEngine = countingRanker
        )

        val state = controller.reduce(
            controller.initialState(),
            StandaloneComparisonIntent.Compare(applesInput, 11L)
        )

        assertTrue(state.comparisonSucceeded)
        assertEquals(1, calls)

        calls = 0

        val mixedInput = """
            Rice
            5 kg
            C${'$'}12.49

            Rice
            5 kg
            A${'$'}13.49
        """.trimIndent()

        val mixedState = controller.reduce(
            controller.initialState(),
            StandaloneComparisonIntent.Compare(mixedInput, 12L)
        )

        assertEquals(
            StandaloneComparisonStatus.MIXED_CURRENCIES,
            mixedState.status
        )
        assertEquals(0, calls)
    }
}
