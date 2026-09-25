package com.valuepilot.app

import com.valuepilot.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserObservedPriceConfirmationDraftPriceTextInputAdapterTest {

    @Test
    fun `explicit ISO currency selects exact standard precision without store or locale inference`() {
        assertEquals(
            UserObservedPriceConfirmationDraftPriceTextInputResult.Success(
                Money(minorUnits = 1_234L, currencyCode = "CAD", fractionDigits = 2)
            ),
            adapt(amount = " 12.34 ", currency = " CAD ")
        )
        assertEquals(
            UserObservedPriceConfirmationDraftPriceTextInputResult.Success(
                Money(minorUnits = 12L, currencyCode = "JPY", fractionDigits = 0)
            ),
            adapt(amount = "12", currency = "JPY")
        )
        assertEquals(
            UserObservedPriceConfirmationDraftPriceTextInputResult.Success(
                Money(minorUnits = 1_005L, currencyCode = "BHD", fractionDigits = 3)
            ),
            adapt(amount = "1.005", currency = "BHD")
        )
    }

    @Test
    fun `same amount under different explicit currencies remains distinct and is never converted`() {
        assertEquals(
            UserObservedPriceConfirmationDraftPriceTextInputResult.Success(
                Money(599L, "CAD", 2)
            ),
            adapt("5.99", "CAD")
        )
        assertEquals(
            UserObservedPriceConfirmationDraftPriceTextInputResult.Success(
                Money(599L, "USD", 2)
            ),
            adapt("5.99", "USD")
        )
    }

    @Test
    fun `amount and currency are independently required`() {
        assertEquals(
            failure(UserObservedPriceConfirmationDraftPriceTextInputFailure.BLANK_AMOUNT),
            adapt("   ", "CAD")
        )
        assertEquals(
            failure(UserObservedPriceConfirmationDraftPriceTextInputFailure.BLANK_CURRENCY),
            adapt("5.99", "   ")
        )
    }

    @Test
    fun `currency must be explicit uppercase registered ISO code with supported precision`() {
        listOf("cad", "CA", "CAD$", "123", "ZZZ").forEach { currency ->
            assertEquals(
                "Expected invalid explicit currency for '$currency'",
                failure(UserObservedPriceConfirmationDraftPriceTextInputFailure.INVALID_CURRENCY_CODE),
                adapt("5.99", currency)
            )
        }

        assertEquals(
            failure(
                UserObservedPriceConfirmationDraftPriceTextInputFailure.UNSUPPORTED_CURRENCY_PRECISION
            ),
            adapt("5.99", "XXX")
        )
    }

    @Test
    fun `amount syntax is locale neutral and precision follows explicit currency`() {
        listOf("1,23", "$1.23", "1 23", ".50", "1.", "+1.00", "1.234").forEach { amount ->
            assertEquals(
                "Expected invalid CAD amount syntax for '$amount'",
                failure(UserObservedPriceConfirmationDraftPriceTextInputFailure.INVALID_AMOUNT_FORMAT),
                adapt(amount, "CAD")
            )
        }
        assertEquals(
            failure(UserObservedPriceConfirmationDraftPriceTextInputFailure.INVALID_AMOUNT_FORMAT),
            adapt("1.0", "JPY")
        )
        assertEquals(
            failure(UserObservedPriceConfirmationDraftPriceTextInputFailure.INVALID_AMOUNT_FORMAT),
            adapt("1.0005", "BHD")
        )
    }

    @Test
    fun `numeric overflow fails closed without emitting Money`() {
        assertEquals(
            failure(UserObservedPriceConfirmationDraftPriceTextInputFailure.OUT_OF_RANGE),
            adapt("9223372036854775808.00", "CAD")
        )
    }

    @Test
    fun `non positive amounts remain typed for the downstream semantic validator`() {
        assertEquals(
            UserObservedPriceConfirmationDraftPriceTextInputResult.Success(
                Money(0L, "CAD", 2)
            ),
            adapt("0", "CAD")
        )
        assertEquals(
            UserObservedPriceConfirmationDraftPriceTextInputResult.Success(
                Money(-123L, "CAD", 2)
            ),
            adapt("-1.23", "CAD")
        )
    }

    @Test
    fun `adapter owns no store locale proof identity time evidence current price or network authority`() {
        val source = source().readText()

        assertTrue(source.contains("Currency.getInstance(currencyCode).defaultFractionDigits"))
        assertTrue(source.contains("Money.parse("))
        assertTrue(source.contains("Regex(\"[A-Z]{3}\")"))

        listOf(
            "Double",
            "Float",
            "Locale.",
            "NumberFormat",
            "PracticalShoppingStoreIdentityScope",
            "storeScope",
            "merchantKey",
            "locationKey",
            "System.currentTimeMillis",
            "UUID",
            "UserProvidedPriceProof",
            "UserObservedPriceConfirmationTransaction",
            "ShoppingEvidence(",
            "EvidenceClaim(",
            "CURRENT_PRICE",
            "ProductionCurrentPrice",
            "android.",
            "java.net",
            "SharedPreferences"
        ).forEach { forbidden ->
            assertFalse("Price text adapter must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun adapt(
        amount: String,
        currency: String
    ): UserObservedPriceConfirmationDraftPriceTextInputResult =
        UserObservedPriceConfirmationDraftPriceTextInputAdapter.adapt(
            UserObservedPriceConfirmationDraftPriceTextInput(
                amountText = amount,
                currencyCodeText = currency
            )
        )

    private fun failure(
        reason: UserObservedPriceConfirmationDraftPriceTextInputFailure
    ): UserObservedPriceConfirmationDraftPriceTextInputResult =
        UserObservedPriceConfirmationDraftPriceTextInputResult.Failure(reason)

    private fun source(): File =
        File(
            System.getProperty("user.dir"),
            "src/main/java/com/valuepilot/app/UserObservedPriceConfirmationDraftPriceTextInputAdapter.kt"
        )
}
