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

internal data class ValuePilotNextBasketIntroPresentation(
    val title: String,
    val detail: String?
)

internal fun valuePilotNextBasketIntroPresentation(
    itemCount: Int,
    hasMissingPriceEvidence: Boolean,
    language: ValuePilotNextLanguage = ValuePilotNextLanguage.ENGLISH
): ValuePilotNextBasketIntroPresentation = when {
    itemCount <= 0 -> ValuePilotNextBasketIntroPresentation(
        title = valuePilotNextText(language, "Your basket is ready for its first item.", "Tu canasta está lista para el primer producto."),
        detail = valuePilotNextText(
            language,
            "Add exact product identities. Prices appear only when fresh evidence is available; availability remains unknown.",
            "Sumá identidades exactas. Los precios aparecen solo cuando hay evidencia reciente; la disponibilidad sigue sin confirmar."
        )
    )
    hasMissingPriceEvidence -> ValuePilotNextBasketIntroPresentation(
        title = valuePilotNextText(language, "Your basket, with price gaps made clear.", "Tu canasta, con los precios faltantes a la vista."),
        detail = null
    )
    else -> ValuePilotNextBasketIntroPresentation(
        title = valuePilotNextText(language, "Your basket, with evidence attached.", "Tu canasta, con evidencia adjunta."),
        detail = null
    )
}

