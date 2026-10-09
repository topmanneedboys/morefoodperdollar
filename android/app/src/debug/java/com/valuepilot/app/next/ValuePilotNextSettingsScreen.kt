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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
internal fun ValuePilotNextSettingsScreen(
    preferences: ValuePilotNextUiPreferences,
    onAppearanceSelected: (ValuePilotNextAppearance) -> Unit,
    onMotionSelected: (ValuePilotNextMotionPreference) -> Unit,
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
                    "Choose an appearance and motion level. These preferences affect the Compose experience only.",
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

        item(key = "settings-accessibility-note") {
            EvidenceFootnote(
                "Text size follows Android display settings. Shopping prices, saved data, and evidence are not changed here."
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
