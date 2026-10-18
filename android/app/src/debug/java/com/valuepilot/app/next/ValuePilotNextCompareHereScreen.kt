package com.valuepilot.app.next

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.valuepilot.app.CompareHereManualDraftActionEvaluator
import com.valuepilot.app.CompareHereManualDraftReadiness
import com.valuepilot.app.CompareHereManualInputAdapter
import com.valuepilot.app.CompareHereManualRouteState
import com.valuepilot.app.CompareHereManualRouteStatus
import com.valuepilot.app.CompareHereUiStatus
import com.valuepilot.app.CompareHereShareCard
import com.valuepilot.core.CompareHerePriceSelection

internal fun usesCompactComparePriceBasisChoices(
    availableWidth: Dp,
    fontScale: Float
): Boolean = availableWidth < 420.dp || fontScale >= 1.2f

@Composable
internal fun ValuePilotNextCompareHereScreen(
    state: ValuePilotNextCompareHereUiState,
    onProductChanged: (Int, String) -> Unit,
    onAddProduct: () -> Unit,
    onRemoveProduct: (Int) -> Unit,
    onPriceSelectionChanged: (CompareHerePriceSelection) -> Unit,
    onLikeForLikeChanged: (Boolean) -> Unit,
    onCompare: () -> Unit,
    onClear: () -> Unit,
    onShare: (CompareHereShareCard) -> Unit,
    onOpenCaptureTools: () -> Unit,
    onBack: () -> Unit
) {
    val language = LocalValuePilotNextLanguage.current
    val actionState =
        CompareHereManualDraftActionEvaluator.evaluate(
            rawBlocks = state.productBlocks,
            likeForLikeConfirmed = state.likeForLikeConfirmed
        )

    PremiumColumn {
        item(key = "compare-here-intro") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)) {
                Eyebrow(valuePilotNextText("COMPARE WHAT YOU SAW", "COMPARÁ LO QUE VISTE"))
                Text(
                    valuePilotNextText("Find the value in your options.", "Encontrá el mejor valor entre tus opciones."),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    valuePilotNextText(
                        "Enter exact shelf details and package sizes. This compares the observations you enter; it does not verify a current store offer.",
                        "Ingresá los datos exactos de góndola y los tamaños de envase. Esto compara tus observaciones; no verifica una oferta actual de tienda."
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item(key = "compare-price-basis") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)) {
                SectionHeading(valuePilotNextText("PRICE BASIS", "BASE DE PRECIO"), valuePilotNextText("Choose what to compare", "Elegí qué comparar"))
                Text(
                    valuePilotNextText(
                        "A missing member price stays unranked. A current price is never used in its place.",
                        "Un precio de socio faltante queda sin ordenar. Nunca se usa un precio actual en su lugar."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val compact = usesCompactComparePriceBasisChoices(
                        maxWidth,
                        LocalDensity.current.fontScale
                    )
                    if (compact) {
                        Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)) {
                            FilterChip(
                                selected = state.priceSelection == CompareHerePriceSelection.CURRENT,
                                onClick = { onPriceSelectionChanged(CompareHerePriceSelection.CURRENT) },
                                label = { Text(valuePilotNextText("Current shelf price", "Precio actual de góndola")) }
                            )
                            FilterChip(
                                selected = state.priceSelection == CompareHerePriceSelection.MEMBER,
                                onClick = { onPriceSelectionChanged(CompareHerePriceSelection.MEMBER) },
                                label = { Text(valuePilotNextText("Member price", "Precio de socio")) }
                            )
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)) {
                            FilterChip(
                                selected = state.priceSelection == CompareHerePriceSelection.CURRENT,
                                onClick = { onPriceSelectionChanged(CompareHerePriceSelection.CURRENT) },
                                label = { Text(valuePilotNextText("Current shelf price", "Precio actual de góndola")) }
                            )
                            FilterChip(
                                selected = state.priceSelection == CompareHerePriceSelection.MEMBER,
                                onClick = { onPriceSelectionChanged(CompareHerePriceSelection.MEMBER) },
                                label = { Text(valuePilotNextText("Member price", "Precio de socio")) }
                            )
                        }
                    }
                }
            }
        }

        item(key = "compare-products-title") {
            SectionHeading(
                valuePilotNextText("PRODUCT DETAILS", "DETALLES DE PRODUCTO"),
                state.productBlocks.size.toString() + valuePilotNextText(
                    if (state.productBlocks.size == 1) " comparison entry" else " comparison entries",
                    if (state.productBlocks.size == 1) " comparación" else " comparaciones"
                )
            )
        }

        itemsIndexed(
            items = state.productBlocks,
            key = { index, _ -> "compare-product-$index" }
        ) { index, block ->
            val productDescription = valuePilotNextText(
                "Product ${index + 1} name, package, price, and promotion details",
                "Nombre, envase, precio y promoción del producto ${index + 1}"
            )
            val removeDescription = valuePilotNextText(
                "Remove comparison option ${index + 1}",
                "Quitar opción de comparación ${index + 1}"
            )
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)) {
                OutlinedTextField(
                    value = block,
                    onValueChange = { onProductChanged(index, it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = productDescription
                        },
                    label = { Text(valuePilotNextText("Option ${index + 1}", "Opción ${index + 1}")) },
                    placeholder = {
                        Text(valuePilotNextText("For example: Apples, 3 kg, current price ARS 5,990", "Ejemplo: manzanas, 3 kg, precio actual ARS 5.990"))
                    },
                    minLines = 3,
                    maxLines = 5,
                    shape = MaterialTheme.shapes.large
                )
                if (state.productBlocks.size > 2) {
                    TextButton(
                        onClick = { onRemoveProduct(index) },
                        modifier = Modifier
                            .align(Alignment.End)
                            .heightIn(min = ValuePilotSpacing.minimumTouchTarget)
                            .semantics {
                                contentDescription = removeDescription
                            }
                    ) {
                        Text(valuePilotNextText("Remove option", "Quitar opción"), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        item(key = "compare-add-product") {
            TextButton(
                onClick = onAddProduct,
                enabled = state.productBlocks.size < CompareHereManualInputAdapter.MAX_OBSERVATIONS,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ValuePilotSpacing.minimumTouchTarget)
            ) {
                Text(valuePilotNextText("Add another option", "Agregar otra opción"))
            }
        }

        item(key = "compare-like-for-like") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = ValuePilotElevation.flat)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(ValuePilotSpacing.medium),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)
                ) {
                    Checkbox(
                        checked = state.likeForLikeConfirmed,
                        onCheckedChange = onLikeForLikeChanged
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = ValuePilotSpacing.small),
                        verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)
                    ) {
                        Text(
                            valuePilotNextText("These are like-for-like alternatives", "Son alternativas comparables"),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            valuePilotNextText(
                                "Confirm only when every option is a substitute you would reasonably choose between. Editing an entry clears this confirmation.",
                                "Confirmá solo cuando todas las opciones sean sustitutos que elegirías razonablemente. Editar una opción borra esta confirmación."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        when (actionState.readiness) {
            CompareHereManualDraftReadiness.ADD_PRODUCTS ->
                item(key = "compare-readiness") {
                    EvidenceFootnote(valuePilotNextText("Enter at least two products with an exact package size and price.", "Ingresá al menos dos productos con tamaño de envase y precio exactos."))
                }
            CompareHereManualDraftReadiness.CONFIRM_LIKE_FOR_LIKE ->
                item(key = "compare-readiness") {
                    EvidenceFootnote(valuePilotNextText("Confirm the products are comparable before requesting an exact-value ranking.", "Confirmá que los productos sean comparables antes de pedir un ranking de valor exacto."))
                }
            CompareHereManualDraftReadiness.READY -> Unit
        }

        state.notice?.let { message ->
            item(key = "compare-notice") {
                EvidenceNotice(valuePilotNextLocalizedDynamicText(language, message).orEmpty())
            }
        }

        if (state.isComparing) {
            item(key = "compare-progress") { LoadingRow(valuePilotNextText("Comparing the exact details you entered…", "Comparando los datos exactos que ingresaste…")) }
        }

        state.routeState?.let { result ->
            item(key = "compare-result") {
                CompareHereResultCard(
                    language = language,
                    routeState = result,
                    onShare = result.shareCard?.let { card -> { onShare(card) } }
                )
            }
        }

        item(key = "compare-submit") {
            PrimaryActionButton(
                label = valuePilotNextText("Compare exact value", "Comparar valor exacto"),
                onClick = onCompare,
                enabled = actionState.compareEnabled && !state.isComparing
            )
        }

        item(key = "compare-capture-handoff") {
            ActionCard(
                eyebrow = valuePilotNextText("SCAN OR IMPORT", "ESCANEÁ O IMPORTÁ"),
                title = valuePilotNextText("Start from a barcode or price-label photo", "Empezá con un código de barras o una foto de etiqueta de precio"),
                detail = valuePilotNextText(
                    "Open the existing capture tools to suggest product text. You review every suggestion and add the price and package details you see.",
                    "Abrí las herramientas existentes para sugerir texto del producto. Revisás cada sugerencia y agregás el precio y los datos del envase que ves."
                ),
                actionLabel = valuePilotNextText("Open capture tools", "Abrir herramientas de captura"),
                onClick = onOpenCaptureTools
            )
        }

        item(key = "compare-clear") {
            TextButton(
                onClick = onClear,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ValuePilotSpacing.minimumTouchTarget)
            ) {
                Text(valuePilotNextText("Clear comparison", "Borrar comparación"))
            }
        }

        item(key = "compare-back") {
            SecondaryActionButton(valuePilotNextText("Back", "Volver"), onBack)
        }
    }
}

