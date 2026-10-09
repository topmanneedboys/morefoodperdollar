package com.valuepilot.app.next

import android.content.Context
import com.valuepilot.app.UserObservedPriceConfirmationActionCoordinator
import com.valuepilot.app.UserObservedPriceConfirmationActionPresentationController
import com.valuepilot.app.UserObservedPriceConfirmationActionSurfaceRenderer
import com.valuepilot.app.UserObservedPriceConfirmationActionUiState
import com.valuepilot.app.UserObservedPriceConfirmationAndroidDraftSubmissionTarget
import com.valuepilot.app.UserObservedPriceConfirmationAndroidSession
import com.valuepilot.app.UserObservedPriceConfirmationDraftFinalization
import com.valuepilot.app.UserObservedPriceConfirmationDraftMissingField
import com.valuepilot.app.UserObservedPriceConfirmationDraftObserver
import com.valuepilot.app.UserObservedPriceConfirmationDraftPriceTextInput
import com.valuepilot.app.UserObservedPriceConfirmationDraftPriceTextInputAdapter
import com.valuepilot.app.UserObservedPriceConfirmationDraftPriceTextInputFailure
import com.valuepilot.app.UserObservedPriceConfirmationDraftPriceTextInputResult
import com.valuepilot.app.UserObservedPriceConfirmationDraftProofContentSelectionCoordinator
import com.valuepilot.app.UserObservedPriceConfirmationDraftProofContentSelectionObserver
import com.valuepilot.app.UserObservedPriceConfirmationDraftProofContentSelectionPresentation
import com.valuepilot.app.UserObservedPriceConfirmationDraftRouteOpenObserver
import com.valuepilot.app.UserObservedPriceConfirmationDraftRouteSession
import com.valuepilot.app.UserObservedPriceConfirmationDraftUiProjector
import com.valuepilot.app.UserObservedPriceConfirmationDraftUiState
import com.valuepilot.app.UserObservedPriceConfirmationDraftObservedAtTextInput
import com.valuepilot.app.UserObservedPriceConfirmationDraftObservedAtTextInputAdapter
import com.valuepilot.app.UserObservedPriceConfirmationDraftObservedAtTextInputFailure
import com.valuepilot.app.UserObservedPriceConfirmationDraftObservedAtTextInputResult
import com.valuepilot.app.UserObservedPriceProofContentReadResult
import com.valuepilot.app.UserObservedPriceSavedConfirmationDraftRouteCoordinator
import com.valuepilot.app.UserObservedPriceSavedPrefillHandoffAttempt
import com.valuepilot.app.UserObservedPriceSavedConfirmationDraftIdentityPrefillAdapter
import com.valuepilot.app.UserProvidedPriceProofType

internal data class ValuePilotNextObservedPriceConfirmationUiState(
    val productName: String,
    val storeDisplayName: String,
    val draft: UserObservedPriceConfirmationDraftUiState? = null,
    val submissionFieldsReady: Boolean = false,
    val proofPresentation: UserObservedPriceConfirmationDraftProofContentSelectionPresentation =
        UserObservedPriceConfirmationDraftProofContentSelectionPresentation.Inactive,
    val action: UserObservedPriceConfirmationActionUiState = inactiveActionState(),
    val priceInputError: String? = null,
    val observedAtInputError: String? = null,
    val proofReferenceInputError: String? = null
) {
    companion object {
        fun inactiveActionState() =
            UserObservedPriceConfirmationActionUiState(
                status = UserObservedPriceConfirmationActionUiState.Status.INACTIVE,
                message = "Confirmation is available only while this draft is open.",
                actionEnabled = false
            )
    }
}

