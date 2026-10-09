package com.valuepilot.app.next

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.valuepilot.app.CompareHereManualDraftActionEvaluator
import com.valuepilot.app.CompareHereManualDraftReadiness
import com.valuepilot.app.CompareHereManualInputAdapter
import com.valuepilot.app.CompareHereManualRouteState
import com.valuepilot.app.CompareHereManualRouteStatus
import com.valuepilot.app.CompareHereUiStatus
import com.valuepilot.app.CompareHereShareCard
import com.valuepilot.core.CompareHerePriceSelection

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
    val actionState =
        CompareHereManualDraftActionEvaluator.evaluate(
            rawBlocks = state.productBlocks,
            likeForLikeConfirmed = state.likeForLikeConfirmed
        )

    PremiumColumn {
        item(key = "compare-here-intro") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)) {
                Eyebrow("COMPARE WHAT YOU SAW")
                Text(
                    "Find the value in your options.",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Enter exact shelf details and package sizes. This compares the observations you enter; it does not verify a current store offer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item(key = "compare-price-basis") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)) {
                SectionHeading("PRICE BASIS", "Choose what to compare")
                Text(
                    "A missing member price stays unranked. A current price is never used in its place.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)) {
                    FilterChip(
                        selected = state.priceSelection == CompareHerePriceSelection.CURRENT,
                        onClick = { onPriceSelectionChanged(CompareHerePriceSelection.CURRENT) },
                        label = { Text("Current shelf price") }
                    )
                    FilterChip(
                        selected = state.priceSelection == CompareHerePriceSelection.MEMBER,
                        onClick = { onPriceSelectionChanged(CompareHerePriceSelection.MEMBER) },
                        label = { Text("Member price") }
                    )
                }
            }
        }

        item(key = "compare-products-title") {
            SectionHeading(
                "PRODUCT DETAILS",
                "${state.productBlocks.size} comparison ${if (state.productBlocks.size == 1) "entry" else "entries"}"
            )
        }

        itemsIndexed(
            items = state.productBlocks,
            key = { index, _ -> "compare-product-$index" }
        ) { index, block ->
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)) {
                OutlinedTextField(
                    value = block,
                    onValueChange = { onProductChanged(index, it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Product ${index + 1} name, package, price and promotion details"
                        },
                    label = { Text("Option ${index + 1}") },
                    placeholder = {
                        Text("For example: Apples, 3 lb, current price CA$5.99")
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
                                contentDescription = "Remove comparison option ${index + 1}"
                            }
                    ) {
                        Text("Remove option", color = MaterialTheme.colorScheme.error)
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
                Text("Add another option")
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
                            "These are like-for-like alternatives",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Confirm only when every option is a substitute you would reasonably choose between. Editing an entry clears this confirmation.",
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
                    EvidenceFootnote("Enter at least two products with an exact package size and price.")
                }
            CompareHereManualDraftReadiness.CONFIRM_LIKE_FOR_LIKE ->
                item(key = "compare-readiness") {
                    EvidenceFootnote("Confirm the products are comparable before requesting an exact-value ranking.")
                }
            CompareHereManualDraftReadiness.READY -> Unit
        }

        state.notice?.let { message ->
            item(key = "compare-notice") { EvidenceNotice(message) }
        }

        if (state.isComparing) {
            item(key = "compare-progress") { LoadingRow("Comparing the exact details you entered…") }
        }

        state.routeState?.let { result ->
            item(key = "compare-result") {
                CompareHereResultCard(
                    routeState = result,
                    onShare = result.shareCard?.let { card -> { onShare(card) } }
                )
            }
        }

        item(key = "compare-submit") {
            PrimaryActionButton(
                label = "Compare exact value",
                onClick = onCompare,
                enabled = actionState.compareEnabled && !state.isComparing
            )
        }

        item(key = "compare-capture-handoff") {
            ActionCard(
                eyebrow = "SCAN OR IMPORT",
                title = "Start from a barcode or price-label photo",
                detail = "Open the existing capture tools to suggest product text. You review every suggestion and add the price and package details you see.",
                actionLabel = "Open capture tools",
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
                Text("Clear comparison")
            }
        }

        item(key = "compare-back") {
            SecondaryActionButton("Back", onBack)
        }
    }
}

@Composable
private fun CompareHereResultCard(
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
            Eyebrow("COMPARISON RESULT")
            Text(routeState.title, style = MaterialTheme.typography.headlineMedium)
            Text(routeState.guidance, style = MaterialTheme.typography.bodyMedium)

            routeState.comparisonState?.let { result ->
                if (result.status == CompareHereUiStatus.READY) {
                    result.rows.firstOrNull { it.bestValue }?.let { best ->
                        Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)) {
                            EvidencePill(
                                "BEST VALUE",
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.onPrimary
                            )
                            Text(best.title, style = MaterialTheme.typography.titleLarge)
                            Text(
                                best.unitRateText,
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                result.rows.forEach { row ->
                    CompareHereResultRow(
                        title = row.title,
                        detail = "${row.priceText} · ${row.quantityText}",
                        unitRate = row.unitRateText,
                        badge = if (row.bestValue) "BEST VALUE" else row.valueRank?.let { "RANK $it" }
                    )
                }
                result.blockedRows.forEach { row ->
                    CompareHereResultRow(
                        title = row.title,
                        detail = row.reasonText,
                        unitRate = null,
                        badge = "UNRANKED"
                    )
                }
                result.notice?.let { EvidenceNotice(it) }
            }

            if (routeState.rejectedProductGuidance.isNotEmpty()) {
                routeState.rejectedProductGuidance.forEach { guidance ->
                    Text(
                        guidance,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            EvidenceFootnote(
                "This result compares your entered observations. It does not confirm a purchase or a live retailer price."
            )

            onShare?.let { action ->
                SecondaryActionButton("Share privacy-safe result", action)
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
                EvidencePill(
                    it,
                    if (it == "BEST VALUE") MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    if (it == "BEST VALUE") MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        unitRate?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
    }
}
