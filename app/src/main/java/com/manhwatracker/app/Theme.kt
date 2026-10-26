package com.manhwatracker.app

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.manhwatracker.app.data.Prefs
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

/** Una paleta: un color principal (matiz en grados) y uno de acento. [sat] baja la saturación para la paleta gris. */
class Palette(val id: String, val label: String, val hue: Float, val accentHue: Float, val sat: Float = 1f)

object Palettes {
    const val DYNAMIC = "DYNAMIC"

    val all = listOf(
        Palette("VIOLET", "Violeta", 258f, 330f),
        Palette("OCEAN", "Océano", 203f, 165f),
        Palette("EMERALD", "Esmeralda", 152f, 52f),
        Palette("SUNSET", "Atardecer", 16f, 335f),
        Palette("ROSE", "Rosa", 334f, 262f),
        Palette("GOLD", "Dorado", 42f, 12f),
        Palette("MINT", "Menta", 172f, 205f),
        Palette("CHERRY", "Cereza", 350f, 25f),
        Palette("LAVENDER", "Lavanda", 282f, 215f),
        Palette("AMBER", "Ámbar", 30f, 350f),
        Palette("LIME", "Lima", 95f, 50f),
        Palette("SKY", "Cielo", 222f, 280f),
        Palette("MIDNIGHT", "Medianoche", 238f, 270f),
        Palette("FUCHSIA", "Fucsia", 305f, 185f),
        Palette("GRAPHITE", "Grafito", 230f, 200f, 0.12f)
    )

    const val CUSTOM = "CUSTOM"

    /** Devuelve la paleta; la personalizada se arma con los dos colores que elegiste. */
    fun resolve(id: String): Palette =
        if (id == CUSTOM) Palette(CUSTOM, "Personalizada", ThemeState.customHue, ThemeState.customAccent)
        else all.firstOrNull { it.id == id } ?: all.first()
}

/** Estado del tema: cambia al instante cuando eliges otra paleta en Apariencia. */
object ThemeState {
    var palette by mutableStateOf("VIOLET")
    var mode by mutableStateOf("SYSTEM") // SYSTEM, LIGHT, DARK
    var amoled by mutableStateOf(false)
    var customHue by mutableStateOf(200f)
    var customAccent by mutableStateOf(320f)

    fun load(context: Context) {
        palette = Prefs.palette(context)
        mode = Prefs.themeMode(context)
        amoled = Prefs.amoled(context)
        customHue = Prefs.customHue(context)
        customAccent = Prefs.customAccent(context)
    }

    fun save(context: Context) = Prefs.setLook(context, palette, mode, amoled, customHue, customAccent)
}

private fun hsv(h: Float, s: Float, v: Float, k: Float) = Color.hsv(h.mod(360f), (s * k).coerceIn(0f, 1f), v.coerceIn(0f, 1f))

private fun darkScheme(p: Palette, amoled: Boolean): ColorScheme {
    val h = p.hue
    val k = p.sat
    val bg = if (amoled) Color.Black else hsv(h, 0.45f, 0.075f, k)
    return darkColorScheme(
        primary = hsv(h, 0.38f, 1.0f, k),
        onPrimary = hsv(h, 0.9f, 0.25f, k),
        primaryContainer = hsv(h, 0.65f, 0.52f, k),
        onPrimaryContainer = hsv(h, 0.12f, 1.0f, k),
        secondary = hsv(h, 0.22f, 0.88f, k),
        onSecondary = hsv(h, 0.6f, 0.2f, k),
        secondaryContainer = hsv(h, 0.4f, 0.32f, k),
        onSecondaryContainer = hsv(h, 0.1f, 0.95f, k),
        tertiary = hsv(p.accentHue, 0.55f, 1.0f, k),
        onTertiary = hsv(p.accentHue, 0.9f, 0.25f, k),
        background = bg,
        onBackground = hsv(h, 0.05f, 0.93f, k),
        surface = bg,
        onSurface = hsv(h, 0.05f, 0.93f, k),
        surfaceVariant = hsv(h, 0.35f, 0.23f, k),
        onSurfaceVariant = hsv(h, 0.10f, 0.85f, k),
        surfaceContainer = if (amoled) hsv(h, 0.4f, 0.07f, k) else hsv(h, 0.40f, 0.12f, k),
        surfaceContainerHigh = if (amoled) hsv(h, 0.4f, 0.10f, k) else hsv(h, 0.40f, 0.155f, k),
        surfaceContainerHighest = if (amoled) hsv(h, 0.4f, 0.13f, k) else hsv(h, 0.38f, 0.19f, k),
        outline = hsv(h, 0.15f, 0.62f, k),
        outlineVariant = hsv(h, 0.30f, 0.30f, k)
    )
}

private fun lightScheme(p: Palette): ColorScheme {
    val h = p.hue
    val k = p.sat
    return lightColorScheme(
        primary = hsv(h, 0.70f, 0.80f, k),
        onPrimary = Color.White,
        primaryContainer = hsv(h, 0.14f, 1.0f, k),
        onPrimaryContainer = hsv(h, 0.9f, 0.30f, k),
        secondary = hsv(h, 0.35f, 0.45f, k),
        onSecondary = Color.White,
        secondaryContainer = hsv(h, 0.12f, 0.97f, k),
        onSecondaryContainer = hsv(h, 0.6f, 0.22f, k),
        tertiary = hsv(p.accentHue, 0.85f, 0.80f, k),
        onTertiary = Color.White,
        background = hsv(h, 0.03f, 0.99f, k),
        onBackground = hsv(h, 0.3f, 0.12f, k),
        surface = hsv(h, 0.03f, 0.99f, k),
        onSurface = hsv(h, 0.3f, 0.12f, k),
        surfaceVariant = hsv(h, 0.10f, 0.92f, k),
        onSurfaceVariant = hsv(h, 0.25f, 0.35f, k),
        surfaceContainer = hsv(h, 0.06f, 0.96f, k),
        surfaceContainerHigh = hsv(h, 0.08f, 0.94f, k),
        surfaceContainerHighest = Color.White,
        outline = hsv(h, 0.2f, 0.5f, k),
        outlineVariant = hsv(h, 0.12f, 0.82f, k)
    )
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp)
)

private val base = Typography()
private val AppTypography = base.copy(
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold)
)

@Composable
fun ManhwaTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = when (ThemeState.mode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> isSystemInDarkTheme()
    }
    val scheme = if (ThemeState.palette == Palettes.DYNAMIC && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        val p = Palettes.resolve(ThemeState.palette)
        if (dark) darkScheme(p, ThemeState.amoled) else lightScheme(p)
    }
    MaterialTheme(colorScheme = scheme, shapes = AppShapes, typography = AppTypography, content = content)
}

/** Degradado de la marca (principal → acento). Pensado para llevar texto blanco encima. */
@Composable
fun brandBrush(): Brush {
    val c = MaterialTheme.colorScheme
    val dark = c.background.luminance() < 0.5f
    val start = if (dark) c.primaryContainer else c.primary
    val end = lerp(start, c.tertiary, if (dark) 0.35f else 0.6f)
    return Brush.linearGradient(listOf(start, end))
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
