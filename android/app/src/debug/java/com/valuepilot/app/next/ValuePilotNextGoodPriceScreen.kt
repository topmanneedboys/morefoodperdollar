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
    val language = LocalValuePilotNextLanguage.current
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
            title = { Text(valuePilotNextText("Clear private price memory?", "¿Borrar la memoria privada de precios?")) },
            text = {
                Text(valuePilotNextText(
                    "This clears the comparison observations stored on this device and removes the current answer from view.",
                    "Esto borra las observaciones de comparación guardadas en este dispositivo y quita la respuesta actual de la vista."
                ))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearMemory = false
                        onClearPrivateMemory()
                    }
                ) { Text(valuePilotNextText("Clear memory", "Borrar memoria")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearMemory = false }) { Text(valuePilotNextText("Keep it", "Conservarla")) }
            }
        )
    }

    if (showSharePreview && shareCard != null) {
        AlertDialog(
            onDismissRequest = { showSharePreview = false },
            title = { Text(valuePilotNextText("Review before sharing", "Revisá antes de compartir")) },
            text = { Text(shareCard.preview + "\n\n" + valuePilotNextText("This share excludes the product name and private history.", "Este envío excluye el nombre del producto y el historial privado.")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSharePreview = false
                        onShare(shareCard)
                    }
                ) { Text(valuePilotNextText("Share", "Compartir")) }
            },
            dismissButton = {
                TextButton(onClick = { showSharePreview = false }) { Text(valuePilotNextText("Cancel", "Cancelar")) }
            }
        )
    }

    state.barcodeChoice?.let { choice ->
        var selectedOptionIndex by rememberSaveable(choice.requestId) {
            mutableIntStateOf(if (choice.options.size == 1) 0 else -1)
        }
        AlertDialog(
            onDismissRequest = { onDismissBarcodeChoice(choice.requestId) },
            title = { Text(valuePilotNextText("Identity suggestion from barcode", "Sugerencia de identidad desde el código de barras")) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)
                ) {
                    Text(
                        if (choice.replacesDraft) {
                            valuePilotNextText(
                                "Choosing a name will replace your current price-check draft, including any package or price details.",
                                "Elegir un nombre reemplazará tu borrador actual de control de precio, incluidos los datos de envase o precio."
                            )
                        } else {
                            valuePilotNextText(
                                "Choose the exact catalog name that matches the package. This does not supply a package quantity or price.",
                                "Elegí el nombre exacto del catálogo que coincide con el envase. Esto no aporta cantidad de envase ni precio."
                            )
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
                        if (choice.replacesDraft) {
                            valuePilotNextText("Replace current draft", "Reemplazar borrador actual")
                        } else {
                            valuePilotNextText("Use this name", "Usar este nombre")
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { onDismissBarcodeChoice(choice.requestId) }) {
                    Text(valuePilotNextText("Cancel", "Cancelar"))
                }
            }
        )
    }

    PremiumColumn {
        item(key = "good-price-intro") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)) {
                Eyebrow(valuePilotNextText("A PERSONAL PRICE CHECK", "UN CONTROL DE PRECIO PERSONAL"))
                Text(
                    valuePilotNextText("What did this pack cost?", "¿Cuánto viste por este envase?"),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    valuePilotNextText(
                        "Add one product, the exact package quantity, and the price you saw. ValuePilot keeps the calculation grounded in those details.",
                        "Agregá un producto, la cantidad exacta del envase y el precio que viste. ValuePilot mantiene el cálculo basado en esos datos."
                    ),
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
                label = { Text(valuePilotNextText("Product, package and price", "Producto, envase y precio")) },
                placeholder = { Text(valuePilotNextText("Product name\nExact package quantity\nObserved price + currency", "Nombre del producto\nCantidad exacta del envase\nPrecio observado + moneda")) },
                supportingText = {
                    Text(
                        valuePilotNextText(
                            "Use one line each for the product, exact package quantity, and price with currency. This is your observation, not a live store offer.",
                            "Usá una línea para el producto, otra para la cantidad exacta del envase y otra para el precio con moneda. Es tu observación, no una oferta actual de tienda."
                        )
                    )
                },
                minLines = 3,
                maxLines = 6,
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
                Text(valuePilotNextText("Which price did you see?", "¿Qué precio viste?"), style = MaterialTheme.typography.titleMedium)
                Text(
                    valuePilotNextText(
                        "Choose whether this is a regular shelf price or a member price. A missing member price stays unknown; ValuePilot never substitutes a current price.",
                        "Elegí si es un precio regular de góndola o de socio. Un precio de socio faltante queda desconocido; ValuePilot nunca reemplaza por un precio actual."
                    ),
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
                        label = { Text(valuePilotNextText("Current shelf prices", "Precios actuales de góndola")) }
                    )
                    FilterChip(
                        modifier = Modifier.fillMaxWidth(),
                        selected = state.priceSelection == CompareHerePriceSelection.MEMBER,
                        onClick = {
                            onPriceSelectionChanged(CompareHerePriceSelection.MEMBER)
                        },
                        label = { Text(valuePilotNextText("Member prices", "Precios de socio")) }
                    )
                }
            }
        }

        item(key = "good-price-actions") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)) {
                PrimaryActionButton(label = valuePilotNextText("Check this price", "Comprobar este precio"), onClick = ::runCheck)
                SecondaryActionButton(label = valuePilotNextText("Clear draft", "Borrar borrador"), onClick = onClearDraft)
                SecondaryActionButton(
                    label = valuePilotNextText("Scan a barcode", "Escanear código de barras"),
                    onClick = {
                        focusManager.clearFocus()
                        keyboard?.hide()
                        onScanBarcode()
                    }
                )
                Text(
                    valuePilotNextText(
                        "An exact catalog match suggests an editable name only. Package quantity and price still need your confirmation.",
                        "Una coincidencia exacta de catálogo solo sugiere un nombre editable. La cantidad del envase y el precio todavía necesitan tu confirmación."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (state.barcodeBusy) {
            item(key = "good-price-barcode-progress") {
                LoadingRow(
                    state.barcodeMessage?.let {
                        valuePilotNextLocalizedDynamicText(language, it).orEmpty()
                    } ?: valuePilotNextText(
                        "Looking up an exact catalog name…",
                        "Buscando un nombre exacto de catálogo…"
                    )
                )
            }
        } else {
            state.barcodeMessage?.let { message ->
                item(key = "good-price-barcode-status") {
                    EvidenceNotice(
                        valuePilotNextLocalizedDynamicText(language, message).orEmpty(),
                        isError = state.barcodeError
                    )
                }
            }
        }

        item(key = "good-price-result") {
            val result = state.route.result
            if (result == null) {
                MessageCard(language, state.route)
            } else {
                GoodPriceResultCard(language, result)
            }
        }

        if (shareCard != null) {
            item(key = "good-price-share") {
                SecondaryActionButton(
                    label = valuePilotNextText("Share this exact calculation", "Compartir este cálculo exacto"),
                    onClick = { showSharePreview = true }
                )
            }
        }

        state.memoryNotice?.let { message ->
            item(key = "good-price-memory-notice") {
                EvidenceNotice(
                    valuePilotNextLocalizedDynamicText(language, message).orEmpty(),
                    isError = state.memoryUnavailable
                )
            }
        }

        if (state.canClearMemory) {
            item(key = "good-price-clear-memory") {
                TextButton(
                    onClick = { confirmClearMemory = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = ValuePilotSpacing.minimumTouchTarget)
                ) { Text(valuePilotNextText("Clear private price memory", "Borrar memoria privada de precios"), color = MaterialTheme.colorScheme.error) }
            }
        }

        item(key = "good-price-disclosure") {
            EvidenceNotice(
                valuePilotNextText(
                    "Your check and any remembered observation stay on this device. No account or network connection is required.",
                    "Tu control y cualquier observación recordada quedan en este dispositivo. No se necesita cuenta ni conexión de red."
                )
            )
        }

        item(key = "good-price-back") {
            SecondaryActionButton(label = valuePilotNextText("Back", "Volver"), onClick = onBack)
        }
    }
}

@Composable
private fun MessageCard(language: ValuePilotNextLanguage, state: GoodPriceCheckRouteState) {
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
            Text(
                valuePilotNextLocalizedDynamicText(language, state.title).orEmpty(),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                valuePilotNextLocalizedDynamicText(language, state.guidance).orEmpty(),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun GoodPriceResultCard(language: ValuePilotNextLanguage, state: GoodPriceCheckUiState) {
    val headline = valuePilotNextLocalizedDynamicText(language, state.headline).orEmpty()
    val priceModeText = valuePilotNextLocalizedDynamicText(language, state.priceModeText).orEmpty()
    val answerTitle = valuePilotNextLocalizedDynamicText(language, state.answerTitle).orEmpty()
    val answerGuidance = valuePilotNextLocalizedDynamicText(language, state.answerGuidance).orEmpty()
    val historyText = valuePilotNextLocalizedDynamicText(language, state.historyText)
    val disclosure = valuePilotNextLocalizedDynamicText(language, state.disclosure).orEmpty()
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
        Text(headline, style = MaterialTheme.typography.titleLarge)
        Text(priceModeText, style = MaterialTheme.typography.bodySmall)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {
                    contentDescription =
                        listOf(
                                priceModeText,
                                answerTitle,
                                state.productName,
                                state.priceText,
                                state.quantityText,
                                state.unitRateText,
                                answerGuidance
                            )
                            .joinToString(". ") { value ->
                                value.trim().trimEnd('.', '!', '?')
                            } + "."
                },
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = container),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(ValuePilotSpacing.large),
                verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)
            ) {
                Text(answerTitle, style = MaterialTheme.typography.titleLarge, color = content)
                Text(state.productName, style = MaterialTheme.typography.titleMedium, color = content)
                Text("${state.priceText}  ·  ${state.quantityText}", color = content)
                Text(state.unitRateText, style = MaterialTheme.typography.titleLarge, color = content)
                Text(answerGuidance, style = MaterialTheme.typography.bodyMedium, color = content)
            }
        }
        historyText?.let { history ->
            EvidenceNotice(history)
        }
        Text(
            disclosure,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
