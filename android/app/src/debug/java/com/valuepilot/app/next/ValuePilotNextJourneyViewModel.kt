package com.valuepilot.app.next

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.valuepilot.app.AndroidPracticalShoppingHomeSessionStore
import com.valuepilot.app.ArgentinaBackendBridgeResult
import com.valuepilot.app.ArgentinaBackendContract
import com.valuepilot.app.ArgentinaBackendConsumerBridge
import com.valuepilot.app.ArgentinaLiveSearchSurfaceHost
import com.valuepilot.app.ArgentinaLiveSearchSurfacePresenter
import com.valuepilot.app.ArgentinaLiveSearchSurfaceState
import com.valuepilot.app.ArgentinaPrivateBasketPlanProjection
import com.valuepilot.app.ArgentinaPrivateBasketPlanProjector
import com.valuepilot.app.ArgentinaPrivateBasketRenderer
import com.valuepilot.app.ArgentinaPrivateBasketRenderState
import com.valuepilot.app.ArgentinaPrivateBasketRequestBuilder
import com.valuepilot.app.ArgentinaPrivateTripCostCalculator
import com.valuepilot.app.ArgentinaPrivateTripCostInput
import com.valuepilot.app.ArgentinaPrivateTripCostResult
import com.valuepilot.app.ArgentinaQualifiedLaunchAreas
import com.valuepilot.app.ArgentinaSearchContextSource
import com.valuepilot.app.ArgentinaLiveSearchDiagnostics
import com.valuepilot.app.PracticalShoppingHomeSession
import com.valuepilot.app.PracticalShoppingPrivateIdentity
import com.valuepilot.app.PracticalShoppingSearchIdentityPresentation
import com.valuepilot.app.PracticalShoppingSavedAndroidSession
import com.valuepilot.app.PracticalShoppingSavedLifecycleRenderer
import com.valuepilot.app.PracticalShoppingSavedLifecycleController
import com.valuepilot.app.PracticalShoppingSavedRouteCoordinator
import com.valuepilot.app.PracticalShoppingSavedStapleLaunchPresenter
import com.valuepilot.app.PracticalShoppingSavedStapleLaunchRenderer
import com.valuepilot.app.PracticalShoppingSavedStapleLaunchUiState
import com.valuepilot.app.PracticalShoppingSavedSurfaceAction
import com.valuepilot.app.PracticalShoppingSavedSurfacePresenter
import com.valuepilot.app.PracticalShoppingSavedSurfaceProjector
import com.valuepilot.app.PracticalShoppingSavedSurfaceRenderer
import com.valuepilot.app.PracticalShoppingSavedSurfaceState
import com.valuepilot.app.PracticalShoppingSavedValidatedSnapshotObserver
import com.valuepilot.app.StapleWatchForegroundFactCheckCapability
import com.valuepilot.app.StapleWatchSavedIdentityHandoffUiAction
import com.valuepilot.app.StapleWatchSavedIdentitySelectionAction
import com.valuepilot.app.StapleWatchSavedSelectionRouteSession
import com.valuepilot.app.StapleWatchSavedSelectionSurfacePresenter
import com.valuepilot.app.StapleWatchSavedSelectionSurfaceRenderer
import com.valuepilot.app.StapleWatchSavedSelectionUiState
import com.valuepilot.app.StapleWatchSavedSetupCompositionCoordinator
import com.valuepilot.app.UserObservedPriceSavedPrefillHandoffAttempt
import com.valuepilot.app.UserObservedPriceSavedPrefillHandoffAttemptObserver
import com.valuepilot.app.UserObservedPriceSavedPrefillHandoffUiProjector
import com.valuepilot.app.UserObservedPriceSavedPrefillHandoffUiState
import com.valuepilot.app.UserObservedPriceSavedSelectionAction
import com.valuepilot.app.UserObservedPriceSavedSelectionCompositionCoordinator
import com.valuepilot.app.UserObservedPriceSavedSelectionRouteSession
import com.valuepilot.app.UserObservedPriceSavedSelectionSurfacePresenter
import com.valuepilot.app.UserObservedPriceSavedSelectionSurfaceRenderer
import com.valuepilot.app.UserObservedPriceSavedSelectionUiState
import com.valuepilot.app.UserObservedPriceProofContentReadResult
import com.valuepilot.app.ValuePilotArgentinaEdgeTransportFactory
import java.util.concurrent.Executors

