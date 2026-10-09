package com.valuepilot.app.next

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.valuepilot.app.PracticalShoppingSavedSurfaceAction
import com.valuepilot.app.PracticalShoppingSavedSurfaceMode
import com.valuepilot.app.PracticalShoppingSavedSurfaceProductRow
import com.valuepilot.app.PracticalShoppingSavedSurfaceState
import com.valuepilot.app.PracticalShoppingSavedSurfaceStoreRow
import com.valuepilot.app.PracticalShoppingSavedStapleLaunchUiState

@Composable
internal fun ValuePilotSavedScreen(
    state: PracticalShoppingSavedSurfaceState,
    stapleLaunch: PracticalShoppingSavedStapleLaunchUiState?,
    onAction: (PracticalShoppingSavedSurfaceAction) -> Unit,
    onCheckPrice: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenStapleWatch: () -> Unit,
    onRecordObservedPrice: () -> Unit,
    onOpenSavedTools: () -> Unit
) {
    var confirmClearAll by remember { mutableStateOf(false) }

    if (confirmClearAll) {
        val clearAction = state.clearAllAction
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("Clear saved choices?") },
            text = {
                Text("This removes your saved product and store choices from this device.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearAll = false
                        clearAction?.let(onAction)
                    },
                    enabled = clearAction != null
                ) {
                    Text("Clear choices")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) {
                    Text("Keep them")
                }
            }
        )
    }

    PremiumColumn {
        item(key = "saved-intro") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)) {
                Eyebrow("YOUR SHOPPING MEMORY")
                Text(
                    "The products and stores you return to.",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Keep useful choices close. Saved names help you find them again; they are not live prices or current offers.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item(key = "saved-existing-tools") {
            ActionCard(
                eyebrow = "PERSONAL PRICE MEMORY",
                title = "Record a price you saw",
                detail = "Choose a saved product and exact saved store, then add your observed price and proof.",
                actionLabel = "Start price confirmation",
                onClick = onRecordObservedPrice
            )
        }

        stapleLaunch?.title?.let { title ->
            item(key = "saved-staple-watch-launch") {
                if (stapleLaunch.action != null) {
                    ActionCard(
                        eyebrow = "RECURRING STAPLES",
                        title = title,
                        detail = stapleLaunch.supportingText
                            ?: "Choose the saved staples you return to most.",
                        actionLabel = stapleLaunch.actionLabel ?: "Set up Watch My Staples",
                        onClick = onOpenStapleWatch
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        stapleLaunch.notice?.let { EvidenceNotice(it) }
                    }
                }
            }
        }

        item(key = "saved-legacy-tools") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)) {
                Text(
                    "Personal price history still opens in the existing ValuePilot screens.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SecondaryActionButton(
                    label = "Open remaining Saved tools",
                    onClick = onOpenSavedTools
                )
            }
        }

        if (state.progressVisible) {
            item(key = "saved-progress") {
                LoadingRow(state.statusMessage ?: "Updating saved choices…")
            }
        } else {
            state.statusMessage?.let { message ->
                item(key = "saved-status") {
                    EvidenceNotice(
                        text = message,
                        isError = state.mode == PracticalShoppingSavedSurfaceMode.ERROR
                    )
                }
            }
        }

        state.notice?.let { message ->
            item(key = "saved-notice") { EvidenceNotice(message) }
        }

        state.productSectionTitle?.let { title ->
            item(key = "saved-products-heading") {
                SectionHeading("SAVED PRODUCTS", title)
            }
            items(state.productRows) { row ->
                SavedProductCard(
                    row = row,
                    onCheckPrice = { action -> onCheckPrice(action.displayName) },
                    onRemove = { action -> onAction(action) }
                )
            }
        }

        state.storeSectionTitle?.let { title ->
            item(key = "saved-stores-heading") {
                SectionHeading("SAVED STORES", title)
            }
            items(state.storeRows) { row ->
                SavedStoreCard(row = row, onRemove = onAction)
            }
        }

        state.emptyMessage?.let { message ->
            item(key = "saved-empty") {
                EmptyStateCard(
                    eyebrow = "A GOOD PLACE TO START",
                    title = "Build a personal shortlist.",
                    detail = message,
                    actionLabel = "Search products",
                    onAction = onSearch
                )
            }
        }

        state.refreshAction?.let { action ->
            item(key = "saved-refresh") {
                if (state.mode == PracticalShoppingSavedSurfaceMode.ERROR) {
                    PrimaryActionButton(
                        label = requireNotNull(state.refreshActionLabel),
                        onClick = { onAction(action) }
                    )
                } else {
                    SecondaryActionButton(
                        label = requireNotNull(state.refreshActionLabel),
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
                    Text("Clear all saved choices", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun SavedProductCard(
    row: PracticalShoppingSavedSurfaceProductRow,
    onCheckPrice: (PracticalShoppingSavedSurfaceAction.CheckProductPrice) -> Unit,
    onRemove: (PracticalShoppingSavedSurfaceAction.Preference) -> Unit
) {
    SavedChoiceCard(
        title = row.title,
        supportingText = row.supportingText,
        actions = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall),
                horizontalAlignment = Alignment.End
            ) {
                row.secondaryAction?.let { action ->
                    TextButton(
                        onClick = { onCheckPrice(action) },
                        modifier = Modifier
                            .heightIn(min = ValuePilotSpacing.minimumTouchTarget)
                            .semantics {
                                contentDescription = row.secondaryActionDescription
                                    ?: row.secondaryActionLabel
                                    ?: "Check price"
                            }
                    ) {
                        Text(row.secondaryActionLabel ?: "Check price")
                    }
                }
                row.action?.let { action ->
                    TextButton(
                        onClick = { onRemove(action) },
                        modifier = Modifier
                            .heightIn(min = ValuePilotSpacing.minimumTouchTarget)
                            .semantics {
                                contentDescription = row.actionDescription
                                    ?: row.actionLabel
                                    ?: "Remove saved product"
                            }
                    ) {
                        Text(row.actionLabel ?: "Remove")
                    }
                }
            }
        }
    )
}

@Composable
private fun SavedStoreCard(
    row: PracticalShoppingSavedSurfaceStoreRow,
    onRemove: (PracticalShoppingSavedSurfaceAction.Preference) -> Unit
) {
    SavedChoiceCard(
        title = row.title,
        supportingText = row.supportingText,
        actions = {
            row.action?.let { action ->
                TextButton(
                    onClick = { onRemove(action) },
                    modifier = Modifier
                        .align(Alignment.End)
                        .heightIn(min = ValuePilotSpacing.minimumTouchTarget)
                        .semantics {
                            contentDescription = row.actionDescription
                                ?: row.actionLabel
                                ?: "Remove saved store"
                        }
                ) {
                    Text(row.actionLabel ?: "Remove")
                }
            }
        }
    )
}

@Composable
private fun SavedChoiceCard(
    title: String,
    supportingText: String,
    actions: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = ValuePilotElevation.flat)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ValuePilotSpacing.large),
            verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            actions()
        }
    }
}
