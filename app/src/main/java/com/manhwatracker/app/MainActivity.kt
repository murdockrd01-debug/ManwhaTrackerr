package com.manhwatracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.manhwatracker.app.data.Manhwa
import com.manhwatracker.app.data.Status

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Si la barra rápida está activada, la volvemos a mostrar (por si se cerró o se reinició el teléfono).
        if (com.manhwatracker.app.data.Prefs.quickBar(this)) QuickAdd.show(this)
        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    App()
                }
            }
        }
    }
}

private fun fmt(d: Double): String =
    if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()

// ---------------------------------------------------------------- Lista

@Composable
fun ListScreen(vm: MainViewModel, onOpen: (Long) -> Unit) {
    val all by vm.items.collectAsState()
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = if (filter == null) all else all.filter { it.status == filter }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filter == null,
                onClick = { filter = null },
                label = { Text("Todos (${all.size})") }
            )
            Status.all.forEach { s ->
                FilterChip(
                    selected = filter == s,
                    onClick = { filter = if (filter == s) null else s },
                    label = { Text(Status.label(s)) }
                )
            }
        }

        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (all.isEmpty())
                        "Aún no tienes nada.\n\nToca “Importar backup” y elige el archivo .tachibk de Komikku, o usa el botón + para agregar uno a mano."
                    else
                        "No hay manhwas en esta categoría."
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp, end = 12.dp, top = 4.dp, bottom = 88.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(shown, key = { it.id }) { m ->
                    ManhwaCard(
                        m = m,
                        onClick = { onOpen(m.id) },
                        onPlusOne = { vm.increment(m) },
                        onCoverMissing = { vm.coverFallback(m) }
                    )
                }
            }
        }
    }
}

@Composable
fun ManhwaCard(
    m: Manhwa,
    onClick: () -> Unit,
    onPlusOne: () -> Unit,
    onCoverMissing: () -> Unit
) {
    LaunchedEffect(m.id, m.coverUrl) {
        if (m.coverUrl.isNullOrBlank()) onCoverMissing()
    }

    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(m, onCoverMissing, Modifier.width(56.dp).height(80.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    m.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val progress = if (m.totalChapters > 0)
                    "Cap. ${fmt(m.lastChapter)} / ${m.totalChapters}"
                else
                    "Cap. ${fmt(m.lastChapter)}"
                Text(
                    "$progress · ${Status.label(m.status)}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (m.totalChapters > 0) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (m.lastChapter / m.totalChapters).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            IconButton(onClick = onPlusOne) {
                Icon(Icons.Filled.Add, contentDescription = "Leí un capítulo más")
            }
        }
    }
}

@Composable
fun Cover(m: Manhwa, onError: () -> Unit, modifier: Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .then(Modifier),
        contentAlignment = Alignment.Center
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceVariant) {}
        if (!m.coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = m.coverUrl,
                contentDescription = m.title,
                contentScale = ContentScale.Crop,
                onError = { onError() },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ---------------------------------------------------------------- Detalle

@Composable
fun DetailScreen(vm: MainViewModel, id: Long, onClose: () -> Unit) {
    val flow = remember(id) { vm.observe(id) }
    val m by flow.collectAsState(initial = null)
    val item = m

    // Si se borró (o no existe), volvemos a la lista.
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(item) {
        if (item != null) seen = true
        else if (seen) onClose()
    }
    if (item == null) return

    var chapterText by remember(item.id) { mutableStateOf(fmt(item.lastChapter)) }
    var totalText by remember(item.id) { mutableStateOf(if (item.totalChapters > 0) item.totalChapters.toString() else "") }
    var status by remember(item.id) { mutableStateOf(item.status) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Cover(item, { vm.coverFallback(item) }, Modifier.width(110.dp).height(156.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { vm.coverFallback(item, force = true) }) {
                    Text("Buscar portada")
                }
            }
        }

        OutlinedTextField(
            value = chapterText,
            onValueChange = { chapterText = it },
            label = { Text("Último capítulo leído") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = totalText,
            onValueChange = { totalText = it.filter { c -> c.isDigit() } },
            label = { Text("Total de capítulos (opcional)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Text("Estado", style = MaterialTheme.typography.labelLarge)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Status.all.forEach { s ->
                FilterChip(
                    selected = status == s,
                    onClick = { status = s },
                    label = { Text(Status.label(s)) }
                )
            }
        }

        Button(
            onClick = {
                val chapter = chapterText.replace(',', '.').toDoubleOrNull() ?: item.lastChapter
                val total = totalText.toIntOrNull() ?: 0
                vm.save(item.copy(lastChapter = chapter, totalChapters = total, status = status))
                onClose()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Guardar") }

        TextButton(
            onClick = { confirmDelete = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Eliminar de mi lista") }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar?") },
            text = { Text("Se quitará “${item.title}” de tu lista. Si vuelves a importar el backup, regresará.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(item)
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") }
            }
        )
    }
}

// ---------------------------------------------------------------- Agregar a mano

@Composable
fun AddDialog(onDismiss: () -> Unit, onConfirm: (String, Double, Int) -> Unit) {
    var title by remember { mutableStateOf("") }
    var chapter by remember { mutableStateOf("") }
    var total by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar manhwa") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = chapter,
                    onValueChange = { chapter = it },
                    label = { Text("Capítulo actual") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = total,
                    onValueChange = { total = it.filter { c -> c.isDigit() } },
                    label = { Text("Total (opcional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("La portada se busca sola en AniList.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    onConfirm(
                        title,
                        chapter.replace(',', '.').toDoubleOrNull() ?: 0.0,
                        total.toIntOrNull() ?: 0
                    )
                }
            ) { Text("Agregar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
