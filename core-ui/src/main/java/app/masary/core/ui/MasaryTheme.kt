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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val MasaryPurple = Color(0xFF6B4BB8)
private val MasaryPurpleDark = Color(0xFF4E328F)
private val MasaryPurpleSoft = Color(0xFFECE5FF)
private val MasaryGold = Color(0xFFFFC857)
private val MasaryNavy = Color(0xFF17223B)
private val MasaryInk = Color(0xFF201A2B)
private val MasaryMuted = Color(0xFF6F6878)
private val MasarySurface = Color(0xFFFFF9FF)
private val MasarySurfaceAlt = Color(0xFFF5F0F8)
private val MasarySuccess = Color(0xFF197A55)
private val MasaryError = Color(0xFFB3261E)

private val MasaryLightColors = lightColorScheme(
    primary = MasaryPurple,
    onPrimary = Color.White,
    primaryContainer = MasaryPurpleSoft,
    onPrimaryContainer = MasaryPurpleDark,
    secondary = MasaryGold,
    onSecondary = Color(0xFF3B2A00),
    secondaryContainer = Color(0xFFFFE7AD),
    onSecondaryContainer = Color(0xFF2D2000),
    tertiary = MasaryNavy,
    onTertiary = Color.White,
    background = MasarySurface,
    onBackground = MasaryInk,
    surface = Color.White,
    onSurface = MasaryInk,
    surfaceVariant = MasarySurfaceAlt,
    onSurfaceVariant = MasaryMuted,
    outline = Color(0xFF827887),
    outlineVariant = Color(0xFFD5CAD8),
    error = MasaryError,
    onError = Color.White,
)

private val MasaryDarkColors = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF38206E),
    primaryContainer = Color(0xFF51358B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFFFD980),
    onSecondary = Color(0xFF402D00),
    secondaryContainer = Color(0xFF5C4200),
    onSecondaryContainer = Color(0xFFFFDEA1),
    tertiary = Color(0xFFBFC8E8),
    onTertiary = Color(0xFF29314B),
    background = Color(0xFF151218),
    onBackground = Color(0xFFEAE1EC),
    surface = Color(0xFF1D1A20),
    onSurface = Color(0xFFEAE1EC),
    surfaceVariant = Color(0xFF49454E),
    onSurfaceVariant = Color(0xFFCCC3CF),
    outline = Color(0xFF968E99),
    outlineVariant = Color(0xFF49454E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val MasaryTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 54.sp,
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 46.sp,
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 36.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 25.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 28.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 24.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
)

private val MasaryShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
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

val LocalMasarySpacing = staticCompositionLocalOf { MasarySpacing() }

object MasaryColors {
    val brandPurple = MasaryPurple
    val brandPurpleDark = MasaryPurpleDark
    val brandPurpleSoft = MasaryPurpleSoft
    val brandGold = MasaryGold
    val brandNavy = MasaryNavy
    val success = MasarySuccess
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
