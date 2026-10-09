package com.valuepilot.app.next

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.valuepilot.app.ArgentinaBackendContract
import com.valuepilot.app.ArgentinaPrivateBasketLineProjection
import com.valuepilot.app.ArgentinaPrivateBasketPlanProjection
import com.valuepilot.app.ArgentinaPrivateBasketPlanStatus
import com.valuepilot.app.ArgentinaPrivateBasketRenderState
import com.valuepilot.app.ArgentinaPrivateBasketStoreProjection
import com.valuepilot.app.ArgentinaPrivateTripCostResult
import com.valuepilot.app.displayText
import com.valuepilot.app.ArgentinaQualifiedStoreMapContext
import com.valuepilot.app.ArgentinaQualifiedStoreMapContextPresentation
import com.valuepilot.app.PracticalShoppingSearchIdentityPresentation
import java.math.BigDecimal

@Composable
internal fun ValuePilotBasketScreen(
    basket: ArgentinaPrivateBasketRenderState,
    planLoading: Boolean,
    onSearch: () -> Unit,
    onRemove: (String) -> Unit,
    onPlan: () -> Unit
) {
    PremiumColumn {
        item(key = "basket-intro") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)) {
                Eyebrow("PRIVATE SHOPPING LIST")
                Text(
                    "Your basket, with evidence attached.",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    basket.evidenceNotice,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (basket.lines.isEmpty()) {
            item(key = "basket-empty") {
                EmptyStateCard(
                    eyebrow = "READY WHEN YOU ARE",
                    title = "Start with a product identity.",
                    detail = "Search results can be saved here. Current prices stay session-only and are never invented for missing evidence.",
                    actionLabel = "Search products",
                    onAction = onSearch
                )
            }
        } else {
            item(key = "basket-count") {
                SectionHeading(
                    "SELECTED PRODUCTS",
                    basket.lines.size.toString() + " item" + if (basket.lines.size == 1) "" else "s"
                )
            }
            items(basket.lines, key = { it.identityKey }) { line ->
                BasketIdentityCard(line = line, onRemove = { onRemove(line.identityKey) })
            }
            item(key = "basket-decision") {
                Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)) {
                    basket.errorNotice?.let { EvidenceNotice(it) }
                    PrimaryActionButton(
                        label = when {
                            planLoading -> "Building your decision…"
                            basket.canPlan -> "Build shopping decision"
                            else -> "Search for current price evidence"
                        },
                        onClick = if (basket.canPlan) onPlan else onSearch,
                        enabled = !planLoading
                    )
                    if (!basket.canPlan) {
                        SecondaryActionButton(
                            "Review the basket with unknown prices",
                            onClick = onPlan,
                            enabled = !planLoading
                        )
                    }
                    Text(
                        "Only identities with fresh qualified quantity and price evidence are sent for a shopping decision. Other items remain unpriced.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun BasketIdentityCard(
    line: ArgentinaPrivateBasketLineProjection,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (line.missing) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = ValuePilotElevation.flat)
    ) {
        Column(
            modifier = Modifier.padding(ValuePilotSpacing.large),
            verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)
        ) {
            Text(line.productName, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(line.packageQuantityText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (line.missing) {
                EvidencePill(
                    "CURRENT PRICE UNKNOWN",
                    MaterialTheme.colorScheme.surface,
                    MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "No fresh eligible price offer is attached to this saved identity.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "Listed price  " + (line.lineTotalText ?: "unknown"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                line.storeName?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                EvidencePill(
                    "AVAILABILITY UNKNOWN",
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            TextButton(
                onClick = onRemove,
                modifier = Modifier.heightIn(min = ValuePilotSpacing.minimumTouchTarget)
            ) {
                Text("Remove from basket", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
internal fun ValuePilotShoppingDecisionScreen(
    basket: ArgentinaPrivateBasketRenderState,
    plan: ArgentinaPrivateBasketPlanProjection?,
    planLoading: Boolean,
    planError: String?,
    tripResult: ArgentinaPrivateTripCostResult?,
    onEditBasket: () -> Unit,
    onTripPlanner: () -> Unit,
    onMap: () -> Unit
) {
    PremiumColumn {
        item(key = "decision-intro") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)) {
                Eyebrow("SHOPPING DECISION")
                Text(
                    "The basket, as the evidence allows.",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Current listed-price totals, unknown coverage and manual trip costs remain separate.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (planLoading) {
            item(key = "decision-loading") {
                LoadingRow("Requesting a fresh qualified basket plan")
            }
        }
        if (plan == null && !planLoading) {
            item(key = "decision-unavailable") {
                EmptyStateCard(
                    eyebrow = "NO CURRENT PLAN",
                    title = "No verified basket total is available yet.",
                    detail = planError ?: "Search items again to attach fresh qualified price and package evidence before planning.",
                    actionLabel = "Review basket",
                    onAction = onEditBasket
                )
            }
        }
        if (plan == null) {
            item(key = "decision-trip-without-plan") {
                ActionCard(
                    eyebrow = "TRAVEL COST",
                    title = tripResult?.displayText() ?: "Calculate a trip from your own assumptions.",
                    detail = "Distance, fuel, toll and parking remain separate from basket prices. ValuePilot does not infer a driving route.",
                    actionLabel = if (tripResult == null) "Open trip planner" else "Review trip assumptions",
                    onClick = onTripPlanner
                )
            }
            item(key = "decision-map-without-plan") {
                SecondaryActionButton("Check for source-backed store locations", onMap)
            }
        }
        if (plan != null) {
            item(key = "decision-summary") { DecisionSummaryCard(plan) }
            item(key = "decision-lines-heading") {
                SectionHeading("LINE BY LINE", "What the plan covers")
            }
            items(plan.lines, key = { it.identityKey }) { line -> DecisionLineCard(line) }
            item(key = "decision-stores") { PlanStoreSummary(plan) }
            item(key = "decision-trip") {
                ActionCard(
                    eyebrow = "TRAVEL COST",
                    title = tripResult?.displayText() ?: "Trip cost is not included yet.",
                    detail = "Enter your own distance, fuel, toll and parking assumptions. ValuePilot does not infer a driving route.",
                    actionLabel = if (tripResult == null) "Add trip assumptions" else "Review trip assumptions",
                    onClick = onTripPlanner
                )
            }
            item(key = "decision-map") {
                PrimaryActionButton("View store context and map handoff", onMap)
            }
            item(key = "decision-edit") {
                TextButton(onClick = onEditBasket, modifier = Modifier.heightIn(min = ValuePilotSpacing.minimumTouchTarget)) {
                    Text("Edit basket")
                }
            }
        }
        if (basket.errorNotice != null && basket.errorNotice != planError) {
            item(key = "decision-basket-note") { EvidenceNotice(basket.errorNotice) }
        }
    }
}

@Composable
private fun DecisionSummaryCard(plan: ArgentinaPrivateBasketPlanProjection) {
    val complete = plan.status == ArgentinaPrivateBasketPlanStatus.CURRENT_COMPLETE
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
            EvidencePill(
                if (complete) "COMPLETE PRICE COVERAGE" else "PARTIAL PRICE COVERAGE",
                MaterialTheme.colorScheme.surface.copy(alpha = 0.74f),
                MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                plan.totalText ?: plan.headline,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                plan.coverageText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                plan.stores.size.toString() + " selected store" +
                    (if (plan.stores.size == 1) "" else "s") + " · release " + plan.releaseDate,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                plan.notice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun DecisionLineCard(line: ArgentinaPrivateBasketLineProjection) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = ValuePilotElevation.flat)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(ValuePilotSpacing.large),
            horizontalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium),
            verticalAlignment = Alignment.Top
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(
                        if (line.missing) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.secondaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (line.missing) "?" else "•",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (line.missing) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)) {
                Text(line.productName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(line.packageQuantityText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (line.missing) "Price evidence unknown" else line.lineTotalText ?: "Listed price unknown",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (line.missing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                line.storeName?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PlanStoreSummary(plan: ArgentinaPrivateBasketPlanProjection) {
    Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)) {
        SectionHeading("STORE CHOICES", "Where the listed prices came from")
        if (plan.stores.isEmpty()) {
            EvidenceNotice("No store coordinates were returned with this plan.")
        } else {
            plan.stores.forEach { store ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(ValuePilotSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)
                ) {
                    Text(store.storeName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    store.addressLabel?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "Store context is separate from availability and travel routing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
internal fun ValuePilotTripPlannerScreen(
    plan: ArgentinaPrivateBasketPlanProjection?,
    tripResult: ArgentinaPrivateTripCostResult?,
    tripError: String?,
    distance: String,
    consumption: String,
    fuelPrice: String,
    tolls: String,
    parking: String,
    onInputChanged: (String, String, String, String, String) -> Unit,
    onCalculate: (String, String, String, String, String) -> Unit,
    onMap: () -> Unit
) {
    PremiumColumn {
        item(key = "trip-intro") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)) {
                Eyebrow("MANUAL TRIP ESTIMATE")
                Text(
                    "See what the journey adds.",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Enter a one-way driving distance and your own vehicle costs. ValuePilot does not infer a route or turn straight-line distance into driving distance.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item(key = "trip-basket-summary") {
            EvidenceNotice(plan?.totalText ?: "Basket price evidence is not available.")
        }
        item(key = "trip-fields") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)) {
                DecimalInput("One-way driving distance", "km", distance) {
                    onInputChanged(it, consumption, fuelPrice, tolls, parking)
                }
                DecimalInput("Fuel consumption", "L / 100 km", consumption) {
                    onInputChanged(distance, it, fuelPrice, tolls, parking)
                }
                DecimalInput("Fuel price", "ARS / L", fuelPrice) {
                    onInputChanged(distance, consumption, it, tolls, parking)
                }
                DecimalInput("Tolls", "ARS · enter 0 if none", tolls) {
                    onInputChanged(distance, consumption, fuelPrice, it, parking)
                }
                DecimalInput("Parking", "ARS · enter 0 if none", parking) {
                    onInputChanged(distance, consumption, fuelPrice, tolls, it)
                }
                Text(
                    "Use a period for decimals. This estimate is separate from the listed basket price.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                tripError?.let { EvidenceNotice(it, isError = true) }
                PrimaryActionButton(
                    "Calculate trip cost",
                    onClick = { onCalculate(distance, consumption, fuelPrice, tolls, parking) }
                )
            }
        }
        tripResult?.let { result ->
            item(key = "trip-result") {
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
                        Eyebrow("ESTIMATED TRIP COST")
                        Text(
                            "ARS " + result.total.toPlainString(),
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Fuel ARS " + result.fuelCost.toPlainString() +
                                " · tolls ARS " + result.tolls.toPlainString() +
                                " · parking ARS " + result.parking.toPlainString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Round trip " + result.roundTripKm.toPlainString() +
                                " km, calculated from your one-way input.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "No route or distance is inferred.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
        item(key = "trip-map") {
            SecondaryActionButton("View store map handoff", onMap)
        }
    }
}

@Composable
private fun DecimalInput(
    label: String,
    supportingLabel: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw -> onValueChange(raw.filter { it.isDigit() || it == '.' }) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        supportingText = { Text(supportingLabel) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        ),
        shape = MaterialTheme.shapes.large
    )
}

@Composable
internal fun ValuePilotMapHandoffScreen(
    plan: ArgentinaPrivateBasketPlanProjection?,
    selectedOffer: ArgentinaBackendContract.SearchOffer?,
    selectedProductName: String?,
    onOpenMap: (ArgentinaPrivateBasketStoreProjection) -> Unit
) {
    PremiumColumn {
        item(key = "map-intro") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)) {
                Eyebrow("SOURCE-BACKED STORE CONTEXT")
                Text(
                    "Take the store context to your map app.",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "ValuePilot passes valid source coordinates to an external map app. It does not draw a map, infer a route, or confirm store availability.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        selectedOffer?.let { offer ->
            item(key = "map-selected-offer") {
                val context = ArgentinaQualifiedStoreMapContextPresentation.fromOffer(
                    offer,
                    selectedProductName ?: offer.productEvidenceKey
                )
                StoreContextCard(
                    context = context,
                    detail = listOfNotNull(
                        selectedProductName,
                        offer.quantity?.let { "Package " + it.amount + " " + it.unit },
                        offer.listPrice.currency + " " + offer.listPrice.amount,
                        "Price evidence " + offer.freshnessStatus.name.lowercase().replace('_', ' '),
                        "Release " + offer.releaseDate
                    ).joinToString(" · "),
                    onOpenMap = context.asStoreProjection()?.let { store -> { onOpenMap(store) } }
                )
            }
        }

        val stores = plan?.stores.orEmpty()
        if (stores.isEmpty() && selectedOffer == null) {
            item(key = "map-empty") {
                EmptyStateCard(
                    eyebrow = "NO STORE COORDINATES",
                    title = "A map handoff is not available yet.",
                    detail = "Build a current basket plan to see source-backed store coordinates. No location or route is guessed."
                )
            }
        } else if (stores.isNotEmpty()) {
            item(key = "map-plan-heading") {
                SectionHeading("SELECTED PLAN", "Source locations")
            }
            items(stores, key = { it.storeKey }) { store ->
                val context = ArgentinaQualifiedStoreMapContextPresentation.fromStore(
                    store,
                    plan?.mapMarkers.orEmpty()
                )
                val validStore = context.asStoreProjection()
                val markerSummary = context.markers.joinToString(" · ") {
                    it.productName + " — " + it.priceText
                }.ifBlank { "No product price marker is attached." }
                StoreContextCard(
                    context = context,
                    detail = markerSummary,
                    onOpenMap = validStore?.let { projection -> { onOpenMap(projection) } }
                )
            }
        }
        item(key = "map-footnote") {
            EvidenceFootnote(
                "Coordinates are source evidence only. No route, travel time, stock, pickup, delivery or availability is inferred."
            )
        }
    }
}

@Composable
private fun StoreContextCard(
    context: ArgentinaQualifiedStoreMapContext,
    detail: String,
    onOpenMap: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = ValuePilotElevation.flat)
    ) {
        Column(
            modifier = Modifier.padding(ValuePilotSpacing.large),
            verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)
        ) {
            Text(context.storeName, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            context.addressLabel?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val coordinates = if (context.latitude != null && context.longitude != null) {
                context.latitude + ", " + context.longitude
            } else null
            Text(
                coordinates ?: "Valid coordinates were not supplied.",
                style = MaterialTheme.typography.labelLarge,
                color = if (coordinates == null) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.secondary
            )
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onOpenMap != null) {
                PrimaryActionButton("Open in map app", onOpenMap)
            } else {
                Text(
                    "External map handoff unavailable without valid source coordinates.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun ArgentinaQualifiedStoreMapContext.asStoreProjection():
    ArgentinaPrivateBasketStoreProjection? {
    val validLatitude = latitude ?: return null
    val validLongitude = longitude ?: return null
    return ArgentinaPrivateBasketStoreProjection(
        storeKey = storeKey,
        storeName = storeName,
        latitude = validLatitude,
        longitude = validLongitude,
        addressLabel = addressLabel
    )
}