@Composable
internal fun ValuePilotBasketScreen(
    basket: ArgentinaPrivateBasketRenderState,
    planLoading: Boolean,
    onSearch: () -> Unit,
    onRemove: (String) -> Unit,
    onPlan: () -> Unit
) {
    val language = LocalValuePilotNextLanguage.current
    PremiumColumn {
        item(key = "basket-intro") {
            val intro = valuePilotNextBasketIntroPresentation(
                itemCount = basket.lines.size,
                hasMissingPriceEvidence = basket.lines.any { it.missing },
                language = LocalValuePilotNextLanguage.current
            )
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)) {
                Eyebrow(valuePilotNextText("PRIVATE SHOPPING LIST", "LISTA PRIVADA DE COMPRAS"))
                Text(
                    intro.title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    intro.detail ?: valuePilotNextLocalizedDynamicText(language, basket.evidenceNotice).orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (basket.lines.isEmpty()) {
            item(key = "basket-empty") {
                EmptyStateCard(
                    eyebrow = valuePilotNextText("READY WHEN YOU ARE", "LISTA CUANDO VOS QUIERAS"),
                    title = valuePilotNextText("Start with a product identity.", "Empezá con una identidad de producto."),
                    detail = valuePilotNextText(
                        "Search results can be saved here. Current prices stay session-only and are never invented for missing evidence.",
                        "Podés guardar resultados de búsqueda acá. Los precios actuales duran la sesión y nunca se inventan cuando falta evidencia."
                    ),
                    actionLabel = valuePilotNextText("Search products", "Buscar productos"),
                    onAction = onSearch
                )
            }
        } else {
            item(key = "basket-count") {
                SectionHeading(
                    valuePilotNextText("SELECTED PRODUCTS", "PRODUCTOS ELEGIDOS"),
                    basket.lines.size.toString() + if (basket.lines.size == 1) {
                        valuePilotNextText(" item", " producto")
                    } else {
                        valuePilotNextText(" items", " productos")
                    }
                )
            }
            items(basket.lines, key = { it.identityKey }) { line ->
                BasketIdentityCard(line = line, onRemove = { onRemove(line.identityKey) })
            }
            item(key = "basket-decision") {
                Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)) {
                    basket.errorNotice?.let {
                        EvidenceNotice(valuePilotNextLocalizedDynamicText(language, it).orEmpty())
                    }
                    PrimaryActionButton(
                        label = when {
                            planLoading -> valuePilotNextText("Building your decision…", "Armando tu decisión…")
                            basket.canPlan -> valuePilotNextText("Build shopping decision", "Armar decisión de compra")
                            else -> valuePilotNextText("Search for current price evidence", "Buscar evidencia de precio actual")
                        },
                        onClick = if (basket.canPlan) onPlan else onSearch,
                        enabled = !planLoading
                    )
                    if (!basket.canPlan) {
                        SecondaryActionButton(
                            valuePilotNextText("Review the basket with unknown prices", "Revisar la canasta con precios desconocidos"),
                            onClick = onPlan,
                            enabled = !planLoading
                        )
                    }
                    Text(
                        valuePilotNextText(
                            "Only identities with fresh qualified quantity and price evidence are sent for a shopping decision. Other items remain unpriced.",
                            "Solo las identidades con cantidad y precio calificados y recientes pasan a la decisión de compra. Los demás productos quedan sin precio."
                        ),
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
                    valuePilotNextText("CURRENT PRICE UNKNOWN", "PRECIO ACTUAL DESCONOCIDO"),
                    MaterialTheme.colorScheme.surface,
                    MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    valuePilotNextText(
                        "No fresh eligible price offer is attached to this saved identity.",
                        "No hay una oferta de precio elegible y reciente asociada a esta identidad guardada."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    valuePilotNextText("Listed price  ", "Precio publicado  ") +
                        (line.lineTotalText ?: valuePilotNextText("unknown", "desconocido")),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                line.storeName?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                EvidencePill(
                    valuePilotNextText("AVAILABILITY UNKNOWN", "DISPONIBILIDAD DESCONOCIDA"),
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            TextButton(
                onClick = onRemove,
                modifier = Modifier.heightIn(min = ValuePilotSpacing.minimumTouchTarget)
            ) {
                Text(valuePilotNextText("Remove from basket", "Quitar de la canasta"), color = MaterialTheme.colorScheme.error)
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
    val language = LocalValuePilotNextLanguage.current
    PremiumColumn {
        item(key = "decision-intro") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)) {
                Eyebrow(valuePilotNextText("SHOPPING DECISION", "DECISIÓN DE COMPRA"))
                Text(
                    valuePilotNextText("The basket, as the evidence allows.", "La canasta, tal como permite la evidencia."),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    valuePilotNextText(
                        "Current listed-price totals, unknown coverage, and manual trip costs remain separate.",
                        "Los totales de precios publicados, la cobertura desconocida y los costos manuales del viaje permanecen separados."
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (planLoading) {
            item(key = "decision-loading") {
                LoadingRow(valuePilotNextText("Requesting a fresh qualified basket plan", "Solicitando un plan de canasta calificado y reciente"))
            }
        }
        if (plan == null && !planLoading) {
            item(key = "decision-unavailable") {
                EmptyStateCard(
                    eyebrow = valuePilotNextText("NO CURRENT PLAN", "SIN PLAN ACTUAL"),
                    title = valuePilotNextText("No verified basket total is available yet.", "Todavía no hay un total verificado de la canasta."),
                    detail = planError?.let {
                        valuePilotNextLocalizedDynamicText(language, it).orEmpty()
                    } ?: valuePilotNextText(
                        "Search items again to attach fresh qualified price and package evidence before planning.",
                        "Buscá los productos de nuevo para adjuntar evidencia reciente y calificada de precio y envase antes de planificar."
                    ),
                    actionLabel = valuePilotNextText("Review basket", "Revisar canasta"),
                    onAction = onEditBasket
                )
            }
        }
        if (plan == null) {
            item(key = "decision-trip-without-plan") {
                ActionCard(
                    eyebrow = valuePilotNextText("TRAVEL COST", "COSTO DEL VIAJE"),
                    title = tripResult?.displayText()?.let {
                        valuePilotNextLocalizedFactText(
                            language,
                            valuePilotNextLocalizedDynamicText(language, it).orEmpty()
                        )
                    } ?: valuePilotNextText("Calculate a trip from your own assumptions.", "Calculá un viaje con tus propios supuestos."),
                    detail = valuePilotNextText(
                        "Distance, fuel, tolls, and parking remain separate from basket prices. ValuePilot does not infer a driving route.",
                        "La distancia, el combustible, los peajes y el estacionamiento permanecen separados de los precios de la canasta. ValuePilot no infiere una ruta."
                    ),
                    actionLabel = if (tripResult == null) valuePilotNextText("Open trip planner", "Abrir planificador de viaje") else valuePilotNextText("Review trip assumptions", "Revisar supuestos del viaje"),
                    onClick = onTripPlanner
                )
            }
            item(key = "decision-map-without-plan") {
                SecondaryActionButton(valuePilotNextText("Check for source-backed store locations", "Buscar ubicaciones de tiendas respaldadas por fuente"), onMap)
            }
        }
        if (plan != null) {
            item(key = "decision-summary") { DecisionSummaryCard(plan) }
            item(key = "decision-lines-heading") {
                SectionHeading(valuePilotNextText("LINE BY LINE", "LÍNEA POR LÍNEA"), valuePilotNextText("What the plan covers", "Qué cubre el plan"))
            }
            items(plan.lines, key = { it.identityKey }) { line -> DecisionLineCard(line) }
            item(key = "decision-stores") { PlanStoreSummary(plan) }
            item(key = "decision-trip") {
                ActionCard(
                    eyebrow = valuePilotNextText("TRAVEL COST", "COSTO DEL VIAJE"),
                    title = tripResult?.displayText()?.let {
                        valuePilotNextLocalizedFactText(
                            language,
                            valuePilotNextLocalizedDynamicText(language, it).orEmpty()
                        )
                    } ?: valuePilotNextText("Trip cost is not included yet.", "El costo del viaje todavía no está incluido."),
                    detail = valuePilotNextText(
                        "Enter your own distance, fuel, tolls, and parking assumptions. ValuePilot does not infer a driving route.",
                        "Ingresá tus propios supuestos de distancia, combustible, peajes y estacionamiento. ValuePilot no infiere una ruta."
                    ),
                    actionLabel = if (tripResult == null) valuePilotNextText("Add trip assumptions", "Agregar supuestos del viaje") else valuePilotNextText("Review trip assumptions", "Revisar supuestos del viaje"),
                    onClick = onTripPlanner
                )
            }
            item(key = "decision-map") {
                PrimaryActionButton(valuePilotNextText("View store context and map handoff", "Ver contexto de tienda y abrir mapa"), onMap)
            }
            item(key = "decision-edit") {
                TextButton(onClick = onEditBasket, modifier = Modifier.heightIn(min = ValuePilotSpacing.minimumTouchTarget)) {
                    Text(valuePilotNextText("Edit basket", "Editar canasta"))
                }
            }
        }
        if (basket.errorNotice != null && basket.errorNotice != planError) {
            item(key = "decision-basket-note") {
                EvidenceNotice(valuePilotNextLocalizedDynamicText(language, basket.errorNotice).orEmpty())
            }
        }
    }
}

@Composable
private fun DecisionSummaryCard(plan: ArgentinaPrivateBasketPlanProjection) {
    val language = LocalValuePilotNextLanguage.current
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
                if (complete) {
                    valuePilotNextText("COMPLETE PRICE COVERAGE", "COBERTURA DE PRECIO COMPLETA")
                } else {
                    valuePilotNextText("PARTIAL PRICE COVERAGE", "COBERTURA DE PRECIO PARCIAL")
                },
                MaterialTheme.colorScheme.surface.copy(alpha = 0.74f),
                MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                valuePilotNextLocalizedFactText(
                    language,
                    valuePilotNextLocalizedDynamicText(language, plan.totalText ?: plan.headline).orEmpty()
                ),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                valuePilotNextLocalizedDynamicText(language, plan.coverageText).orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                plan.stores.size.toString() + if (plan.stores.size == 1) {
                    valuePilotNextText(" selected store", " tienda seleccionada")
                } else {
                    valuePilotNextText(" selected stores", " tiendas seleccionadas")
                } + valuePilotNextText(" · release ", " · publicación ") + plan.releaseDate,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                valuePilotNextLocalizedDynamicText(language, plan.notice).orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun DecisionLineCard(line: ArgentinaPrivateBasketLineProjection) {
    val language = LocalValuePilotNextLanguage.current
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
                Text(
                    valuePilotNextLocalizedDynamicText(language, line.packageQuantityText).orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (line.missing) {
                        valuePilotNextText("Price evidence unknown", "Evidencia de precio desconocida")
                    } else {
                        valuePilotNextLocalizedDynamicText(language, line.lineTotalText).orEmpty()
                            .ifBlank { valuePilotNextText("Listed price unknown", "Precio publicado desconocido") }
                    },
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
        SectionHeading(valuePilotNextText("STORE CHOICES", "ELECCIONES DE TIENDA"), valuePilotNextText("Where the listed prices came from", "De dónde vienen los precios publicados"))
        if (plan.stores.isEmpty()) {
            EvidenceNotice(valuePilotNextText("No store coordinates were returned with this plan.", "Este plan no devolvió coordenadas de tiendas."))
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
                        valuePilotNextText(
                            "Store context is separate from availability and travel routing.",
                            "El contexto de tienda es independiente de la disponibilidad y de la ruta del viaje."
                        ),
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
    val language = LocalValuePilotNextLanguage.current
    PremiumColumn {
        item(key = "trip-intro") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)) {
                Eyebrow(valuePilotNextText("MANUAL TRIP ESTIMATE", "ESTIMACIÓN MANUAL DEL VIAJE"))
                Text(
                    valuePilotNextText("See what the journey adds.", "Mirá cuánto suma el recorrido."),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    valuePilotNextText(
                        "Enter a one-way driving distance and your own vehicle costs. ValuePilot does not infer a route or turn straight-line distance into driving distance.",
                        "Ingresá una distancia de manejo de ida y tus costos del vehículo. ValuePilot no infiere una ruta ni convierte distancia en línea recta en distancia de manejo."
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item(key = "trip-basket-summary") {
            EvidenceNotice(
                plan?.totalText?.let {
                    valuePilotNextLocalizedDynamicText(language, it).orEmpty()
                }
                    ?: valuePilotNextText(
                        "No basket plan is attached. This estimate uses only the distance and vehicle costs you enter.",
                        "No hay un plan de canasta adjunto. Esta estimación usa solo la distancia y los costos del vehículo que ingreses."
                    )
            )
        }
        item(key = "trip-fields") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)) {
                DecimalInput(valuePilotNextText("One-way driving distance", "Distancia de manejo de ida"), "km", distance) {
                    onInputChanged(it, consumption, fuelPrice, tolls, parking)
                }
                DecimalInput(valuePilotNextText("Fuel consumption", "Consumo de combustible"), "L / 100 km", consumption) {
                    onInputChanged(distance, it, fuelPrice, tolls, parking)
                }
                DecimalInput(valuePilotNextText("Fuel price", "Precio del combustible"), "ARS / L", fuelPrice) {
                    onInputChanged(distance, consumption, it, tolls, parking)
                }
                DecimalInput(valuePilotNextText("Tolls", "Peajes"), valuePilotNextText("ARS · enter 0 if none", "ARS · ingresá 0 si no hay"), tolls) {
                    onInputChanged(distance, consumption, fuelPrice, it, parking)
                }
                DecimalInput(valuePilotNextText("Parking", "Estacionamiento"), valuePilotNextText("ARS · enter 0 if none", "ARS · ingresá 0 si no hay"), parking) {
                    onInputChanged(distance, consumption, fuelPrice, tolls, it)
                }
                Text(
                    valuePilotNextText(
                        "Use a period for decimals. This estimate is separate from the listed basket price.",
                        "Usá un punto para los decimales. Esta estimación es independiente del precio publicado de la canasta."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                tripError?.let {
                    EvidenceNotice(
                        valuePilotNextLocalizedDynamicText(language, it).orEmpty(),
                        isError = true
                    )
                }
                PrimaryActionButton(
                    valuePilotNextText("Calculate trip cost", "Calcular costo del viaje"),
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
                        Eyebrow(valuePilotNextText("ESTIMATED TRIP COST", "COSTO ESTIMADO DEL VIAJE"))
                        Text(
                            valuePilotNextLocalizedFactText(
                                language,
                                "ARS " + result.total.toPlainString()
                            ),
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            valuePilotNextLocalizedFactText(
                                language,
                                valuePilotNextText("Fuel ARS ", "Combustible ARS ") + result.fuelCost.toPlainString() +
                                    valuePilotNextText(" · tolls ARS ", " · peajes ARS ") + result.tolls.toPlainString() +
                                    valuePilotNextText(" · parking ARS ", " · estacionamiento ARS ") + result.parking.toPlainString()
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            valuePilotNextLocalizedFactText(
                                language,
                                valuePilotNextText("Round trip ", "Viaje de ida y vuelta de ") + result.roundTripKm.toPlainString() +
                                    valuePilotNextText(" km, calculated from your one-way input.", " km, calculado a partir de tu ingreso de ida.")
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            valuePilotNextText("No route or distance is inferred.", "No se infiere ninguna ruta ni distancia."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
        item(key = "trip-map") {
            SecondaryActionButton(valuePilotNextText("View store map handoff", "Ver contexto de tienda y mapa"), onMap)
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
    val language = LocalValuePilotNextLanguage.current
    PremiumColumn {
        item(key = "map-intro") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)) {
                Eyebrow(valuePilotNextText("SOURCE-BACKED STORE CONTEXT", "CONTEXTO DE TIENDA CON FUENTE"))
                Text(
                    valuePilotNextText("Take the store context to your map app.", "Llevá el contexto de tienda a tu app de mapas."),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    valuePilotNextText(
                        "ValuePilot passes valid source coordinates to an external map app. It does not draw a map, infer a route, or confirm store availability.",
                        "ValuePilot pasa coordenadas válidas de la fuente a una app de mapas externa. No dibuja un mapa, infiere una ruta ni confirma la disponibilidad de la tienda."
                    ),
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
                        offer.quantity?.let {
                            valuePilotNextText("Package ", "Envase ") +
                                valuePilotNextLocalizedFactText(language, it.amount + " " + it.unit)
                        },
                        valuePilotNextLocalizedFactText(
                            language,
                            offer.listPrice.currency + " " + offer.listPrice.amount
                        ),
                        valuePilotNextText("Price evidence ", "Evidencia de precio ") + offer.freshnessStatus.name.lowercase().replace('_', ' '),
                        valuePilotNextText("Release ", "Publicación ") + offer.releaseDate
                    ).joinToString(" · "),
                    onOpenMap = context.asStoreProjection()?.let { store -> { onOpenMap(store) } }
                )
            }
        }

        val stores = plan?.stores.orEmpty()
        if (stores.isEmpty() && selectedOffer == null) {
            item(key = "map-empty") {
                EmptyStateCard(
                    eyebrow = valuePilotNextText("NO STORE COORDINATES", "SIN COORDENADAS DE TIENDA"),
                    title = valuePilotNextText("A map handoff is not available yet.", "Todavía no se puede abrir el mapa."),
                    detail = valuePilotNextText(
                        "Build a current basket plan to see source-backed store coordinates. No location or route is guessed.",
                        "Armá un plan actual de canasta para ver coordenadas de tiendas respaldadas por fuente. No se adivina ninguna ubicación ni ruta."
                    )
                )
            }
        } else if (stores.isNotEmpty()) {
            item(key = "map-plan-heading") {
                SectionHeading(valuePilotNextText("SELECTED PLAN", "PLAN ELEGIDO"), valuePilotNextText("Source locations", "Ubicaciones de la fuente"))
            }
            items(stores, key = { it.storeKey }) { store ->
                val context = ArgentinaQualifiedStoreMapContextPresentation.fromStore(
                    store,
                    plan?.mapMarkers.orEmpty()
                )
                val validStore = context.asStoreProjection()
                val markerSummary = context.markers.joinToString(" · ") {
                    it.productName + " — " + valuePilotNextLocalizedFactText(language, it.priceText)
                }.ifBlank { valuePilotNextText("No product price marker is attached.", "No hay un marcador de precio de producto adjunto.") }
                StoreContextCard(
                    context = context,
                    detail = markerSummary,
                    onOpenMap = validStore?.let { projection -> { onOpenMap(projection) } }
                )
            }
        }
        item(key = "map-footnote") {
            EvidenceFootnote(
                valuePilotNextText(
                    "Coordinates are source evidence only. No route, travel time, stock, pickup, delivery, or availability is inferred.",
                    "Las coordenadas son solo evidencia de la fuente. No se infieren ruta, tiempo de viaje, stock, retiro, entrega ni disponibilidad."
                )
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
                coordinates ?: valuePilotNextText("Valid coordinates were not supplied.", "No se proporcionaron coordenadas válidas."),
                style = MaterialTheme.typography.labelLarge,
                color = if (coordinates == null) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.secondary
            )
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onOpenMap != null) {
                PrimaryActionButton(valuePilotNextText("Open in map app", "Abrir en la app de mapas"), onOpenMap)
            } else {
                Text(
                    valuePilotNextText(
                        "External map handoff unavailable without valid source coordinates.",
                        "No se puede abrir un mapa externo sin coordenadas válidas de la fuente."
                    ),
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
