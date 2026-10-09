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
    val classicHandoffRequired =
        state?.let { requiresClassicStapleWatchHandoff(it.factCheckCapability) } == true

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
                    if (classicHandoffRequired) {
                        "Current-price and savings checks are still in the classic app. This Compose preview does not save a Watch list."
                    } else {
                        "This setup only records temporary identity choices. It does not produce current prices, a route, a savings decision, or an alert."
                    }
                )
            }
        }

        if (state == null) {
            item(key = "staple-watch-loading") {
                LoadingRow("Checking your saved products and stores…")
            }
        } else if (classicHandoffRequired) {
            item(key = "staple-watch-legacy-handoff") {
                ActionCard(
                    eyebrow = "NOT IN COMPOSE YET",
                    title = "Continue with the existing Watch flow",
                    detail = "Current-price and savings checks have not moved into Compose. Open Saved in the classic app, then choose Watch My Staples. This preview has not saved a watch list or temporary selections.",
                    actionLabel = "Open classic Saved & Watch tools",
                    onClick = onOpenLegacyStapleWatch
                )
            }

            item(key = "staple-watch-back") {
                SecondaryActionButton(label = "Back to Saved", onClick = onBack)
            }
        } else {
            item(key = "staple-watch-selection-summary") {
                WatchSetupProgressCard(
                    state = state,
                    status = when {
                        state.status == StapleWatchSavedSelectionUiStatus.DISPLAY_METADATA_INCOMPLETE ->
                            "REFRESH NAMES"
                        state.status == StapleWatchSavedSelectionUiStatus.READY_FOR_FACT_CHECK ->
                            "READY FOR CHECK"
                        else -> "SETUP IN PROGRESS"
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
                items(
                    items = state.productRows,
                    key = { row -> row.action.itemKey.value }
                ) { row ->
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
private fun WatchSetupProgressCard(
    state: StapleWatchSavedSelectionUiState,
    status: String
) {
    val hasEnoughStaples = state.watchedItemCount >= MIN_WATCHED_SAVED_ITEMS_FOR_HANDOFF
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
                Eyebrow("YOUR WATCH LIST")
                Text(
                    state.selectionSummary,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)) {
                SetupRequirementRow(
                    label = "Choose at least $MIN_WATCHED_SAVED_ITEMS_FOR_HANDOFF staples",
                    detail = if (hasEnoughStaples) {
                        "${state.watchedItemCount} selected · minimum met"
                    } else {
                        "${state.watchedItemCount} selected · $MIN_WATCHED_SAVED_ITEMS_FOR_HANDOFF required"
                    },
                    complete = hasEnoughStaples
                )
                SetupRequirementRow(
                    label = "Choose your usual store",
                    detail = if (state.usualStoreSelected) "Store selected" else "One saved store required",
                    complete = state.usualStoreSelected
                )
            }

            EvidencePill(
                text = status,
                container = if (status == "READY FOR CHECK") {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                },
                content = if (status == "READY FOR CHECK") {
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
