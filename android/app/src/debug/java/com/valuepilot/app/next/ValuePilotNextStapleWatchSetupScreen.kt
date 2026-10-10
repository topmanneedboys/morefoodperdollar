package com.valuepilot.app.next

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.valuepilot.app.MIN_WATCHED_SAVED_ITEMS_FOR_HANDOFF
import com.valuepilot.app.StapleWatchForegroundFactCheckCapability
import com.valuepilot.app.StapleWatchFactResolutionUiState
import com.valuepilot.app.StapleWatchSavedIdentityHandoffUiAction
import com.valuepilot.app.StapleWatchSavedIdentitySelectionAction
import com.valuepilot.app.StapleWatchSavedSelectionUiState
import com.valuepilot.app.StapleWatchSavedSelectionUiStatus

internal fun requiresClassicStapleWatchHandoff(
    capability: StapleWatchForegroundFactCheckCapability
): Boolean = capability == StapleWatchForegroundFactCheckCapability.NOT_CONFIGURED

@Composable
internal fun ValuePilotNextStapleWatchSetupScreen(
    state: StapleWatchSavedSelectionUiState?,
    onAction: (StapleWatchSavedIdentitySelectionAction) -> Unit,
    onContinue: (StapleWatchSavedIdentityHandoffUiAction) -> Unit,
    onOpenLegacyStapleWatch: () -> Unit,
    onBack: () -> Unit
) {
    val language = LocalValuePilotNextLanguage.current
    val classicHandoffRequired =
        state?.let { requiresClassicStapleWatchHandoff(it.factCheckCapability) } == true

    PremiumColumn {
        item(key = "staple-watch-intro") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)) {
                Eyebrow(valuePilotNextText("RECURRING STAPLES", "ESENCIALES RECURRENTES"))
                Text(
                    valuePilotNextText("Watch My Staples", "Mis esenciales"),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    valuePilotNextLocalizedDynamicText(language, state?.guidance)
                        ?: valuePilotNextText("Choose at least two saved products and a usual store to prepare a future comparison.", "Elegí al menos dos productos guardados y una tienda habitual para preparar una comparación futura."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                EvidenceFootnote(
                    if (classicHandoffRequired) {
                        valuePilotNextText(
                            "Current-price and savings checks are still in the classic app. This Compose preview does not save a Watch list.",
                            "Los controles de precio actual y ahorro siguen en la app clásica. Esta vista previa Compose no guarda una lista de seguimiento."
                        )
                    } else {
                        valuePilotNextText(
                            "This setup only records temporary identity choices. It does not produce current prices, a route, a savings decision, or an alert.",
                            "Esta configuración solo registra elecciones temporales de identidad. No produce precios actuales, rutas, decisiones de ahorro ni alertas."
                        )
                    }
                )
            }
        }

        if (state == null) {
            item(key = "staple-watch-loading") {
                LoadingRow(valuePilotNextText("Checking your saved products and stores…", "Comprobando tus productos y tiendas guardados…"))
            }
        } else if (
            state.productRows.size < MIN_WATCHED_SAVED_ITEMS_FOR_HANDOFF ||
                state.storeRows.isEmpty()
        ) {
            item(key = "staple-watch-missing-saved-choices") {
                ActionCard(
                    eyebrow = valuePilotNextText("SAVED CHOICES NEEDED", "FALTAN ELECCIONES GUARDADAS"),
                    title = when {
                        state.productRows.size < MIN_WATCHED_SAVED_ITEMS_FOR_HANDOFF &&
                            state.storeRows.isEmpty() -> valuePilotNextText("Save products and a store first.", "Primero guardá productos y una tienda.")
                        state.productRows.size < MIN_WATCHED_SAVED_ITEMS_FOR_HANDOFF ->
                            valuePilotNextText("Save two products first.", "Primero guardá dos productos.")
                        else -> valuePilotNextText("Save a usual store first.", "Primero guardá una tienda habitual.")
                    },
                    detail = valuePilotNextLocalizedDynamicText(language, state.guidance).orEmpty(),
                    actionLabel = valuePilotNextText("Open classic Saved", "Abrir Guardados clásico"),
                    onClick = onOpenLegacyStapleWatch
                )
            }

            item(key = "staple-watch-back") {
                SecondaryActionButton(label = valuePilotNextText("Back to Saved", "Volver a Guardados"), onClick = onBack)
            }
        } else if (classicHandoffRequired) {
            item(key = "staple-watch-legacy-handoff") {
                ActionCard(
                    eyebrow = valuePilotNextText("NOT IN COMPOSE YET", "TODAVÍA NO ESTÁ EN COMPOSE"),
                    title = valuePilotNextText("Continue with the existing Watch flow", "Continuar con el flujo existente de Seguimiento"),
                    detail = valuePilotNextText(
                        "Current-price and savings checks have not moved into Compose. Open Saved in the classic app, then choose Watch My Staples. This preview has not saved a watch list or temporary selections.",
                        "Los controles de precio actual y ahorro todavía no pasaron a Compose. Abrí Guardados en la app clásica y elegí Mis esenciales. Esta vista previa no guardó una lista ni selecciones temporales."
                    ),
                    actionLabel = valuePilotNextText("Open classic Saved & Watch tools", "Abrir herramientas clásicas de Guardados y Seguimiento"),
                    onClick = onOpenLegacyStapleWatch
                )
            }

            item(key = "staple-watch-back") {
                SecondaryActionButton(label = valuePilotNextText("Back to Saved", "Volver a Guardados"), onClick = onBack)
            }
        } else {
            item(key = "staple-watch-selection-summary") {
                WatchSetupProgressCard(
                    state = state,
                    status = when {
                        state.status == StapleWatchSavedSelectionUiStatus.DISPLAY_METADATA_INCOMPLETE ->
                            valuePilotNextText("REFRESH NAMES", "ACTUALIZAR NOMBRES")
                        state.status == StapleWatchSavedSelectionUiStatus.READY_FOR_FACT_CHECK ->
                            valuePilotNextText("READY FOR CHECK", "LISTO PARA COMPROBAR")
                        else -> valuePilotNextText("SETUP IN PROGRESS", "CONFIGURACIÓN EN CURSO")
                    }
                )
            }

            state.notice?.let { message ->
                item(key = "staple-watch-notice") {
                    EvidenceNotice(
                        text = valuePilotNextLocalizedDynamicText(language, message).orEmpty(),
                        isError = state.selectedDisplayNameBlockerCount > 0
                    )
                }
            }

            state.factResolutionProgress?.let { progress ->
                item(key = "staple-watch-fact-progress") {
                    FactResolutionCard(progress)
                }
            }

            state.productSectionTitle?.let { title ->
                item(key = "staple-watch-products-heading") {
                    SectionHeading(
                        valuePilotNextText("CHOOSE YOUR STAPLES", "ELEGÍ TUS ESENCIALES"),
                        valuePilotNextLocalizedDynamicText(language, title).orEmpty()
                    )
                }
                items(
                    items = state.productRows,
                    key = { row -> row.action.itemKey.value }
                ) { row ->
                    StapleChoiceCard(
                        title = row.title,
                        selected = row.watched,
                        actionLabel = valuePilotNextLocalizedDynamicText(language, row.actionLabel).orEmpty(),
                        actionDescription = valuePilotNextLocalizedDynamicText(language, row.actionDescription).orEmpty(),
                        action = row.action,
                        onAction = onAction
                    )
                }
            }

            state.storeSectionTitle?.let { title ->
                item(key = "staple-watch-stores-heading") {
                    SectionHeading(
                        valuePilotNextText("USUAL STORE", "TIENDA HABITUAL"),
                        valuePilotNextLocalizedDynamicText(language, title).orEmpty()
                    )
                }
                items(state.storeRows) { row ->
                    StapleChoiceCard(
                        title = row.title,
                        selected = row.usualStore,
                        actionLabel = valuePilotNextLocalizedDynamicText(language, row.actionLabel).orEmpty(),
                        actionDescription = valuePilotNextLocalizedDynamicText(language, row.actionDescription).orEmpty(),
                        action = row.action,
                        onAction = onAction
                    )
                }
            }

            state.clearSelectionAction?.let { action ->
                item(key = "staple-watch-clear") {
                    TextButton(
                        onClick = { onAction(action) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = ValuePilotSpacing.minimumTouchTarget)
                    ) {
                        Text(
                            valuePilotNextLocalizedDynamicText(language, state.clearSelectionActionLabel)
                                ?: valuePilotNextText("Clear selections", "Borrar elecciones")
                        )
                    }
                }
            }

            state.continueAction?.let { action ->
                item(key = "staple-watch-continue") {
                    PrimaryActionButton(
                        label = valuePilotNextLocalizedDynamicText(language, state.continueActionLabel)
                            ?: valuePilotNextText("Continue", "Continuar"),
                        onClick = { onContinue(action) }
                    )
                }
            }

            item(key = "staple-watch-back") {
                SecondaryActionButton(label = valuePilotNextText("Back to Saved", "Volver a Guardados"), onClick = onBack)
            }
        }
    }
}

