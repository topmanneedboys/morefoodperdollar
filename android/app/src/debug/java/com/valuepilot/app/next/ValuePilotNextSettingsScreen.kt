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
                Eyebrow("YOUR EXPERIENCE")
                Text(
                    "Make ValuePilot feel right for you.",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Choose an appearance, motion level, and display density. These preferences affect the Compose experience only.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item(key = "settings-appearance") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)) {
                SectionHeading("APPEARANCE", "Choose your color scheme")
                Column(
                    modifier = Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)
                ) {
                    SettingChoiceRow(
                        title = "Follow device",
                        detail = "Use your Android light or dark setting.",
                        selected = preferences.appearance == ValuePilotNextAppearance.SYSTEM,
                        onClick = { onAppearanceSelected(ValuePilotNextAppearance.SYSTEM) }
                    )
                    SettingChoiceRow(
                        title = "Light",
                        detail = "Use the warm, bright ValuePilot palette.",
                        selected = preferences.appearance == ValuePilotNextAppearance.LIGHT,
                        onClick = { onAppearanceSelected(ValuePilotNextAppearance.LIGHT) }
                    )
                    SettingChoiceRow(
                        title = "Dark",
                        detail = "Use a low-light palette with the same price contrast.",
                        selected = preferences.appearance == ValuePilotNextAppearance.DARK,
                        onClick = { onAppearanceSelected(ValuePilotNextAppearance.DARK) }
                    )
                }
            }
        }

        item(key = "settings-motion") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)) {
                SectionHeading("MOTION", "Keep transitions comfortable")
                Column(
                    modifier = Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)
                ) {
                    SettingChoiceRow(
                        title = "Follow device",
                        detail = "Respect Android's animation setting.",
                        selected = preferences.motion == ValuePilotNextMotionPreference.SYSTEM,
                        onClick = { onMotionSelected(ValuePilotNextMotionPreference.SYSTEM) }
                    )
                    SettingChoiceRow(
                        title = "Reduce motion",
                        detail = "Use short fades without sliding transitions.",
                        selected = preferences.motion == ValuePilotNextMotionPreference.REDUCED,
                        onClick = { onMotionSelected(ValuePilotNextMotionPreference.REDUCED) }
                    )
                    SettingChoiceRow(
                        title = "Full motion",
                        detail = "Use the standard, brief screen transitions.",
                        selected = preferences.motion == ValuePilotNextMotionPreference.FULL,
                        onClick = { onMotionSelected(ValuePilotNextMotionPreference.FULL) }
                    )
                }
            }
        }

        item(key = "settings-density") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)) {
                SectionHeading("DISPLAY DENSITY", "Choose how much fits on screen")
                Column(
                    modifier = Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)
                ) {
                    SettingChoiceRow(
                        title = "Comfortable",
                        detail = "Keep the current generous spacing.",
                        selected = preferences.density == ValuePilotNextDensity.COMFORTABLE,
                        onClick = { onDensitySelected(ValuePilotNextDensity.COMFORTABLE) }
                    )
                    SettingChoiceRow(
                        title = "Compact",
                        detail = "Fit more cards while keeping touch targets comfortable.",
                        selected = preferences.density == ValuePilotNextDensity.COMPACT,
                        onClick = { onDensitySelected(ValuePilotNextDensity.COMPACT) }
                    )
                }
            }
        }

        item(key = "settings-language") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)) {
                SectionHeading("LANGUAGE", "Choose your ValuePilot language")
                Column(
                    modifier = Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)
                ) {
                    SettingChoiceRow(
                        title = "Follow device",
                        detail = "Use Spanish on Spanish devices and English otherwise.",
                        selected = preferences.language == ValuePilotNextLanguage.SYSTEM,
                        onClick = { onLanguageSelected(ValuePilotNextLanguage.SYSTEM) }
                    )
                    SettingChoiceRow(
                        title = "English",
                        detail = "Use English labels while keeping Argentina and ARS.",
                        selected = preferences.language == ValuePilotNextLanguage.ENGLISH,
                        onClick = { onLanguageSelected(ValuePilotNextLanguage.ENGLISH) }
                    )
                    SettingChoiceRow(
                        title = "Español (Argentina)",
                        detail = "Usar español argentino con Argentina y ARS.",
                        selected = preferences.language == ValuePilotNextLanguage.SPANISH_AR,
                        onClick = { onLanguageSelected(ValuePilotNextLanguage.SPANISH_AR) }
                    )
                }
            }
        }

        if (shouldOfferPresentationPreferenceReset(preferences)) {
            item(key = "settings-reset-preferences") {
                TextButton(
                    onClick = onResetPreferences,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = ValuePilotSpacing.minimumTouchTarget)
                        .semantics {
                            contentDescription = "Restore default appearance, motion, density, and language"
                        }
                ) {
                    Text("Restore default experience")
                }
            }
        }

        item(key = "settings-accessibility-note") {
            EvidenceFootnote(
                "Text size follows Android display settings. Compact spacing does not shrink touch targets. Shopping prices, saved data, and evidence are not changed here."
            )
        }

        item(key = "settings-back") {
            SecondaryActionButton("Back", onBack)
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
