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
                Eyebrow("PERSONAL PRICE MEMORY")
                Text(
                    "Confirm a price you saw",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Choose one saved product and one exact saved store. You will enter your own observed price and proof in the next step.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                EvidenceFootnote(
                    "A personal observation is not a retailer-confirmed current offer."
                )
            }
        }

        if (savedState.progressVisible) {
            item(key = "observed-price-saved-loading") {
                LoadingRow(savedState.statusMessage ?: "Checking saved choices…")
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
                            eyebrow = "SAVED CHOICES NEED A REFRESH",
                            title = "We couldn't prepare price confirmation.",
                            detail = savedState.statusMessage
                                ?: "Refresh your saved choices and try again.",
                            actionLabel = refreshAction?.let {
                                savedState.refreshActionLabel ?: "Refresh saved choices"
                            } ?: "Back to Saved",
                            onAction = {
                                if (refreshAction != null) onSavedAction(refreshAction)
                                else onBackToSaved()
                            }
                        )
                    }
                } else {
                    item(key = "observed-price-selection-loading") {
                        LoadingRow("Preparing your saved choices…")
                    }
                }
            }

            selection.productRows.isEmpty() && selection.storeRows.isEmpty() -> {
                item(key = "observed-price-selection-empty") {
                    EmptyStateCard(
                        eyebrow = "START WITH SAVED CHOICES",
                        title = "Save a product and store first.",
                        detail = selection.notice
                            ?: "Observed-price confirmation needs one saved product and one exact saved store.",
                        actionLabel = "Back to Saved",
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
                        SectionHeading("PRODUCT", title)
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
                        SectionHeading("EXACT STORE", title)
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
                            eyebrow = "ONE MORE SAVED CHOICE",
                            title = if (missingProduct) {
                                "Save a product to continue."
                            } else {
                                "Save an exact store to continue."
                            },
                            detail = "Price confirmation needs both a saved product and an exact saved store.",
                            actionLabel = "Back to Saved",
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
                            Text("Clear selected product and store")
                        }
                    }
                }

                selection.checkPrefillAction?.let { action ->
                    item(key = "observed-price-check-prefill") {
                        PrimaryActionButton(
                            label = selection.checkPrefillActionLabel
                                ?: "Check saved product and store",
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
                    label = savedState.refreshActionLabel ?: "Refresh saved choices",
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
                        text = "SELECTED",
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
            Eyebrow("SAVED IDENTITY CHECK")
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
                    "Price and proof entry still opens in the existing ValuePilot screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PrimaryActionButton(
                    label = "Continue to price confirmation",
                    onClick = onContinue
                )
            }
        }
    }
}
