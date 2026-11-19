package player.vvvion.app.ui

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

val Ink = Color(0xFF101014)
val InkRaised = Color(0xFF1E1E24)
val InkLine = Color(0xFF3A3A42)
val Mist = Color(0xFFE6E1E5)
val Muted = Color(0xFFCAC4D0)
val Accent = Color(0xFFFFFFFF)
val AccentInk = Color(0xFF1A1C1C)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF1A1C1C),
    primaryContainer = Color(0xFFE4E2E4),
    onPrimaryContainer = Color(0xFF1A1C1C),
    secondary = Color(0xFFCCC6D0),
    onSecondary = Color(0xFF332F36),
    secondaryContainer = Color(0xFF4A4450),
    onSecondaryContainer = Color(0xFFF0DCF8),
    tertiary = Color(0xFFD0C6B0),
    onTertiary = Color(0xFF363025),
    background = Color(0xFF101014),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF101014),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceContainerLowest = Color(0xFF0B0B0F),
    surfaceContainerLow = Color(0xFF19191D),
    surfaceContainer = Color(0xFF1E1E22),
    surfaceContainerHigh = Color(0xFF28282D),
    surfaceContainerHighest = Color(0xFF333338),
    outline = Color(0xFF948F99),
    outlineVariant = Color(0xFF49454F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val Amoled = Dark.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF0A0A0A),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF050505),
    surfaceContainer = Color(0xFF0A0A0A),
    surfaceContainerHigh = Color(0xFF121212),
    surfaceContainerHighest = Color(0xFF1A1A1A),
)

private val Type = Typography(
    headlineMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 18.sp),
    bodyMedium = TextStyle(fontSize = 16.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
)

@Composable
fun VvvionTheme(amoled: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (amoled) Amoled else Dark,
        typography = Type,
        shapes = AppShapes,
        content = content,
    )
}