internal enum class AddPrivateIdentityResult {
    ADDED,
    REFRESHED,
    SOURCE_UNAVAILABLE,
    BASKET_LIMIT,
    PERSISTENCE_UNAVAILABLE
}

/** Immutable state shared by the debug Compose journey and its existing 10133 providers. */
internal data class ValuePilotNextJourneyUiState(
    val query: String,
    val search: ArgentinaLiveSearchSurfaceState,
    val privateIdentities: List<PracticalShoppingPrivateIdentity>,
    val basket: ArgentinaPrivateBasketRenderState,
    val saved: PracticalShoppingSavedSurfaceState =
        PracticalShoppingSavedSurfaceProjector.project(
            PracticalShoppingSavedLifecycleController().initialState()
        ),
    val stapleWatchLaunch: PracticalShoppingSavedStapleLaunchUiState? = null,
    val stapleWatchSetup: StapleWatchSavedSelectionUiState? = null,
    val observedPriceSelection: UserObservedPriceSavedSelectionUiState? = null,
    val observedPricePrefillResult: UserObservedPriceSavedPrefillHandoffUiState? = null,
    val observedPriceConfirmation: ValuePilotNextObservedPriceConfirmationUiState? = null,
    val plan: ArgentinaPrivateBasketPlanProjection? = null,
    val planLoading: Boolean = false,
    val planError: String? = null,
    val tripResult: ArgentinaPrivateTripCostResult? = null,
    val tripError: String? = null,
    val actionNotice: String? = null
)

/**
 * Keeps the qualified Search and Saved lifecycle owners alive across Activity recreation.
 * Search offer evidence remains session-only; persisted private identities and Saved choices
 * continue to use their established stores.
 */
