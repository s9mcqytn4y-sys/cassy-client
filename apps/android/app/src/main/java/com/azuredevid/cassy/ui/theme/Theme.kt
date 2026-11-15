package com.azuredevid.cassy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.azuredevid.cassy.R

// Cassy Brand Palette (PRD v4 - packages/design-tokens/tokens.json)
val CassyPandan = Color(0xFF1F5F52)
val CassyKunyit = Color(0xFFE9A23B)
val CassyTinta = Color(0xFF17211E)
val CassyPutihTulang = Color(0xFFF7F5F0)

// Full-bleed dark Pandan pairing & splash showcase tokens
val BrandPanelPandan = CassyPandan
val BrandPanelSurface = Color(0xFF15473D)
val BrandPanelBorder = Color(0xFF2B7364)
val BrandPanelMotif = Color(0xFF297364)
val BrandStatusOnlineDot = Color(0xFF4ADE80)
val BrandStatusOfflineDot = Color(0xFFFF6B6B)
val BrandStatusOfflineText = Color(0xFFFFDAD6)
val BrandStatusCheckingDot = CassyKunyit

// Semantic Light Tokens (aligned to packages/design-tokens/tokens.json cassyLight)
val LightCanvas = CassyPutihTulang
val LightSurface = Color(0xFFFFFFFF)
val LightMuted = Color(0xFFEFECE4)
val LightInk = CassyTinta
val LightSecondaryInk = Color(0xFF56605B)
val LightAccent = CassyPandan
val LightBorder = Color(0xFFE2DDD2)
val LightBorderStrong = Color(0xFF8C877C)
val LightAccentText = Color(0xFF8A5A12)

// Semantic Dark Tokens (aligned to packages/design-tokens/tokens.json cassyDark)
val DarkCanvas = Color(0xFF0F1513)
val DarkSurface = Color(0xFF18211E)
val DarkMuted = Color(0xFF222D29)
val DarkInk = Color(0xFFECEFEA)
val DarkSecondaryInk = Color(0xFFA9B3AD)
val DarkPrimaryAction = Color(0xFF5FB8A2)
val DarkBorder = Color(0xFF2E3A35)
val DarkBorderStrong = Color(0xFF6C7A73)
val DarkAccent = Color(0xFFF2B65A)

val StatusOkBgLight = Color(0xFFE3F2E6)
val StatusOkFgLight = Color(0xFF1E6B34)
val StatusWarnBgLight = Color(0xFFFDF0D8)
val StatusWarnFgLight = Color(0xFF8A5300)
val StatusErrBgLight = Color(0xFFFBE7E5)
val StatusErrFgLight = Color(0xFFB3261E)
val StatusInfoBgLight = Color(0xFFE4EEF8)
val StatusInfoFgLight = Color(0xFF245B8F)

val StatusOkBgDark = Color(0xFF16301F)
val StatusOkFgDark = Color(0xFF7FD493)
val StatusWarnBgDark = Color(0xFF352710)
val StatusWarnFgDark = Color(0xFFF7C66B)
val StatusErrBgDark = Color(0xFF3A1714)
val StatusErrFgDark = Color(0xFFFF8A80)
val StatusInfoBgDark = Color(0xFF14263A)
val StatusInfoFgDark = Color(0xFF8CC0F2)

val CassyFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold)
)

private const val TABULAR_NUMS = "tnum"

val CassyTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = CassyFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        fontFeatureSettings = TABULAR_NUMS
    ),
    displaySmall = TextStyle(
        fontFamily = CassyFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontFeatureSettings = TABULAR_NUMS
    ),
    headlineLarge = TextStyle(
        fontFamily = CassyFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        fontFeatureSettings = TABULAR_NUMS
    ),
    headlineMedium = TextStyle(
        fontFamily = CassyFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        fontFeatureSettings = TABULAR_NUMS
    ),
    headlineSmall = TextStyle(
        fontFamily = CassyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontFeatureSettings = TABULAR_NUMS
    ),
    titleLarge = TextStyle(
        fontFamily = CassyFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontFeatureSettings = TABULAR_NUMS
    ),
    titleMedium = TextStyle(
        fontFamily = CassyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontFeatureSettings = TABULAR_NUMS
    ),
    titleSmall = TextStyle(
        fontFamily = CassyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = TABULAR_NUMS
    ),
    bodyLarge = TextStyle(
        fontFamily = CassyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontFeatureSettings = TABULAR_NUMS
    ),
    bodyMedium = TextStyle(
        fontFamily = CassyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = TABULAR_NUMS
    ),
    bodySmall = TextStyle(
        fontFamily = CassyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontFeatureSettings = TABULAR_NUMS
    ),
    labelLarge = TextStyle(
        fontFamily = CassyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = TABULAR_NUMS
    ),
    labelMedium = TextStyle(
        fontFamily = CassyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontFeatureSettings = TABULAR_NUMS
    ),
    labelSmall = TextStyle(
        fontFamily = CassyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontFeatureSettings = TABULAR_NUMS
    )
)

