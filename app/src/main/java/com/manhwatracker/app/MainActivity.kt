package com.manhwatracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.manhwatracker.app.data.Manhwa
import com.manhwatracker.app.data.Prefs
import com.manhwatracker.app.data.Status
import com.manhwatracker.app.data.titleKeyOf

class MainActivity : ComponentActivity() {
    /**
     * Cada vez que la app pasa a primer plano (al abrirla o al volver a ella) se mira si hay una copia
     * nueva de Komikku. Así una copia manual hecha mientras la app estaba en segundo plano se lee al volver.
     */
    override fun onStart() {
        super.onStart()
        ViewModelProvider(this)[MainViewModel::class.java].autoSync()
    }

    /** Al salir de la app se guarda la copia automática (si hay carpeta elegida y ya toca). */
    override fun onStop() {
        super.onStop()
        com.manhwatracker.app.data.AutoSync.backupInBackground(this)
    }

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

/** Formas de ordenar la lista. El texto guardado en Prefs es el valor de cada constante. */
private object SortMode {
    const val RECENT = "RECENT"
    const val TITLE = "TITLE"
    const val NEAR_END = "NEAR_END"
    const val MOST_LEFT = "MOST_LEFT"
    const val RATING = "RATING"

    val all = listOf(RECENT, TITLE, NEAR_END, MOST_LEFT, RATING)

    fun label(s: String) = when (s) {
        RECENT -> "Recientes"
        TITLE -> "Título (A–Z)"
        NEAR_END -> "Casi terminados"
        MOST_LEFT -> "Más por leer"
        RATING -> "Mejor calificados"
        else -> s
    }
}

/** Capítulos que te faltan, o null si no sabemos el total. */
private fun remaining(m: Manhwa): Double? =
    if (m.totalChapters > 0) maxOf(0.0, m.totalChapters - m.lastChapter) else null

private fun sortItems(list: List<Manhwa>, mode: String): List<Manhwa> = when (mode) {
    SortMode.TITLE -> list.sortedBy { it.title.lowercase() }
    // Primero los que les faltan pocos capítulos; los ya terminados y los sin total van al final.
    SortMode.NEAR_END -> list.sortedWith(
        compareBy<Manhwa>(
            { m -> val r = remaining(m); if (r == null) 2 else if (r == 0.0) 1 else 0 },
            { m -> remaining(m) ?: 0.0 }
        )
    )
    SortMode.MOST_LEFT -> list.sortedWith(
        compareBy<Manhwa> { remaining(it) == null }.thenByDescending { remaining(it) ?: 0.0 }
    )
    // Los que no tienen nota (0) quedan al final.
    SortMode.RATING -> list.sortedWith(compareByDescending<Manhwa> { it.rating }.thenBy { it.title.lowercase() })
    else -> list // RECENT: la base de datos ya los entrega del más reciente al más viejo
}

@Composable
fun ListScreen(vm: MainViewModel, onOpen: (Long) -> Unit) {
    val context = LocalContext.current
    val all by vm.items.collectAsState()
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by remember { mutableStateOf(Prefs.sort(context)) }
    var sortMenu by remember { mutableStateOf(false) }
    var grid by remember { mutableStateOf(Prefs.grid(context)) }

    val shown = remember(all, filter, query, sort) {
        val byStatus = if (filter == null) all else all.filter { it.status == filter }
        // titleKey ya viene sin mayúsculas, espacios ni símbolos: la búsqueda los ignora también.
        val key = titleKeyOf(query.trim())
        val bySearch = if (query.isBlank()) byStatus else byStatus.filter { it.titleKey.contains(key) }
        sortItems(bySearch, sort)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Buscar") },
                singleLine = true,
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Borrar búsqueda")
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            )
            Box {
                OutlinedButton(onClick = { sortMenu = true }) { Text("Orden y vista") }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    SortMode.all.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(SortMode.label(mode) + if (mode == sort) "  ✓" else "") },
                            onClick = {
                                sort = mode
                                Prefs.setSort(context, mode)
                                sortMenu = false
                            }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(if (grid) "Ver como lista" else "Ver como cuadrícula de portadas") },
                        onClick = {
                            grid = !grid
                            Prefs.setGrid(context, grid)
                            sortMenu = false
                        }
                    )
                }
            }
        }

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
                    when {
                        all.isEmpty() ->
                            "Aún no tienes nada.\n\nToca “Importar backup” y elige el archivo .tachibk de Komikku, o usa el botón + para agregar uno a mano."
                        query.isNotBlank() -> "Nada coincide con “${query.trim()}”."
                        else -> "No hay manhwas en esta categoría."
                    }
                )
            }
        } else if (grid) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp, end = 12.dp, top = 4.dp, bottom = 88.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(shown, key = { it.id }) { m ->
                    ManhwaGridCard(
                        m = m,
                        onClick = { onOpen(m.id) },
                        onPlusOne = { vm.increment(m) },
                        onCoverMissing = { vm.coverFallback(m) }
                    )
                }
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
                    "$progress · ${Status.label(m.status)}" + if (m.rating > 0) " · ★ ${m.rating}" else "",
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

