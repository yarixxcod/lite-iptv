package app.liteiptv.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF0B0E13)
val InkRaised = Color(0xFF151B22)
val InkLine = Color(0xFF2A3542)
val Mist = Color(0xFFE7EEF3)
val Muted = Color(0xFF93A1AD)
val Accent = Color(0xFF3EE0B0)
val AccentInk = Color(0xFF06241C)

private val Dark = darkColorScheme(
    primary = Accent,
    onPrimary = AccentInk,
    secondary = Accent,
    background = Ink,
    onBackground = Mist,
    surface = InkRaised,
    onSurface = Mist,
    surfaceVariant = Color(0xFF1C242D),
    onSurfaceVariant = Muted,
    outline = InkLine,
    error = Color(0xFFFF6B6B),
)

private val Amoled = Dark.copy(background = Color.Black, surface = Color.Black, surfaceVariant = Color(0xFF0A0A0A))

private val Type = Typography(
    headlineMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 18.sp),
    bodyMedium = TextStyle(fontSize = 16.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
)

@Composable
fun LiteTheme(amoled: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (amoled) Amoled else Dark,
        typography = Type,
        shapes = AppShapes,
        content = content,
    )
}
