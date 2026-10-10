package com.valuepilot.app.next

internal fun valuePilotNextText(
    language: ValuePilotNextLanguage,
    english: String,
    spanishArgentina: String
): String =
    if (language == ValuePilotNextLanguage.SPANISH_AR) spanishArgentina else english
