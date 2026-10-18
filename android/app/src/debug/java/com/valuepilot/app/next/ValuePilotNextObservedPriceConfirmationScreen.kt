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
    val language = LocalValuePilotNextLanguage.current
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
                    Text(valuePilotNextText("Use date", "Usar fecha"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(valuePilotNextText("Cancel", "Cancelar"))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    PremiumColumn {
        item(key = "observed-price-confirmation-intro") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)) {
                Eyebrow(valuePilotNextText("PERSONAL PRICE MEMORY", "MEMORIA PERSONAL DE PRECIOS"))
                Text(
                    valuePilotNextText("Confirm a price you saw", "Confirmá un precio que viste"),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    valuePilotNextText(
                        "Add the amount, when you saw it, and a receipt or price-tag proof. ValuePilot keeps this as your observation; it is not a retailer-confirmed current offer.",
                        "Agregá el importe, cuándo lo viste y una evidencia de ticket o etiqueta de precio. ValuePilot lo conserva como observación; no es una oferta actual confirmada por un minorista."
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (state == null) {
            item(key = "observed-price-confirmation-loading") {
                LoadingRow(valuePilotNextText("Preparing your selected product and store…", "Preparando tu producto y tienda elegidos…"))
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
                    eyebrow = valuePilotNextText("01 · OBSERVED PRICE", "01 · PRECIO OBSERVADO"),
                    title = valuePilotNextText("What price did you see?", "¿Qué precio viste?"),
                    detail = valuePilotNextText(
                        "Enter the amount and currency exactly as shown. Currency is never inferred from the saved store.",
                        "Ingresá el importe y la moneda exactamente como aparecen. La moneda nunca se infiere de la tienda guardada."
                    )
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
                    state.priceInputError?.let {
                        InputError(valuePilotNextLocalizedDynamicText(language, it).orEmpty())
                    }
                    PrimaryActionButton(
                        label = valuePilotNextText("Set observed price", "Guardar precio observado"),
                        onClick = { onSetPrice(amountText, currencyCodeText) }
                    )
                    DraftFieldStatus(
                        state = state,
                        field = UserObservedPriceConfirmationDraftMissingField.PRICE,
                        readyLabel = valuePilotNextText("Observed price set", "Precio observado guardado")
                    )
                }
            }

            item(key = "observed-price-confirmation-time") {
                ConfirmationSectionCard(
                    eyebrow = valuePilotNextText("02 · OBSERVATION TIME", "02 · HORA DE OBSERVACIÓN"),
                    title = valuePilotNextText("When did you see it?", "¿Cuándo lo viste?"),
                    detail = valuePilotNextText(
                        "Use the factual local date and time, then enter its explicit UTC offset. Your device clock and timezone are not used.",
                        "Usá la fecha y hora local reales e ingresá su desfase UTC explícito. No se usan el reloj ni la zona horaria del dispositivo."
                    )
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
                                label = { Text(valuePilotNextText("Date", "Fecha")) },
                                placeholder = { Text("YYYY-MM-DD") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                                trailingIcon = if (compact) null else {
                                    {
                                        TextButton(onClick = { showDatePicker = true }) {
                                            Text(valuePilotNextText("Choose", "Elegir"))
                                        }
                                    }
                                }
                            )
                            if (compact) {
                                TextButton(
                                    onClick = { showDatePicker = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(valuePilotNextText("Choose date from calendar", "Elegir fecha del calendario"))
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
                        valuePilotNextText(
                            "Accepted time formats: HH:MM or HH:MM:SS. Offset examples: -03:00 or Z.",
                            "Formatos de hora aceptados: HH:MM o HH:MM:SS. Ejemplos de desfase: -03:00 o Z."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    state.observedAtInputError?.let {
                        InputError(valuePilotNextLocalizedDynamicText(language, it).orEmpty())
                    }
                    PrimaryActionButton(
                        label = valuePilotNextText("Set observation time", "Guardar hora de observación"),
                        onClick = { onSetObservedAt(dateText, timeText, utcOffsetText) }
                    )
                    DraftFieldStatus(
                        state = state,
                        field = UserObservedPriceConfirmationDraftMissingField.OBSERVED_AT,
                        readyLabel = valuePilotNextText("Observation time set", "Hora de observación guardada")
                    )
                }
            }

            item(key = "observed-price-confirmation-proof") {
                ConfirmationSectionCard(
                    eyebrow = valuePilotNextText("03 · PROOF", "03 · EVIDENCIA"),
                    title = valuePilotNextText("Attach your receipt or price tag", "Adjuntá tu ticket o etiqueta de precio"),
                    detail = valuePilotNextText(
                        "Give this proof a reference label and choose its type. The file is read only after you select it and stays temporary until you confirm.",
                        "Dale una referencia a esta evidencia y elegí su tipo. El archivo se lee solo después de seleccionarlo y permanece temporal hasta que confirmes."
                    )
                ) {
                    OutlinedTextField(
                        value = artifactIdText,
                        onValueChange = { artifactIdText = it.take(160) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(valuePilotNextText("Proof reference", "Referencia de evidencia")) },
                        placeholder = { Text(valuePilotNextText("For example, receipt-sept-1", "Ejemplo: ticket-sept-1")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)) {
                        FilterChip(
                            selected = proofTypeName == UserProvidedPriceProofType.RECEIPT.name,
                            onClick = { proofTypeName = UserProvidedPriceProofType.RECEIPT.name },
                            label = { Text(valuePilotNextText("Receipt", "Ticket")) }
                        )
                        FilterChip(
                            selected = proofTypeName == UserProvidedPriceProofType.PRICE_TAG.name,
                            onClick = { proofTypeName = UserProvidedPriceProofType.PRICE_TAG.name },
                            label = { Text(valuePilotNextText("Price tag", "Etiqueta de precio")) }
                        )
                    }
                    state.proofReferenceInputError?.let {
                        InputError(valuePilotNextLocalizedDynamicText(language, it).orEmpty())
                    }
                    PrimaryActionButton(
                        label = valuePilotNextText("Set proof details", "Guardar datos de evidencia"),
                        onClick = {
                            val selected = UserProvidedPriceProofType.entries
                                .firstOrNull { it.name == proofTypeName }
                            onSetProofReference(artifactIdText, selected)
                        }
                    )
                    DraftFieldStatus(
                        state = state,
                        field = UserObservedPriceConfirmationDraftMissingField.ARTIFACT_ID,
                        readyLabel = valuePilotNextText("Proof details set", "Datos de evidencia guardados")
                    )
                    DraftFieldStatus(
                        state = state,
                        field = UserObservedPriceConfirmationDraftMissingField.PROOF_TYPE,
                        readyLabel = valuePilotNextText("Proof type set", "Tipo de evidencia guardado")
                    )
                    ProofSelectionStatus(state.proofPresentation)
                    SecondaryActionButton(
                        label = valuePilotNextText("Choose receipt or price-tag file", "Elegir archivo de ticket o etiqueta"),
                        onClick = onSelectProof
                    )
                }
            }

            item(key = "observed-price-confirmation-submit") {
                ConfirmationSectionCard(
                    eyebrow = valuePilotNextText("FINAL CHECK", "COMPROBACIÓN FINAL"),
                    title = state.draft?.statusTitle?.let {
                        valuePilotNextLocalizedDynamicText(language, it).orEmpty()
                    } ?: valuePilotNextText("Confirmation details needed", "Faltan datos de confirmación"),
                    detail = state.draft?.guidance?.let {
                        valuePilotNextLocalizedDynamicText(language, it).orEmpty()
                    } ?: valuePilotNextText("Set the observed price, observation time, and proof details above.", "Guardá arriba el precio observado, la hora de observación y los datos de evidencia."),
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
                                        if (language == ValuePilotNextLanguage.SPANISH_AR) {
                                            "• Falta " + valuePilotNextLocalizedDynamicText(language, requirement.label).orEmpty()
                                        } else {
                                            "• " + requirement.label + " needed"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    Text(
                        valuePilotNextLocalizedDynamicText(language, state.action.message).orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.action.status == UserObservedPriceConfirmationActionUiState.Status.CONFIRMED) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    PrimaryActionButton(
                        label = valuePilotNextLocalizedDynamicText(language, state.action.actionLabel).orEmpty(),
                        onClick = onSubmit,
                        enabled = state.submissionFieldsReady &&
                            state.proofPresentation is UserObservedPriceConfirmationDraftProofContentSelectionPresentation.Ready &&
                            state.action.actionEnabled
                    )
                    Text(
                        state.draft?.notice?.let {
                            valuePilotNextLocalizedDynamicText(language, it).orEmpty()
                        } ?: valuePilotNextText(
                                "Draft completeness does not mean the confirmation is accepted. Observed prices are not retailer-confirmed current prices.",
                                "Completar el borrador no significa que la confirmación haya sido aceptada. Los precios observados no son precios actuales confirmados por minoristas."
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item(key = "observed-price-confirmation-back") {
                SecondaryActionButton(label = valuePilotNextText("Back to saved choices", "Volver a elecciones guardadas"), onClick = onBack)
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
        label = { Text(valuePilotNextText("Amount", "Importe")) },
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
        label = { Text(valuePilotNextText("Currency", "Moneda")) },
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
        label = { Text(valuePilotNextText("Local time", "Hora local")) },
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
        label = { Text(valuePilotNextText("UTC offset", "Desfase UTC")) },
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
            Eyebrow(valuePilotNextText("SAVED PRODUCT · EXACT STORE", "PRODUCTO GUARDADO · TIENDA EXACTA"))
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
        if (missing) valuePilotNextText("Not set yet", "Todavía no guardado") else readyLabel,
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
            valuePilotNextText("Proof file not selected", "No seleccionaste un archivo de evidencia")
        UserObservedPriceConfirmationDraftProofContentSelectionPresentation.AwaitingSelection ->
            valuePilotNextText("Choose an image or PDF to attach as supporting proof.", "Elegí una imagen o PDF para adjuntar como evidencia.")
        is UserObservedPriceConfirmationDraftProofContentSelectionPresentation.Ready ->
            valuePilotNextText("Proof selected · ${presentation.byteLength} bytes held temporarily while this draft is open.", "Evidencia seleccionada · ${presentation.byteLength} bytes retenidos temporalmente mientras el borrador está abierto.")
        is UserObservedPriceConfirmationDraftProofContentSelectionPresentation.Rejected ->
            when (presentation.issue) {
                com.valuepilot.app.UserObservedPriceProofContentReadIssue.UNSUPPORTED_URI -> valuePilotNextText("This file location is not supported. Choose a local image or PDF.", "Esta ubicación no es compatible. Elegí una imagen o PDF local.")
                com.valuepilot.app.UserObservedPriceProofContentReadIssue.SOURCE_UNAVAILABLE -> valuePilotNextText("The selected file could not be opened. Choose another file.", "No se pudo abrir el archivo seleccionado. Elegí otro.")
                com.valuepilot.app.UserObservedPriceProofContentReadIssue.EMPTY_CONTENT -> valuePilotNextText("The selected file is empty. Choose another file.", "El archivo seleccionado está vacío. Elegí otro.")
                com.valuepilot.app.UserObservedPriceProofContentReadIssue.INPUT_TOO_LARGE -> valuePilotNextText("The selected file is too large. Choose a smaller file.", "El archivo seleccionado es demasiado grande. Elegí uno más pequeño.")
                com.valuepilot.app.UserObservedPriceProofContentReadIssue.READ_FAILED -> valuePilotNextText("The selected file could not be read. Choose another file.", "No se pudo leer el archivo seleccionado. Elegí otro.")
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
