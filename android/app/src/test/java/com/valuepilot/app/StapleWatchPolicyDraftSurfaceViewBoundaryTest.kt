package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StapleWatchPolicyDraftSurfaceViewBoundaryTest {

    @Test
    fun physicalPolicyViewConsumesProjectedStateAndEmitsTypedActionsOnly() {
        val source = source("StapleWatchPolicyDraftSurfaceView.kt").readText()

        assertTrue(source.contains("StapleWatchPolicyDraftSurfaceRenderer"))
        assertTrue(source.contains("render(state: StapleWatchPolicyDraftUiState)"))
        assertTrue(
            source.contains(
                "var onAction: ((StapleWatchPolicyDraftUiAction) -> Unit)? = null"
            )
        )
        assertTrue(
            source.contains(
                "var onContinueAction: ((StapleWatchPolicyHandoffUiAction) -> Unit)? = null"
            )
        )
        assertTrue(source.contains("visibility = View.GONE"))
        assertFalse(source.contains("visibility = View.VISIBLE"))

        listOf(
            "StapleWatchPolicyDraft(",
            "StapleWatchPolicyDraftFinalizer",
            "StapleWatchPolicySetupCompositionCoordinator",
            "StapleWatchPolicyBaselineMoneySpec",
            "StapleWatchEconomicEvaluator",
            "StapleWatchEconomicDecision",
            "StapleWatchPolicy(",
            "Money(",
            "SharedPreferences",
            "WorkManager",
            "NotificationManager",
            "System.currentTimeMillis",
            "startActivity",
            "Intent("
        ).forEach { forbidden ->
            assertFalse("Physical policy View must not own $forbidden", source.contains(forbidden))
        }
    }

    @Test
    fun allRawNumericTextFlowsThroughPromotedExactInputAdapter() {
        val source = source("StapleWatchPolicyDraftSurfaceView.kt").readText()

        assertTrue(source.contains("StapleWatchPolicyDraftTextInputAdapter.adapt("))
        assertTrue(source.contains("StapleWatchPolicyDraftTextInput::MinimumSwitchSavings"))
        assertTrue(source.contains("StapleWatchPolicyDraftTextInput::MaxAdditionalTravelSeconds"))
        assertTrue(source.contains("StapleWatchPolicyDraftTextInput::MaxAdditionalDistanceMetres"))
        assertTrue(source.contains("StapleWatchPolicyDraftTextInput::MinimumStapleItemCount"))
        assertTrue(
            source.contains(
                "onAction?.invoke(StapleWatchPolicyDraftUiAction.SetDistanceUnlimited)"
            )
        )
        assertTrue(source.contains("onAction?.invoke(result.action)"))

        listOf(
            ".toLongOrNull()",
            ".toIntOrNull()",
            "Regex(",
            "NumberFormat",
            "DecimalFormat",
            "Currency.getInstance",
            "Locale.getDefault",
            ".toDouble()"
        ).forEach { forbidden ->
            assertFalse("Physical policy View must not bypass the input adapter with $forbidden", source.contains(forbidden))
        }
    }

    @Test
    fun continuationUsesProjectedMarkerWithoutInterpretingReadiness() {
        val source = source("StapleWatchPolicyDraftSurfaceView.kt").readText()

        assertTrue(source.contains("state.continueAction"))
        assertTrue(source.contains("?.takeIf { onContinueAction != null }"))
        assertTrue(source.contains("label = requireNotNull(state.continueActionLabel)"))
        assertTrue(source.contains("setOnClickListener { onContinueAction?.invoke(action) }"))
        assertFalse(source.contains("state.status"))
        assertFalse(source.contains("state.missingRequirements"))
        assertFalse(source.contains("READY_FOR_POLICY_HANDOFF"))
        assertFalse(source.contains("StapleWatchPolicyHandoffUiAction.Request"))
    }

    @Test
    fun physicalViewDoesNotPersistInputOrChooseDomainDefaults() {
        val source = source("StapleWatchPolicyDraftSurfaceView.kt").readText()

        assertTrue(source.contains("isSaveEnabled = false"))
        assertTrue(source.contains("state.minimumSwitchSavingsMinorUnits"))
        assertTrue(source.contains("state.maxAdditionalTravelSeconds"))
        assertTrue(source.contains("state.maxAdditionalDistanceMetres"))
        assertTrue(source.contains("state.minimumStapleItemCount"))
        assertTrue(source.contains("state.missingRequirementLabels"))
        assertTrue(source.contains("missingRequirementsCard(state.missingRequirementLabels)"))
        assertFalse(source.contains("MIN_STAPLE_WATCH_POLICY_ITEM_COUNT"))
        assertFalse(source.contains("MAX_STAPLE_WATCH_POLICY_ITEM_COUNT"))
        assertFalse(source.contains("minimumSwitchSavings ="))
        assertFalse(source.contains("maxAdditionalTravelSeconds ="))
        assertFalse(source.contains("maxAdditionalDistanceMetres ="))
        assertFalse(source.contains("minimumStapleItemCount ="))
    }

    @Test
    fun editableControlsKeepProjectedFieldContextForAssistiveTechnology() {
        val source = source("StapleWatchPolicyDraftSurfaceView.kt").readText()

        assertTrue(source.contains("fieldLabel = state.distanceLimitLabel"))
        assertTrue(source.contains("fieldLabel = label"))
        assertTrue(
            source.contains(
                "contentDescription = \"${'$'}fieldLabel. Enter ${'$'}unitLabel.\""
            )
        )
        assertTrue(source.contains("contentDescription = \"Apply ${'$'}fieldLabel\""))
        assertTrue(
            source.contains(
                "contentDescription = \"Set ${'$'}{state.distanceLimitLabel} to no limit\""
            )
        )
    }

    @Test
    fun projectedPolicyFeedbackUsesPoliteAccessibilityLiveRegions() {
        val source = source("StapleWatchPolicyDraftSurfaceView.kt").readText()

        assertTrue(
            source.contains(
                "accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE"
            )
        )
        assertEquals(
            2,
            source
                .split("accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE")
                .size - 1
        )
        assertTrue(source.contains("state.notice?.let { message -> addView(notice(message)) }"))
        assertTrue(source.contains("addView(missingRequirementsCard(state.missingRequirementLabels))"))
    }

    @Test
    fun exactTextFormatterUsesExplicitFractionDigitsWithoutLocaleOrFloatingPoint() {
        assertEquals("", StapleWatchPolicyDraftTextValueFormatter.money(null, 2))
        assertEquals("15.00", StapleWatchPolicyDraftTextValueFormatter.money(1_500L, 2))
        assertEquals("1.500", StapleWatchPolicyDraftTextValueFormatter.money(1_500L, 3))
        assertEquals("15", StapleWatchPolicyDraftTextValueFormatter.money(15L, 0))
        assertEquals("0.05", StapleWatchPolicyDraftTextValueFormatter.money(5L, 2))
        assertEquals("-0.05", StapleWatchPolicyDraftTextValueFormatter.money(-5L, 2))
        assertEquals(
            "-92233720368547758.08",
            StapleWatchPolicyDraftTextValueFormatter.money(Long.MIN_VALUE, 2)
        )
        assertEquals("900", StapleWatchPolicyDraftTextValueFormatter.whole(900L))
        assertEquals("12", StapleWatchPolicyDraftTextValueFormatter.whole(12))
        assertEquals("", StapleWatchPolicyDraftTextValueFormatter.whole(null as Long?))
        assertEquals("", StapleWatchPolicyDraftTextValueFormatter.whole(null as Int?))

        val source = source("StapleWatchPolicyDraftSurfaceView.kt").readText()
        assertTrue(source.contains("minorUnits.toString()"))
        assertTrue(source.contains("digits.padStart(fractionDigits + 1, '0')"))
        assertFalse(source.contains("Math.abs"))
        assertFalse(source.contains("NumberFormat"))
        assertFalse(source.contains("DecimalFormat"))
        assertFalse(source.contains("BigDecimal"))
        assertFalse(source.contains(".toDouble()"))
    }

    private fun source(name: String): File {
        val workingDirectory =
            requireNotNull(System.getProperty("user.dir")) { "Missing user.dir for source boundary test" }
        return File(workingDirectory, "src/main/java/com/valuepilot/app/$name").also {
            assertTrue("Missing source $name at ${it.absolutePath}", it.isFile)
        }
    }
}
