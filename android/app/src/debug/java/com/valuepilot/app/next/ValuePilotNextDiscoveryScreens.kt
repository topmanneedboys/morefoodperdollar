package com.valuepilot.app.next

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valuepilot.app.ArgentinaBackendContract
import com.valuepilot.app.ArgentinaLiveSearchSurfaceMode
import com.valuepilot.app.ArgentinaLiveSearchSurfaceState
import com.valuepilot.app.PracticalShoppingSearchIdentityPresentation

internal fun usesCompactDiscoveryContent(availableWidth: Dp, fontScale: Float): Boolean =
    availableWidth < 420.dp || fontScale >= 1.2f

@Composable
internal fun ValuePilotHomeScreen(
    basketCount: Int,
    areaLabel: String,
    onSearch: () -> Unit,
    onBasket: () -> Unit,
    onGoodPriceCheck: () -> Unit,
    onTripPlanner: () -> Unit,
    onCompare: () -> Unit
) {
    PremiumColumn {
        item(key = "home-intro") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.large)) {
                Eyebrow("VALUE, MADE CLEAR")
                Text(
                    "Shop with the full picture.",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "See product identity, listed-price evidence and the cost of getting there as separate, understandable parts of the decision.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                EvidencePill(
                    "SEARCH AREA  ·  $areaLabel",
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        item(key = "home-search") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = ValuePilotElevation.flat)
            ) {
                Column(
                    modifier = Modifier.padding(ValuePilotSpacing.xxLarge),
                    verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)
                ) {
                    Text(
                        "Start with an exact product identity.",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "Search sends a user-triggered request to the qualified Argentina source. Prices appear only when fresh listed-price evidence is attached.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    PrimaryActionButton("Search products", onSearch)
                }
            }
        }
        item(key = "home-basket") {
            ActionCard(
                eyebrow = "YOUR PRIVATE BASKET",
                title = if (basketCount == 0) {
                    "Keep chosen identities together."
                } else {
                    basketCount.toString() + " selected product" + if (basketCount == 1) "" else "s"
                },
                detail = if (basketCount == 0) {
                    "Add exact identities from Search. Saved identities stay on this device; current offer evidence remains separate and session-only."
                } else {
                    "Review price coverage, remove items, or request a current multi-store shopping decision."
                },
                actionLabel = if (basketCount == 0) "Open basket" else "Review basket",
                onClick = onBasket
            )
        }
        item(key = "home-principle") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(ValuePilotSpacing.small),
                verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)
            ) {
                Eyebrow("THE VALUEPILOT PROMISE")
                Text(
                    "Unknown stays unknown.",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Package quantity, unit price, source freshness and store context stay visible. A listed price never implies stock or a verified saving.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item(key = "home-manual-tools-heading") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)) {
                Eyebrow("MANUAL SHOPPING TOOLS")
                Text(
                    "Use the details you observed.",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
        item(key = "home-good-price-check") {
            ActionCard(
                eyebrow = "PERSONAL PRICE CHECK",
                title = "Compare the value in a pack.",
                detail = "Enter the exact package quantity and shelf price you saw. The calculation uses only your entries.",
                actionLabel = "Check a shelf price",
                onClick = onGoodPriceCheck
            )
        }
        item(key = "home-trip-planner") {
            ActionCard(
                eyebrow = "MANUAL TRIP ESTIMATE",
                title = "Add the cost of getting there.",
                detail = "Enter your own driving distance, fuel use, fuel price, tolls and parking. No route or fuel price is inferred.",
                actionLabel = "Estimate trip cost",
                onClick = onTripPlanner
            )
        }
        item(key = "home-compare") {
            TextButton(
                onClick = onCompare,
                modifier = Modifier.heightIn(min = ValuePilotSpacing.minimumTouchTarget)
            ) {
                Text("Open the current product comparison")
            }
        }
    }
}