/** Debug Compose owner for the existing, verified observed-price confirmation pipeline. */
internal class ValuePilotNextObservedPriceConfirmationJourney(
    context: Context,
    private val requestProofSelection: () -> Unit,
    private val publish: (ValuePilotNextObservedPriceConfirmationUiState?) -> Unit
) : AutoCloseable {
    private val applicationContext = context.applicationContext ?: context
    private var pendingAttempt: UserObservedPriceSavedPrefillHandoffAttempt? = null
    private var routeCoordinator: UserObservedPriceSavedConfirmationDraftRouteCoordinator? = null
    private var proofCoordinator: UserObservedPriceConfirmationDraftProofContentSelectionCoordinator? = null
    private var actionController: UserObservedPriceConfirmationActionPresentationController? = null
    private var androidSession: UserObservedPriceConfirmationAndroidSession? = null
    private var activeState: ValuePilotNextObservedPriceConfirmationUiState? = null
    private var closed = false

    fun prepare(attempt: UserObservedPriceSavedPrefillHandoffAttempt): Boolean {
        if (closed || !attempt.accepted) return false
        val prefill = attempt.prefill ?: return false
        if (UserObservedPriceSavedConfirmationDraftIdentityPrefillAdapter.adapt(attempt) == null) {
            return false
        }
        pendingAttempt = attempt
        activeState = ValuePilotNextObservedPriceConfirmationUiState(
            productName = prefill.productName,
            storeDisplayName = prefill.storeDisplayName
        )
        publish(activeState)
        return true
    }

    fun onRouteVisibilityChanged(visible: Boolean) {
        if (closed) return
        if (!visible) {
            closeActiveRoute()
            return
        }
        if (routeCoordinator != null) return
        val attempt = pendingAttempt ?: return
        openRoute(attempt)
    }

    fun onPriceInput(amountText: String, currencyCodeText: String) {
        val route = routeCoordinator ?: return
        when (
            val result = UserObservedPriceConfirmationDraftPriceTextInputAdapter.adapt(
                UserObservedPriceConfirmationDraftPriceTextInput(
                    amountText = amountText,
                    currencyCodeText = currencyCodeText
                )
            )
        ) {
            is UserObservedPriceConfirmationDraftPriceTextInputResult.Success -> {
                route.onPriceInput(result.price)
                updateState { it.copy(priceInputError = null) }
            }
            is UserObservedPriceConfirmationDraftPriceTextInputResult.Failure ->
                updateState { it.copy(priceInputError = result.reason.toMessage()) }
        }
    }

    fun onObservedAtInput(dateText: String, timeText: String, utcOffsetText: String) {
        val route = routeCoordinator ?: return
        when (
            val result = UserObservedPriceConfirmationDraftObservedAtTextInputAdapter.adapt(
                UserObservedPriceConfirmationDraftObservedAtTextInput(
                    dateText = dateText,
                    timeText = timeText,
                    utcOffsetText = utcOffsetText
                )
            )
        ) {
            is UserObservedPriceConfirmationDraftObservedAtTextInputResult.Success -> {
                route.onObservedAtInput(result.observedAtEpochMillis)
                updateState { it.copy(observedAtInputError = null) }
            }
            is UserObservedPriceConfirmationDraftObservedAtTextInputResult.Failure ->
                updateState { it.copy(observedAtInputError = result.reason.toMessage()) }
        }
    }

    fun onProofReferenceInput(artifactId: String, proofType: UserProvidedPriceProofType?) {
        val route = routeCoordinator ?: return
        val message = when {
            artifactId.isBlank() -> "Enter a proof reference."
            proofType == null -> "Choose Receipt or Price tag."
            else -> null
        }
        if (message != null) {
            updateState { it.copy(proofReferenceInputError = message) }
            return
        }
        route.onProofReferenceInput(artifactId, requireNotNull(proofType))
        updateState { it.copy(proofReferenceInputError = null) }
    }

    fun onProofSelectionRequested() {
        proofCoordinator?.onSelectRequested()
    }

    fun onProofContentReadResult(result: UserObservedPriceProofContentReadResult) {
        proofCoordinator?.onContentReadResult(result)
    }

    fun onSubmitRequested() {
        actionController?.onSubmitRequested()
    }

    fun cancel() {
        if (closed) return
        pendingAttempt = null
        closeActiveRoute()
        activeState = null
        publish(null)
    }

    override fun close() {
        if (closed) return
        cancel()
        closed = true
    }

    private fun openRoute(attempt: UserObservedPriceSavedPrefillHandoffAttempt) {
        val attemptPrefill = attempt.prefill ?: return
        val stateRenderer = UserObservedPriceConfirmationActionSurfaceRenderer { action ->
            updateState { it.copy(action = action) }
        }
        lateinit var actionCoordinator: UserObservedPriceConfirmationActionCoordinator
        val createdActionController =
            UserObservedPriceConfirmationActionPresentationController(
                renderer = stateRenderer,
                submitAction = { actionCoordinator.submit() }
            )
        val createdAndroidSession =
            UserObservedPriceConfirmationAndroidSession.create(
                context = applicationContext,
                completionListener = createdActionController
            )
        val createdProofCoordinator =
            UserObservedPriceConfirmationDraftProofContentSelectionCoordinator(
                requestForegroundSelection = requestProofSelection,
                observer = UserObservedPriceConfirmationDraftProofContentSelectionObserver { presentation ->
                    updateState { it.copy(proofPresentation = presentation) }
                    createdActionController.onDraftOrProofChanged()
                }
            )
        val createdRouteCoordinator =
            UserObservedPriceSavedConfirmationDraftRouteCoordinator(
                routeOpenObserver = UserObservedPriceConfirmationDraftRouteOpenObserver { },
                sessionFactory = {
                    UserObservedPriceConfirmationDraftRouteSession(
                        observer = UserObservedPriceConfirmationDraftObserver { finalization ->
                            val draftState = UserObservedPriceConfirmationDraftUiProjector.project(finalization)
                            val readyForSubmission =
                                finalization.missingFields == setOf(
                                    UserObservedPriceConfirmationDraftMissingField.CONFIRMATION_ID,
                                    UserObservedPriceConfirmationDraftMissingField.CONFIRMED_AT
                                )
                            updateState {
                                it.copy(
                                    draft = draftState,
                                    submissionFieldsReady = readyForSubmission
                                )
                            }
                            createdActionController.onDraftOrProofChanged()
                        }
                    )
                }
            )
        actionCoordinator =
            UserObservedPriceConfirmationActionCoordinator(
                routeCoordinator = createdRouteCoordinator,
                proofContentCoordinator = createdProofCoordinator,
                target = UserObservedPriceConfirmationAndroidDraftSubmissionTarget(createdAndroidSession)
            )

        routeCoordinator = createdRouteCoordinator
        proofCoordinator = createdProofCoordinator
        actionController = createdActionController
        androidSession = createdAndroidSession
        createdRouteCoordinator.onAttempt(attempt)
        createdRouteCoordinator.onRouteVisibilityChanged(true)
        createdProofCoordinator.onRouteVisibilityChanged(true)
        createdActionController.onRouteVisibilityChanged(true)
        activeState = activeState?.copy(
            productName = attemptPrefill.productName,
            storeDisplayName = attemptPrefill.storeDisplayName
        )
        publish(activeState)
    }

    private fun closeActiveRoute() {
        routeCoordinator?.onRouteVisibilityChanged(false)
        proofCoordinator?.onRouteVisibilityChanged(false)
        actionController?.onRouteVisibilityChanged(false)
        routeCoordinator?.close()
        proofCoordinator?.close()
        actionController?.close()
        androidSession?.close()
        routeCoordinator = null
        proofCoordinator = null
        actionController = null
        androidSession = null
    }

    private fun updateState(
        transform: (ValuePilotNextObservedPriceConfirmationUiState) -> ValuePilotNextObservedPriceConfirmationUiState
    ) {
        val current = activeState ?: return
        activeState = transform(current)
        publish(activeState)
    }

    private fun UserObservedPriceConfirmationDraftPriceTextInputFailure.toMessage(): String =
        when (this) {
            UserObservedPriceConfirmationDraftPriceTextInputFailure.BLANK_AMOUNT -> "Enter the observed price."
            UserObservedPriceConfirmationDraftPriceTextInputFailure.BLANK_CURRENCY -> "Enter the currency code."
            UserObservedPriceConfirmationDraftPriceTextInputFailure.INVALID_CURRENCY_CODE -> "Use a valid uppercase ISO currency code, such as ARS or USD."
            UserObservedPriceConfirmationDraftPriceTextInputFailure.UNSUPPORTED_CURRENCY_PRECISION -> "That currency precision is not supported."
            UserObservedPriceConfirmationDraftPriceTextInputFailure.INVALID_AMOUNT_FORMAT -> "Use digits and an optional decimal point, such as 12.50."
            UserObservedPriceConfirmationDraftPriceTextInputFailure.OUT_OF_RANGE -> "That price is outside the supported range."
        }

    private fun UserObservedPriceConfirmationDraftObservedAtTextInputFailure.toMessage(): String =
        when (this) {
            UserObservedPriceConfirmationDraftObservedAtTextInputFailure.BLANK_DATE -> "Enter the observation date."
            UserObservedPriceConfirmationDraftObservedAtTextInputFailure.BLANK_TIME -> "Enter the observation time."
            UserObservedPriceConfirmationDraftObservedAtTextInputFailure.BLANK_UTC_OFFSET -> "Enter the UTC offset."
            UserObservedPriceConfirmationDraftObservedAtTextInputFailure.INVALID_DATE_FORMAT -> "Use YYYY-MM-DD."
            UserObservedPriceConfirmationDraftObservedAtTextInputFailure.INVALID_TIME_FORMAT -> "Use HH:MM or HH:MM:SS in 24-hour time."
            UserObservedPriceConfirmationDraftObservedAtTextInputFailure.INVALID_UTC_OFFSET -> "Use Z or an explicit offset such as -03:00."
            UserObservedPriceConfirmationDraftObservedAtTextInputFailure.INVALID_DATE_TIME -> "Enter a real calendar date and time."
        }
}
