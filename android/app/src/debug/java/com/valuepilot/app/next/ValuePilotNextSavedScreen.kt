package com.valuepilot.app.next

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.valuepilot.app.PracticalShoppingSavedSurfaceAction
import com.valuepilot.app.PracticalShoppingSavedSurfaceMode
import com.valuepilot.app.PracticalShoppingSavedSurfaceProductRow
import com.valuepilot.app.PracticalShoppingSavedSurfaceState
import com.valuepilot.app.PracticalShoppingSavedSurfaceStoreRow
import com.valuepilot.app.PracticalShoppingSavedStapleLaunchUiState

internal data class ValuePilotNextPurchaseHistoryAvailabilityPresentation(
    val eyebrow: String,
    val title: String,
    val detail: String
)

internal fun valuePilotNextPurchaseHistoryAvailabilityPresentation() =
    ValuePilotNextPurchaseHistoryAvailabilityPresentation(
        eyebrow = "PURCHASE HISTORY · UNAVAILABLE",
        title = "Purchase history is not available in this preview.",
        detail = "No purchase or receipt source is connected. Private price history tracks price observations you entered; it does not record what you bought."
    )

@Composable
internal fun ValuePilotSavedScreen(
    state: PracticalShoppingSavedSurfaceState,
    language: ValuePilotNextLanguage,
    stapleLaunch: PracticalShoppingSavedStapleLaunchUiState?,
    onAction: (PracticalShoppingSavedSurfaceAction) -> Unit,
    onCheckPrice: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenStapleWatch: () -> Unit,
    onOpenPrivatePriceHistory: () -> Unit,
    onRecordObservedPrice: () -> Unit
) {
    var confirmClearAll by remember { mutableStateOf(false) }

    if (confirmClearAll) {
        val clearAction = state.clearAllAction
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = {
                Text(valuePilotNextText(language, "Clear saved choices?", "¿Borrar elecciones guardadas?"))
            },
            text = {
                Text(
                    valuePilotNextText(
                        language,
                        "This removes your saved product and store choices from this device.",
                        "Esto elimina de este dispositivo tus productos y tiendas guardados."
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearAll = false
                        clearAction?.let(onAction)
                    },
                    enabled = clearAction != null
                ) {
                    Text(valuePilotNextText(language, "Clear choices", "Borrar elecciones"))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) {
                    Text(valuePilotNextText(language, "Keep them", "Conservarlas"))
                }
            }
        )
    }

    PremiumColumn {
        item(key = "saved-intro") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)) {
                Eyebrow(valuePilotNextText(language, "YOUR SHOPPING MEMORY", "TU MEMORIA DE COMPRAS"))
                Text(
                    valuePilotNextText(
                        language,
                        "The products and stores\nyou return to.",
                        "Los productos y tiendas\na los que volvés."
                    ),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    valuePilotNextText(
                        language,
                        "Keep useful choices close. Saved names help you find them again; they are not live prices or current offers.",
                        "Tené cerca tus elecciones útiles. Los nombres guardados te ayudan a encontrarlas; no son precios en vivo ni ofertas actuales."
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (state.progressVisible) {
            item(key = "saved-progress") {
                LoadingRow(
                    valuePilotNextLocalizedDynamicText(language, state.statusMessage)
                        ?: valuePilotNextText(
                            language,
                            "Updating saved choices…",
                            "Actualizando elecciones guardadas…"
                        )
                )
            }
        } else {
            state.statusMessage?.let { message ->
                item(key = "saved-status") {
                    EvidenceNotice(
                        text = valuePilotNextLocalizedDynamicText(language, message).orEmpty(),
                        isError = state.mode == PracticalShoppingSavedSurfaceMode.ERROR
                    )
                }
            }
        }

        state.notice?.let { message ->
            item(key = "saved-notice") {
                EvidenceNotice(valuePilotNextLocalizedDynamicText(language, message).orEmpty())
            }
        }

        state.productSectionTitle?.let { title ->
            item(key = "saved-products-heading") {
                SectionHeading(
                    valuePilotNextText(language, "SAVED PRODUCTS", "PRODUCTOS GUARDADOS"),
                    valuePilotNextLocalizedDynamicText(language, title).orEmpty()
                )
            }
            items(state.productRows) { row ->
                SavedProductCard(
                    row = row,
                    language = language,
                    onCheckPrice = { action -> onCheckPrice(action.displayName) },
                    onRemove = { action -> onAction(action) }
                )
            }
        }

        state.storeSectionTitle?.let { title ->
            item(key = "saved-stores-heading") {
                SectionHeading(
                    valuePilotNextText(language, "SAVED STORES", "TIENDAS GUARDADAS"),
                    valuePilotNextLocalizedDynamicText(language, title).orEmpty()
                )
            }
            items(state.storeRows) { row ->
                SavedStoreCard(row = row, language = language, onRemove = onAction)
            }
        }

        state.emptyMessage?.let { message ->
            item(key = "saved-empty") {
                SavedShortlistCard(
                    language = language,
                    detail = valuePilotNextLocalizedDynamicText(language, message).orEmpty(),
                    onSearch = onSearch
                )
            }
        }

        state.refreshAction?.let { action ->
            item(key = "saved-refresh") {
                if (state.mode == PracticalShoppingSavedSurfaceMode.ERROR) {
                    PrimaryActionButton(
                        label = valuePilotNextLocalizedDynamicText(
                            language,
                            requireNotNull(state.refreshActionLabel)
                        ).orEmpty(),
                        onClick = { onAction(action) }
                    )
                } else {
                    SecondaryActionButton(
                        label = valuePilotNextLocalizedDynamicText(
                            language,
                            requireNotNull(state.refreshActionLabel)
                        ).orEmpty(),
                        onClick = { onAction(action) }
                    )
                }
            }
        }

        if (state.clearAllAction != null) {
            item(key = "saved-clear-all") {
                TextButton(
                    onClick = { confirmClearAll = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = ValuePilotSpacing.minimumTouchTarget)
                ) {
                    Text(
                        valuePilotNextText(
                            language,
                            "Clear all saved choices",
                            "Borrar todas las elecciones guardadas"
                        ),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        item(key = "saved-tools-heading") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)) {
                Eyebrow(valuePilotNextText(language, "PERSONAL TOOLS", "HERRAMIENTAS PERSONALES"))
                Text(
                    valuePilotNextText(
                        language,
                        "Put your saved choices to work.",
                        "Usá tus elecciones guardadas."
                    ),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        item(key = "saved-existing-tools") {
            SavedToolCard(
                eyebrow = valuePilotNextText(language, "PERSONAL PRICE MEMORY", "MEMORIA DE PRECIOS PERSONALES"),
                title = valuePilotNextText(language, "Record a price you saw", "Registrar un precio que viste"),
                detail = valuePilotNextText(
                    language,
                    "Choose a saved product and exact saved store, then add your observed price and proof.",
                    "Elegí un producto y una tienda exactos guardados; después agregá el precio observado y la evidencia."
                ),
                actionLabel = valuePilotNextText(language, "Start price confirmation", "Iniciar confirmación de precio"),
                onClick = onRecordObservedPrice
            )
        }

        item(key = "saved-private-price-history") {
            SavedToolCard(
                eyebrow = valuePilotNextText(language, "ON THIS DEVICE", "EN ESTE DISPOSITIVO"),
                title = valuePilotNextText(language, "Your private price history", "Tu historial privado de precios"),
                detail = valuePilotNextText(
                    language,
                    "Review past comparison observations and manage the copy stored on this device. They are not live store prices.",
                    "Revisá observaciones anteriores y administrá la copia guardada en este dispositivo. No son precios actuales de tiendas."
                ),
                actionLabel = valuePilotNextText(language, "Review price history", "Revisar historial de precios"),
                onClick = onOpenPrivatePriceHistory
            )
        }

        item(key = "saved-purchase-history-unavailable") {
            PurchaseHistoryUnavailableCard(
                language = language,
                presentation = valuePilotNextPurchaseHistoryAvailabilityPresentation()
            )
        }

        item(key = "saved-staple-watch-launch") {
            SavedToolCard(
                eyebrow = valuePilotNextText(language, "RECURRING STAPLES", "ESENCIALES RECURRENTES"),
                title = valuePilotNextLocalizedDynamicText(language, stapleLaunch?.title)
                    ?: valuePilotNextText(language, "Watch My Staples", "Mis esenciales"),
                detail = valuePilotNextLocalizedDynamicText(language, stapleLaunch?.supportingText)
                    ?: valuePilotNextLocalizedDynamicText(language, stapleLaunch?.notice)
                    ?: valuePilotNextText(
                        language,
                        "Review the saved product and store requirements for recurring staples.",
                        "Revisá los requisitos de productos y tienda guardados para tus esenciales recurrentes."
                    ),
                actionLabel = valuePilotNextLocalizedDynamicText(language, stapleLaunch?.actionLabel)
                    ?: valuePilotNextText(language, "Review setup requirements", "Revisar requisitos"),
                onClick = onOpenStapleWatch
            )
        }
    }
}

@Composable
private fun SavedShortlistCard(
    language: ValuePilotNextLanguage,
    detail: String,
    onSearch: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = ValuePilotElevation.flat)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ValuePilotSpacing.xxLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)
        ) {
            Text(
                "⌑",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Light
            )
            Eyebrow(valuePilotNextText(language, "A GOOD PLACE TO START", "UN BUEN LUGAR PARA EMPEZAR"))
            Text(
                valuePilotNextText(language, "Build a personal shortlist.", "Armá tu lista personal."),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PrimaryActionButton(
                label = valuePilotNextText(language, "Search products", "Buscar productos"),
                onClick = onSearch
            )
        }
    }
}

@Composable
private fun SavedToolCard(
    eyebrow: String,
    title: String,
    detail: String,
    actionLabel: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = ValuePilotElevation.flat)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ValuePilotSpacing.large),
            verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)
                ) {
                    Eyebrow(eyebrow)
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    "→",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ValuePilotSpacing.minimumTouchTarget)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(actionLabel)
                    Text("→", color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

@Composable
private fun PurchaseHistoryUnavailableCard(
    language: ValuePilotNextLanguage,
    presentation: ValuePilotNextPurchaseHistoryAvailabilityPresentation
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = ValuePilotElevation.flat)
    ) {
        Column(
            modifier = Modifier.padding(ValuePilotSpacing.large),
            verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Eyebrow(
                    valuePilotNextText(
                        language,
                        presentation.eyebrow,
                        "HISTORIAL DE COMPRAS · NO DISPONIBLE"
                    )
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                ) {
                    Text(
                        valuePilotNextText(language, "NOT CONNECTED", "NO CONECTADO"),
                        modifier = Modifier.padding(horizontal = ValuePilotSpacing.small, vertical = ValuePilotSpacing.xSmall),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                valuePilotNextText(
                    language,
                    presentation.title,
                    "El historial de compras no está disponible en esta vista previa."
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                valuePilotNextText(
                    language,
                    presentation.detail,
                    "No hay una fuente de compras o comprobantes conectada. El historial privado registra precios que ingresaste; no registra lo que compraste."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SavedProductCard(
    row: PracticalShoppingSavedSurfaceProductRow,
    language: ValuePilotNextLanguage,
    onCheckPrice: (PracticalShoppingSavedSurfaceAction.CheckProductPrice) -> Unit,
    onRemove: (PracticalShoppingSavedSurfaceAction.Preference) -> Unit
) {
    SavedChoiceCard(
        title = row.title,
        supportingText = valuePilotNextLocalizedDynamicText(language, row.supportingText).orEmpty(),
        language = language,
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.secondaryAction?.let { action ->
                    TextButton(
                        onClick = { onCheckPrice(action) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = ValuePilotSpacing.minimumTouchTarget)
                            .semantics {
                                contentDescription = valuePilotNextLocalizedDynamicText(
                                    language,
                                    row.secondaryActionDescription ?: row.secondaryActionLabel
                                )
                                    ?: valuePilotNextText(language, "Check price", "Comprobar precio")
                            }
                    ) {
                        Text(
                            valuePilotNextLocalizedDynamicText(language, row.secondaryActionLabel)
                                ?: valuePilotNextText(language, "Check price", "Comprobar precio")
                        )
                    }
                }
                row.action?.let { action ->
                    TextButton(
                        onClick = { onRemove(action) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = ValuePilotSpacing.minimumTouchTarget)
                            .semantics {
                                contentDescription = valuePilotNextLocalizedDynamicText(
                                    language,
                                    row.actionDescription ?: row.actionLabel
                                )
                                    ?: valuePilotNextText(language, "Remove saved product", "Quitar producto guardado")
                            }
                    ) {
                        Text(
                            valuePilotNextLocalizedDynamicText(language, row.actionLabel)
                                ?: valuePilotNextText(language, "Remove", "Quitar")
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun SavedStoreCard(
    row: PracticalShoppingSavedSurfaceStoreRow,
    language: ValuePilotNextLanguage,
    onRemove: (PracticalShoppingSavedSurfaceAction.Preference) -> Unit
) {
    SavedChoiceCard(
        title = row.title,
        supportingText = valuePilotNextLocalizedDynamicText(language, row.supportingText).orEmpty(),
        language = language,
        actions = {
            row.action?.let { action ->
                TextButton(
                    onClick = { onRemove(action) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = ValuePilotSpacing.minimumTouchTarget)
                        .semantics {
                            contentDescription = valuePilotNextLocalizedDynamicText(
                                language,
                                row.actionDescription ?: row.actionLabel
                            )
                                ?: valuePilotNextText(language, "Remove saved store", "Quitar tienda guardada")
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            valuePilotNextLocalizedDynamicText(language, row.actionLabel)
                                ?: valuePilotNextText(language, "Remove", "Quitar")
                        )
                        Text("→", color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    )
}

@Composable
private fun SavedChoiceCard(
    title: String,
    supportingText: String,
    language: ValuePilotNextLanguage,
    actions: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = ValuePilotElevation.flat)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ValuePilotSpacing.large),
            verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)
                ) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                ) {
                    Text(
                        valuePilotNextText(language, "SAVED", "GUARDADO"),
                        modifier = Modifier.padding(horizontal = ValuePilotSpacing.small, vertical = ValuePilotSpacing.xSmall),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Spacer(Modifier.size(ValuePilotSpacing.xSmall))
            actions()
        }
    }
}
