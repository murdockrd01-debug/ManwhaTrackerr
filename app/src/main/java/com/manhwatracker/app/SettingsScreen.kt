package com.manhwatracker.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.width
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

@Composable
private fun SettingsPage(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        content()
    }
}

@Composable
private fun NavRow(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 22.sp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", fontSize = 24.sp, color = MaterialTheme.colorScheme.outline)
    }
}

/** Ajustes: una lista corta; cada fila abre su propia página. */
@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    androidx.activity.compose.BackHandler(enabled = page != null) { page = null }
    when (page) {
        "general" -> SettingsPage("General") { GeneralPage() }
        "capture" -> SettingsPage("Captura rápida") { CapturePage() }
        "updates" -> UpdatesPage()
        "help" -> SettingsPage("Ayuda y errores") { HelpPage() }
        else -> Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingCard {
                SwitchRow(
                    "Estudio de inglés",
                    if (Features.study) "Activo: palabras, repaso y racha." else "Apagado: solo tracker.",
                    Features.study
                ) { Features.setStudy(ctx, it) }
            }
            Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column {
                    NavRow("🌐", "General", if (Features.study) "Idioma, app para leer y orden del repaso" else "Idioma y app para leer") { page = "general" }
                    androidx.compose.material3.HorizontalDivider()
                    if (Features.study) {
                        NavRow("⚡", "Captura rápida", if (CaptureService.isEnabled(ctx)) "Activada" else "Sin activar") { page = "capture" }
                        androidx.compose.material3.HorizontalDivider()
                    }
                    NavRow("⬆️", "Actualizaciones", "Versión ${UpdateCheck.installedVersion(ctx)}") { page = "updates" }
                    androidx.compose.material3.HorizontalDivider()
                    NavRow("🛟", "Ayuda y errores", "Guía de inicio y registro de errores") { page = "help" }
                }
            }
        }
    }
}

@Composable
private fun GeneralPage() {
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingCard {
            Text("Idioma", style = MaterialTheme.typography.titleMedium)
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

        SettingCard {
            val readers = remember { ReaderLink.installed(ctx) }
            var chosen by remember { mutableStateOf(Prefs.reader(ctx)) }
            Text("App para leer", style = MaterialTheme.typography.titleMedium)
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

    }
}

@Composable
private fun CapturePage() {
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (Features.study) {
            SettingCard {
                var on by remember { mutableStateOf(CaptureService.isEnabled(ctx)) }
                val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
                androidx.compose.runtime.DisposableEffect(owner) {
                    val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
                        if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) on = CaptureService.isEnabled(ctx)
                    }
                    owner.lifecycle.addObserver(obs)
                    onDispose { owner.lifecycle.removeObserver(obs) }
                }
                Text(if (on) "✓ Activada" else "Sin activar", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Mientras lees en Komikku, toma una captura con un gesto, toca los globos que quieras y se guardan en tu vocabulario con el manhwa y capítulo en que vas. " +
                        "Todo se lee dentro del teléfono; no se envía nada.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "1. Si Android lo pide, entra a la info de la app, toca ⋮ y elige \"Permitir ajustes restringidos\".\n" +
                        "2. En Accesibilidad, activa \"Manhwa Tracker: capturar texto\".\n" +
                        "3. Úsala con el botón de accesibilidad, manteniendo las dos teclas de volumen, o con el mosaico \"Guardar texto\" del panel rápido (edítalo para agregarlo).",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        ctx.startActivity(
                            android.content.Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.parse("package:" + ctx.packageName)
                            )
                        )
                    }) { Text("Info de la app") }
                    Button(onClick = {
                        ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }) { Text("Accesibilidad") }
                }
                var bubbleOn by remember { mutableStateOf(Prefs.bubble(ctx)) }
                SwitchRow(
                    "Burbuja flotante",
                    "Un botón que flota sobre Komikku: tócalo para escribir una palabra viendo el manhwa (sin notificación) y mantenlo pulsado para capturar la pantalla. Arrástralo donde quieras.",
                    bubbleOn
                ) {
                    bubbleOn = it
                    Prefs.setBubble(ctx, it)
                    CaptureService.instance?.refreshBubble()
                }
                if (bubbleOn && !on) {
                    Text("La burbuja aparece cuando activas la captura rápida en Accesibilidad.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            SettingCard {
                Text("Barra rápida en notificaciones", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Si la quitaste sin querer, vuelve a mostrarla aquí. Si la quitas deslizándola, vuelve a aparecer sola. Si no sale, revisa que las notificaciones de la app estén permitidas.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = {
                    Prefs.setQuickBar(ctx, true)
                    QuickAdd.show(ctx)
                }) { Text("Mostrar la barra otra vez") }
            }
        }

    }
}

@Composable
private fun UpdatesPage() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var autoCheck by remember { mutableStateOf(Prefs.updateCheck(ctx)) }
    var status by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf<String?>(null) }
    var showCurrent by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var apk by remember { mutableStateOf<java.io.File?>(null) }

    fun runCheck() {
        checking = true
        status = null
        scope.launch {
            val f = UpdateCheck.latest(ctx)
            checking = false
            found = f != null
            notes = f?.notes
            status = if (f != null) "Hay una versión nueva: ${f.tag}" else "Tienes la última versión (o no hay internet)."
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

    SettingsPage("Actualizaciones") {
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
            if (found) notes?.let {
                Text("Novedades:", style = MaterialTheme.typography.labelLarge)
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = { showCurrent = true }) { Text("Ver las novedades de esta versión") }
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }

    }
    if (showCurrent) {
        val cur = UpdateCheck.installedNotes(ctx)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCurrent = false },
            title = { Text("Novedades de la versión " + UpdateCheck.installedVersion(ctx)) },
            text = { Text(cur ?: "Esta versión no tiene novedades anotadas.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { showCurrent = false }) { Text("Entendido") } }
        )
    }
}


@Composable
private fun HelpPage() {
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingCard {
            Text("Guía de inicio", style = MaterialTheme.typography.titleMedium)
            Text("Un repaso rápido de cómo usar la app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { Features.showIntro = true }) { Text("Ver la guía") }
        }
        SettingCard {
            var errors by remember { mutableStateOf(com.manhwatracker.app.data.CrashLog.count(ctx)) }
            Text(
                if (errors == 0) "Sin errores guardados." else "Errores guardados: $errors",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Si la app se cierra sola o algo falla, el detalle queda aquí. Compártelo para poder arreglarlo; no incluye tus palabras ni tus manhwas.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = errors > 0, onClick = {
                    val text = com.manhwatracker.app.data.CrashLog.read(ctx)
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(android.content.Intent.EXTRA_SUBJECT, "Manhwa Tracker: registro de errores")
                        .putExtra(android.content.Intent.EXTRA_TEXT, text)
                    ctx.startActivity(android.content.Intent.createChooser(send, null))
                }) { Text("Compartir") }
                OutlinedButton(enabled = errors > 0, onClick = {
                    com.manhwatracker.app.data.CrashLog.clear(ctx)
                    errors = 0
                }) { Text("Borrar") }
            }
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
