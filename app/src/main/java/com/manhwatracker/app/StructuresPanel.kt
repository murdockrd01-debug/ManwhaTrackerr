package com.manhwatracker.app

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manhwatracker.app.data.AppDatabase
import com.manhwatracker.app.data.Prefs
import com.manhwatracker.app.data.PrintSheet
import com.manhwatracker.app.data.Structure
import com.manhwatracker.app.data.StructureDetector
import com.manhwatracker.app.data.Structures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private enum class Filter { ALL, PENDING, DONE }

/** Guía de estructuras del inglés (y tus frases). Vive dentro de la pestaña Estudio. */
@Composable
fun StructuresPanel() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var done by remember { mutableStateOf(Prefs.structDone(ctx)) }
    var filter by remember { mutableStateOf(Filter.ALL) }
    var open by remember { mutableStateOf<String?>(null) }
    var showPrint by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }
    var myPhrases by remember { mutableStateOf(Prefs.structPhrases(ctx)) }
    val shown = remember(done, filter) {
        Structures.all.filter { when (filter) { Filter.ALL -> true; Filter.PENDING -> it.id !in done; Filter.DONE -> it.id in done } }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${Structures.all.count { it.id in done }} / ${Structures.all.size} " + tr("Entendidas").lowercase(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = { showPrint = true }) { Text("🖨 " + tr("Imprimir / PDF")) }
        }
        LinearProgressIndicator(progress = { Structures.progress(done) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Guía") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Mis frases") })
        }
        if (tab == 1) {
            PhrasesTab(myPhrases) { myPhrases = it; Prefs.setStructPhrases(ctx, it) }
        } else {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter == Filter.ALL, onClick = { filter = Filter.ALL }, label = { Text("Todas") })
                FilterChip(selected = filter == Filter.PENDING, onClick = { filter = Filter.PENDING }, label = { Text("Pendientes") })
                FilterChip(selected = filter == Filter.DONE, onClick = { filter = Filter.DONE }, label = { Text("Entendidas") })
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(shown, key = { it.id }) { s ->
                    StructureCard(
                        s, expanded = open == s.id, understood = s.id in done,
                        onToggle = { open = if (open == s.id) null else s.id },
                        onUnderstood = {
                            done = if (s.id in done) done - s.id else done + s.id
                            Prefs.setStructDone(ctx, done)
                        }
                    )
                }
            }
        }
    }

    if (showPrint) {
        var withStructures by remember { mutableStateOf(true) }
        var withWords by remember { mutableStateOf(true) }
        var onlyPending by remember { mutableStateOf(true) }
        var withPhrases by remember { mutableStateOf(myPhrases.isNotEmpty()) }
        AlertDialog(
            onDismissRequest = { showPrint = false },
            title = { Text("Qué incluir en la hoja") },
            text = {
                Column {
                    CheckRow("Estructuras (con espacio para tus notas)", withStructures) { withStructures = it }
                    if (myPhrases.isNotEmpty()) CheckRow("Mis frases agrupadas por estructura (para practicar a mano)", withPhrases) { withPhrases = it }
                    CheckRow("Mis palabras guardadas (con columna para tu frase)", withWords) { withWords = it }
                    if (withWords) CheckRow("Solo las que aún no aprendí", onlyPending) { onlyPending = it }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val words = if (withWords) withContext(Dispatchers.IO) {
                            AppDatabase.get(ctx).words().all().filter { !onlyPending || !it.learned }
                                .map { PrintSheet.WordRow(it.text, it.translation, it.example) }
                        } else emptyList()
                        val structs = if (withStructures) Structures.all else emptyList()
                        val groups = if (withPhrases) PrintSheet.group(myPhrases) else emptyList()
                        if (structs.isEmpty() && words.isEmpty() && groups.isEmpty()) {
                            Toast.makeText(ctx, tr("Nada que imprimir: elige algo"), Toast.LENGTH_SHORT).show()
                        } else {
                            showPrint = false
                            StudyPrint.print(ctx, PrintSheet.html(structs, words, DateFormat.getDateInstance().format(Date()), groups))
                        }
                    }
                }) { Text("Imprimir o guardar PDF") }
            },
            dismissButton = { TextButton(onClick = { showPrint = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StructureCard(s: Structure, expanded: Boolean, understood: Boolean, onToggle: () -> Unit, onUnderstood: () -> Unit) {
    val ctx = LocalContext.current
    Card(Modifier.fillMaxWidth().clickable(onClick = onToggle), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Text(s.pattern, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                if (understood) Text("✓", fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
            }
            if (expanded) {
                Text(s.explanation, style = MaterialTheme.typography.bodyMedium)
                s.examples.forEach { (en, es) ->
                    Column {
                        Text(en, fontWeight = FontWeight.Medium)
                        Text(es, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("⚠ " + s.tip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                // Pruébate: completar la frase (recordar activamente sirve más que solo releer).
                var typed by remember(s.id) { mutableStateOf("") }
                var verdict by remember(s.id) { mutableStateOf<Boolean?>(null) }
                var reveal by remember(s.id) { mutableStateOf(false) }
                Text("Pruébate", fontWeight = FontWeight.SemiBold)
                Text(s.quiz.first)
                OutlinedTextField(
                    value = typed, onValueChange = { typed = it; verdict = null }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Escribe la respuesta") }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(enabled = typed.isNotBlank(), onClick = { verdict = Structures.isCorrect(s.quiz.second, typed) }) { Text("Comprobar") }
                    TextButton(onClick = { reveal = true }) { Text("Ver respuesta") }
                    when (verdict) {
                        true -> Text("¡Correcto!", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        false -> Text("Casi. Mira la respuesta.", color = MaterialTheme.colorScheme.error)
                        null -> {}
                    }
                }
                if (reveal || verdict == false) Text(tr("Respuesta: ") + s.quiz.second, fontWeight = FontWeight.Medium)

                var note by remember(s.id) { mutableStateOf(Prefs.structNote(ctx, s.id)) }
                OutlinedTextField(
                    value = note, onValueChange = { note = it; Prefs.setStructNote(ctx, s.id, it) }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Mi nota") }, minLines = 2
                )
                OutlinedButton(onClick = onUnderstood) { Text(if (understood) "Marcar como pendiente" else "Ya la entiendo") }
            }
        }
    }
}

/** Escribe o pega una frase en inglés y la app dice qué estructuras usa; guarda tus frases para practicar y para imprimir. */
@Composable
private fun PhrasesTab(saved: List<String>, onChange: (List<String>) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var analyzed by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Escribe o pega una frase en inglés y te digo qué estructura usa.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = input, onValueChange = { input = it; analyzed = null }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Frase en inglés") }, minLines = 2
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = input.isNotBlank(), onClick = { analyzed = input.trim() }) { Text("Analizar") }
                    OutlinedButton(enabled = input.isNotBlank(), onClick = {
                        val one = input.trim().replace(Regex("\\s+"), " ")
                        if (one !in saved) onChange(saved + one)
                        analyzed = one
                    }) { Text("Guardar frase") }
                }
                analyzed?.let { MatchList(it) }
                OutlinedButton(onClick = {
                    scope.launch {
                        val fromStudy = withContext(Dispatchers.IO) {
                            AppDatabase.get(ctx).words().all().map { it.text.trim() }.filter { it.any { c -> c.isWhitespace() } }
                        }
                        val add = fromStudy.filter { it !in saved }
                        if (add.isEmpty()) Toast.makeText(ctx, tr("No hay frases nuevas en Estudio"), Toast.LENGTH_SHORT).show()
                        else onChange(saved + add)
                    }
                }) { Text("Traer mis frases de Estudio") }
                if (saved.isNotEmpty()) Text("Mis frases guardadas", fontWeight = FontWeight.SemiBold)
            }
        }
        items(saved, key = { it }) { s ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(s, fontWeight = FontWeight.Medium)
                    MatchList(s)
                    TextButton(onClick = { onChange(saved - s) }) { Text("Quitar") }
                }
            }
        }
    }
}

@Composable
private fun MatchList(sentence: String) {
    val matches = remember(sentence) { StructureDetector.detect(sentence) }
    if (matches.isEmpty()) {
        Text("No reconocí una estructura clara. Prueba con una frase completa.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        matches.forEach { m ->
            val st = Structures.byId(m.id) ?: return@forEach
            Column {
                Text("📐 " + st.title + " — " + m.why, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                Text(st.pattern, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
