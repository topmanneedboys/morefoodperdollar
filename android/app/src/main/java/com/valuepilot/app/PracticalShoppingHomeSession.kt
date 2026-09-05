package com.valuepilot.app

import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingItemRequestDetail
import com.valuepilot.core.ShoppingRequest

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
                LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice.DEFAULT,
        val requestDetailsLifecycleState: ByteArray? = null
    )

    /**
     * Full immutable Home state, including optional typed item intent details.
     *
     * The existing sample controller remains the sole owner of list resolution and
     * planning. [requestDetails] is carried alongside it and is deliberately not
     * consulted by the sample planner yet.
     */
    data class State(
        val model: LocalSamplePracticalShoppingDemo.Model,
        val requestDetails: PracticalShoppingRequestDetailsSessionState
    )

    fun initialState(): State =
        State(
            model = LocalSamplePracticalShoppingDemo.initialModel(),
            requestDetails = PracticalShoppingRequestDetailsSession.initial()
        )

    /** Applies raw Home editing without silently changing the established detail session. */
    fun queryChanged(state: State, rawQuery: String): State =
        state.copy(
            model =
                LocalSamplePracticalShoppingDemo.reduce(
                    state.model,
                    LocalSamplePracticalShoppingDemo.Intent.QueryChanged(rawQuery)
                )
        )

    /** Submits a list and reconciles details only against the resulting stable item keys. */
    fun submit(state: State, rawQuery: String): State {
        val changed = queryChanged(state, rawQuery)
        val submitted =
            LocalSamplePracticalShoppingDemo.reduce(
                changed.model,
                LocalSamplePracticalShoppingDemo.Intent.Submit
            )
        return reconcileDetails(submitted, changed.requestDetails)
    }

    /**
     * Re-runs only an already-completed list through the existing deterministic Home controller.
     *
     * Replay starts from the completed model rather than routing through [queryChanged]. A
     * completed model may carry a resolved refinement (for example the shopper's chosen chicken
     * cut); treating the unchanged query as a new draft would discard that explicit choice and
     * make a one-tap repeat unexpectedly ask the same question again.
     */
    fun shopAgain(state: State): State {
        if (
            state.model.ui.status != LocalSamplePracticalShoppingDemo.Status.RESULT ||
                state.model.ui.query.isBlank()
        ) {
            return state
        }
        val replayedModel =
            LocalSamplePracticalShoppingDemo.reduce(
                state.model,
                LocalSamplePracticalShoppingDemo.Intent.Submit
            )
        return reconcileDetails(replayedModel, state.requestDetails)
    }

    fun removeItem(state: State, itemKey: ShoppingItemKey): State {
        val next = removeItem(state.model, itemKey)
        return reconcileDetails(next, state.requestDetails)
    }

    fun removeUnknownItem(state: State, token: String): State {
        val next = removeUnknownItem(state.model, token)
        return reconcileDetails(next, state.requestDetails)
    }

    fun chooseChicken(
        state: State,
        choice: LocalSamplePracticalShoppingDemo.ChickenChoice
    ): State {
        val next = chooseChicken(state.model, choice)
        return reconcileDetails(next, state.requestDetails)
    }

    fun chooseExtraStopMinimumSavings(
        state: State,
        choice: LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice
    ): State {
        val next = chooseExtraStopMinimumSavings(state.model, choice)
        return reconcileDetails(next, state.requestDetails)
    }

    /** Adds or replaces one explicit shopper intent without touching the plan result. */
    fun withItemDetail(
        state: State,
        detail: ShoppingItemRequestDetail
    ): State =
        state.copy(
            requestDetails =
                PracticalShoppingRequestDetailsSession.withItemDetail(
                    state.requestDetails,
                    detail
                )
        )

    /** Clears one explicit intent and leaves all other item details untouched. */
    fun withoutItemDetail(state: State, itemKey: ShoppingItemKey): State =
        state.copy(
            requestDetails =
                PracticalShoppingRequestDetailsSession.withoutItemDetail(
                    state.requestDetails,
                    itemKey
                )
        )

    /** Encodes only the bounded typed intent payload for Android lifecycle storage. */
    fun snapshot(state: State): Snapshot {
        val modelSnapshot = snapshot(state.model)
        return modelSnapshot.copy(
            requestDetailsLifecycleState =
                if (modelSnapshot.wasSubmitted) {
                    PracticalShoppingRequestDetailsSession
                        .encodedOrNull(state.requestDetails)
                        ?.clone()
                } else {
                    null
                }
        )
    }

    /** Restores the model and opens details only for the exact restored submitted request. */
    fun restoreState(snapshot: Snapshot): State {
        val model = restore(snapshot)
        val request = establishedRequest(model)
        val details =
            request?.let {
                PracticalShoppingRequestDetailsSession.open(
                    request = it,
                    encodedLifecycleState = snapshot.requestDetailsLifecycleState
                )
            } ?: PracticalShoppingRequestDetailsSession.initial()
        return State(model = model, requestDetails = details)
    }

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

    fun removeUnknownItem(
        model: LocalSamplePracticalShoppingDemo.Model,
        token: String
    ): LocalSamplePracticalShoppingDemo.Model =
        LocalSamplePracticalShoppingDemo.reduce(
            model,
            LocalSamplePracticalShoppingDemo.Intent.RemoveUnknownItem(token)
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

    private fun reconcileDetails(
        model: LocalSamplePracticalShoppingDemo.Model,
        details: PracticalShoppingRequestDetailsSessionState
    ): State =
        State(
            model = model,
            requestDetails =
                establishedRequest(model)?.let { request ->
                    PracticalShoppingRequestDetailsSession.reconcileTo(details, request)
                } ?: PracticalShoppingRequestDetailsSession.initial()
        )

    private fun establishedRequest(
        model: LocalSamplePracticalShoppingDemo.Model
    ): ShoppingRequest? {
        if (
            model.ui.status != LocalSamplePracticalShoppingDemo.Status.NEEDS_REFINEMENT &&
                model.ui.status != LocalSamplePracticalShoppingDemo.Status.RESULT
        ) {
            return null
        }

        val itemKeys = model.ui.items.map { it.key }
        return itemKeys.takeIf { it.isNotEmpty() }?.let(::ShoppingRequest)
    }
}