private val CassyLightScheme = lightColorScheme(
    primary = CassyPandan,
    onPrimary = Color.White,
    primaryContainer = StatusOkBgLight,
    onPrimaryContainer = CassyPandan,
    secondary = CassyKunyit,
    onSecondary = CassyTinta,
    secondaryContainer = StatusOkBgLight,
    onSecondaryContainer = CassyPandan,
    tertiary = CassyKunyit,
    onTertiary = CassyTinta,
    tertiaryContainer = StatusWarnBgLight,
    onTertiaryContainer = LightAccentText,
    background = LightCanvas,
    onBackground = LightInk,
    surface = LightSurface,
    onSurface = LightInk,
    surfaceVariant = LightMuted,
    onSurfaceVariant = LightSecondaryInk,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightMuted,
    surfaceContainerHigh = LightMuted,
    surfaceContainerHighest = LightMuted,
    outline = LightBorder,
    outlineVariant = LightBorderStrong,
    error = StatusErrFgLight,
    onError = Color.White,
    errorContainer = StatusErrBgLight,
    onErrorContainer = StatusErrFgLight
)

private val CassyDarkScheme = darkColorScheme(
    primary = DarkPrimaryAction,
    onPrimary = DarkCanvas,
    primaryContainer = StatusOkBgDark,
    onPrimaryContainer = DarkPrimaryAction,
    secondary = DarkAccent,
    onSecondary = DarkCanvas,
    secondaryContainer = StatusOkBgDark,
    onSecondaryContainer = DarkPrimaryAction,
    tertiary = DarkAccent,
    onTertiary = DarkCanvas,
    tertiaryContainer = StatusWarnBgDark,
    onTertiaryContainer = DarkAccent,
    background = DarkCanvas,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkMuted,
    onSurfaceVariant = DarkSecondaryInk,
    surfaceContainerLowest = DarkCanvas,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkMuted,
    surfaceContainerHigh = DarkMuted,
    surfaceContainerHighest = DarkMuted,
    outline = DarkBorder,
    outlineVariant = DarkBorderStrong,
    error = StatusErrFgDark,
    onError = DarkCanvas,
    errorContainer = StatusErrBgDark,
    onErrorContainer = StatusErrFgDark
)

@Composable
fun CassyTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) CassyDarkScheme else CassyLightScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = CassyTypography,
        content = content
    )
}

@Composable
fun cassyIsDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

@Composable
fun statusOkBg(): Color = if (cassyIsDark()) StatusOkBgDark else StatusOkBgLight

@Composable
fun statusOkFg(): Color = if (cassyIsDark()) StatusOkFgDark else StatusOkFgLight

@Composable
fun statusWarnBg(): Color = if (cassyIsDark()) StatusWarnBgDark else StatusWarnBgLight

@Composable
fun statusWarnFg(): Color = if (cassyIsDark()) StatusWarnFgDark else StatusWarnFgLight

@Composable
fun statusErrBg(): Color = if (cassyIsDark()) StatusErrBgDark else StatusErrBgLight

@Composable
fun statusErrFg(): Color = if (cassyIsDark()) StatusErrFgDark else StatusErrFgLight

@Composable
fun statusInfoBg(): Color = if (cassyIsDark()) StatusInfoBgDark else StatusInfoBgLight

@Composable
fun statusInfoFg(): Color = if (cassyIsDark()) StatusInfoFgDark else StatusInfoFgLight

@Composable
fun accentText(): Color = if (cassyIsDark()) DarkAccent else LightAccentText