@Composable
private fun CompareHereResultCard(
    language: ValuePilotNextLanguage,
    routeState: CompareHereManualRouteState,
    onShare: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.34f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = ValuePilotElevation.flat)
    ) {
        Column(
            modifier = Modifier.padding(ValuePilotSpacing.large),
            verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)
        ) {
            Eyebrow(valuePilotNextText("COMPARISON RESULT", "RESULTADO DE COMPARACIÓN"))
            Text(
                valuePilotNextLocalizedDynamicText(language, routeState.title).orEmpty(),
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                valuePilotNextLocalizedDynamicText(language, routeState.guidance).orEmpty(),
                style = MaterialTheme.typography.bodyMedium
            )

            routeState.comparisonState?.let { result ->
                if (result.status == CompareHereUiStatus.READY) {
                    result.rows.firstOrNull { it.bestValue }?.let { best ->
                        Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)) {
                            EvidencePill(
                                valuePilotNextText("BEST VALUE", "MEJOR VALOR"),
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.onPrimary
                            )
                            Text(best.title, style = MaterialTheme.typography.titleLarge)
                            Text(
                                valuePilotNextLocalizedFactText(language, best.unitRateText),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                result.rows.forEach { row ->
                    CompareHereResultRow(
                        title = row.title,
                        detail = valuePilotNextLocalizedFactText(
                            language,
                            "${row.priceText} · ${row.quantityText}"
                        ),
                        unitRate = valuePilotNextLocalizedFactText(language, row.unitRateText),
                        badge = if (row.bestValue) {
                            valuePilotNextText("BEST VALUE", "MEJOR VALOR")
                        } else {
                            row.valueRank?.let { valuePilotNextText("RANK $it", "PUESTO $it") }
                        }
                    )
                }
                result.blockedRows.forEach { row ->
                    CompareHereResultRow(
                        title = row.title,
                        detail = valuePilotNextLocalizedDynamicText(language, row.reasonText).orEmpty(),
                        unitRate = null,
                        badge = valuePilotNextText("UNRANKED", "SIN ORDENAR")
                    )
                }
                result.notice?.let {
                    EvidenceNotice(valuePilotNextLocalizedDynamicText(language, it).orEmpty())
                }
            }

            if (routeState.rejectedProductGuidance.isNotEmpty()) {
                routeState.rejectedProductGuidance.forEach { guidance ->
                    Text(
                        valuePilotNextLocalizedDynamicText(language, guidance).orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            EvidenceFootnote(
                valuePilotNextText(
                    "This result compares your entered observations. It does not confirm a purchase or a live retailer price.",
                    "Este resultado compara tus observaciones ingresadas. No confirma una compra ni un precio actual de minorista."
                )
            )

            onShare?.let { action ->
                SecondaryActionButton(valuePilotNextText("Share privacy-safe result", "Compartir resultado privado"), action)
            }
        }
    }
}

@Composable
private fun CompareHereResultRow(
    title: String,
    detail: String,
    unitRate: String?,
    badge: String?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ValuePilotSpacing.xSmall),
        verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium
            )
            badge?.let {
                val isBestValue = it == "BEST VALUE" || it == "MEJOR VALOR"
                EvidencePill(
                    it,
                    if (isBestValue) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    if (isBestValue) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        unitRate?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
    }
}
