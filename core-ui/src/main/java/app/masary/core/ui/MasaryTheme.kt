package app.masary.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val MasaryNavy = Color(0xFF061A45)
private val MasaryNavyDeep = Color(0xFF041432)
private val MasaryNavySoft = Color(0xFF14213D)
private val MasaryGold = Color(0xFFDAA545)
private val MasaryGoldBright = Color(0xFFF2C14E)
private val MasaryWhite = Color(0xFFFFFFFF)
private val MasaryBackground = Color(0xFFF6F8FC)
private val MasaryWarm = Color(0xFFFFF9EC)
private val MasaryIce = Color(0xFFEAF2FF)
private val MasaryMuted = Color(0xFF6F7A90)
private val MasaryBorder = Color(0xFFE4E9F1)
private val MasarySuccess = Color(0xFF2ECC71)
private val MasaryWarning = Color(0xFFF39C12)
private val MasaryError = Color(0xFFE74C3C)
private val MasaryInfo = Color(0xFF3498DB)

private val MasaryLightColors = lightColorScheme(
    primary = MasaryNavy,
    onPrimary = MasaryWhite,
    primaryContainer = MasaryIce,
    onPrimaryContainer = MasaryNavyDeep,
    secondary = MasaryGold,
    onSecondary = MasaryNavyDeep,
    secondaryContainer = MasaryWarm,
    onSecondaryContainer = MasaryNavyDeep,
    tertiary = MasaryNavySoft,
    onTertiary = MasaryWhite,
    background = MasaryBackground,
    onBackground = MasaryNavyDeep,
    surface = MasaryWhite,
    onSurface = MasaryNavyDeep,
    surfaceVariant = Color(0xFFF0F3F8),
    onSurfaceVariant = MasaryMuted,
    outline = Color(0xFFB0B7C3),
    outlineVariant = MasaryBorder,
    error = MasaryError,
    onError = MasaryWhite,
    errorContainer = Color(0xFFFFE7E4),
    onErrorContainer = Color(0xFF7A1B14),
)

private val MasaryDarkColors = darkColorScheme(
    primary = MasaryGoldBright,
    onPrimary = MasaryNavyDeep,
    primaryContainer = Color(0xFF10285B),
    onPrimaryContainer = MasaryWhite,
    secondary = MasaryGold,
    onSecondary = MasaryNavyDeep,
    secondaryContainer = Color(0xFF493718),
    onSecondaryContainer = Color(0xFFFFE8B0),
    tertiary = Color(0xFFB9C8E6),
    onTertiary = MasaryNavyDeep,
    background = MasaryNavyDeep,
    onBackground = Color(0xFFF3F6FC),
    surface = Color(0xFF0B214A),
    onSurface = Color(0xFFF7F9FC),
    surfaceVariant = Color(0xFF17305E),
    onSurfaceVariant = Color(0xFFC6CEE0),
    outline = Color(0xFF8A96AE),
    outlineVariant = Color(0xFF31476E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF7A271F),
    onErrorContainer = Color(0xFFFFDAD5),
)

private val MasaryTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 42.sp,
        lineHeight = 52.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp,
        lineHeight = 44.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 29.sp,
        lineHeight = 39.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 25.sp,
        lineHeight = 35.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        lineHeight = 29.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 25.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 28.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 24.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
)

private val MasaryShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Immutable
data class MasarySpacing(
    val xSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val xLarge: Dp = 32.dp,
    val xxLarge: Dp = 48.dp,
)

@Immutable
data class MasaryElevation(
    val flat: Dp = 0.dp,
    val subtle: Dp = 2.dp,
    val card: Dp = 5.dp,
    val floating: Dp = 10.dp,
)

val LocalMasarySpacing = staticCompositionLocalOf { MasarySpacing() }
val LocalMasaryElevation = staticCompositionLocalOf { MasaryElevation() }

object MasaryColors {
    val brandNavy = MasaryNavy
    val brandNavyDeep = MasaryNavyDeep
    val brandNavySoft = MasaryNavySoft
    val brandGold = MasaryGold
    val brandGoldBright = MasaryGoldBright
    val white = MasaryWhite
    val background = MasaryBackground
    val warmSurface = MasaryWarm
    val iceSurface = MasaryIce
    val muted = MasaryMuted
    val border = MasaryBorder
    val success = MasarySuccess
    val warning = MasaryWarning
    val error = MasaryError
    val info = MasaryInfo

    // Temporary compatibility aliases for modules that have not yet moved to the official palette.
    val brandPurple = MasaryNavy
    val brandPurpleDark = MasaryNavyDeep
    val brandPurpleSoft = MasaryIce
}

@Composable
fun MasaryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) MasaryDarkColors else MasaryLightColors,
        typography = MasaryTypography,
        shapes = MasaryShapes,
        content = content,
    )
}
