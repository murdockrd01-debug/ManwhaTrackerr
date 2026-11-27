package com.manhwatracker.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.manhwatracker.app.data.Structures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Estructuras gramaticales del inglés: explicación, práctica rápida, tus notas y hoja para imprimir. Solo con el modo estudio. */
class StructuresActivity : ComponentActivity() {
    // Se guarda para que el WebView no se libere antes de terminar de preparar la impresión.
    private var printView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeState.load(this)
        setContent { ManhwaTheme { Surface(Modifier.fillMaxSize()) { StructuresScreen(onBack = { finish() }, onPrint = { printHtml(it) }) } } }
    }

    private fun printHtml(html: String) {
        val wv = WebView(this)
        printView = wv
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val pm = getSystemService(Context.PRINT_SERVICE) as PrintManager
                pm.print("Lectoon - Estudio", view.createPrintDocumentAdapter("Lectoon-estudio"), PrintAttributes.Builder().build())
            }
        }
        wv.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    companion object {
        fun open(ctx: Context) = ctx.startActivity(Intent(ctx, StructuresActivity::class.java))
    }
}

private enum class Filter { ALL, PENDING, DONE }

@Composable
private fun StructuresScreen(onBack: () -> Unit, onPrint: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var done by remember { mutableStateOf(Prefs.structDone(ctx)) }
    var filter by remember { mutableStateOf(Filter.ALL) }
    var open by remember { mutableStateOf<String?>(null) }
    var showPrint by remember { mutableStateOf(false) }
    val shown = remember(done, filter) {
        Structures.all.filter { when (filter) { Filter.ALL -> true; Filter.PENDING -> it.id !in done; Filter.DONE -> it.id in done } }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Volver")) }
            Column(Modifier.weight(1f)) {
                Text("Estructuras", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("${Structures.all.count { it.id in done }} / ${Structures.all.size} " + tr("Entendidas").lowercase(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { showPrint = true }) { Text("🖨 " + tr("Imprimir / PDF")) }
        }
        LinearProgressIndicator(progress = { Structures.progress(done) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = filter == Filter.ALL, onClick = { filter = Filter.ALL }, label = { Text("Todas") })
            FilterChip(selected = filter == Filter.PENDING, onClick = { filter = Filter.PENDING }, label = { Text("Pendientes") })
            FilterChip(selected = filter == Filter.DONE, onClick = { filter = Filter.DONE }, label = { Text("Entendidas") })
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

    if (showPrint) {
        var withStructures by remember { mutableStateOf(true) }
        var withWords by remember { mutableStateOf(true) }
        var onlyPending by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showPrint = false },
            title = { Text("Qué incluir en la hoja") },
            text = {
                Column {
                    CheckRow("Estructuras (con espacio para tus notas)", withStructures) { withStructures = it }
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
                        if (structs.isEmpty() && words.isEmpty()) {
                            Toast.makeText(ctx, tr("Nada que imprimir: elige algo"), Toast.LENGTH_SHORT).show()
                        } else {
                            showPrint = false
                            onPrint(PrintSheet.html(structs, words, DateFormat.getDateInstance().format(Date())))
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