/** Tarjeta de la vista en cuadrícula: portada grande, título y capítulo. */
@Composable
fun ManhwaGridCard(
    m: Manhwa,
    onClick: () -> Unit,
    onPlusOne: () -> Unit,
    onCoverMissing: () -> Unit
) {
    LaunchedEffect(m.id, m.coverUrl) {
        if (m.coverUrl.isNullOrBlank()) onCoverMissing()
    }

    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column {
            Cover(m, onCoverMissing, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
            Row(
                Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        m.title,
                        style = MaterialTheme.typography.titleSmall,
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "Cap. ${fmt(m.lastChapter)}" + if (m.rating > 0) " · ★ ${m.rating}" else "",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(onClick = onPlusOne, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = "Leí un capítulo más")
                }
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

/**
 * Detalle de un manhwa: progreso, estado, tu nota y comentario, y las palabras que guardaste de él.
 * [onReviewWords] abre el repaso solo con las palabras de este manhwa (recibe su titleKey y su título).
 */
@Composable
fun DetailScreen(
    vm: MainViewModel,
    id: Long,
    onClose: () -> Unit,
    onReviewWords: (titleKey: String, title: String) -> Unit
) {
    val flow = remember(id) { vm.observe(id) }
    val m by flow.collectAsState(initial = null)
    val allWords by vm.words.collectAsState()
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
    var rating by remember(item.id) { mutableStateOf(item.rating) }
    var review by remember(item.id) { mutableStateOf(item.review.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Las palabras de este manhwa: se comparan los títulos sin mayúsculas ni símbolos.
    val mine = remember(allWords, item.titleKey) {
        allWords.filter { w -> w.manhwaTitle?.let { titleKeyOf(it) } == item.titleKey }
    }
    val pending = mine.count { !it.learned }

    /** Guarda lo que hay escrito en la pantalla. */
    fun saveEdits() {
        val chapter = chapterText.replace(',', '.').toDoubleOrNull() ?: item.lastChapter
        val total = totalText.toIntOrNull() ?: 0
        vm.saveDetail(
            item,
            item.copy(
                lastChapter = chapter,
                totalChapters = total,
                status = status,
                rating = rating,
                review = review.trim().ifEmpty { null }
            )
        )
    }

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

        Text(
            if (rating > 0) "Mi nota: $rating / 10" else "Mi nota",
            style = MaterialTheme.typography.labelLarge
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            (1..10).forEach { n ->
                FilterChip(
                    selected = rating == n,
                    // Tocar la nota elegida otra vez la quita.
                    onClick = { rating = if (rating == n) 0 else n },
                    label = { Text("$n") }
                )
            }
        }
        OutlinedTextField(
            value = review,
            onValueChange = { review = it },
            label = { Text("Mi comentario (opcional)") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider()
        Text(
            "Palabras de este manhwa (${mine.size})",
            style = MaterialTheme.typography.titleSmall
        )
        if (mine.isEmpty()) {
            Text(
                "Aún no guardaste palabras de este manhwa. En Estudio, al guardar una palabra, elige este título.",
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            mine.forEach { w ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        w.text + (w.translation?.let { " — $it" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (w.learned) {
                        Text("✓", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            OutlinedButton(
                onClick = {
                    saveEdits()
                    onReviewWords(item.titleKey, item.title)
                },
                enabled = pending > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (pending > 0) "Repasar solo estas ($pending)" else "Ya aprendiste todas estas")
            }
        }
        HorizontalDivider()

        Button(
            onClick = {
                saveEdits()
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
