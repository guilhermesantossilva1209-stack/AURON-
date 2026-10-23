package br.com.auron.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

/** Original AURON palette. Dark by design, including when Android uses a light theme. */
object AuronColors {
    val Background = Color(0xFF080A0F)
    val Surface = Color(0xFF11151D)
    val Elevated = Color(0xFF1A1F29)
    val Gold = Color(0xFFD4AF37)
    val GoldBright = Color(0xFFFFD700)
    val Muted = Color(0xFFADB3C0)
    val Text = Color(0xFFFFFFFF)
    val Success = Color(0xFF9BE0B2)
    val Danger = Color(0xFFFFB4AB)
}

private val AuronColorScheme = darkColorScheme(
    primary = AuronColors.Gold,
    onPrimary = AuronColors.Background,
    primaryContainer = Color(0xFF423711),
    onPrimaryContainer = Color(0xFFF8E7A3),
    secondary = Color(0xFFE1CD8B),
    onSecondary = Color(0xFF282312),
    secondaryContainer = Color(0xFF302B1C),
    onSecondaryContainer = Color(0xFFF2E3B5),
    tertiary = AuronColors.Success,
    background = AuronColors.Background,
    onBackground = AuronColors.Text,
    surface = AuronColors.Surface,
    onSurface = AuronColors.Text,
    surfaceVariant = AuronColors.Elevated,
    onSurfaceVariant = AuronColors.Muted,
    outline = Color(0xFF596171),
    outlineVariant = Color(0xFF303744),
    error = AuronColors.Danger,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val AuronTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 48.sp, lineHeight = 56.sp, letterSpacing = (-1).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 27.sp, lineHeight = 35.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.3.sp),
)

@Composable
fun AuronTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AuronColorScheme,
        typography = AuronTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(22.dp),
            extraLarge = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}
