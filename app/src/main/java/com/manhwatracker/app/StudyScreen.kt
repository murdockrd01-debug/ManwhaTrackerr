package com.manhwatracker.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.manhwatracker.app.data.Manhwa
import com.manhwatracker.app.data.Prefs
import com.manhwatracker.app.data.titleKeyOf
import com.manhwatracker.app.data.Status
import com.manhwatracker.app.data.Utility
import com.manhwatracker.app.data.Word

private const val F_ALL = "ALL"
private const val F_REVIEW = "REVIEW"
private const val F_LEARNED = "LEARNED"

private const val S_RECENT = "RECENT"
private const val S_USEFUL = "USEFUL"
private const val S_AZ = "AZ"

@Composable
fun StudyScreen(vm: MainViewModel, onReview: () -> Unit) {
    val context = LocalContext.current
    val all by vm.words.collectAsState()
    val speaker = rememberSpeaker()

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(F_ALL) }
    var sort by rememberSaveable { mutableStateOf(S_RECENT) }
    var searching by rememberSaveable { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(searching) { if (searching) runCatching { searchFocus.requestFocus() } }
    var quickBar by remember { mutableStateOf(Prefs.quickBar(context)) }

    // Completa las palabras que se guardaron sin internet.
    LaunchedEffect(Unit) { vm.retryPending() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Prefs.setQuickBar(context, true)
            quickBar = true
            QuickAdd.show(context)
        } else {
            vm.showMessage("Sin permiso de notificaciones no puedo mostrar la barra rápida")
        }
    }

    val q = query.trim()
    val shown = all.filter { w ->
        val okFilter = when (filter) {
            F_REVIEW -> !w.learned
            F_LEARNED -> w.learned
            else -> true
        }
        val okQuery = q.isEmpty() ||
            listOfNotNull(w.text, w.translation, w.manhwaTitle).any { it.contains(q, ignoreCase = true) }
        okFilter && okQuery
    }.let { list ->
        when (sort) {
            S_USEFUL -> list.sortedWith(Utility.byUsefulness)
            S_AZ -> list.sortedBy { it.text.lowercase() }
            else -> list
        }
    }

    // Las 3 más útiles que todavía no aprendes: buen punto de partida.
    val suggested = all.filter {
        !it.learned && Utility.tier(it.freq, it.text).let { t -> t == Utility.Tier.VERY_USEFUL || t == Utility.Tier.USEFUL }
    }.sortedWith(Utility.byUsefulness).take(3)

    var menuOpen by remember { mutableStateOf(false) }

    fun toggleQuickBar(checked: Boolean) {
        if (checked) {
            val needsPermission = Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            if (needsPermission) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                Prefs.setQuickBar(context, true)
                quickBar = true
                QuickAdd.show(context)
            }
        } else {
            Prefs.setQuickBar(context, false)
            quickBar = false
            QuickAdd.hide(context)
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Una sola tarjeta de arriba: repaso y, si hay, las palabras que más te conviene aprender.
        item {
            val now = System.currentTimeMillis()
            val due = all.count { !it.learned && it.dueAt <= now }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(brandBrush())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            due == 1 -> "1 palabra por repasar"
                            due > 1 -> "$due palabras por repasar"
                            else -> "Todo al día"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    if (suggested.isNotEmpty()) {
                        Text(
                            "Empieza por: " + suggested.joinToString(", ") { it.text },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1
                        )
                    }
                }
                Button(
                    onClick = onReview,
                    enabled = all.any { !it.learned },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF3B2A99))
                ) { Text("Repasar") }
            }
        }

        // Barra compacta: lupa, filtros y menú (orden y barra rápida).
        item {
            if (searching) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Buscar palabra, traducción o manhwa") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).focusRequester(searchFocus)
                    )
                    IconButton(onClick = {
                        query = ""
                        searching = false
                    }) { Icon(Icons.Filled.Clear, contentDescription = tr("Cerrar búsqueda")) }
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { searching = true }) {
                        Icon(Icons.Filled.Search, contentDescription = tr("Buscar"))
                    }
                    Row(
                        Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(selected = filter == F_ALL, onClick = { filter = F_ALL }, label = { Text("Todas (${all.size})") })
                        FilterChip(
                            selected = filter == F_REVIEW,
                            onClick = { filter = if (filter == F_REVIEW) F_ALL else F_REVIEW },
                            label = { Text("Por repasar (${all.count { !it.learned }})") }
                        )
                        FilterChip(
                            selected = filter == F_LEARNED,
                            onClick = { filter = if (filter == F_LEARNED) F_ALL else F_LEARNED },
                            label = { Text("Aprendidas (${all.count { it.learned }})") }
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = tr("Orden y opciones"))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            listOf(S_RECENT to "Orden: recientes", S_USEFUL to "Orden: más útiles primero", S_AZ to "Orden: A–Z").forEach { (id, label) ->
                                DropdownMenuItem(
                                    text = { Text(label + if (sort == id) "  ✓" else "") },
                                    onClick = {
                                        sort = id
                                        menuOpen = false
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Barra rápida en notificaciones" + if (quickBar) "  ✓" else "") },
                                onClick = {
                                    menuOpen = false
                                    toggleQuickBar(!quickBar)
                                }
                            )
                        }
                    }
                }
            }
        }

        if (shown.isEmpty()) {
            item {
                Text(
                    if (all.isEmpty())
                        "Todavía no tienes palabras.\n\nToca el botón + para guardar una palabra o una línea de diálogo. La traducción y el significado se buscan solos."
                    else
                        "No hay palabras con ese filtro.",
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            items(shown, key = { it.id }) { w ->
                WordCard(
                    w = w,
                    speaker = speaker,
                    onUnavailable = { vm.showMessage(SPEAKER_UNAVAILABLE) },
                    onLearned = { vm.toggleLearned(w) },
                    onRetry = { vm.relookup(w) },
                    onDelete = { vm.deleteWord(w) }
                )
            }
        }
    }
}

