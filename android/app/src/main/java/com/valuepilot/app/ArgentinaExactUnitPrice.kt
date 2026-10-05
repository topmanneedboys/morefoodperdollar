package com.valuepilot.app

import com.valuepilot.core.BaseUnit
import com.valuepilot.core.DeterministicValueMath
import com.valuepilot.core.Money
import com.valuepilot.core.NormalizedQuantity
import com.valuepilot.core.Offer
import com.valuepilot.core.RateUnit
import java.math.BigDecimal
import java.math.RoundingMode

/** Exact unit-price evidence shared by Basket and qualified store context. */
internal data class ArgentinaExactUnitPrice(
    val money: Money,
    val basis: String
)

internal object ArgentinaExactUnitPriceMath {
    fun from(
        price: ArgentinaBackendContract.Money,
        quantity: ArgentinaBackendContract.Quantity
    ): ArgentinaExactUnitPrice? = runCatching {
        val normalized = normalizedQuantity(quantity) ?: return null
        val fractionDigits = maxOf(2, price.decimal.scale()).also { require(it <= 6) }
        val exactMoney = Money.parse(price.amount, price.currency, fractionDigits)
        val rate = DeterministicValueMath.pricePerBaseUnit(
            offer = Offer(current = exactMoney),
            quantity = normalized
        )
        val amount = BigDecimal.valueOf(rate.currencyMicrosPerUnit)
            .movePointLeft(6)
            .setScale(2, RoundingMode.HALF_UP)
        ArgentinaExactUnitPrice(
            money = Money.parse(amount.toPlainString(), rate.currencyCode, 2),
            basis = rateUnitLabel(rate.unit)
        )
    }.getOrNull()

    fun text(
        price: ArgentinaBackendContract.Money,
        quantity: ArgentinaBackendContract.Quantity
    ): String? = from(price, quantity)?.let { "${moneyText(it.money)}/${it.basis}" }

    private fun normalizedQuantity(quantity: ArgentinaBackendContract.Quantity): NormalizedQuantity? {
        val unit = when (quantity.dimension) {
            "MASS" -> BaseUnit.GRAM
            "VOLUME" -> BaseUnit.MILLILITRE
            "COUNT" -> BaseUnit.COUNT
            else -> return null
        }
        val micros = BigDecimal(quantity.baseAmount)
            .movePointRight(6)
            .longValueExact()
        return NormalizedQuantity(micros, unit)
    }

    private fun rateUnitLabel(unit: RateUnit): String =
        when (unit) {
            RateUnit.KILOGRAM -> "kg"
            RateUnit.LITRE -> "L"
            RateUnit.ITEM -> "item"
            RateUnit.SQUARE_INCH -> "in²"
        }

    private fun moneyText(money: Money): String =
        "${money.currencyCode} ${BigDecimal.valueOf(money.minorUnits)
            .movePointLeft(money.fractionDigits)
            .setScale(money.fractionDigits, RoundingMode.UNNECESSARY)
            .toPlainString()}"
}
