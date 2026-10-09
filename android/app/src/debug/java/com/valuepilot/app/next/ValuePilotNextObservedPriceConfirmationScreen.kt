package com.valuepilot.app.next

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.valuepilot.app.UserObservedPriceConfirmationActionUiState
import com.valuepilot.app.UserObservedPriceConfirmationDraftMissingField
import com.valuepilot.app.UserObservedPriceConfirmationDraftProofContentSelectionPresentation
import com.valuepilot.app.UserProvidedPriceProofType

internal fun usesCompactObservedPriceConfirmationFields(
    availableWidth: Dp,
    fontScale: Float
): Boolean = availableWidth < 420.dp || fontScale >= 1.2f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ValuePilotNextObservedPriceConfirmationScreen(
    state: ValuePilotNextObservedPriceConfirmationUiState?,
    onSetPrice: (String, String) -> Unit,
    onSetObservedAt: (String, String, String) -> Unit,
    onSetProofReference: (String, UserProvidedPriceProofType?) -> Unit,
    onSelectProof: () -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit
) {
    var amountText by rememberSaveable { mutableStateOf("") }
    var currencyCodeText by rememberSaveable { mutableStateOf("") }
    var dateText by rememberSaveable { mutableStateOf("") }
    var timeText by rememberSaveable { mutableStateOf("") }
    var utcOffsetText by rememberSaveable { mutableStateOf("") }
    var artifactIdText by rememberSaveable { mutableStateOf("") }
    var proofTypeName by rememberSaveable { mutableStateOf("") }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    if (showDatePicker) {
        val datePickerState = key(dateText) {
            rememberDatePickerState(
                initialSelectedDateMillis = ValuePilotObservedDatePickerFormat.toEpochMillis(dateText)
            )
        }
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedDate ->
                            dateText = ValuePilotObservedDatePickerFormat.fromEpochMillis(selectedDate)
                        }
                        showDatePicker = false
                    },
                    enabled = datePickerState.selectedDateMillis != null
                ) {
                    Text("Use date")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    PremiumColumn {
        item(key = "observed-price-confirmation-intro") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)) {
                Eyebrow("PERSONAL PRICE MEMORY")
                Text(
                    "Confirm a price you saw",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Add the amount, when you saw it, and a receipt or price-tag proof. ValuePilot keeps this as your observation; it is not a retailer-confirmed current offer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (state == null) {
            item(key = "observed-price-confirmation-loading") {
                LoadingRow("Preparing your selected product and store…")
            }
        } else {
            item(key = "observed-price-confirmation-identity") {
                ConfirmationIdentityCard(
                    productName = state.productName,
                    storeName = state.storeDisplayName
                )
            }

            item(key = "observed-price-confirmation-price") {
                ConfirmationSectionCard(
                    eyebrow = "01 · OBSERVED PRICE",
                    title = "What price did you see?",
                    detail = "Enter the amount and currency exactly as shown. Currency is never inferred from the saved store."
                ) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val compact = usesCompactObservedPriceConfirmationFields(
                            maxWidth,
                            LocalDensity.current.fontScale
                        )
                        if (compact) {
                            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)) {
                                ObservedPriceAmountField(amountText, { amountText = it }, Modifier.fillMaxWidth())
                                ObservedPriceCurrencyField(
                                    currencyCodeText,
                                    { currencyCodeText = it.uppercase() },
                                    Modifier.fillMaxWidth()
                                )
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)) {
                                ObservedPriceAmountField(amountText, { amountText = it }, Modifier.weight(1f))
                                ObservedPriceCurrencyField(
                                    currencyCodeText,
                                    { currencyCodeText = it.uppercase() },
                                    Modifier.weight(0.72f)
                                )
                            }
                        }
                    }
                    state.priceInputError?.let { InputError(it) }
                    PrimaryActionButton(
                        label = "Set observed price",
                        onClick = { onSetPrice(amountText, currencyCodeText) }
                    )
                    DraftFieldStatus(
                        state = state,
                        field = UserObservedPriceConfirmationDraftMissingField.PRICE,
                        readyLabel = "Observed price set"
                    )
                }
            }

            item(key = "observed-price-confirmation-time") {
                ConfirmationSectionCard(
                    eyebrow = "02 · OBSERVATION TIME",
                    title = "When did you see it?",
                    detail = "Use the factual local date and time, then enter its explicit UTC offset. Your device clock and timezone are not used."
                ) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val compact = usesCompactObservedPriceConfirmationFields(
                            maxWidth,
                            LocalDensity.current.fontScale
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)) {
                            OutlinedTextField(
                                value = dateText,
                                onValueChange = { dateText = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Date") },
                                placeholder = { Text("YYYY-MM-DD") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                                trailingIcon = if (compact) null else {
                                    {
                                        TextButton(onClick = { showDatePicker = true }) {
                                            Text("Choose")
                                        }
                                    }
                                }
                            )
                            if (compact) {
                                TextButton(
                                    onClick = { showDatePicker = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Choose date from calendar")
                                }
                            }
                        }
                    }
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val compact = usesCompactObservedPriceConfirmationFields(
                            maxWidth,
                            LocalDensity.current.fontScale
                        )
                        if (compact) {
                            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)) {
                                ObservedPriceLocalTimeField(timeText, { timeText = it }, Modifier.fillMaxWidth())
                                ObservedPriceUtcOffsetField(
                                    utcOffsetText,
                                    { utcOffsetText = it },
                                    Modifier.fillMaxWidth()
                                )
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)) {
                                ObservedPriceLocalTimeField(timeText, { timeText = it }, Modifier.weight(1f))
                                ObservedPriceUtcOffsetField(
                                    utcOffsetText,
                                    { utcOffsetText = it },
                                    Modifier.weight(0.85f)
                                )
                            }
                        }
                    }
                    Text(
                        "Accepted time formats: HH:MM or HH:MM:SS. Offset examples: -03:00 or Z.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    state.observedAtInputError?.let { InputError(it) }
                    PrimaryActionButton(
                        label = "Set observation time",
                        onClick = { onSetObservedAt(dateText, timeText, utcOffsetText) }
                    )
                    DraftFieldStatus(
                        state = state,
                        field = UserObservedPriceConfirmationDraftMissingField.OBSERVED_AT,
                        readyLabel = "Observation time set"
                    )
                }
            }

            item(key = "observed-price-confirmation-proof") {
                ConfirmationSectionCard(
                    eyebrow = "03 · PROOF",
                    title = "Attach your receipt or price tag",
                    detail = "Give this proof a reference label and choose its type. The file is read only after you select it and stays temporary until you confirm."
                ) {
                    OutlinedTextField(
                        value = artifactIdText,
                        onValueChange = { artifactIdText = it.take(160) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Proof reference") },
                        placeholder = { Text("For example, receipt-sept-1") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)) {
                        FilterChip(
                            selected = proofTypeName == UserProvidedPriceProofType.RECEIPT.name,
                            onClick = { proofTypeName = UserProvidedPriceProofType.RECEIPT.name },
                            label = { Text("Receipt") }
                        )
                        FilterChip(
                            selected = proofTypeName == UserProvidedPriceProofType.PRICE_TAG.name,
                            onClick = { proofTypeName = UserProvidedPriceProofType.PRICE_TAG.name },
                            label = { Text("Price tag") }
                        )
                    }
                    state.proofReferenceInputError?.let { InputError(it) }
                    PrimaryActionButton(
                        label = "Set proof details",
                        onClick = {
                            val selected = UserProvidedPriceProofType.entries
                                .firstOrNull { it.name == proofTypeName }
                            onSetProofReference(artifactIdText, selected)
                        }
                    )
                    DraftFieldStatus(
                        state = state,
                        field = UserObservedPriceConfirmationDraftMissingField.ARTIFACT_ID,
                        readyLabel = "Proof details set"
                    )
                    DraftFieldStatus(
                        state = state,
                        field = UserObservedPriceConfirmationDraftMissingField.PROOF_TYPE,
                        readyLabel = "Proof type set"
                    )
                    ProofSelectionStatus(state.proofPresentation)
                    SecondaryActionButton(
                        label = "Choose receipt or price-tag file",
                        onClick = onSelectProof
                    )
                }
            }

            item(key = "observed-price-confirmation-submit") {
                ConfirmationSectionCard(
                    eyebrow = "FINAL CHECK",
                    title = state.draft?.statusTitle ?: "Confirmation details needed",
                    detail = state.draft?.guidance
                        ?: "Set the observed price, observation time, and proof details above."
                ) {
                    state.draft?.missingRequirements
                        ?.filterNot { it.field == UserObservedPriceConfirmationDraftMissingField.CONFIRMATION_ID ||
                            it.field == UserObservedPriceConfirmationDraftMissingField.CONFIRMED_AT
                        }
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { requirements ->
                            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)) {
                                requirements.forEach { requirement ->
                                    Text(
                                        "• ${requirement.label} needed",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    Text(
                        state.action.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.action.status == UserObservedPriceConfirmationActionUiState.Status.CONFIRMED) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    PrimaryActionButton(
                        label = state.action.actionLabel,
                        onClick = onSubmit,
                        enabled = state.submissionFieldsReady &&
                            state.proofPresentation is UserObservedPriceConfirmationDraftProofContentSelectionPresentation.Ready &&
                            state.action.actionEnabled
                    )
                    Text(
                        state.draft?.notice
                            ?: "Draft completeness does not mean the confirmation is accepted. Observed prices are not retailer-confirmed current prices.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item(key = "observed-price-confirmation-back") {
                SecondaryActionButton(label = "Back to saved choices", onClick = onBack)
            }
        }
    }
}

@Composable
private fun ObservedPriceAmountField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text("Amount") },
        placeholder = { Text("12.50") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

@Composable
private fun ObservedPriceCurrencyField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text("Currency") },
        placeholder = { Text("ARS") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
    )
}