@Composable
private fun WatchSetupProgressCard(
    state: StapleWatchSavedSelectionUiState,
    status: String
) {
    val hasEnoughStaples = state.watchedItemCount >= MIN_WATCHED_SAVED_ITEMS_FOR_HANDOFF
    val readyForCheck = state.status == StapleWatchSavedSelectionUiStatus.READY_FOR_FACT_CHECK
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
        elevation = CardDefaults.cardElevation(defaultElevation = ValuePilotElevation.flat)
    ) {
        Column(
            modifier = Modifier.padding(ValuePilotSpacing.large),
            verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)) {
                Eyebrow(valuePilotNextText("YOUR WATCH LIST", "TU LISTA DE SEGUIMIENTO"))
                Text(
                    valuePilotNextText(
                        "${state.watchedItemCount} staples selected ($MIN_WATCHED_SAVED_ITEMS_FOR_HANDOFF minimum) · " +
                            if (state.usualStoreSelected) "Usual store selected" else "Usual store not selected",
                        "${state.watchedItemCount} esenciales elegidos (mínimo $MIN_WATCHED_SAVED_ITEMS_FOR_HANDOFF) · " +
                            if (state.usualStoreSelected) "Tienda habitual elegida" else "Tienda habitual no elegida"
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)) {
                SetupRequirementRow(
                    label = valuePilotNextText("Choose at least $MIN_WATCHED_SAVED_ITEMS_FOR_HANDOFF staples", "Elegí al menos $MIN_WATCHED_SAVED_ITEMS_FOR_HANDOFF esenciales"),
                    detail = if (hasEnoughStaples) {
                        valuePilotNextText("${state.watchedItemCount} selected · minimum met", "${state.watchedItemCount} elegidos · mínimo cumplido")
                    } else {
                        valuePilotNextText("${state.watchedItemCount} selected · $MIN_WATCHED_SAVED_ITEMS_FOR_HANDOFF required", "${state.watchedItemCount} elegidos · se necesitan $MIN_WATCHED_SAVED_ITEMS_FOR_HANDOFF")
                    },
                    complete = hasEnoughStaples
                )
                SetupRequirementRow(
                    label = valuePilotNextText("Choose your usual store", "Elegí tu tienda habitual"),
                    detail = if (state.usualStoreSelected) valuePilotNextText("Store selected", "Tienda elegida") else valuePilotNextText("One saved store required", "Se necesita una tienda guardada"),
                    complete = state.usualStoreSelected
                )
            }

            EvidencePill(
                text = status,
                container = if (readyForCheck) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                },
                content = if (readyForCheck) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun SetupRequirementRow(
    label: String,
    detail: String,
    complete: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = ValuePilotSpacing.medium, vertical = ValuePilotSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)
        ) {
            Surface(
                shape = CircleShape,
                color = if (complete) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (complete) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Box(
                    modifier = Modifier.size(30.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (complete) "✓" else "·",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StapleChoiceCard(
    title: String,
    selected: Boolean,
    actionLabel: String,
    actionDescription: String,
    action: StapleWatchSavedIdentitySelectionAction,
    onAction: (StapleWatchSavedIdentitySelectionAction) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = ValuePilotElevation.flat)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ValuePilotSpacing.large, end = ValuePilotSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = ValuePilotSpacing.large),
                verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (selected) {
                    EvidencePill(
                        text = valuePilotNextText("SELECTED", "ELEGIDO"),
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            TextButton(
                onClick = { onAction(action) },
                modifier = Modifier
                    .heightIn(min = ValuePilotSpacing.minimumTouchTarget)
                    .semantics { contentDescription = actionDescription }
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun SummaryCard(eyebrow: String, title: String, detail: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = ValuePilotElevation.flat)
    ) {
        Column(
            modifier = Modifier.padding(ValuePilotSpacing.large),
            verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)
        ) {
            Eyebrow(eyebrow)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FactResolutionCard(state: StapleWatchFactResolutionUiState) {
    val allSupplied = state.resolvedRequirementCount == state.totalRequirementCount
    Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)) {
        SummaryCard(
            eyebrow = valuePilotNextText("REQUIRED FACT CHECKS", "COMPROBACIONES NECESARIAS"),
            title = valuePilotNextText(
                "${state.resolvedRequirementCount} of ${state.totalRequirementCount} checks supplied",
                "${state.resolvedRequirementCount} de ${state.totalRequirementCount} comprobaciones aportadas"
            ),
            detail = valuePilotNextText(
                if (allSupplied) {
                    "All required fact categories are supplied. Policy and display metadata still remain separate before a decision can be shown."
                } else {
                    "${state.resolvedRequirementCount} of ${state.totalRequirementCount} required fact checks supplied. No switch decision is available yet."
                },
                if (allSupplied) {
                    "Se aportaron todas las categorías de hechos necesarias. La política y los metadatos visuales siguen separados antes de mostrar una decisión."
                } else {
                    "Se aportaron ${state.resolvedRequirementCount} de ${state.totalRequirementCount} comprobaciones necesarias. Todavía no hay una decisión de cambio disponible."
                }
            )
        )
        state.unresolvedRequirementLabels.forEach { label ->
            Text(
                valuePilotNextText(
                    "Still needed · $label",
                    "Todavía falta · ${valuePilotNextLocalizedDynamicText(LocalValuePilotNextLanguage.current, label).orEmpty()}"
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
