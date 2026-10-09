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
                    "The products and stores\nyou return to.",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Keep useful choices close. Saved names help you find them again; they are not live prices or current offers.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                SavedShortlistCard(detail = message, onSearch = onSearch)
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

        item(key = "saved-tools-heading") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)) {
                Eyebrow("PERSONAL TOOLS")
                Text(
                    "Put your saved choices to work.",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        item(key = "saved-existing-tools") {
            SavedToolCard(
                eyebrow = "PERSONAL PRICE MEMORY",
                title = "Record a price you saw",
                detail = "Choose a saved product and exact saved store, then add your observed price and proof.",
                actionLabel = "Start price confirmation",
                onClick = onRecordObservedPrice
            )
        }

        item(key = "saved-private-price-history") {
            SavedToolCard(
                eyebrow = "ON THIS DEVICE",
                title = "Your private price history",
                detail = "Review past comparison observations and manage the copy stored on this device. They are not live store prices.",
                actionLabel = "Review price history",
                onClick = onOpenPrivatePriceHistory
            )
        }

        item(key = "saved-purchase-history-unavailable") {
            PurchaseHistoryUnavailableCard(
                presentation = valuePilotNextPurchaseHistoryAvailabilityPresentation()
            )
        }

        item(key = "saved-staple-watch-launch") {
            SavedToolCard(
                eyebrow = "RECURRING STAPLES",
                title = stapleLaunch?.title ?: "Watch My Staples",
                detail = stapleLaunch?.supportingText
                    ?: stapleLaunch?.notice
                    ?: "Review the saved product and store requirements for recurring staples.",
                actionLabel = stapleLaunch?.actionLabel ?: "Review setup requirements",
                onClick = onOpenStapleWatch
            )
        }
    }
}

@Composable
private fun SavedShortlistCard(
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
            Eyebrow("A GOOD PLACE TO START")
            Text(
                "Build a personal shortlist.",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PrimaryActionButton(label = "Search products", onClick = onSearch)
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
                Eyebrow(presentation.eyebrow)
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                ) {
                    Text(
                        "NOT CONNECTED",
                        modifier = Modifier.padding(horizontal = ValuePilotSpacing.small, vertical = ValuePilotSpacing.xSmall),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                presentation.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                presentation.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                            .weight(1f)
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
                        .fillMaxWidth()
                        .heightIn(min = ValuePilotSpacing.minimumTouchTarget)
                        .semantics {
                            contentDescription = row.actionDescription
                                ?: row.actionLabel
                                ?: "Remove saved store"
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(row.actionLabel ?: "Remove")
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
                        "SAVED",
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
