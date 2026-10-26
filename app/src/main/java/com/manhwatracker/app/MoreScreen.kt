package com.manhwatracker.app

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Pantalla "Más": accesos a las secciones secundarias y a las copias de seguridad. */
@Composable
fun MoreScreen(
    onRatings: () -> Unit,
    onSync: () -> Unit,
    onLook: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onRestore: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MoreRow("⭐", "Calificaciones", "Tu ranking, favoritos y promedio", onRatings)
        MoreRow("🎨", "Apariencia", "Colores, modo claro u oscuro", onLook)
        MoreRow("🔄", "Sincronización y copias", "Importar de Komikku solo y guardar tus datos", onSync)

        Spacer(Modifier.height(6.dp))
        Text("Copias manuales", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        MoreRow("📥", "Importar copia de Komikku", "Elige un archivo .tachibk", onImport)
        MoreRow("📤", "Exportar mi copia", "Guarda tus manhwas, palabras y notas en un archivo", onExport)
        MoreRow("♻️", "Restaurar mi copia", "Recupera un archivo .json que exportaste", onRestore)
    }
}

@Composable
private fun MoreRow(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(brandBrush()),
                contentAlignment = Alignment.Center
            ) { Text(icon, fontSize = 20.sp) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", fontSize = 24.sp, color = MaterialTheme.colorScheme.outline)
        }
    }
}

/** Elegir paleta de colores y modo claro / oscuro. Los cambios se ven al instante. */
@Composable
fun AppearanceScreen() {
    val context = LocalContext.current

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Paleta de colores", style = MaterialTheme.typography.titleMedium)

        val options = Palettes.all.map { it.id to it.label } +
            listOf(Palettes.CUSTOM to "Personalizada") +
            (if (Build.VERSION.SDK_INT >= 31) listOf(Palettes.DYNAMIC to "Colores del teléfono") else emptyList())

        options.chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (id, label) ->
                    Swatch(id, label, Modifier.weight(1f)) {
                        ThemeState.palette = id
                        ThemeState.save(context)
                    }
                }
                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        if (ThemeState.palette == Palettes.CUSTOM) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Tu combinación", style = MaterialTheme.typography.titleSmall)
                    Text("Color principal", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = ThemeState.customHue,
                        onValueChange = { ThemeState.customHue = it },
                        onValueChangeFinished = { ThemeState.save(context) },
                        valueRange = 0f..360f
                    )
                    Text("Color de acento", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = ThemeState.customAccent,
                        onValueChange = { ThemeState.customAccent = it },
                        onValueChangeFinished = { ThemeState.save(context) },
                        valueRange = 0f..360f
                    )
                }
            }
        }

        Text("Modo", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("SYSTEM" to "Automático", "LIGHT" to "Claro", "DARK" to "Oscuro").forEach { (id, label) ->
                FilterChip(
                    selected = ThemeState.mode == id,
                    onClick = {
                        ThemeState.mode = id
                        ThemeState.save(context)
                    },
                    label = { Text(label) }
                )
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Negro puro (AMOLED)", style = MaterialTheme.typography.titleSmall)
                Text(
                    "En modo oscuro usa fondo negro. Ahorra batería en pantallas OLED.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = ThemeState.amoled,
                onCheckedChange = {
                    ThemeState.amoled = it
                    ThemeState.save(context)
                }
            )
        }
    }
}

/** Un círculo de color con su nombre debajo; el elegido lleva un aro. */
@Composable
private fun Swatch(id: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    val selected = ThemeState.palette == id
    val colors: List<Color> = if (id == Palettes.DYNAMIC) {
        listOf(Color(0xFF8AB4F8), Color(0xFFF28B82), Color(0xFFFDD663))
    } else {
        val p = Palettes.resolve(id)
        listOf(
            Color.hsv(p.hue, 0.7f * p.sat, 0.85f),
            Color.hsv(p.accentHue, 0.8f * p.sat, 0.9f)
        )
    }
    Column(
        modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            Modifier
                .size(50.dp)
                .clip(CircleShape)
                .border(
                    if (selected) 3.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    CircleShape
                )
                .padding(4.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(colors)),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
