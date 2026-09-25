package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareHereShareCardTest {

    @Test
    fun `complete result projects a generic privacy safe card`() {
        val card = CompareHereShareCardProjector.project(readyState())

        assertNotNull(card)
        val result = requireNotNull(card)
        assertEquals("ValuePilot found a better unit price", result.title)
        assertTrue(result.text.contains("I compared 2 grocery options with ValuePilot"))
        assertTrue(result.text.contains("Best exact unit price: 8.42 CAD/kg"))
        assertTrue(result.text.contains("not live store pricing"))
        assertFalse(result.text.contains("Organic Chicken Breast"))
        assertFalse(result.preview.contains("Organic Chicken Breast"))
        assertFalse(result.text.contains("receipt", ignoreCase = true))
        assertFalse(result.text.contains("location", ignoreCase = true))
        assertFalse(result.text.contains("account", ignoreCase = true))
    }

    @Test
    fun `tied result is shareable without claiming a unique winner`() {
        val card =
            CompareHereShareCardProjector.project(
                readyState(
                    rows =
                        listOf(
                            row(title = "First", rank = 1, best = true),
                            row(title = "Second", rank = 1, best = true)
                        )
                )
            )

        assertNotNull(card)
        val result = requireNotNull(card)
        assertEquals("ValuePilot found a tied best unit price", result.title)
        assertTrue(result.text.contains("Lowest exact unit price: 8.42 CAD/kg (tie)"))
        assertFalse(result.text.contains("better unit price"))
    }

    @Test
    fun `incomplete or incompatible result cannot produce a share claim`() {
        assertNull(
            CompareHereShareCardProjector.project(
                readyState(
                    status = CompareHereUiStatus.NOT_ENOUGH_DATA,
                    rows =
                        listOf(
                            row(title = "First", rank = null, best = false),
                            row(title = "Second", rank = null, best = false)
                        )
                )
            )
        )
        assertNull(
            CompareHereShareCardProjector.project(
                readyState(
                    status = CompareHereUiStatus.NOT_ENOUGH_DATA,
                    rows =
                        listOf(
                            row(title = "Only", rank = null, best = false)
                        )
                )
            )
        )
    }

    @Test
    fun `card remains bounded and control free`() {
        val card = CompareHereShareCardProjector.project(readyState())

        assertNotNull(card)
        val result = requireNotNull(card)
        assertTrue(result.title.length <= 600)
        assertTrue(result.text.length <= 600)
        assertTrue(result.preview.length <= 600)
        assertTrue(result.text.none { Character.isISOControl(it.code) })
        assertTrue(result.preview.none { Character.isISOControl(it.code) })
    }

    private fun readyState(
        status: CompareHereUiStatus = CompareHereUiStatus.READY,
        rows: List<CompareHereUiRow> =
            listOf(
                row(title = "Organic Chicken Breast", rank = 1, best = true),
                row(title = "Family Chicken Breast", rank = 2, best = false)
            )
    ): CompareHereUiState =
        CompareHereUiState(
            headline = "Compare here",
            priceModeText = "Current prices",
            status = status,
            statusTitle = if (status == CompareHereUiStatus.READY) "Best value" else "Need more exact information",
            guidance = "Lower exact unit price wins.",
            rows = rows,
            blockedRows = emptyList(),
            omittedDisplayNameCount = 0,
            notice = null
        )

    private fun row(title: String, rank: Int?, best: Boolean): CompareHereUiRow =
        CompareHereUiRow(
            title = title,
            priceText = "13.47 CAD",
            quantityText = "1.6 kg",
            unitRateText = "8.42 CAD/kg",
            valueRank = rank,
            bestValue = best
        )
}