@Composable
private fun ObservedPriceLocalTimeField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text("Local time") },
        placeholder = { Text("HH:MM") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
    )
}

@Composable
private fun ObservedPriceUtcOffsetField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text("UTC offset") },
        placeholder = { Text("-03:00") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
    )
}

@Composable
private fun ConfirmationIdentityCard(productName: String, storeName: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = ValuePilotElevation.flat)
    ) {
        Column(
            modifier = Modifier.padding(ValuePilotSpacing.large),
            verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)
        ) {
            Eyebrow("SAVED PRODUCT · EXACT STORE")
            Text(productName, style = MaterialTheme.typography.titleLarge)
            Text(storeName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ConfirmationSectionCard(
    eyebrow: String,
    title: String,
    detail: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = ValuePilotElevation.flat)
    ) {
        Column(
            modifier = Modifier.padding(ValuePilotSpacing.large),
            verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)
        ) {
            Eyebrow(eyebrow)
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun DraftFieldStatus(
    state: ValuePilotNextObservedPriceConfirmationUiState,
    field: UserObservedPriceConfirmationDraftMissingField,
    readyLabel: String
) {
    val missing = state.draft?.missingRequirements?.any { it.field == field } ?: true
    Text(
        if (missing) "Not set yet" else readyLabel,
        style = MaterialTheme.typography.bodySmall,
        color = if (missing) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun ProofSelectionStatus(
    presentation: UserObservedPriceConfirmationDraftProofContentSelectionPresentation
) {
    val message = when (presentation) {
        UserObservedPriceConfirmationDraftProofContentSelectionPresentation.Inactive ->
            "Proof file not selected"
        UserObservedPriceConfirmationDraftProofContentSelectionPresentation.AwaitingSelection ->
            "Choose an image or PDF to attach as supporting proof."
        is UserObservedPriceConfirmationDraftProofContentSelectionPresentation.Ready ->
            "Proof selected · ${presentation.byteLength} bytes held temporarily while this draft is open."
        is UserObservedPriceConfirmationDraftProofContentSelectionPresentation.Rejected ->
            when (presentation.issue) {
                com.valuepilot.app.UserObservedPriceProofContentReadIssue.UNSUPPORTED_URI -> "This file location is not supported. Choose a local image or PDF."
                com.valuepilot.app.UserObservedPriceProofContentReadIssue.SOURCE_UNAVAILABLE -> "The selected file could not be opened. Choose another file."
                com.valuepilot.app.UserObservedPriceProofContentReadIssue.EMPTY_CONTENT -> "The selected file is empty. Choose another file."
                com.valuepilot.app.UserObservedPriceProofContentReadIssue.INPUT_TOO_LARGE -> "The selected file is too large. Choose a smaller file."
                com.valuepilot.app.UserObservedPriceProofContentReadIssue.READ_FAILED -> "The selected file could not be read. Choose another file."
            }
    }
    EvidenceNotice(
        text = message,
        isError = presentation is UserObservedPriceConfirmationDraftProofContentSelectionPresentation.Rejected
    )
}

@Composable
private fun InputError(message: String) {
    Text(
        message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}
