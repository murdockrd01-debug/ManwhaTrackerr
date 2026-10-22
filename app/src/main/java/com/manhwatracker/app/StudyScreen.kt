package com.manhwatracker.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.manhwatracker.app.data.Word

private const val F_ALL = "ALL"
private const val F_REVIEW = "REVIEW"
private const val F_LEARNED = "LEARNED"

@Composable
fun StudyScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val all by vm.words.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(F_ALL) }
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
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Barra rápida en notificaciones", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Guarda palabras sin salir de Komikku: baja la barra de notificaciones, toca “Guardar palabra”, escribe y envía. " +
                                "También puedes seleccionar texto en otras apps y elegir “Guardar en vocabulario”.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = quickBar,
                        onCheckedChange = { checked ->
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
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Buscar palabra, traducción o manhwa") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filter == F_ALL,
                    onClick = { filter = F_ALL },
                    label = { Text("Todas (${all.size})") }
                )
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
            item {
                Text(
                    "Toca una palabra para ver el significado. Marca la casilla cuando ya te la sepas.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            items(shown, key = { it.id }) { w ->
                WordCard(
                    w = w,
                    onLearned = { vm.toggleLearned(w) },
                    onRetry = { vm.relookup(w) },
                    onDelete = { vm.deleteWord(w) }
                )
            }
        }
    }
}

@Composable
fun WordCard(w: Word, onLearned: () -> Unit, onRetry: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(w.text, style = MaterialTheme.typography.titleMedium)
                    Text(
                        w.translation
                            ?: if (!w.lookupDone) "Traducción pendiente (se busca cuando haya internet)" else "—",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Checkbox(checked = w.learned, onCheckedChange = { onLearned() })
            }

            val tag = listOfNotNull(w.manhwaTitle, w.chapter?.let { "cap. $it" }).joinToString(" · ")
            if (tag.isNotBlank()) {
                Text(tag, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            if (expanded) {
                w.definition?.let {
                    Text("English", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                w.definitionEs?.let {
                    Text("Español", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                w.example?.let {
                    Text("Ej: $it", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
                }
                w.note?.let { Text("Nota: $it", style = MaterialTheme.typography.bodyMedium) }
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

@Composable
fun AddWordDialog(
    manhwas: List<Manhwa>,
    onDismiss: () -> Unit,
    onConfirm: (text: String, manhwa: String, chapter: String, note: String) -> Unit
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var manhwa by remember {
        mutableStateOf(Prefs.lastManhwa(context) ?: manhwas.firstOrNull()?.title.orEmpty())
    }
    var chapter by remember { mutableStateOf(Prefs.lastChapter(context).orEmpty()) }

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
                OutlinedTextField(
                    value = manhwa,
                    onValueChange = { manhwa = it },
                    label = { Text("Manhwa (opcional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (manhwas.isNotEmpty()) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        manhwas.take(8).forEach { m ->
                            FilterChip(
                                selected = manhwa == m.title,
                                onClick = { manhwa = m.title },
                                label = { Text(m.title, maxLines = 1) }
                            )
                        }
                    }
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
}
