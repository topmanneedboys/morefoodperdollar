package com.valuepilot.app.next

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

/** ValuePilot's original warm, editorial palette. */
internal object ValuePilotPalette {
    val brand = Color(0xFFB64F34)
    val ink = Color(0xFF262722)
    val mutedInk = Color(0xFF5F625B)
    val ivory = Color(0xFFF6F3EC)
    val paper = Color(0xFFFFFCF6)
    val line = Color(0xFFE2DCD1)
    val green = Color(0xFF315F50)
    val positive = Color(0xFF23664F)
    val warning = Color(0xFF8C570B)
    val error = Color(0xFFAC3329)
}

/** Four-point spacing rhythm, with room for dense price details and comfortable browsing. */
internal object ValuePilotSpacing {
    val hairline = 1.dp
    val xSmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val xLarge = 20.dp
    val xxLarge = 24.dp
    val section = 32.dp
    val page = 40.dp
    val minimumTouchTarget = 48.dp
}

internal object ValuePilotElevation {
    val flat = 0.dp
    val raised = 2.dp
    val floating = 6.dp
}

internal object ValuePilotMotion {
    const val quickMillis = 120
    const val standardMillis = 180
    const val emphasisMillis = 260

    fun durationMillis(baseMillis: Int, reducedMotion: Boolean): Int =
        if (reducedMotion) 0 else baseMillis
}

/** Semantic status roles stay consistent across light and dark palettes. */
internal object ValuePilotSemanticColors {
    @Composable
    fun loading(): Color = MaterialTheme.colorScheme.primary

    @Composable
    fun ready(): Color = MaterialTheme.colorScheme.secondary

    @Composable
    fun empty(): Color = MaterialTheme.colorScheme.onSurfaceVariant

    @Composable
    fun offline(): Color = MaterialTheme.colorScheme.tertiary

    @Composable
    fun error(): Color = MaterialTheme.colorScheme.error
}

private val LightScheme = lightColorScheme(
    primary = ValuePilotPalette.brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF4D9CF),
    onPrimaryContainer = Color(0xFF4E2418),
    secondary = ValuePilotPalette.green,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEBE3),
    onSecondaryContainer = Color(0xFF183A30),
    tertiary = Color(0xFF79542D),
    onTertiary = Color.White,
    background = ValuePilotPalette.ivory,
    onBackground = ValuePilotPalette.ink,
    surface = ValuePilotPalette.paper,
    onSurface = ValuePilotPalette.ink,
    surfaceVariant = Color(0xFFECE7DD),
    onSurfaceVariant = ValuePilotPalette.mutedInk,
    outline = Color(0xFF817B70),
    outlineVariant = ValuePilotPalette.line,
    error = ValuePilotPalette.error,
    onError = Color.White,
    errorContainer = Color(0xFFF8DEDB),
    onErrorContainer = Color(0xFF4C1713)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFFA27E),
    onPrimary = Color(0xFF421D12),
    primaryContainer = Color(0xFF783B27),
    onPrimaryContainer = Color(0xFFFFDBCE),
    secondary = Color(0xFF9FD2BD),
    onSecondary = Color(0xFF17392F),
    secondaryContainer = Color(0xFF31584A),
    onSecondaryContainer = Color(0xFFBCE9D5),
    tertiary = Color(0xFFE3C19A),
    onTertiary = Color(0xFF402D16),
    background = Color(0xFF1B1D19),
    onBackground = Color(0xFFF1EEE6),
    surface = Color(0xFF232620),
    onSurface = Color(0xFFF1EEE6),
    surfaceVariant = Color(0xFF343830),
    onSurfaceVariant = Color(0xFFC6C4BA),
    outline = Color(0xFF96978D),
    outlineVariant = Color(0xFF484B43),
    error = Color(0xFFFFB4A9),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD4)
)

private val ValuePilotTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.7).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.35).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 25.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 21.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.65.sp
    )
)

private val ValuePilotShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
internal fun ValuePilotNextTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = ValuePilotTypography,
        shapes = ValuePilotShapes,
        content = content
    )
}
