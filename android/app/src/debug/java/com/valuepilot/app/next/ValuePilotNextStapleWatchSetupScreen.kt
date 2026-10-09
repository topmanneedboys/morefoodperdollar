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
import com.valuepilot.app.StapleWatchFactResolutionUiState
import com.valuepilot.app.StapleWatchSavedIdentityHandoffUiAction
import com.valuepilot.app.StapleWatchSavedIdentitySelectionAction
import com.valuepilot.app.StapleWatchSavedSelectionUiState

@Composable
internal fun ValuePilotNextStapleWatchSetupScreen(
    state: StapleWatchSavedSelectionUiState?,
    onAction: (StapleWatchSavedIdentitySelectionAction) -> Unit,
    onContinue: (StapleWatchSavedIdentityHandoffUiAction) -> Unit,
    onBack: () -> Unit
) {
    PremiumColumn {
        item(key = "staple-watch-intro") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)) {
                Eyebrow("RECURRING STAPLES")
                Text(
                    "Watch My Staples",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    state?.guidance
                        ?: "Choose at least two saved products and a usual store to prepare a future comparison.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                EvidenceFootnote(
                    "This setup only records temporary identity choices. It does not produce current prices, a route, a savings decision, or an alert."
                )
            }
        }

        if (state == null) {
            item(key = "staple-watch-loading") {
                LoadingRow("Checking your saved products and stores…")
            }
        } else {
            item(key = "staple-watch-selection-summary") {
                SummaryCard(
                    eyebrow = "YOUR WATCH LIST",
                    title = state.selectionSummary,
                    detail = if (state.usualStoreSelected) {
                        "Usual store selected"
                    } else {
                        "Choose one usual store"
                    }
                )
            }

            state.notice?.let { message ->
                item(key = "staple-watch-notice") {
                    EvidenceNotice(text = message, isError = state.selectedDisplayNameBlockerCount > 0)
                }
            }

            if (state.productRows.isEmpty() || state.storeRows.isEmpty()) {
                item(key = "staple-watch-missing-saved-choices") {
                    EmptyStateCard(
                        eyebrow = "SAVED CHOICES CHANGED",
                        title = if (state.productRows.isEmpty()) {
                            "Save named products to continue."
                        } else {
                            "Save a named store to continue."
                        },
                        detail = "Watch My Staples needs at least two named products and one named store.",
                        actionLabel = "Back to Saved",
                        onAction = onBack
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
                    SectionHeading("CHOOSE YOUR STAPLES", title)
                }
                items(state.productRows) { row ->
                    StapleChoiceCard(
                        title = row.title,
                        selected = row.watched,
                        actionLabel = row.actionLabel,
                        actionDescription = row.actionDescription,
                        action = row.action,
                        onAction = onAction
                    )
                }
            }

            state.storeSectionTitle?.let { title ->
                item(key = "staple-watch-stores-heading") {
                    SectionHeading("USUAL STORE", title)
                }
                items(state.storeRows) { row ->
                    StapleChoiceCard(
                        title = row.title,
                        selected = row.usualStore,
                        actionLabel = row.actionLabel,
                        actionDescription = row.actionDescription,
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
                        Text(state.clearSelectionActionLabel ?: "Clear selections")
                    }
                }
            }

            state.continueAction?.let { action ->
                item(key = "staple-watch-continue") {
                    PrimaryActionButton(
                        label = state.continueActionLabel ?: "Continue",
                        onClick = { onContinue(action) }
                    )
                }
            }

            item(key = "staple-watch-back") {
                SecondaryActionButton(label = "Back to Saved", onClick = onBack)
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
                        text = "SELECTED",
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
    Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)) {
        SummaryCard(
            eyebrow = "REQUIRED FACT CHECKS",
            title = "${state.resolvedRequirementCount} of ${state.totalRequirementCount} checks supplied",
            detail = state.guidance
        )
        state.unresolvedRequirementLabels.forEach { label ->
            Text(
                "Still needed · $label",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
