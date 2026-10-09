package com.valuepilot.app.next

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.valuepilot.app.PracticalShoppingPrivatePriceHistoryForgetPresentation
import com.valuepilot.app.PracticalShoppingPrivatePriceHistoryPresentation
import com.valuepilot.app.PracticalShoppingPrivatePriceHistoryRow

@Composable
internal fun ValuePilotNextPrivatePriceHistoryScreen(
    state: ValuePilotNextPrivatePriceHistoryUiState?,
    onRefresh: () -> Unit,
    onForget: (String) -> Unit,
    onClearAll: () -> Unit,
    onExport: (String) -> Unit,
    onOpenComparison: () -> Unit,
    onBack: () -> Unit
) {
    var showForgetDialog by remember { mutableStateOf(false) }
    var selectedObservationId by remember { mutableStateOf<String?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    val forgetPresentation = state?.forgetPresentation
    val selectedChoiceExists = forgetPresentation?.choices?.any {
        it.observationId == selectedObservationId
    } == true

    if (showForgetDialog && forgetPresentation != null) {
        ForgetObservationDialog(
            presentation = forgetPresentation,
            selectedObservationId = selectedObservationId,
            onSelect = { selectedObservationId = it },
            onDismiss = {
                showForgetDialog = false
                selectedObservationId = null
            },
            onConfirm = {
                selectedObservationId?.let(onForget)
                showForgetDialog = false
                selectedObservationId = null
            }
        )
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Clear private history?") },
            text = {
                Text(
                    "This permanently removes the comparison observations stored on this device. It does not change saved choices, live offers, or shopping plans."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearAllDialog = false
                        onClearAll()
                    }
                ) { Text("Clear history") }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) { Text("Keep history") }
            }
        )
    }

    if (showExportDialog && state?.exportPreview != null) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Review before sharing") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)
                ) {
                    Text(
                        state.exportPreview,
                        style = MaterialTheme.typography.bodySmall
                    )
                    EvidenceFootnote(
                        "This copy contains private observations from this device. Review it before choosing a destination. It does not contain store availability or current-offer claims."
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExportDialog = false
                        onExport(requireNotNull(state.exportPreview))
                    }
                ) { Text("Choose where to share") }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text("Cancel") }
            }
        )
    }

    PremiumColumn {
        item(key = "private-price-history-intro") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)) {
                Eyebrow("PERSONAL PRICE MEMORY")
                Text(
                    "Your private price history",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Past comparison observations stored on this device. They are personal context, not live store prices, inventory, retailer offers, or a guarantee.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        when (state?.status) {
            null,
            ValuePilotNextPrivatePriceHistoryStatus.LOADING -> {
                item(key = "private-price-history-loading") {
                    LoadingRow("Reading history stored on this device…")
                }
            }

            ValuePilotNextPrivatePriceHistoryStatus.UNAVAILABLE -> {
                item(key = "private-price-history-unavailable") {
                    EmptyStateCard(
                        eyebrow = "HISTORY UNAVAILABLE",
                        title = "We couldn't open private history.",
                        detail = state.notice
                            ?: "Try again or open Scan & compare for its recovery options.",
                        actionLabel = "Try again",
                        onAction = onRefresh
                    )
                }
                item(key = "private-price-history-recovery") {
                    SecondaryActionButton(
                        label = "Open Scan & compare",
                        onClick = onOpenComparison
                    )
                }
            }

            ValuePilotNextPrivatePriceHistoryStatus.EMPTY -> {
                state.notice?.let { notice ->
                    item(key = "private-price-history-empty-notice") {
                        EvidenceNotice(notice)
                    }
                }
                item(key = "private-price-history-empty") {
                    EmptyStateCard(
                        eyebrow = "NO OBSERVATIONS YET",
                        title = "Your history will appear here.",
                        detail = state.presentation?.intro
                            ?: "Private comparison observations stay on this device and are not current store offers.",
                        actionLabel = "Open Scan & compare",
                        onAction = onOpenComparison
                    )
                }
            }

            ValuePilotNextPrivatePriceHistoryStatus.AVAILABLE -> {
                state.notice?.let { notice ->
                    item(key = "private-price-history-notice") {
                        EvidenceNotice(notice)
                    }
                }
                state.presentation?.let { presentation ->
                    item(key = "private-price-history-summary") {
                        HistorySummaryCard(presentation)
                    }
                    items(presentation.rows.size) { index ->
                        HistoryRowCard(presentation.rows[index])
                    }
                }

                if (state.exportPreview != null) {
                    item(key = "private-price-history-export") {
                        SecondaryActionButton(
                            label = "Review and share a copy",
                            onClick = { showExportDialog = true }
                        )
                    }
                }

                if (forgetPresentation != null) {
                    item(key = "private-price-history-forget") {
                        SecondaryActionButton(
                            label = "Forget one observation",
                            onClick = {
                                selectedObservationId = null
                                showForgetDialog = true
                            }
                        )
                    }
                }

                item(key = "private-price-history-clear") {
                    TextButton(
                        onClick = { showClearAllDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = ValuePilotSpacing.minimumTouchTarget)
                    ) {
                        Text("Clear all private history", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        item(key = "private-price-history-back") {
            SecondaryActionButton(label = "Back to Saved", onClick = onBack)
        }
    }
}

@Composable
private fun HistorySummaryCard(presentation: PracticalShoppingPrivatePriceHistoryPresentation) {
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
            Eyebrow("${presentation.rows.size} EXACT HISTORIES SHOWN")
            Text(presentation.title, style = MaterialTheme.typography.titleLarge)
            Text(presentation.intro, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (presentation.omittedRowCount > 0) {
                Text(
                    "${presentation.omittedRowCount} more exact package histories are stored on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HistoryRowCard(row: PracticalShoppingPrivatePriceHistoryRow) {
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
            Eyebrow("${row.observationCount} ${if (row.observationCount == 1) "OBSERVATION" else "OBSERVATIONS"} · ${row.sourceText.uppercase()}")
            Text(row.displayName, style = MaterialTheme.typography.titleLarge)
            Text(row.latestPriceText, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                "Last observed ${row.latestObservedText}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Lowest observed · ${row.lowestPriceText}", style = MaterialTheme.typography.bodyMedium)
            Text("Unit rate · ${row.latestUnitRateText}", style = MaterialTheme.typography.bodyMedium)
            Text("Observed range · ${row.rangeText}", style = MaterialTheme.typography.bodyMedium)
            row.recentMovementText?.let { movement ->
                Text(movement, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "Package · ${row.packageText} · ${row.priceBasisText}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            row.promotionText?.let { promotion ->
                Text(promotion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ForgetObservationDialog(
    presentation: PracticalShoppingPrivatePriceHistoryForgetPresentation,
    selectedObservationId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(presentation.title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)
            ) {
                Text(presentation.message, style = MaterialTheme.typography.bodySmall)
                presentation.choices.forEach { choice ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(choice.observationId) }
                            .padding(vertical = ValuePilotSpacing.xSmall),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = choice.observationId == selectedObservationId,
                            onClick = { onSelect(choice.observationId) }
                        )
                        Text(choice.label, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = selectedObservationId != null) {
                Text("Forget selected")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
