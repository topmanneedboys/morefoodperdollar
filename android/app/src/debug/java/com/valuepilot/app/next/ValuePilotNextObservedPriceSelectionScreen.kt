package com.valuepilot.app.next

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.valuepilot.app.PracticalShoppingSavedSurfaceAction
import com.valuepilot.app.PracticalShoppingSavedSurfaceMode
import com.valuepilot.app.PracticalShoppingSavedSurfaceState
import com.valuepilot.app.UserObservedPriceSavedPrefillCheckUiAction
import com.valuepilot.app.UserObservedPriceSavedPrefillHandoffUiState
import com.valuepilot.app.UserObservedPriceSavedPrefillHandoffUiStatus
import com.valuepilot.app.UserObservedPriceSavedSelectionAction
import com.valuepilot.app.UserObservedPriceSavedSelectionUiState

@Composable
internal fun ValuePilotObservedPriceSelectionScreen(
    savedState: PracticalShoppingSavedSurfaceState,
    selection: UserObservedPriceSavedSelectionUiState?,
    prefillResult: UserObservedPriceSavedPrefillHandoffUiState?,
    onSelectionAction: (UserObservedPriceSavedSelectionAction) -> Unit,
    onCheckPrefill: (UserObservedPriceSavedPrefillCheckUiAction) -> Unit,
    onSavedAction: (PracticalShoppingSavedSurfaceAction) -> Unit,
    onContinueToConfirmation: () -> Unit,
    onBackToSaved: () -> Unit
) {
    PremiumColumn {
        item(key = "observed-price-intro") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)) {
                Eyebrow(valuePilotNextText("PERSONAL PRICE MEMORY", "MEMORIA PERSONAL DE PRECIOS"))
                Text(
                    valuePilotNextText("Confirm a price you saw", "Confirmá un precio que viste"),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    valuePilotNextText(
                        "Choose one saved product and one exact saved store. You will enter your own observed price and proof in the next step.",
                        "Elegí un producto guardado y una tienda guardada exacta. En el siguiente paso vas a ingresar tu precio observado y la evidencia."
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                EvidenceFootnote(
                    valuePilotNextText(
                        "A personal observation is not a retailer-confirmed current offer.",
                        "Una observación personal no es una oferta actual confirmada por un minorista."
                    )
                )
            }
        }

        if (savedState.progressVisible) {
            item(key = "observed-price-saved-loading") {
                LoadingRow(savedState.statusMessage ?: valuePilotNextText("Checking saved choices…", "Comprobando elecciones guardadas…"))
            }
        } else {
            savedState.statusMessage?.let { message ->
                item(key = "observed-price-saved-status") {
                    EvidenceNotice(
                        text = message,
                        isError = savedState.mode == PracticalShoppingSavedSurfaceMode.ERROR
                    )
                }
            }
        }

        when {
            selection == null -> {
                if (savedState.mode == PracticalShoppingSavedSurfaceMode.ERROR) {
                    val refreshAction = savedState.refreshAction
                    item(key = "observed-price-selection-error") {
                        EmptyStateCard(
                            eyebrow = valuePilotNextText("SAVED CHOICES NEED A REFRESH", "HAY QUE ACTUALIZAR LAS ELECCIONES GUARDADAS"),
                            title = valuePilotNextText("We couldn't prepare price confirmation.", "No pudimos preparar la confirmación de precio."),
                            detail = savedState.statusMessage
                                ?: valuePilotNextText("Refresh your saved choices and try again.", "Actualizá tus elecciones guardadas y probá de nuevo."),
                            actionLabel = refreshAction?.let {
                                savedState.refreshActionLabel ?: valuePilotNextText("Refresh saved choices", "Actualizar elecciones guardadas")
                            } ?: valuePilotNextText("Back to Saved", "Volver a Guardados"),
                            onAction = {
                                if (refreshAction != null) onSavedAction(refreshAction)
                                else onBackToSaved()
                            }
                        )
                    }
                } else {
                    item(key = "observed-price-selection-loading") {
                        LoadingRow(valuePilotNextText("Preparing your saved choices…", "Preparando tus elecciones guardadas…"))
                    }
                }
            }

            selection.productRows.isEmpty() && selection.storeRows.isEmpty() -> {
                item(key = "observed-price-selection-empty") {
                    EmptyStateCard(
                        eyebrow = valuePilotNextText("START WITH SAVED CHOICES", "EMPEZÁ CON ELECCIONES GUARDADAS"),
                        title = valuePilotNextText("Save a product and store first.", "Primero guardá un producto y una tienda."),
                        detail = selection.notice
                            ?: valuePilotNextText("Observed-price confirmation needs one saved product and one exact saved store.", "La confirmación de precio observado necesita un producto guardado y una tienda guardada exacta."),
                        actionLabel = valuePilotNextText("Back to Saved", "Volver a Guardados"),
                        onAction = onBackToSaved
                    )
                }
            }

            else -> {
                selection.notice?.let { message ->
                    item(key = "observed-price-selection-notice") {
                        EvidenceNotice(message)
                    }
                }

                selection.productSectionTitle?.let { title ->
                    item(key = "observed-price-products-title") {
                        SectionHeading(valuePilotNextText("PRODUCT", "PRODUCTO"), title)
                    }
                }
                items(selection.productRows) { row ->
                    SelectionChoiceCard(
                        title = row.title,
                        selected = row.selected,
                        actionLabel = row.actionLabel,
                        actionDescription = row.actionDescription,
                        onAction = { onSelectionAction(row.action) }
                    )
                }

                selection.storeSectionTitle?.let { title ->
                    item(key = "observed-price-stores-title") {
                        SectionHeading(valuePilotNextText("EXACT STORE", "TIENDA EXACTA"), title)
                    }
                }
                items(selection.storeRows) { row ->
                    SelectionChoiceCard(
                        title = row.title,
                        selected = row.selected,
                        actionLabel = row.actionLabel,
                        actionDescription = row.actionDescription,
                        onAction = { onSelectionAction(row.action) }
                    )
                }

                if (selection.productRows.isEmpty() || selection.storeRows.isEmpty()) {
                    val missingProduct = selection.productRows.isEmpty()
                    item(key = "observed-price-missing-saved-choice") {
                        EmptyStateCard(
                            eyebrow = valuePilotNextText("ONE MORE SAVED CHOICE", "FALTA UNA ELECCIÓN GUARDADA"),
                            title = if (missingProduct) {
                                valuePilotNextText("Save a product to continue.", "Guardá un producto para continuar.")
                            } else {
                                valuePilotNextText("Save an exact store to continue.", "Guardá una tienda exacta para continuar.")
                            },
                            detail = valuePilotNextText("Price confirmation needs both a saved product and an exact saved store.", "La confirmación de precio necesita un producto guardado y una tienda guardada exacta."),
                            actionLabel = valuePilotNextText("Back to Saved", "Volver a Guardados"),
                            onAction = onBackToSaved
                        )
                    }
                }

                selection.clearSelectionAction?.let { action ->
                    item(key = "observed-price-clear-selection") {
                        TextButton(
                            onClick = { onSelectionAction(action) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = ValuePilotSpacing.minimumTouchTarget)
                        ) {
                            Text(valuePilotNextText("Clear selected product and store", "Borrar producto y tienda elegidos"))
                        }
                    }
                }

                selection.checkPrefillAction?.let { action ->
                    item(key = "observed-price-check-prefill") {
                        PrimaryActionButton(
                            label = selection.checkPrefillActionLabel
                                ?: valuePilotNextText("Check saved product and store", "Comprobar producto y tienda guardados"),
                            onClick = { onCheckPrefill(action) },
                            enabled = !savedState.progressVisible
                        )
                    }
                }
            }
        }

        prefillResult?.let { result ->
            item(key = "observed-price-prefill-result") {
                PrefillResultCard(
                    result = result,
                    onContinue = onContinueToConfirmation
                )
            }
        }

        savedState.refreshAction?.let { action ->
            item(key = "observed-price-refresh-saved") {
                SecondaryActionButton(
                    label = savedState.refreshActionLabel ?: valuePilotNextText("Refresh saved choices", "Actualizar elecciones guardadas"),
                    onClick = { onSavedAction(action) },
                    enabled = !savedState.progressVisible
                )
            }
        }
    }
}

