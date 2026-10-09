package com.valuepilot.app.next

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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

internal data class ValuePilotSpacingScale(
    val hairline: Dp,
    val xSmall: Dp,
    val small: Dp,
    val medium: Dp,
    val large: Dp,
    val xLarge: Dp,
    val xxLarge: Dp,
    val section: Dp,
    val page: Dp,
    val minimumTouchTarget: Dp
)

private val ComfortableSpacing = ValuePilotSpacingScale(
    hairline = 1.dp,
    xSmall = 4.dp,
    small = 8.dp,
    medium = 12.dp,
    large = 16.dp,
    xLarge = 20.dp,
    xxLarge = 24.dp,
    section = 32.dp,
    page = 40.dp,
    minimumTouchTarget = 48.dp
)

private val CompactSpacing = ValuePilotSpacingScale(
    hairline = 1.dp,
    xSmall = 3.dp,
    small = 6.dp,
    medium = 9.dp,
    large = 12.dp,
    xLarge = 16.dp,
    xxLarge = 18.dp,
    section = 24.dp,
    page = 30.dp,
    minimumTouchTarget = 48.dp
)

internal fun valuePilotSpacingScaleFor(density: ValuePilotNextDensity): ValuePilotSpacingScale =
    when (density) {
        ValuePilotNextDensity.COMFORTABLE -> ComfortableSpacing
        ValuePilotNextDensity.COMPACT -> CompactSpacing
    }

private val LocalValuePilotSpacingScale =
    staticCompositionLocalOf { valuePilotSpacingScaleFor(ValuePilotNextDensity.COMFORTABLE) }

/** Compact mode tightens content spacing while preserving Android's 48 dp touch target. */
internal object ValuePilotSpacing {
    val hairline: Dp
        @Composable get() = LocalValuePilotSpacingScale.current.hairline
    val xSmall: Dp
        @Composable get() = LocalValuePilotSpacingScale.current.xSmall
    val small: Dp
        @Composable get() = LocalValuePilotSpacingScale.current.small
    val medium: Dp
        @Composable get() = LocalValuePilotSpacingScale.current.medium
    val large: Dp
        @Composable get() = LocalValuePilotSpacingScale.current.large
    val xLarge: Dp
        @Composable get() = LocalValuePilotSpacingScale.current.xLarge
    val xxLarge: Dp
        @Composable get() = LocalValuePilotSpacingScale.current.xxLarge
    val section: Dp
        @Composable get() = LocalValuePilotSpacingScale.current.section
    val page: Dp
        @Composable get() = LocalValuePilotSpacingScale.current.page
    val minimumTouchTarget: Dp
        @Composable get() = LocalValuePilotSpacingScale.current.minimumTouchTarget
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
    density: ValuePilotNextDensity = ValuePilotNextDensity.COMFORTABLE,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalValuePilotSpacingScale provides valuePilotSpacingScaleFor(density)
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = ValuePilotTypography,
            shapes = ValuePilotShapes,
            content = content
        )
    }
}
