package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StapleWatchPolicyDraftTextInputAdapterTest {

    @Test
    fun savingsUsesRenderedCurrencyPrecisionAndExactMinorUnits() {
        assertEquals(
            StapleWatchPolicyDraftTextInputResult.Success(
                StapleWatchPolicyDraftUiAction.SetMinimumSwitchSavingsMinorUnits(1_234L)
            ),
            StapleWatchPolicyDraftTextInputAdapter.adapt(
                state = state(currencyCode = "CAD", fractionDigits = 2),
                input = StapleWatchPolicyDraftTextInput.MinimumSwitchSavings(" 12.34 ")
            )
        )
        assertEquals(
            StapleWatchPolicyDraftTextInputResult.Success(
                StapleWatchPolicyDraftUiAction.SetMinimumSwitchSavingsMinorUnits(1_005L)
            ),
            StapleWatchPolicyDraftTextInputAdapter.adapt(
                state = state(currencyCode = "BHD", fractionDigits = 3),
                input = StapleWatchPolicyDraftTextInput.MinimumSwitchSavings("1.005")
            )
        )
        assertEquals(
            StapleWatchPolicyDraftTextInputResult.Success(
                StapleWatchPolicyDraftUiAction.SetMinimumSwitchSavingsMinorUnits(12L)
            ),
            StapleWatchPolicyDraftTextInputAdapter.adapt(
                state = state(currencyCode = "JPY", fractionDigits = 0),
                input = StapleWatchPolicyDraftTextInput.MinimumSwitchSavings("12")
            )
        )
    }

    @Test
    fun savingsCanonicalSyntaxRejectsLocaleGroupingSymbolsAndWrongPrecision() {
        val state = state(currencyCode = "CAD", fractionDigits = 2)

        listOf("1,23", "$1.23", "1 23", ".50", "1.", "1.234", "+1.00").forEach { value ->
            assertEquals(
                "Expected invalid canonical money syntax for '$value'",
                StapleWatchPolicyDraftTextInputResult.Failure(
                    StapleWatchPolicyDraftTextInputFailure.INVALID_FORMAT
                ),
                StapleWatchPolicyDraftTextInputAdapter.adapt(
                    state = state,
                    input = StapleWatchPolicyDraftTextInput.MinimumSwitchSavings(value)
                )
            )
        }

        assertEquals(
            StapleWatchPolicyDraftTextInputResult.Failure(
                StapleWatchPolicyDraftTextInputFailure.INVALID_FORMAT
            ),
            StapleWatchPolicyDraftTextInputAdapter.adapt(
                state = state(currencyCode = "JPY", fractionDigits = 0),
                input = StapleWatchPolicyDraftTextInput.MinimumSwitchSavings("1.0")
            )
        )
    }

    @Test
    fun blankInputsAreTypedBeforeAnyNumericParsing() {
        val state = state()
        listOf<StapleWatchPolicyDraftTextInput>(
            StapleWatchPolicyDraftTextInput.MinimumSwitchSavings("   "),
            StapleWatchPolicyDraftTextInput.MaxAdditionalTravelSeconds("\t"),
            StapleWatchPolicyDraftTextInput.MaxAdditionalDistanceMetres("\n"),
            StapleWatchPolicyDraftTextInput.MinimumStapleItemCount("")
        ).forEach { input ->
            assertEquals(
                StapleWatchPolicyDraftTextInputResult.Failure(
                    StapleWatchPolicyDraftTextInputFailure.BLANK
                ),
                StapleWatchPolicyDraftTextInputAdapter.adapt(state, input)
            )
        }
    }

    @Test
    fun signedWholeNumbersMapDirectlyToTypedBaseUnitActions() {
        val state = state()

        assertEquals(
            StapleWatchPolicyDraftTextInputResult.Success(
                StapleWatchPolicyDraftUiAction.SetMaxAdditionalTravelSeconds(900L)
            ),
            StapleWatchPolicyDraftTextInputAdapter.adapt(
                state,
                StapleWatchPolicyDraftTextInput.MaxAdditionalTravelSeconds(" 900 ")
            )
        )
        assertEquals(
            StapleWatchPolicyDraftTextInputResult.Success(
                StapleWatchPolicyDraftUiAction.SetMaxAdditionalDistanceMetres(2_500L)
            ),
            StapleWatchPolicyDraftTextInputAdapter.adapt(
                state,
                StapleWatchPolicyDraftTextInput.MaxAdditionalDistanceMetres("2500")
            )
        )
        assertEquals(
            StapleWatchPolicyDraftTextInputResult.Success(
                StapleWatchPolicyDraftUiAction.SetMinimumStapleItemCount(12)
            ),
            StapleWatchPolicyDraftTextInputAdapter.adapt(
                state,
                StapleWatchPolicyDraftTextInput.MinimumStapleItemCount("12")
            )
        )
    }

    @Test
    fun negativeNumbersParseButRemainForDraftDomainValidation() {
        val state = state()

        assertEquals(
            StapleWatchPolicyDraftTextInputResult.Success(
                StapleWatchPolicyDraftUiAction.SetMinimumSwitchSavingsMinorUnits(-123L)
            ),
            StapleWatchPolicyDraftTextInputAdapter.adapt(
                state,
                StapleWatchPolicyDraftTextInput.MinimumSwitchSavings("-1.23")
            )
        )
        assertEquals(
            StapleWatchPolicyDraftTextInputResult.Success(
                StapleWatchPolicyDraftUiAction.SetMaxAdditionalTravelSeconds(-1L)
            ),
            StapleWatchPolicyDraftTextInputAdapter.adapt(
                state,
                StapleWatchPolicyDraftTextInput.MaxAdditionalTravelSeconds("-1")
            )
        )
        assertEquals(
            StapleWatchPolicyDraftTextInputResult.Success(
                StapleWatchPolicyDraftUiAction.SetMaxAdditionalDistanceMetres(-1L)
            ),
            StapleWatchPolicyDraftTextInputAdapter.adapt(
                state,
                StapleWatchPolicyDraftTextInput.MaxAdditionalDistanceMetres("-1")
            )
        )
        assertEquals(
            StapleWatchPolicyDraftTextInputResult.Success(
                StapleWatchPolicyDraftUiAction.SetMinimumStapleItemCount(-1)
            ),
            StapleWatchPolicyDraftTextInputAdapter.adapt(
                state,
                StapleWatchPolicyDraftTextInput.MinimumStapleItemCount("-1")
            )
        )
    }

    @Test
    fun wholeNumberSyntaxRejectsDecimalsGroupingAndPlusSigns() {
        val state = state()
        val inputs =
            listOf<StapleWatchPolicyDraftTextInput>(
                StapleWatchPolicyDraftTextInput.MaxAdditionalTravelSeconds("1.5"),
                StapleWatchPolicyDraftTextInput.MaxAdditionalTravelSeconds("1,000"),
                StapleWatchPolicyDraftTextInput.MaxAdditionalDistanceMetres("1 000"),
                StapleWatchPolicyDraftTextInput.MinimumStapleItemCount("+2")
            )

        inputs.forEach { input ->
            assertEquals(
                StapleWatchPolicyDraftTextInputResult.Failure(
                    StapleWatchPolicyDraftTextInputFailure.INVALID_FORMAT
                ),
                StapleWatchPolicyDraftTextInputAdapter.adapt(state, input)
            )
        }
    }

    @Test
    fun numericOverflowFailsClosedWithoutProducingAction() {
        val state = state()

        assertEquals(
            StapleWatchPolicyDraftTextInputResult.Failure(
                StapleWatchPolicyDraftTextInputFailure.OUT_OF_RANGE
            ),
            StapleWatchPolicyDraftTextInputAdapter.adapt(
                state,
                StapleWatchPolicyDraftTextInput.MinimumSwitchSavings(
                    "9223372036854775808.00"
                )
            )
        )
        assertEquals(
            StapleWatchPolicyDraftTextInputResult.Failure(
                StapleWatchPolicyDraftTextInputFailure.OUT_OF_RANGE
            ),
            StapleWatchPolicyDraftTextInputAdapter.adapt(
                state,
                StapleWatchPolicyDraftTextInput.MaxAdditionalTravelSeconds(
                    "9223372036854775808"
                )
            )
        )
        assertEquals(
            StapleWatchPolicyDraftTextInputResult.Failure(
                StapleWatchPolicyDraftTextInputFailure.OUT_OF_RANGE
            ),
            StapleWatchPolicyDraftTextInputAdapter.adapt(
                state,
                StapleWatchPolicyDraftTextInput.MinimumStapleItemCount("2147483648")
            )
        )
    }

    @Test
    fun adapterUsesOnlyRenderedMoneySpecAndOwnsNoDomainOrPlatformAuthority() {
        val source = source().readText()

        assertTrue(source.contains("state.currencyCode"))
        assertTrue(source.contains("state.currencyFractionDigits"))
        assertTrue(source.contains("Money.parse("))
        assertTrue(source.contains("value.toLongOrNull()"))
        assertTrue(source.contains("value.toIntOrNull()"))
        assertTrue(source.contains("SetMinimumSwitchSavingsMinorUnits(money.minorUnits)"))

        listOf(
            "Double",
            "Float",
            "BigDecimal",
            "NumberFormat",
            "Locale.",
            "Currency.getInstance",
            "java.text",
            "android.",
            "StapleWatchPolicy(",
            "StapleWatchPolicyDraft.start",
            "StapleWatchPolicyDraftFinalizer",
            "withMinimumSwitchSavingsMinorUnits",
            "withMaxAdditionalTravelSeconds",
            "AtMostMetres(",
            "withMinimumStapleItemCount",
            "MIN_STAPLE_WATCH_POLICY_ITEM_COUNT",
            "MAX_STAPLE_WATCH_POLICY_ITEM_COUNT",
            "StapleWatchEconomicEvaluator",
            "StapleWatchForegroundEvaluationCoordinator",
            "SharedPreferences",
            "WorkManager",
            "NotificationManager",
            "MainActivity"
        ).forEach { forbidden ->
            assertFalse("Text adapter must not own $forbidden", source.contains(forbidden))
        }

        assertFalse(source.contains("state.status"))
        assertFalse(source.contains("state.missingRequirements"))
        assertFalse(source.contains("state.continueAction"))
    }

    private fun state(
        currencyCode: String = "CAD",
        fractionDigits: Int = 2
    ): StapleWatchPolicyDraftUiState =
        StapleWatchPolicyDraftUiState(
            status = StapleWatchPolicyDraftUiStatus.NEEDS_POLICY_INPUT,
            headline = "Switch preferences",
            guidance = "Choose every switch rule before continuing.",
            currencyCode = currencyCode,
            currencyFractionDigits = fractionDigits,
            minimumSwitchSavingsLabel = "Minimum savings",
            minimumSwitchSavingsMinorUnits = null,
            minimumSwitchSavingsUnitLabel = currencyCode,
            maxAdditionalTravelLabel = "Maximum extra travel time",
            maxAdditionalTravelSeconds = null,
            maxAdditionalTravelUnitLabel = "seconds",
            distanceLimitLabel = "Maximum extra distance",
            distanceLimitMode = StapleWatchPolicyDistanceLimitUiMode.UNANSWERED,
            maxAdditionalDistanceMetres = null,
            maxAdditionalDistanceUnitLabel = "metres",
            minimumStapleItemCountLabel = "Minimum watched staples",
            minimumStapleItemCount = null,
            missingRequirements =
                listOf(
                    StapleWatchPolicyDraftRequirement.MINIMUM_SWITCH_SAVINGS,
                    StapleWatchPolicyDraftRequirement.MAX_ADDITIONAL_TRAVEL,
                    StapleWatchPolicyDraftRequirement.DISTANCE_LIMIT_CHOICE,
                    StapleWatchPolicyDraftRequirement.MINIMUM_STAPLE_ITEM_COUNT
                ),
            notice = "Complete all switch preferences to continue.",
            continueAction = null,
            continueActionLabel = null
        )

    private fun source(): File =
        File(
            System.getProperty("user.dir"),
            "src/main/java/com/valuepilot/app/StapleWatchPolicyDraftTextInputAdapter.kt"
        )
}
