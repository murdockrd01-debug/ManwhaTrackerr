package com.manhwatracker.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.manhwatracker.app.data.Status
import com.manhwatracker.app.data.Utility

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9A4FF),
    onPrimary = Color(0xFF2B1B66),
    primaryContainer = Color(0xFF423384),
    onPrimaryContainer = Color(0xFFE6DEFF),
    secondary = Color(0xFFCBC2E8),
    onSecondary = Color(0xFF332D4D),
    secondaryContainer = Color(0xFF3F3858),
    onSecondaryContainer = Color(0xFFE8DFFF),
    tertiary = Color(0xFFFFC857),
    background = Color(0xFF0F0E17),
    onBackground = Color(0xFFE8E4F2),
    surface = Color(0xFF0F0E17),
    onSurface = Color(0xFFE8E4F2),
    surfaceVariant = Color(0xFF2A2740),
    onSurfaceVariant = Color(0xFFC8C3DA),
    surfaceContainer = Color(0xFF181624),
    surfaceContainerHigh = Color(0xFF1F1C2E),
    surfaceContainerHighest = Color(0xFF221F33),
    outline = Color(0xFF8F8AA6),
    outlineVariant = Color(0xFF3A3652)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6B4EFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6DEFF),
    onPrimaryContainer = Color(0xFF22005D),
    secondary = Color(0xFF5E5875),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6DEFA),
    onSecondaryContainer = Color(0xFF1B1730),
    tertiary = Color(0xFFB77900),
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF1B1A22),
    surface = Color(0xFFFAF8FF),
    onSurface = Color(0xFF1B1A22),
    surfaceVariant = Color(0xFFE7E2F3),
    onSurfaceVariant = Color(0xFF48455A),
    surfaceContainer = Color(0xFFF1EDFB),
    surfaceContainerHigh = Color(0xFFEBE6F7),
    surfaceContainerHighest = Color(0xFFFFFFFF),
    outline = Color(0xFF7A7690),
    outlineVariant = Color(0xFFCBC6DC)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

private val base = Typography()
private val AppTypography = base.copy(
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold)
)

@Composable
fun ManhwaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content
    )
}

/** Color de cada estado (se usa en etiquetas y franjas de las tarjetas). */
fun statusColor(status: String): Color = when (status) {
    Status.READING -> Color(0xFF4CC38A)
    Status.PAUSED -> Color(0xFFF0A93B)
    Status.COMPLETED -> Color(0xFF6C8CFF)
    else -> Color(0xFF9A96B0)
}

/** Color de la nota: dorado para las mejores, apagado para las bajas. */
fun ratingColor(rating: Int): Color = when {
    rating >= 9 -> Color(0xFFFFC857)
    rating >= 7 -> Color(0xFF4CC38A)
    rating >= 5 -> Color(0xFF9A96B0)
    else -> Color(0xFFE5736A)
}

/** Color de cada nivel de utilidad de una palabra. */
fun tierColor(t: Utility.Tier): Color = when (t) {
    Utility.Tier.VERY_USEFUL -> Color(0xFF4CC38A)
    Utility.Tier.USEFUL -> Color(0xFF6C8CFF)
    Utility.Tier.UNCOMMON -> Color(0xFFF0A93B)
    Utility.Tier.RARE -> Color(0xFFE5736A)
    Utility.Tier.PHRASE -> Color(0xFFB9A4FF)
    else -> Color(0xFF9A96B0)
}
