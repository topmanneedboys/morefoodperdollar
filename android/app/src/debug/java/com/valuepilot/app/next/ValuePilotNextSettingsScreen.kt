package com.valuepilot.app.next

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
internal fun ValuePilotNextSettingsScreen(
    preferences: ValuePilotNextUiPreferences,
    onAppearanceSelected: (ValuePilotNextAppearance) -> Unit,
    onMotionSelected: (ValuePilotNextMotionPreference) -> Unit,
    onDensitySelected: (ValuePilotNextDensity) -> Unit,
    onLanguageSelected: (ValuePilotNextLanguage) -> Unit,
    onResetPreferences: () -> Unit,
    onBack: () -> Unit
) {
    PremiumColumn {
        item(key = "settings-intro") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)) {
                Eyebrow(valuePilotNextText("YOUR EXPERIENCE", "TU EXPERIENCIA"))
                Text(
                    valuePilotNextText("Make ValuePilot feel right for you.", "Hacé que ValuePilot se sienta tuyo."),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    valuePilotNextText(
                        "Choose an appearance, motion level, and display density. These preferences affect the Compose experience only.",
                        "Elegí apariencia, movimiento y densidad de pantalla. Estas preferencias afectan solo la experiencia Compose."
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item(key = "settings-appearance") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)) {
                SectionHeading(valuePilotNextText("APPEARANCE", "APARIENCIA"), valuePilotNextText("Choose your color scheme", "Elegí tu esquema de colores"))
                Column(
                    modifier = Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)
                ) {
                    SettingChoiceRow(
                        title = valuePilotNextText("Follow device", "Seguir dispositivo"),
                        detail = valuePilotNextText("Use your Android light or dark setting.", "Usar la configuración clara u oscura de Android."),
                        selected = preferences.appearance == ValuePilotNextAppearance.SYSTEM,
                        onClick = { onAppearanceSelected(ValuePilotNextAppearance.SYSTEM) }
                    )
                    SettingChoiceRow(
                        title = valuePilotNextText("Light", "Claro"),
                        detail = valuePilotNextText("Use the warm, bright ValuePilot palette.", "Usar la paleta cálida y luminosa de ValuePilot."),
                        selected = preferences.appearance == ValuePilotNextAppearance.LIGHT,
                        onClick = { onAppearanceSelected(ValuePilotNextAppearance.LIGHT) }
                    )
                    SettingChoiceRow(
                        title = valuePilotNextText("Dark", "Oscuro"),
                        detail = valuePilotNextText("Use a low-light palette with the same price contrast.", "Usar una paleta de baja luz con el mismo contraste de precios."),
                        selected = preferences.appearance == ValuePilotNextAppearance.DARK,
                        onClick = { onAppearanceSelected(ValuePilotNextAppearance.DARK) }
                    )
                }
            }
        }

        item(key = "settings-motion") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)) {
                SectionHeading(valuePilotNextText("MOTION", "MOVIMIENTO"), valuePilotNextText("Keep transitions comfortable", "Mantené cómodas las transiciones"))
                Column(
                    modifier = Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)
                ) {
                    SettingChoiceRow(
                        title = valuePilotNextText("Follow device", "Seguir dispositivo"),
                        detail = valuePilotNextText("Respect Android's animation setting.", "Respetar la configuración de animaciones de Android."),
                        selected = preferences.motion == ValuePilotNextMotionPreference.SYSTEM,
                        onClick = { onMotionSelected(ValuePilotNextMotionPreference.SYSTEM) }
                    )
                    SettingChoiceRow(
                        title = valuePilotNextText("Reduce motion", "Reducir movimiento"),
                        detail = valuePilotNextText("Use short fades without sliding transitions.", "Usar fundidos breves sin transiciones deslizantes."),
                        selected = preferences.motion == ValuePilotNextMotionPreference.REDUCED,
                        onClick = { onMotionSelected(ValuePilotNextMotionPreference.REDUCED) }
                    )
                    SettingChoiceRow(
                        title = valuePilotNextText("Full motion", "Movimiento completo"),
                        detail = valuePilotNextText("Use the standard, brief screen transitions.", "Usar las transiciones de pantalla breves estándar."),
                        selected = preferences.motion == ValuePilotNextMotionPreference.FULL,
                        onClick = { onMotionSelected(ValuePilotNextMotionPreference.FULL) }
                    )
                }
            }
        }

        item(key = "settings-density") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)) {
                SectionHeading(valuePilotNextText("DISPLAY DENSITY", "DENSIDAD DE PANTALLA"), valuePilotNextText("Choose how much fits on screen", "Elegí cuánto entra en pantalla"))
                Column(
                    modifier = Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)
                ) {
                    SettingChoiceRow(
                        title = valuePilotNextText("Comfortable", "Cómoda"),
                        detail = valuePilotNextText("Keep the current generous spacing.", "Mantener el espaciado generoso actual."),
                        selected = preferences.density == ValuePilotNextDensity.COMFORTABLE,
                        onClick = { onDensitySelected(ValuePilotNextDensity.COMFORTABLE) }
                    )
                    SettingChoiceRow(
                        title = valuePilotNextText("Compact", "Compacta"),
                        detail = valuePilotNextText("Fit more cards while keeping touch targets comfortable.", "Mostrar más tarjetas sin reducir los objetivos táctiles."),
                        selected = preferences.density == ValuePilotNextDensity.COMPACT,
                        onClick = { onDensitySelected(ValuePilotNextDensity.COMPACT) }
                    )
                }
            }
        }

        item(key = "settings-language") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)) {
                SectionHeading(valuePilotNextText("LANGUAGE", "IDIOMA"), valuePilotNextText("Choose your ValuePilot language", "Elegí el idioma de ValuePilot"))
                Column(
                    modifier = Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)
                ) {
                    SettingChoiceRow(
                        title = valuePilotNextText("Follow device", "Seguir dispositivo"),
                        detail = valuePilotNextText("Use Spanish on Spanish devices and English otherwise.", "Usar español en dispositivos en español e inglés en los demás."),
                        selected = preferences.language == ValuePilotNextLanguage.SYSTEM,
                        onClick = { onLanguageSelected(ValuePilotNextLanguage.SYSTEM) }
                    )
                    SettingChoiceRow(
                        title = valuePilotNextText("English", "English"),
                        detail = valuePilotNextText("Use English labels while keeping Argentina and ARS.", "Usar etiquetas en inglés manteniendo Argentina y ARS."),
                        selected = preferences.language == ValuePilotNextLanguage.ENGLISH,
                        onClick = { onLanguageSelected(ValuePilotNextLanguage.ENGLISH) }
                    )
                    SettingChoiceRow(
                        title = valuePilotNextText("Spanish (Argentina)", "Español (Argentina)"),
                        detail = valuePilotNextText("Use Argentine Spanish while keeping Argentina and ARS.", "Usar español argentino con Argentina y ARS."),
                        selected = preferences.language == ValuePilotNextLanguage.SPANISH_AR,
                        onClick = { onLanguageSelected(ValuePilotNextLanguage.SPANISH_AR) }
                    )
                }
            }
        }

        if (shouldOfferPresentationPreferenceReset(preferences)) {
            item(key = "settings-reset-preferences") {
                val resetDescription = valuePilotNextText(
                    "Restore default appearance, motion, density, and language",
                    "Restaurar apariencia, movimiento, densidad e idioma predeterminados"
                )
                TextButton(
                    onClick = onResetPreferences,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = ValuePilotSpacing.minimumTouchTarget)
                        .semantics {
                            contentDescription = resetDescription
                        }
                ) {
                    Text(valuePilotNextText("Restore default experience", "Restaurar experiencia predeterminada"))
                }
            }
        }

        item(key = "settings-accessibility-note") {
            EvidenceFootnote(
                valuePilotNextText(
                    "Text size follows Android display settings. Compact spacing does not shrink touch targets. Shopping prices, saved data, and evidence are not changed here.",
                    "El tamaño del texto sigue la configuración de pantalla de Android. El espaciado compacto no reduce los objetivos táctiles. Acá no cambian precios, datos guardados ni evidencia."
                )
            )
        }

        item(key = "settings-back") {
                SecondaryActionButton(valuePilotNextText("Back", "Volver"), onBack)
        }
    }
}

@Composable
private fun SettingChoiceRow(
    title: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = MaterialTheme.shapes.large
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ValuePilotSpacing.minimumTouchTarget)
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.surface
            )
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.48f)
                else MaterialTheme.colorScheme.outlineVariant,
                shape = shape
            )
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(horizontal = ValuePilotSpacing.medium, vertical = ValuePilotSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        RadioButton(selected = selected, onClick = null)
    }
}
