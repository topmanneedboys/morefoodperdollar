package com.valuepilot.app

import com.valuepilot.core.ShoppingItemKey

/**
 * Small application-level owner for the Home Practical Shopping session.
 *
 * It preserves/restores controller state without teaching Android lifecycle code
 * how shopping decisions work. The controller and shared-core planner remain the
 * only places that resolve the sample list and choose a practical shopping plan.
 */
object PracticalShoppingHomeSession {

    data class Snapshot(
        val query: String,
        val wasSubmitted: Boolean,
        val chickenChoice: LocalSamplePracticalShoppingDemo.ChickenChoice?,
        val extraStopMinimumSavingsChoice:
            LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice =
                LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice.DEFAULT
    )

    fun submit(
        model: LocalSamplePracticalShoppingDemo.Model,
        rawQuery: String
    ): LocalSamplePracticalShoppingDemo.Model {
        val changed =
            LocalSamplePracticalShoppingDemo.reduce(
                model,
                LocalSamplePracticalShoppingDemo.Intent.QueryChanged(rawQuery)
            )
        return LocalSamplePracticalShoppingDemo.reduce(
            changed,
            LocalSamplePracticalShoppingDemo.Intent.Submit
        )
    }

    fun chooseChicken(
        model: LocalSamplePracticalShoppingDemo.Model,
        choice: LocalSamplePracticalShoppingDemo.ChickenChoice
    ): LocalSamplePracticalShoppingDemo.Model =
        LocalSamplePracticalShoppingDemo.reduce(
            model,
            LocalSamplePracticalShoppingDemo.Intent.ChooseChicken(choice)
        )

    fun removeItem(
        model: LocalSamplePracticalShoppingDemo.Model,
        itemKey: ShoppingItemKey
    ): LocalSamplePracticalShoppingDemo.Model =
        LocalSamplePracticalShoppingDemo.reduce(
            model,
            LocalSamplePracticalShoppingDemo.Intent.RemoveItem(itemKey)
        )

    fun chooseExtraStopMinimumSavings(
        model: LocalSamplePracticalShoppingDemo.Model,
        choice: LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice
    ): LocalSamplePracticalShoppingDemo.Model =
        LocalSamplePracticalShoppingDemo.reduce(
            model,
            LocalSamplePracticalShoppingDemo.Intent.ChooseExtraStopMinimumSavings(choice)
        )

    fun snapshot(model: LocalSamplePracticalShoppingDemo.Model): Snapshot =
        Snapshot(
            query = model.ui.query,
            wasSubmitted =
                model.ui.query.isNotBlank() &&
                    model.ui.status != LocalSamplePracticalShoppingDemo.Status.IDLE,
            chickenChoice = model.selectedChicken,
            extraStopMinimumSavingsChoice = model.ui.extraStopMinimumSavingsChoice
        )

    fun restore(snapshot: Snapshot): LocalSamplePracticalShoppingDemo.Model {
        var model = LocalSamplePracticalShoppingDemo.initialModel()
        model = chooseExtraStopMinimumSavings(model, snapshot.extraStopMinimumSavingsChoice)
        model =
            LocalSamplePracticalShoppingDemo.reduce(
                model,
                LocalSamplePracticalShoppingDemo.Intent.QueryChanged(snapshot.query)
            )

        if (!snapshot.wasSubmitted) return model

        model =
            LocalSamplePracticalShoppingDemo.reduce(
                model,
                LocalSamplePracticalShoppingDemo.Intent.Submit
            )

        snapshot.chickenChoice?.let { choice ->
            model = chooseChicken(model, choice)
        }

        return model
    }
}
