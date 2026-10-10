package com.valuepilot.app.next

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

internal fun valuePilotNextText(
    language: ValuePilotNextLanguage,
    english: String,
    spanishArgentina: String
): String =
    if (language == ValuePilotNextLanguage.SPANISH_AR) spanishArgentina else english

/** The single language source used by every native preview surface. */
internal val LocalValuePilotNextLanguage =
    staticCompositionLocalOf { ValuePilotNextLanguage.ENGLISH }

@Composable
internal fun valuePilotNextText(
    english: String,
    spanishArgentina: String
): String = valuePilotNextText(
    LocalValuePilotNextLanguage.current,
    english,
    spanishArgentina
)
