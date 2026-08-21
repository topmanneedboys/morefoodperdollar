package com.valuepilot.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class GoldenContractTest {
    companion object {
        @JvmStatic @BeforeClass fun loadModel() = TestModelLoader.load()
    }

    @Test fun canonicalParsingFixtureMatchesAndroidEngine() {
        val cases = fixture().getJSONArray("parsing")
        for (index in 0 until cases.length()) {
            val expected = cases.getJSONObject(index)
            val item = requireNotNull(ValueEngine.analyze(expected.getString("rawText"))) { expected.getString("id") }
            assertEquals(expected.getString("name"), item.name)
            assertFalse(item.name.contains("member", ignoreCase = true))
            assertFalse(item.name.contains("previous price", ignoreCase = true))
            assertEquals(expected.getDouble("currentPrice"), item.offer.currentPrice, .0001)
            expected.optString("quantityKind").takeIf(String::isNotBlank)?.let {
                assertEquals(it, item.quantity?.kind?.name)
                assertEquals(expected.getDouble("quantityBase"), item.quantity!!.amountBase, .0001)
            }
            if (expected.has("memberPrice")) assertEquals(expected.getDouble("memberPrice"), item.offer.memberPrice!!, .0001)
            if (expected.has("previousPrice")) assertEquals(expected.getDouble("previousPrice"), item.offer.previousPrice!!, .0001)
            if (expected.has("promotionReceivedMultiplier")) assertEquals(expected.getDouble("promotionReceivedMultiplier"), item.promotion.receivedMultiplier, .0001)
        }
    }

    @Test fun rankingFixtureIsDeterministic() {
        val cases = fixture().getJSONArray("ranking")
        val items = (0 until cases.length()).map { ValueEngine.analyze(cases.getJSONObject(it).getString("rawText"))!! }
        val ranked = ValueEngine.rank(items, RankMode.UNIT)
        for (index in 0 until cases.length()) {
            val expected = cases.getJSONObject(index)
            val name = ValueEngine.analyze(expected.getString("rawText"))!!.name
            assertEquals(expected.getInt("expectedRank"), ranked.single { it.item.name == name }.rank)
        }
    }

    @Test fun sessionAndUnrelatedMatchingFixtureFailClosed() {
        val fixture = fixture()
        val session = fixture.getJSONObject("session")
        val manager = SearchSessionManager()
        val first = manager.observeExplicitQuery("fixture.provider", session.getString("firstQuery"), 1_000L).context
        val next = manager.observeExplicitQuery("fixture.provider", session.getString("nextQuery"), 2_000L).context
        assertNotEquals(first.sessionId, next.sessionId)

        val matching = fixture.getJSONObject("matching")
        val target = ValueEngine.analyze("${matching.getString("target")}\n\$11.65")!!
        val unrelated = ValueEngine.analyze("${matching.getString("unrelated")}\n\$11.65")!!
        assertFalse(SearchRelevance.evaluate(target.name, unrelated.name).include)
        assertEquals("REJECT", matching.getString("expected"))
    }

    @Test fun exactMoneyFixtureUsesMinorUnits() {
        val money = fixture().getJSONObject("moneyArithmetic")
        val operands = money.getJSONArray("minorOperands")
        val total = (0 until operands.length()).sumOf { operands.getLong(it) }
        assertEquals(money.getLong("expectedMinor"), total)
        assertTrue(money.getString("currencyCode").length == 3)
    }

    private fun fixture(): JSONObject {
        val file = File(System.getProperty("user.dir"), "../../shared-fixtures/valuepilot-golden-v1.json").canonicalFile
        return JSONObject(file.readText())
    }
}
