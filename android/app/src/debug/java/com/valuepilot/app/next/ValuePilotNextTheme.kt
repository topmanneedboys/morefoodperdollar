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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.valuepilot.app.R

/** ValuePilot's original warm, editorial palette. */
internal object ValuePilotPalette {
    val brand = Color(0xFFB64F34)
    val ink = Color(0xFF262722)
    val mutedInk = Color(0xFF5D5E58)
    val ivory = Color(0xFFF6F3EC)
    val paper = Color(0xFFFFFFFF)
    val line = Color(0xFFE4DFD3)
    val green = Color(0xFF3D6B52)
    val positive = Color(0xFF3D6B52)
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

internal val ValuePilotLightColorScheme = lightColorScheme(
    primary = Color(0xFF11120E),
    onPrimary = Color.White,
    primaryContainer = ValuePilotPalette.ink,
    onPrimaryContainer = Color(0xFFFAF8F3),
    secondary = Color(0xFFA13F26),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF8ECE8),
    onSecondaryContainer = Color(0xFF741F08),
    tertiary = ValuePilotPalette.positive,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE9F0EB),
    onTertiaryContainer = Color(0xFF214F38),
    background = ValuePilotPalette.ivory,
    onBackground = ValuePilotPalette.ink,
    surface = ValuePilotPalette.paper,
    onSurface = ValuePilotPalette.ink,
    surfaceVariant = Color(0xFFEFEBE1),
    onSurfaceVariant = ValuePilotPalette.mutedInk,
    outline = Color(0xFF817B70),
    outlineVariant = ValuePilotPalette.line,
    error = ValuePilotPalette.error,
    onError = Color.White,
    errorContainer = Color(0xFFF8DEDB),
    onErrorContainer = Color(0xFF4C1713)
)

internal val ValuePilotDarkColorScheme = darkColorScheme(
    primary = Color(0xFFF3F0E8),
    onPrimary = Color(0xFF1C1C18),
    primaryContainer = Color(0xFF3A3B35),
    onPrimaryContainer = Color(0xFFF3F0E8),
    secondary = Color(0xFFFFA184),
    onSecondary = Color(0xFF36170D),
    secondaryContainer = Color(0xFF713B2C),
    onSecondaryContainer = Color(0xFFFFD9CC),
    tertiary = Color(0xFF9FD2B6),
    onTertiary = Color(0xFF17392A),
    tertiaryContainer = Color(0xFF284D3A),
    onTertiaryContainer = Color(0xFFC0E9D0),
    background = Color(0xFF1C1C18),
    onBackground = Color(0xFFF1EEE6),
    surface = Color(0xFF262722),
    onSurface = Color(0xFFF1EEE6),
    surfaceVariant = Color(0xFF34352F),
    onSurfaceVariant = Color(0xFFC6C4BA),
    outline = Color(0xFF96978D),
    outlineVariant = Color(0xFF484B43),
    error = Color(0xFFFFB4A9),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD4)
)

private val ValuePilotJakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans_variable, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_variable, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_variable, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_variable, FontWeight.Bold)
)

private val ValuePilotTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = ValuePilotJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-1.08).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = ValuePilotJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.75).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = ValuePilotJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.48).sp
    ),
    titleLarge = TextStyle(
        fontFamily = ValuePilotJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.27).sp
    ),
    titleMedium = TextStyle(
        fontFamily = ValuePilotJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.16).sp
    ),
    bodyLarge = TextStyle(
        fontFamily = ValuePilotJakartaSans,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = ValuePilotJakartaSans,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = ValuePilotJakartaSans,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = ValuePilotJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = ValuePilotJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.24.sp
    ),
    labelSmall = TextStyle(
        fontFamily = ValuePilotJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.44.sp
    )
)

private val ValuePilotShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
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
            colorScheme = if (darkTheme) ValuePilotDarkColorScheme else ValuePilotLightColorScheme,
            typography = ValuePilotTypography,
            shapes = ValuePilotShapes,
            content = content
        )
    }
}