internal class ValuePilotNextJourneyViewModel : ViewModel() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val planExecutor = Executors.newSingleThreadExecutor()
    private val area = ArgentinaQualifiedLaunchAreas.BuenosAiresCaba
    private val offerEvidence = linkedMapOf<String, List<ArgentinaBackendContract.SearchOffer>>()

    private var store: AndroidPracticalShoppingHomeSessionStore? = null
    private var applicationContext: Context? = null
    private var searchHost: ArgentinaLiveSearchSurfaceHost? = null
    private var savedRouteCoordinator: PracticalShoppingSavedRouteCoordinator? = null
    private var stapleWatchSetupCoordinator: StapleWatchSavedSetupCompositionCoordinator? = null
    private var observedPriceSelectionCoordinator:
        UserObservedPriceSavedSelectionCompositionCoordinator? = null
    private var observedPricePrefillAttempt: UserObservedPriceSavedPrefillHandoffAttempt? = null
    private var observedPriceConfirmationJourney: ValuePilotNextObservedPriceConfirmationJourney? = null
    private var observedPriceProofPickerRequest: (() -> Unit)? = null
    private var initialized = false
    private var cleared = false
    private var planRequestId = 0L

    var uiState by mutableStateOf(
        ValuePilotNextJourneyUiState(
            query = "",
            search = ArgentinaLiveSearchSurfacePresenter.initial(area),
            privateIdentities = emptyList(),
            basket = ArgentinaPrivateBasketRenderer.render(
                identities = emptyList(),
                offersByIdentity = emptyMap(),
                plan = null
            )
        )
    )
        private set

    fun start(context: Context) {
        if (initialized || cleared) return
        check(Looper.myLooper() == Looper.getMainLooper())
        initialized = true
        applicationContext = context.applicationContext ?: context
        store = AndroidPracticalShoppingHomeSessionStore(requireNotNull(applicationContext))
        observedPriceConfirmationJourney =
            ValuePilotNextObservedPriceConfirmationJourney(
                context = requireNotNull(applicationContext),
                requestProofSelection = { observedPriceProofPickerRequest?.invoke() },
                publish = { next ->
                    if (!cleared) uiState = uiState.copy(observedPriceConfirmation = next)
                }
            )
        val observedPriceSelectionPresenter =
            UserObservedPriceSavedSelectionSurfacePresenter(
                UserObservedPriceSavedSelectionSurfaceRenderer(::publishObservedPriceSelection)
            )
        val stapleWatchSetupPresenter =
            StapleWatchSavedSelectionSurfacePresenter(
                factCheckCapability = StapleWatchForegroundFactCheckCapability.NOT_CONFIGURED,
                renderer = StapleWatchSavedSelectionSurfaceRenderer(::publishStapleWatchSetup)
            )
        stapleWatchSetupCoordinator =
            StapleWatchSavedSetupCompositionCoordinator(
                sessionFactory = { snapshot ->
                    StapleWatchSavedSelectionRouteSession(
                        initialSnapshot = snapshot,
                        presenter = stapleWatchSetupPresenter
                    )
                }
            )
        observedPriceSelectionCoordinator =
            UserObservedPriceSavedSelectionCompositionCoordinator(
                prefillHandoffAttemptObserver =
                    UserObservedPriceSavedPrefillHandoffAttemptObserver(::publishObservedPricePrefillAttempt)
            ) { snapshot ->
                UserObservedPriceSavedSelectionRouteSession(
                    initialSnapshot = snapshot,
                    presenter = observedPriceSelectionPresenter
                )
            }
        savedRouteCoordinator =
            PracticalShoppingSavedRouteCoordinator {
                val renderer =
                    PracticalShoppingSavedSurfacePresenter(
                        PracticalShoppingSavedSurfaceRenderer(::publishSavedSurface)
                    )
                val stapleLaunchPresenter =
                    PracticalShoppingSavedStapleLaunchPresenter(
                        PracticalShoppingSavedStapleLaunchRenderer(::publishStapleWatchLaunch)
                    )
                val compositeRenderer =
                    PracticalShoppingSavedLifecycleRenderer { state ->
                        renderer.render(state)
                        stapleLaunchPresenter.render(state)
                    }
                PracticalShoppingSavedAndroidSession.create(
                    context = requireNotNull(applicationContext),
                    renderer = compositeRenderer,
                    snapshotObserver =
                        PracticalShoppingSavedValidatedSnapshotObserver { snapshot ->
                            observedPriceSelectionCoordinator?.onSnapshot(snapshot)
                            stapleWatchSetupCoordinator?.onSnapshot(snapshot)
                        }
                )
            }
        val identities = store?.load()?.privateIdentities.orEmpty()
        uiState = uiState.copy(
            privateIdentities = identities,
            basket = renderBasket(identities, null, null, null)
        )
        searchHost = ArgentinaLiveSearchSurfaceHost.createProduction(area) { next ->
            val publish = {
                if (!cleared && next.generation >= uiState.search.generation) {
                    uiState = uiState.copy(search = next)
                }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) publish()
            else mainHandler.post(publish)
        }
    }

    fun onSavedRouteVisibilityChanged(visible: Boolean) {
        if (!initialized || cleared) return
        check(Looper.myLooper() == Looper.getMainLooper())
        savedRouteCoordinator?.onRouteVisibilityChanged(visible)
    }

    fun onSavedSurfaceAction(action: PracticalShoppingSavedSurfaceAction) {
        if (!initialized || cleared) return
        check(Looper.myLooper() == Looper.getMainLooper())
        savedRouteCoordinator?.onSurfaceAction(action)
    }

    fun onStapleWatchSetupRouteVisibilityChanged(visible: Boolean) {
        if (!initialized || cleared) return
        check(Looper.myLooper() == Looper.getMainLooper())
        stapleWatchSetupCoordinator?.onRouteVisibilityChanged(visible)
    }

    fun onStapleWatchSetupAction(action: StapleWatchSavedIdentitySelectionAction) {
        if (!initialized || cleared) return
        check(Looper.myLooper() == Looper.getMainLooper())
        stapleWatchSetupCoordinator?.onSurfaceAction(action)
    }

    fun onStapleWatchSetupContinue(action: StapleWatchSavedIdentityHandoffUiAction) {
        if (!initialized || cleared) return
        check(Looper.myLooper() == Looper.getMainLooper())
        stapleWatchSetupCoordinator?.onContinueAction(action)
    }

    fun onObservedPriceSelectionRouteVisibilityChanged(visible: Boolean) {
        if (!initialized || cleared) return
        check(Looper.myLooper() == Looper.getMainLooper())
        observedPriceSelectionCoordinator?.onRouteVisibilityChanged(visible)
        if (!visible) {
            observedPricePrefillAttempt = null
            uiState = uiState.copy(observedPricePrefillResult = null)
        }
    }

    fun onObservedPriceSelectionAction(action: UserObservedPriceSavedSelectionAction) {
        if (!initialized || cleared) return
        check(Looper.myLooper() == Looper.getMainLooper())
        observedPricePrefillAttempt = null
        uiState = uiState.copy(observedPricePrefillResult = null)
        observedPriceSelectionCoordinator?.onSurfaceAction(action)
    }

    fun onCheckObservedPricePrefill(
        action: com.valuepilot.app.UserObservedPriceSavedPrefillCheckUiAction
    ) {
        if (!initialized || cleared) return
        check(Looper.myLooper() == Looper.getMainLooper())
        observedPriceSelectionCoordinator?.onCheckPrefillAction(action)
    }

    fun currentObservedPricePrefillAttemptOrNull(): UserObservedPriceSavedPrefillHandoffAttempt? {
        if (!initialized || cleared) return null
        return observedPricePrefillAttempt?.takeIf { it.accepted }
    }

    fun prepareObservedPriceConfirmation(
        attempt: UserObservedPriceSavedPrefillHandoffAttempt
    ): Boolean {
        if (!initialized || cleared) return false
        check(Looper.myLooper() == Looper.getMainLooper())
        return observedPriceConfirmationJourney?.prepare(attempt) == true
    }

    fun onObservedPriceConfirmationRouteVisibilityChanged(visible: Boolean) {
        if (!initialized || cleared) return
        check(Looper.myLooper() == Looper.getMainLooper())
        observedPriceConfirmationJourney?.onRouteVisibilityChanged(visible)
    }

    fun cancelObservedPriceConfirmation() {
        if (!initialized || cleared) return
        check(Looper.myLooper() == Looper.getMainLooper())
        observedPriceConfirmationJourney?.cancel()
    }

    fun onObservedPriceConfirmationPriceInput(amountText: String, currencyCodeText: String) {
        if (!initialized || cleared) return
        check(Looper.myLooper() == Looper.getMainLooper())
        observedPriceConfirmationJourney?.onPriceInput(amountText, currencyCodeText)
    }

    fun onObservedPriceConfirmationTimeInput(
        dateText: String,
        timeText: String,
        utcOffsetText: String
    ) {
        if (!initialized || cleared) return
        check(Looper.myLooper() == Looper.getMainLooper())
        observedPriceConfirmationJourney?.onObservedAtInput(dateText, timeText, utcOffsetText)
    }

    fun onObservedPriceConfirmationProofReferenceInput(
        artifactId: String,
        proofType: com.valuepilot.app.UserProvidedPriceProofType?
    ) {
        if (!initialized || cleared) return
        check(Looper.myLooper() == Looper.getMainLooper())
        observedPriceConfirmationJourney?.onProofReferenceInput(artifactId, proofType)
    }

    fun onObservedPriceConfirmationProofSelectionRequested() {
        if (!initialized || cleared) return
        check(Looper.myLooper() == Looper.getMainLooper())
        observedPriceConfirmationJourney?.onProofSelectionRequested()
    }

    fun onObservedPriceProofContentReadResult(result: UserObservedPriceProofContentReadResult) {
        if (!initialized || cleared) return
        check(Looper.myLooper() == Looper.getMainLooper())
        observedPriceConfirmationJourney?.onProofContentReadResult(result)
    }

    fun onObservedPriceConfirmationSubmitRequested() {
        if (!initialized || cleared) return
        check(Looper.myLooper() == Looper.getMainLooper())
        observedPriceConfirmationJourney?.onSubmitRequested()
    }

    fun attachObservedPriceProofPicker(request: (() -> Unit)?) {
        if (!initialized || cleared) return
        check(Looper.myLooper() == Looper.getMainLooper())
        observedPriceProofPickerRequest = request
    }

    private fun publishSavedSurface(state: PracticalShoppingSavedSurfaceState) {
        val publish = {
            if (!cleared) uiState = uiState.copy(saved = state)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) publish()
        else mainHandler.post(publish)
    }

    private fun publishStapleWatchLaunch(state: PracticalShoppingSavedStapleLaunchUiState) {
        val publish = {
            if (!cleared) uiState = uiState.copy(stapleWatchLaunch = state)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) publish()
        else mainHandler.post(publish)
    }

    private fun publishStapleWatchSetup(state: StapleWatchSavedSelectionUiState) {
        val publish = {
            if (!cleared) uiState = uiState.copy(stapleWatchSetup = state)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) publish()
        else mainHandler.post(publish)
    }

    private fun publishObservedPriceSelection(state: UserObservedPriceSavedSelectionUiState) {
        val publish = {
            if (!cleared) uiState = uiState.copy(observedPriceSelection = state)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) publish()
        else mainHandler.post(publish)
    }

    private fun publishObservedPricePrefillAttempt(
        attempt: UserObservedPriceSavedPrefillHandoffAttempt
    ) {
        observedPricePrefillAttempt = attempt.takeIf { it.accepted }
        val result = UserObservedPriceSavedPrefillHandoffUiProjector.project(attempt)
        val publish = {
            if (!cleared) uiState = uiState.copy(observedPricePrefillResult = result)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) publish()
        else mainHandler.post(publish)
    }

    fun refreshPrivateIdentitiesFromStore() {
        if (!initialized || cleared) return
        check(Looper.myLooper() == Looper.getMainLooper())
        val stored = store?.load()?.privateIdentities ?: return
        if (stored == uiState.privateIdentities) return
        planRequestId = Math.addExact(planRequestId, 1L)
        offerEvidence.clear()
        uiState = uiState.copy(
            privateIdentities = stored,
            plan = null,
            planLoading = false,
            planError = null,
            basket = renderBasket(stored, null, null, null),
            actionNotice = "Your saved basket was refreshed."
        )
    }

    fun queryChanged(rawQuery: String) {
        if (!initialized || cleared) return
        uiState = uiState.copy(query = rawQuery, actionNotice = null)
        searchHost?.queryChanged(rawQuery)
    }

    fun submitSearch(rawQuery: String = uiState.query) {
        if (!initialized || cleared) return
        uiState = uiState.copy(query = rawQuery, actionNotice = null)
        searchHost?.submit(rawQuery)
    }

    fun loadMoreSearchResults() {
        if (!initialized || cleared) return
        searchHost?.loadMore()
    }

    fun addSearchMatch(
        match: PracticalShoppingSearchIdentityPresentation.Match
    ): AddPrivateIdentityResult {
        if (!initialized || cleared) return AddPrivateIdentityResult.PERSISTENCE_UNAVAILABLE
        val identity = PracticalShoppingPrivateIdentity.fromSearchMatch(match)
            ?: return AddPrivateIdentityResult.SOURCE_UNAVAILABLE
        val current = uiState.privateIdentities
        val existing = current.any { it.identityKey == identity.identityKey }
        if (!existing && current.size >= PracticalShoppingPrivateIdentity.MAX_COUNT) {
            return AddPrivateIdentityResult.BASKET_LIMIT
        }
        val next = if (existing) current else current + identity
        if (!existing && !persistPrivateIdentities(next)) {
            return AddPrivateIdentityResult.PERSISTENCE_UNAVAILABLE
        }
        offerEvidence[identity.identityKey] = match.qualifiedOffers.toList()
        clearPlanForBasketEdit()
        uiState = uiState.copy(
            privateIdentities = next,
            basket = renderBasket(next, null, null, null),
            actionNotice = if (existing) {
                "Current Search evidence refreshed for this item."
            } else {
                "Added to your private basket. Price evidence remains separate from the saved identity."
            }
        )
        return if (existing) AddPrivateIdentityResult.REFRESHED else AddPrivateIdentityResult.ADDED
    }

    fun removePrivateIdentity(identityKey: String): Boolean {
        if (!initialized || cleared || identityKey.isBlank()) return false
        val next = uiState.privateIdentities.filterNot { it.identityKey == identityKey }
        if (next.size == uiState.privateIdentities.size) return false
        if (!persistPrivateIdentities(next)) {
            uiState = uiState.copy(actionNotice = "The saved basket could not be updated.")
            return false
        }
        offerEvidence.remove(identityKey)
        clearPlanForBasketEdit()
        uiState = uiState.copy(
            privateIdentities = next,
            basket = renderBasket(next, null, null, null),
            actionNotice = "Removed from your private basket."
        )
        return true
    }

    fun requestBasketPlan() {
        if (!initialized || cleared) return
        val identities = uiState.privateIdentities.toList()
        val evidenceSnapshot = offerEvidence.mapValues { (_, offers) -> offers.toList() }
        val built = ArgentinaPrivateBasketRequestBuilder.build(
            identities = identities,
            offersByIdentity = evidenceSnapshot,
            area = area
        )
        val id = Math.addExact(planRequestId, 1L)
        planRequestId = id
        val unresolvedNotice = built.unresolvedIdentityKeys.size.takeIf { it > 0 }?.let {
            "$it selected item(s) have unknown current price evidence and will stay unpriced."
        }
        uiState = uiState.copy(
            plan = null,
            planLoading = built.request != null,
            planError = unresolvedNotice,
            basket = renderBasket(identities, null, null, unresolvedNotice),
            actionNotice = null
        )
        val request = built.request ?: return
        planExecutor.submit {
            val outcome = runCatching {
                val transport = ValuePilotArgentinaEdgeTransportFactory.create()
                val statusResult = ArgentinaBackendConsumerBridge(transport, null).status()
                val status = (statusResult as? ArgentinaBackendBridgeResult.Success)?.value
                    ?: run {
                        (statusResult as? ArgentinaBackendBridgeResult.Failure)?.let {
                            ArgentinaLiveSearchDiagnostics.record(it.code, it.metadata)
                        }
                        error("The qualified Argentina release is unavailable.")
                    }
                if (
                    status.freshness != ArgentinaBackendContract.Freshness.FRESH ||
                    !status.backendCodeQualified ||
                    !status.androidNetworkingAuthorized
                ) {
                    error("The qualified Argentina release is not authorized for this request.")
                }
                val expectedRelease = ArgentinaBackendContract.ExpectedRelease(
                    releaseId = status.releaseId ?: error("The qualified release has no id."),
                    releaseDate = status.releaseDate ?: error("The qualified release has no date.")
                )
                val result = ArgentinaBackendConsumerBridge(transport, expectedRelease).shop(request)
                result as? ArgentinaBackendBridgeResult.Success
                    ?: run {
                        (result as? ArgentinaBackendBridgeResult.Failure)?.let {
                            ArgentinaLiveSearchDiagnostics.record(it.code, it.metadata)
                        }
                        error("The basket plan could not be refreshed.")
                    }
            }
            mainHandler.post {
                if (cleared || id != planRequestId) return@post
                val success = outcome.getOrNull()
                if (success == null) {
                    val errorMessage =
                        "The basket plan could not be refreshed. Current prices remain unknown."
                    uiState = uiState.copy(
                        plan = null,
                        planLoading = false,
                        planError = errorMessage,
                        basket = renderBasket(
                            uiState.privateIdentities,
                            null,
                            uiState.tripResult,
                            errorMessage
                        )
                    )
                } else {
                    val plan = ArgentinaPrivateBasketPlanProjector.project(
                        response = success.value,
                        identities = uiState.privateIdentities
                    )
                    uiState = uiState.copy(
                        plan = plan,
                        planLoading = false,
                        planError = unresolvedNotice,
                        basket = renderBasket(
                            uiState.privateIdentities,
                            plan,
                            uiState.tripResult,
                            unresolvedNotice
                        )
                    )
                }
            }
        }
    }

    fun calculateTrip(
        oneWayDistance: String,
        fuelConsumption: String,
        fuelPrice: String,
        tolls: String,
        parking: String
    ) {
        try {
            val result = ArgentinaPrivateTripCostCalculator.calculate(
                ArgentinaPrivateTripCostInput(
                    oneWayDrivingKm = ArgentinaPrivateTripCostCalculator.parseDecimal(
                        oneWayDistance,
                        "one-way distance"
                    ),
                    fuelConsumptionLitresPer100Km = ArgentinaPrivateTripCostCalculator.parseDecimal(
                        fuelConsumption,
                        "fuel consumption"
                    ),
                    fuelPricePerLitre = ArgentinaPrivateTripCostCalculator.parseDecimal(
                        fuelPrice,
                        "fuel price"
                    ),
                    tolls = ArgentinaPrivateTripCostCalculator.parseDecimal(tolls, "tolls"),
                    parking = ArgentinaPrivateTripCostCalculator.parseDecimal(parking, "parking")
                )
            )
            uiState = uiState.copy(
                tripResult = result,
                tripError = null,
                basket = renderBasket(
                    uiState.privateIdentities,
                    uiState.plan,
                    result,
                    uiState.planError
                )
            )
        } catch (error: IllegalArgumentException) {
            uiState = uiState.copy(tripError = error.message ?: "Enter valid trip assumptions.")
        } catch (error: IllegalStateException) {
            uiState = uiState.copy(tripError = error.message ?: "Enter valid trip assumptions.")
        }
    }

    private fun persistPrivateIdentities(
        identities: List<PracticalShoppingPrivateIdentity>
    ): Boolean =
        runCatching {
            val identityStore = store ?: return false
            val snapshot = identityStore.load()
                ?: PracticalShoppingHomeSession.Snapshot(
                    query = "",
                    wasSubmitted = false,
                    chickenChoice = null
                )
            identityStore.save(snapshot.copy(privateIdentities = identities))
            identityStore.load()?.privateIdentities == identities
        }.getOrDefault(false)

    private fun clearPlanForBasketEdit() {
        planRequestId = Math.addExact(planRequestId, 1L)
        uiState = uiState.copy(
            plan = null,
            planLoading = false,
            planError = null
        )
    }

    private fun renderBasket(
        identities: List<PracticalShoppingPrivateIdentity>,
        plan: ArgentinaPrivateBasketPlanProjection?,
        trip: ArgentinaPrivateTripCostResult?,
        error: String?
    ): ArgentinaPrivateBasketRenderState =
        ArgentinaPrivateBasketRenderer.render(
            identities = identities,
            offersByIdentity = offerEvidence,
            plan = plan,
            tripResult = trip,
            errorNotice = error
        )

    override fun onCleared() {
        cleared = true
        planRequestId = Math.addExact(planRequestId, 1L)
        savedRouteCoordinator?.close()
        savedRouteCoordinator = null
        stapleWatchSetupCoordinator?.close()
        stapleWatchSetupCoordinator = null
        observedPriceSelectionCoordinator?.close()
        observedPriceSelectionCoordinator = null
        observedPricePrefillAttempt = null
        observedPriceConfirmationJourney?.close()
        observedPriceConfirmationJourney = null
        observedPriceProofPickerRequest = null
        applicationContext = null
        searchHost?.close()
        searchHost = null
        planExecutor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
        super.onCleared()
    }
}