@Composable
internal fun ValuePilotSearchScreen(
    query: String,
    state: ArgentinaLiveSearchSurfaceState,
    onQueryChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenMatch: (PracticalShoppingSearchIdentityPresentation.Match) -> Unit
) {
    PremiumColumn {
        item(key = "search-intro") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)) {
                Eyebrow("QUALIFIED LIVE SEARCH")
                Text(
                    "Find the product first.",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Identity matches are separate from current price evidence. No stock or availability is inferred.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                EvidencePill(
                    state.areaLabel.uppercase(),
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item(key = "search-field") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Product name or identity") },
                    placeholder = { Text("For example, yerba mate") },
                    singleLine = true,
                    maxLines = 1,
                    enabled = state.mode != ArgentinaLiveSearchSurfaceMode.LOADING,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    shape = MaterialTheme.shapes.large
                )
                PrimaryActionButton(
                    label = if (state.mode == ArgentinaLiveSearchSurfaceMode.LOADING) "Searching…" else "Search this area",
                    onClick = onSubmit,
                    enabled = query.trim().isNotEmpty() &&
                        query.length <= ArgentinaBackendContract.MAX_QUERY_CHARS &&
                        state.mode != ArgentinaLiveSearchSurfaceMode.LOADING
                )
                Text(
                    "Search is limited to " + ArgentinaBackendContract.MAX_QUERY_CHARS + " characters.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item(key = "search-state") {
            SearchStatePanel(state)
        }

        val presentation = state.presentation
        val matches = presentation?.matches.orEmpty()
        if (matches.isNotEmpty()) {
            item(key = "search-results-heading") {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val compact = usesCompactDiscoveryContent(maxWidth, LocalDensity.current.fontScale)
                    if (compact) {
                        Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)) {
                            SearchResultsCount(matches.size)
                            Text(
                                "Page " + ((presentation?.page ?: 0) + 1),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            SearchResultsCount(matches.size)
                            Text(
                                "Page " + ((presentation?.page ?: 0) + 1),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            items(matches, key = { it.identityKey }) { match ->
                SearchResultCard(match = match, onClick = { onOpenMatch(match) })
            }
            if (state.mode == ArgentinaLiveSearchSurfaceMode.LOADING) {
                item(key = "search-more-loading") {
                    LoadingRow("Loading more matching identities")
                }
            } else if (presentation?.hasMore == true && presentation.nextPage != null) {
                item(key = "search-load-more") {
                    SecondaryActionButton("Load more results", onLoadMore)
                }
            }
            item(key = "search-caveat") {
                EvidenceFootnote(state.caveat)
            }
        }
    }
}

@Composable
private fun SearchResultsCount(count: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)) {
        Eyebrow("MATCHING IDENTITIES")
        Text(
            count.toString() + " result" + if (count == 1) "" else "s",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun SearchStatePanel(state: ArgentinaLiveSearchSurfaceState) {
    val unavailable = state.mode == ArgentinaLiveSearchSurfaceMode.AUTHORIZATION_UNAVAILABLE ||
        state.mode == ArgentinaLiveSearchSurfaceMode.STALE_RELEASE ||
        state.mode == ArgentinaLiveSearchSurfaceMode.TEMPORARILY_UNAVAILABLE ||
        state.mode == ArgentinaLiveSearchSurfaceMode.TIMEOUT
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (unavailable) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.58f)
            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            if (unavailable) MaterialTheme.colorScheme.error.copy(alpha = 0.28f)
            else MaterialTheme.colorScheme.outlineVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = ValuePilotElevation.flat)
    ) {
        Row(
            modifier = Modifier.padding(ValuePilotSpacing.large),
            horizontalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium),
            verticalAlignment = Alignment.Top
        ) {
            if (state.mode == ArgentinaLiveSearchSurfaceMode.LOADING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            if (unavailable) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.secondary,
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)) {
                Text(
                    state.statusMessage,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    state.caveat,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    match: PracticalShoppingSearchIdentityPresentation.Match,
    onClick: () -> Unit
) {
    val currentOffer = match.qualifiedOffers.firstOrNull {
        it.freshnessStatus == ArgentinaBackendContract.Freshness.FRESH
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = ValuePilotElevation.flat)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = usesCompactDiscoveryContent(maxWidth, LocalDensity.current.fontScale)
            Column(
                modifier = Modifier.padding(ValuePilotSpacing.large),
                verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)
            ) {
                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)) {
                        SearchIdentitySummary(match)
                        currentOffer?.let { SearchPriceLabel(it, Modifier.align(Alignment.End)) }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium),
                        verticalAlignment = Alignment.Top
                    ) {
                        SearchIdentitySummary(match, Modifier.weight(1f))
                        currentOffer?.let { SearchPriceLabel(it) }
                    }
                }
                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)) {
                        SearchMatchPill(match.matchLabel)
                        SearchFreshnessLabel(currentOffer != null)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SearchMatchPill(match.matchLabel)
                        SearchFreshnessLabel(currentOffer != null)
                    }
                }
                if (currentOffer != null) {
                    val unit = currentOffer.unitPrice?.let { value ->
                        val basis = currentOffer.unitPriceBasis?.takeIf(String::isNotBlank)
                        value.currency + " " + value.amount + (basis?.let { " / " + it } ?: "")
                    }
                    Text(
                        listOfNotNull(unit, currentOffer.storeName, "Availability unknown").joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        if (match.qualifiedOffers.isNotEmpty()) {
                            "Price evidence is not fresh enough to show as current."
                        } else {
                            "No current price or store offer is attached to this identity."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("Open product details", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun SearchIdentitySummary(
    match: PracticalShoppingSearchIdentityPresentation.Match,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)) {
        Text(
            match.displayName,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        listOfNotNull(match.brand, match.packageQuantityLabel)
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" · ")
            ?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
    }
}

@Composable
private fun SearchPriceLabel(
    offer: ArgentinaBackendContract.SearchOffer,
    modifier: Modifier = Modifier
) {
    Text(
        offer.listPrice.currency + " " + offer.listPrice.amount,
        modifier = modifier,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun SearchMatchPill(matchLabel: String) {
    EvidencePill(
        matchLabel.uppercase(),
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.onPrimaryContainer
    )
}

@Composable
private fun SearchFreshnessLabel(hasFreshPrice: Boolean) {
    Text(
        if (hasFreshPrice) "Fresh listed-price evidence" else "Identity match",
        style = MaterialTheme.typography.labelMedium,
        color = if (hasFreshPrice) MaterialTheme.colorScheme.secondary
        else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
internal fun ValuePilotProductDetailScreen(
    match: PracticalShoppingSearchIdentityPresentation.Match?,
    isSearchLoading: Boolean,
    onAddToBasket: (PracticalShoppingSearchIdentityPresentation.Match) -> Unit,
    onSearchAgain: () -> Unit,
    onOpenMapContext: (ArgentinaBackendContract.SearchOffer) -> Unit
) {
    PremiumColumn {
        if (match == null) {
            item(key = "product-detail-unavailable") {
                if (isSearchLoading) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = ValuePilotSpacing.section),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.large)
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text("Restoring the selected Search identity…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    EmptyStateCard(
                        eyebrow = "SEARCH RESULT",
                        title = "This selection is no longer in the current result set.",
                        detail = "Run Search again to refresh its identity and any current offer evidence.",
                        actionLabel = "Return to Search",
                        onAction = onSearchAgain
                    )
                }
            }
        } else {
            item(key = "product-detail-identity") {
                Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)) {
                    Eyebrow("PRODUCT IDENTITY")
                    Text(match.displayName, style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onBackground)
                    listOfNotNull(match.brand, match.packageQuantityLabel)
                        .takeIf { it.isNotEmpty() }
                        ?.joinToString(" · ")
                        ?.let {
                            Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val compact = usesCompactDiscoveryContent(maxWidth, LocalDensity.current.fontScale)
                        if (compact) {
                            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)) {
                                SearchMatchPill(match.matchLabel)
                                EvidencePill(
                                    match.sourceLabel ?: "UNVERIFIED SOURCE",
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)) {
                                SearchMatchPill(match.matchLabel)
                                EvidencePill(
                                    match.sourceLabel ?: "UNVERIFIED SOURCE",
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Text(
                        "Product identity, package quantity and listed-price evidence are separate facts. Missing package or price information is not filled in.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item(key = "product-detail-offers-title") {
                SectionHeading(
                    "OFFER EVIDENCE",
                    if (match.qualifiedOffers.isEmpty()) "No current offer attached" else "Source-listed offers"
                )
            }
            if (match.qualifiedOffers.isEmpty()) {
                item(key = "product-detail-no-offer") {
                    EmptyStateCard(
                        eyebrow = "IDENTITY ONLY",
                        title = "Price and store are unknown.",
                        detail = "The qualified Search result includes an exact product identity but no attached listed-price evidence."
                    )
                }
            } else {
                items(match.qualifiedOffers, key = { it.offerKey }) { offer ->
                    OfferEvidenceCard(offer, onOpenMapContext = { onOpenMapContext(offer) })
                }
            }
            item(key = "product-detail-add") {
                Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)) {
                    PrimaryActionButton(
                        label = "Save identity to private basket",
                        onClick = { onAddToBasket(match) },
                        enabled = !match.sourceLabel.isNullOrBlank()
                    )
                    EvidenceFootnote(
                        "Only the source-labelled identity is saved durably. Search offer evidence remains session-only and is rechecked before a shopping decision."
                    )
                }
            }
        }
    }
}

@Composable
private fun OfferEvidenceCard(
    offer: ArgentinaBackendContract.SearchOffer,
    onOpenMapContext: () -> Unit
) {
    val fresh = offer.freshnessStatus == ArgentinaBackendContract.Freshness.FRESH
    val latitude = offer.storeLatitude.toBigDecimalOrNull()
    val longitude = offer.storeLongitude.toBigDecimalOrNull()
    val validCoordinates =
        latitude != null && latitude >= java.math.BigDecimal("-90") && latitude <= java.math.BigDecimal("90") &&
            longitude != null && longitude >= java.math.BigDecimal("-180") && longitude <= java.math.BigDecimal("180")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = ValuePilotElevation.flat)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = usesCompactDiscoveryContent(maxWidth, LocalDensity.current.fontScale)
            Column(
                modifier = Modifier.padding(ValuePilotSpacing.large),
                verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)
            ) {
                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)) {
                        Eyebrow(if (fresh) "FRESH LISTED PRICE" else "NOT CURRENT")
                        Text(
                            offer.listPrice.currency + " " + offer.listPrice.amount,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        OfferFreshnessPill(offer, fresh)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)) {
                            Eyebrow(if (fresh) "FRESH LISTED PRICE" else "NOT CURRENT")
                            Text(
                                offer.listPrice.currency + " " + offer.listPrice.amount,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        OfferFreshnessPill(offer, fresh)
                    }
                }
                Text(offer.storeName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                offer.quantity?.let {
                    Text(
                        "Package " + it.amount + " " + it.unit,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } ?: Text("Package quantity unknown", color = MaterialTheme.colorScheme.onSurfaceVariant)
                offer.unitPrice?.let { unit ->
                    val basis = offer.unitPriceBasis?.takeIf(String::isNotBlank)
                    Text(
                        "Unit price  " + unit.currency + " " + unit.amount + (basis?.let { " / " + it } ?: ""),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                } ?: Text("Unit price not supplied by this offer", color = MaterialTheme.colorScheme.onSurfaceVariant)
                EvidencePill(
                    "AVAILABILITY UNKNOWN",
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    offer.provenance.provider + " · release " + offer.releaseDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "A published price does not confirm stock, pickup, delivery or a best-value ranking.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (validCoordinates) {
                    TextButton(
                        onClick = onOpenMapContext,
                        modifier = Modifier.heightIn(min = ValuePilotSpacing.minimumTouchTarget)
                    ) {
                        Text("View source store context")
                    }
                }
            }
        }
    }
}

@Composable
private fun OfferFreshnessPill(
    offer: ArgentinaBackendContract.SearchOffer,
    fresh: Boolean
) {
    EvidencePill(
        offer.freshnessStatus.name.replace('_', ' '),
        if (fresh) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
        if (fresh) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer
    )
}