@Composable
private fun SelectionChoiceCard(
    title: String,
    selected: Boolean,
    actionLabel: String,
    actionDescription: String,
    onAction: () -> Unit
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
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (selected) {
                    EvidencePill(
                        text = valuePilotNextText("SELECTED", "ELEGIDO"),
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            TextButton(
                onClick = onAction,
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
private fun PrefillResultCard(
    result: UserObservedPriceSavedPrefillHandoffUiState,
    onContinue: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (
                result.status == UserObservedPriceSavedPrefillHandoffUiStatus.IDENTITY_PREFILL_READY
            ) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = ValuePilotElevation.flat)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ValuePilotSpacing.large),
            verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)
        ) {
            Eyebrow(valuePilotNextText("SAVED IDENTITY CHECK", "COMPROBACIÓN DE IDENTIDAD GUARDADA"))
            Text(
                result.headline,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                result.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (result.status == UserObservedPriceSavedPrefillHandoffUiStatus.IDENTITY_PREFILL_READY) {
                Text(
                    "${result.productName} · ${result.storeDisplayName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    valuePilotNextText(
                        "Price and proof entry still opens in the existing ValuePilot screen.",
                        "El ingreso de precio y evidencia todavía se abre en la pantalla existente de ValuePilot."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PrimaryActionButton(
                    label = valuePilotNextText("Continue to price confirmation", "Continuar a la confirmación de precio"),
                    onClick = onContinue
                )
            }
        }
    }
}
