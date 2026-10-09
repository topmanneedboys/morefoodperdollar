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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.valuepilot.app.GoodPriceCheckAnswerTone
import com.valuepilot.app.GoodPriceCheckRouteState
import com.valuepilot.app.GoodPriceCheckUiState
import com.valuepilot.app.R
import com.valuepilot.core.CompareHerePriceSelection

@Composable
internal fun ValuePilotNextGoodPriceScreen(
    state: ValuePilotNextGoodPriceUiState,
    onDraftChanged: (String) -> Unit,
    onPriceSelectionChanged: (CompareHerePriceSelection) -> Unit,
    onCheck: () -> Unit,
    onClearDraft: () -> Unit,
    onClearPrivateMemory: () -> Unit,
    onScanBarcode: () -> Unit,
    onChooseBarcodeOption: (Long, Int) -> Unit,
    onDismissBarcodeChoice: (Long) -> Unit,
    onShare: (com.valuepilot.app.GoodPriceShareCard) -> Unit,
    onBack: () -> Unit
) {
    var confirmClearMemory by remember { mutableStateOf(false) }
    var showSharePreview by remember { mutableStateOf(false) }
    val shareCard = state.route.shareCard
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val draftFocusRequester = remember { FocusRequester() }
    var handledFocusRequestId by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(state.barcodeFocusRequestId) {
        if (state.barcodeFocusRequestId > handledFocusRequestId) {
            draftFocusRequester.requestFocus()
            keyboard?.show()
            handledFocusRequestId = state.barcodeFocusRequestId
        }
    }

    fun runCheck() {
        focusManager.clearFocus()
        keyboard?.hide()
        onCheck()
    }

    if (confirmClearMemory) {
        AlertDialog(
            onDismissRequest = { confirmClearMemory = false },
            title = { Text("Clear private price memory?") },
            text = {
                Text("This clears the comparison observations stored on this device and removes the current answer from view.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearMemory = false
                        onClearPrivateMemory()
                    }
                ) { Text("Clear memory") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearMemory = false }) { Text("Keep it") }
            }
        )
    }

    if (showSharePreview && shareCard != null) {
        AlertDialog(
            onDismissRequest = { showSharePreview = false },
            title = { Text("Review before sharing") },
            text = { Text(shareCard.preview + "\n\nThis share excludes the product name and private history.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSharePreview = false
                        onShare(shareCard)
                    }
                ) { Text("Share") }
            },
            dismissButton = {
                TextButton(onClick = { showSharePreview = false }) { Text("Cancel") }
            }
        )
    }

    state.barcodeChoice?.let { choice ->
        var selectedOptionIndex by rememberSaveable(choice.requestId) {
            mutableIntStateOf(if (choice.options.size == 1) 0 else -1)
        }
        AlertDialog(
            onDismissRequest = { onDismissBarcodeChoice(choice.requestId) },
            title = { Text(stringResource(R.string.good_price_barcode_match_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)
                ) {
                    Text(
                        if (choice.replacesDraft) {
                            stringResource(R.string.good_price_barcode_replace_warning)
                        } else {
                            "Choose the exact catalog name that matches the package. This does not supply a package quantity or price."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    choice.options.forEachIndexed { index, option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedOptionIndex = index }
                                .padding(vertical = ValuePilotSpacing.xSmall),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedOptionIndex == index,
                                onClick = { selectedOptionIndex = index }
                            )
                            Text(
                                option.label,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = selectedOptionIndex >= 0,
                    onClick = {
                        onChooseBarcodeOption(choice.requestId, selectedOptionIndex)
                    }
                ) {
                    Text(
                        stringResource(
                            if (choice.replacesDraft) {
                                R.string.good_price_barcode_replace_draft
                            } else {
                                R.string.good_price_barcode_use_name
                            }
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { onDismissBarcodeChoice(choice.requestId) }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    PremiumColumn {
        item(key = "good-price-intro") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)) {
                Eyebrow("A PERSONAL PRICE CHECK")
                Text(
                    "What did this pack cost?",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Add one product, the exact package quantity, and the price you saw. ValuePilot keeps the calculation grounded in those details.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item(key = "good-price-input") {
            OutlinedTextField(
                value = state.rawBlock,
                onValueChange = { onDraftChanged(it.take(4_096)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(draftFocusRequester),
                label = { Text("Product, package and price") },
                placeholder = { Text(stringResource(R.string.good_price_input_hint)) },
                supportingText = { Text(stringResource(R.string.good_price_input_body)) },
                minLines = 4,
                maxLines = 8,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { runCheck() }),
                shape = MaterialTheme.shapes.large
            )
        }

        item(key = "good-price-price-selection") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)) {
                Text("Which price did you see?", style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.good_price_selection_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)) {
                    FilterChip(
                        modifier = Modifier.fillMaxWidth(),
                        selected = state.priceSelection == CompareHerePriceSelection.CURRENT,
                        onClick = {
                            onPriceSelectionChanged(CompareHerePriceSelection.CURRENT)
                        },
                        label = { Text(stringResource(R.string.price_selection_current)) }
                    )
                    FilterChip(
                        modifier = Modifier.fillMaxWidth(),
                        selected = state.priceSelection == CompareHerePriceSelection.MEMBER,
                        onClick = {
                            onPriceSelectionChanged(CompareHerePriceSelection.MEMBER)
                        },
                        label = { Text(stringResource(R.string.price_selection_member)) }
                    )
                }
            }
        }

        item(key = "good-price-actions") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)) {
                PrimaryActionButton(label = "Check this price", onClick = ::runCheck)
                SecondaryActionButton(label = "Clear draft", onClick = onClearDraft)
                SecondaryActionButton(
                    label = "Scan a barcode",
                    onClick = {
                        focusManager.clearFocus()
                        keyboard?.hide()
                        onScanBarcode()
                    }
                )
                Text(
                    "An exact catalog match suggests an editable name only. Package quantity and price still need your confirmation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (state.barcodeBusy) {
            item(key = "good-price-barcode-progress") {
                LoadingRow(state.barcodeMessage ?: "Looking up an exact catalog name…")
            }
        } else {
            state.barcodeMessage?.let { message ->
                item(key = "good-price-barcode-status") {
                    EvidenceNotice(message, isError = state.barcodeError)
                }
            }
        }

        item(key = "good-price-result") {
            val result = state.route.result
            if (result == null) {
                MessageCard(state.route)
            } else {
                GoodPriceResultCard(result)
            }
        }

        if (shareCard != null) {
            item(key = "good-price-share") {
                SecondaryActionButton(
                    label = "Share this exact calculation",
                    onClick = { showSharePreview = true }
                )
            }
        }

        state.memoryNotice?.let { message ->
            item(key = "good-price-memory-notice") {
                EvidenceNotice(message, isError = state.memoryUnavailable)
            }
        }

        if (state.canClearMemory) {
            item(key = "good-price-clear-memory") {
                TextButton(
                    onClick = { confirmClearMemory = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = ValuePilotSpacing.minimumTouchTarget)
                ) { Text("Clear private price memory", color = MaterialTheme.colorScheme.error) }
            }
        }

        item(key = "good-price-disclosure") {
            EvidenceNotice(
                stringResource(R.string.good_price_privacy)
            )
        }

        item(key = "good-price-back") {
            SecondaryActionButton(label = "Back", onClick = onBack)
        }
    }
}

@Composable
private fun MessageCard(state: GoodPriceCheckRouteState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(ValuePilotSpacing.large),
            verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)
        ) {
            Text(state.title, style = MaterialTheme.typography.titleLarge)
            Text(state.guidance, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun GoodPriceResultCard(state: GoodPriceCheckUiState) {
    val (container, content) =
        when (state.answerTone) {
            GoodPriceCheckAnswerTone.POSITIVE ->
                MaterialTheme.colorScheme.tertiaryContainer to
                    MaterialTheme.colorScheme.onTertiaryContainer
            GoodPriceCheckAnswerTone.NEUTRAL ->
                MaterialTheme.colorScheme.surfaceVariant to
                    MaterialTheme.colorScheme.onSurfaceVariant
            GoodPriceCheckAnswerTone.CAUTION ->
                MaterialTheme.colorScheme.secondaryContainer to
                    MaterialTheme.colorScheme.onSecondaryContainer
        }
    Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)) {
        Text(state.headline, style = MaterialTheme.typography.titleLarge)
        Text(state.priceModeText, style = MaterialTheme.typography.bodySmall)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {
                    contentDescription = com.valuepilot.app.goodPriceAnswerCardContentDescription(state)
                },
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = container),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(ValuePilotSpacing.large),
                verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)
            ) {
                Text(state.answerTitle, style = MaterialTheme.typography.titleLarge, color = content)
                Text(state.productName, style = MaterialTheme.typography.titleMedium, color = content)
                Text("${state.priceText}  ·  ${state.quantityText}", color = content)
                Text(state.unitRateText, style = MaterialTheme.typography.titleLarge, color = content)
                Text(state.answerGuidance, style = MaterialTheme.typography.bodyMedium, color = content)
            }
        }
        state.historyText?.let { history ->
            EvidenceNotice(history)
        }
        Text(
            state.disclosure,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