/** Etiqueta de color con el nivel de utilidad de la palabra. */
@Composable
fun TierBadge(tier: Utility.Tier) {
    if (tier == Utility.Tier.UNKNOWN) return
    val color = tierColor(tier)
    Text(
        tier.label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
fun WordCard(
    w: Word,
    speaker: Speaker,
    onUnavailable: () -> Unit,
    onLearned: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val tier = Utility.tier(w.freq, w.text)

    Card(Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (tier != Utility.Tier.UNKNOWN) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(RoundedCornerShape(50))
                            .background(tierColor(tier))
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(w.text, style = MaterialTheme.typography.titleMedium)
                    Text(
                        w.translation
                            ?: if (!w.lookupDone) "Traducción pendiente (se busca cuando haya internet)" else "—",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Checkbox(checked = w.learned, onCheckedChange = { onLearned() })
            }

            val tag = listOfNotNull(w.manhwaTitle, w.chapter?.let { "cap. $it" }).joinToString(" · ")
            if (tag.isNotBlank()) {
                Text(tag, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }

            if (expanded) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TierBadge(tier)
                    Text(tier.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(1f))
                    TextButton(onClick = { if (!speaker.speak(w.text)) onUnavailable() }) { Text("🔊 Escuchar") }
                }

                // Tu propia línea de diálogo: suele ser el mejor ejemplo para recordarla.
                w.note?.let {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                            .padding(10.dp)
                    ) {
                        Text("En tu manhwa", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(it, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
                    }
                }

                w.definition?.let {
                    Text("Significado (English)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                w.definitionEs?.let {
                    Text("Significado (Español)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }

                val examples = w.example?.split("\n")?.filter { it.isNotBlank() }.orEmpty()
                if (examples.isNotEmpty()) {
                    Text("Ejemplos", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    examples.forEach { line ->
                        val (en, es) = Utility.splitExample(line)
                        Column(Modifier.padding(bottom = 2.dp)) {
                            Text("• $en", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
                            es?.let {
                                Text(
                                    "   $it",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (w.definition == null && w.definitionEs == null && w.example == null && w.note == null) {
                    Text(
                        "No encontré una definición para esto. Toca “Buscar de nuevo” cuando tengas internet.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row {
                    TextButton(onClick = onRetry) { Text("Buscar de nuevo") }
                    TextButton(onClick = { confirmDelete = true }) { Text("Eliminar") }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar?") },
            text = { Text("Se borrará “${w.text}” de tu vocabulario.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") }
            }
        )
    }
}

/** Lista con buscador para elegir a qué manhwa pertenece una palabra. [onPick] recibe el título (o null = ninguno). */
@Composable
fun ManhwaPickerDialog(
    manhwas: List<Manhwa>,
    current: String?,
    onPick: (Manhwa?) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val key = titleKeyOf(query.trim())
    val shown = remember(manhwas, query) {
        val list = if (query.isBlank()) manhwas else manhwas.filter { it.titleKey.contains(key) }
        // Los que estás leyendo salen primero.
        list.sortedBy { if (it.status == Status.READING) 0 else 1 }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿De qué manhwa es?") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Buscar") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.height(300.dp)) {
                    item {
                        TextButton(onClick = { onPick(null) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Ninguno (sin manhwa)")
                        }
                    }
                    if (shown.isEmpty()) {
                        item { Text("Nada coincide.", style = MaterialTheme.typography.bodySmall) }
                    }
                    items(shown, key = { it.id }) { m ->
                        TextButton(onClick = { onPick(m) }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                (if (m.title == current) "✓ " else "") + m.title,
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun AddWordDialog(
    manhwas: List<Manhwa>,
    onDismiss: () -> Unit,
    onConfirm: (text: String, manhwa: String, chapter: String, note: String) -> Unit,
    initialManhwa: String? = null,
    initialChapter: String? = null
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var manhwa by remember {
        mutableStateOf(initialManhwa ?: Prefs.lastManhwa(context) ?: manhwas.firstOrNull()?.title.orEmpty())
    }
    var chapter by remember { mutableStateOf(initialChapter ?: Prefs.lastChapter(context).orEmpty()) }
    var picking by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Guardar palabra o frase") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Palabra o línea de diálogo") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Nota o contexto (opcional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Manhwa", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (manhwa.isBlank()) "Elegir manhwa…" else manhwa, maxLines = 1)
                }
                OutlinedTextField(
                    value = chapter,
                    onValueChange = { chapter = it },
                    label = { Text("Capítulo (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "La traducción y el significado se buscan solos.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = { onConfirm(text, manhwa, chapter, note) }
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )

    if (picking) {
        ManhwaPickerDialog(
            manhwas = manhwas,
            current = manhwa,
            onPick = { m ->
                if (m != null) {
                    // Al cambiar de manhwa se propone el último capítulo que llevas en él.
                    if (m.title != manhwa) chapter = if (m.lastChapter > 0) {
                        if (m.lastChapter % 1.0 == 0.0) m.lastChapter.toInt().toString() else m.lastChapter.toString()
                    } else ""
                    manhwa = m.title
                } else {
                    manhwa = ""
                    chapter = ""
                }
                picking = false
            },
            onDismiss = { picking = false }
        )
    }
}
