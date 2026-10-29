package com.manhwatracker.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manhwatracker.app.data.Prefs
import com.manhwatracker.app.data.UpdateCheck
import kotlinx.coroutines.launch

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun SwitchRow(title: String, text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Ajustes ordenados: modo, actualizaciones, guía y versión. */
@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var autoCheck by remember { mutableStateOf(Prefs.updateCheck(ctx)) }
    var status by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var apk by remember { mutableStateOf<java.io.File?>(null) }

    fun runCheck() {
        checking = true
        status = null
        scope.launch {
            val v = UpdateCheck.newerVersion(ctx)
            checking = false
            found = v != null
            status = if (v != null) "Hay una versión nueva: $v" else "Tienes la última versión (o no hay internet)."
        }
    }

    fun installNow() {
        val f = apk ?: return
        status = when (UpdateCheck.install(ctx, f)) {
            UpdateCheck.InstallResult.STARTED -> null
            UpdateCheck.InstallResult.NEEDS_PERMISSION ->
                "Permite a Manhwa Tracker instalar apps, vuelve aquí y toca Instalar."
            UpdateCheck.InstallResult.FAILED -> "No se pudo abrir el instalador."
        }
    }

    // Al abrir Ajustes se busca solo, para que el botón de descargar ya esté listo.
    androidx.compose.runtime.LaunchedEffect(Unit) { if (Prefs.updateCheck(ctx)) runCheck() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("General", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        SettingCard {
            SwitchRow(
                "Estudio de inglés",
                if (Features.study) "Activo: palabras, repaso y racha." else "Apagado: solo tracker.",
                Features.study
            ) { Features.setStudy(ctx, it) }
        }

        Text("Idioma", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        SettingCard {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("auto" to "Auto", "es" to "Español", "en" to "English").forEach { (id, label) ->
                    androidx.compose.material3.FilterChip(
                        selected = Tr.lang == id,
                        onClick = { Tr.lang = id; Prefs.setLanguage(ctx, id) },
                        label = { Text(label) }
                    )
                }
            }
            Text("Auto sigue el idioma del teléfono.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (Features.study) {
            Text("Repaso", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            SettingCard {
                var byUse by remember { mutableStateOf(Prefs.reviewByUse(ctx)) }
                Text("Orden del repaso", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.FilterChip(
                        selected = byUse, onClick = { byUse = true; Prefs.setReviewByUse(ctx, true) },
                        label = { Text("Más útiles primero") }
                    )
                    androidx.compose.material3.FilterChip(
                        selected = !byUse, onClick = { byUse = false; Prefs.setReviewByUse(ctx, false) },
                        label = { Text("Más antiguas primero") }
                    )
                }
                Text(
                    "Cada ronda trae hasta 30 palabras. Con \"más útiles\", las que más se usan en inglés salen antes.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text("App para leer", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        SettingCard {
            val readers = remember { ReaderLink.installed(ctx) }
            var chosen by remember { mutableStateOf(Prefs.reader(ctx)) }
            if (readers.isEmpty()) {
                Text("No encontré Komikku, Mihon ni Mangayomi instalados.", style = MaterialTheme.typography.bodySmall)
            } else {
                Text("Es la app que se abre con el botón de cada manhwa.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.FilterChip(
                        selected = chosen == null, onClick = { chosen = null; Prefs.setReader(ctx, null) },
                        label = { Text("Automático") }
                    )
                    readers.forEach { r ->
                        androidx.compose.material3.FilterChip(
                            selected = chosen == r.pkg, onClick = { chosen = r.pkg; Prefs.setReader(ctx, r.pkg) },
                            label = { Text(r.name) }
                        )
                    }
                }
            }
        }

        Text("Actualizaciones", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        SettingCard {
            SwitchRow("Avisarme de versiones nuevas", "Al abrir la app, busca en GitHub si hay una más reciente.", autoCheck) {
                autoCheck = it
                Prefs.setUpdateCheck(ctx, it)
            }
            Text("Versión instalada: ${UpdateCheck.installedVersion(ctx)}", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !checking && progress == null,
                    onClick = { runCheck() }
                ) { Text(if (checking) "Buscando…" else "Buscar ahora") }
                if (apk != null) {
                    Button(onClick = { installNow() }) { Text("Instalar") }
                } else if (found) {
                    Button(enabled = progress == null, onClick = {
                        progress = 0f
                        status = null
                        scope.launch {
                            val f = UpdateCheck.download(ctx) { progress = it }
                            progress = null
                            if (f == null) status = "No se pudo descargar. Revisa tu internet."
                            else { apk = f; installNow() }
                        }
                    }) { Text("Descargar e instalar") }
                }
            }
            progress?.let { p ->
                if (p >= 0f) {
                    androidx.compose.material3.LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                    Text("Descargando… ${(p * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                } else {
                    androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }

        Text("Ayuda", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        SettingCard {
            Text("Guía de inicio", style = MaterialTheme.typography.titleMedium)
            Text("Un repaso rápido de cómo usar la app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { Features.showIntro = true }) { Text("Ver la guía") }
        }
    }
}

private data class IntroPage(val icon: String, val title: String, val text: String)

/** Guía corta de 4 pasos que se muestra tras elegir el modo (y desde Ajustes). */
@Composable
fun IntroGuide(onDone: () -> Unit) {
    val pages = remember(Features.study) {
        buildList {
            add(IntroPage("📚", "Tu biblioteca", "Agrega manhwas con el botón + o impórtalos desde Komikku. Con el botón + de cada tarjeta sumas un capítulo."))
            add(IntroPage("🔄", "Sincroniza con Komikku", "En Más › Sincronización elige la carpeta donde Komikku guarda sus copias: la app las lee sola y tu progreso se actualiza."))
            if (Features.study) add(IntroPage("🔤", "Guarda palabras", "Selecciona texto en cualquier app y elige \"Guardar en vocabulario\". Luego repásalas con tarjetas en Repaso."))
            else add(IntroPage("💡", "Descubre", "En Descubrir verás recomendaciones según lo que lees, tendencias y reseñas."))
            add(IntroPage("🎨", "Hazla tuya", "En Más › Apariencia y Icono cambias colores y diseño. En Ajustes activas los avisos de versión nueva."))
        }
    }
    var i by remember { mutableStateOf(0) }
    val page = pages[i]
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().background(brandBrush()).padding(horizontal = 24.dp, vertical = 56.dp)) {
            Column {
                Text(page.icon, fontSize = 48.sp)
                Spacer(Modifier.height(8.dp))
                Text(page.title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.weight(1f).padding(24.dp)) {
            Text(page.text, style = MaterialTheme.typography.bodyLarge)
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${i + 1} / ${pages.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (i < pages.lastIndex) {
                OutlinedButton(onClick = onDone) { Text("Saltar") }
                Spacer(Modifier.padding(4.dp))
                Button(onClick = { i++ }) { Text("Siguiente") }
            } else {
                Button(onClick = onDone) { Text("Empezar") }
            }
        }
    }
}
