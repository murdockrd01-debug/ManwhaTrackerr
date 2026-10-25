package com.manhwatracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.compose.foundation.background
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.manhwatracker.app.data.AniList
import com.manhwatracker.app.data.Genres
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
        ThemeState.load(this)
        setContent {
            ManhwaTheme {
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

    val lists by vm.lists.collectAsState()
    val entries by vm.entries.collectAsState()
    var listFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    var genreFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var newList by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    // Trae los géneros de AniList en segundo plano (solo los que faltan).
    LaunchedEffect(all.size) { vm.fillMeta() }

    // Si la lista elegida se borró, se quita el filtro.
    val activeList = lists.firstOrNull { it.id == listFilter }
    LaunchedEffect(lists) { if (listFilter != null && activeList == null) listFilter = null }

    val genreCounts = remember(all) {
        all.flatMap { Genres.parse(it.genres) }.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(14)
    }

    val shown = remember(all, filter, query, sort, listFilter, genreFilter, entries) {
        val inList = listFilter?.let { id -> entries.filter { it.listId == id }.map { it.titleKey }.toSet() }
        val byList = if (inList == null) all else all.filter { it.titleKey in inList }
        val byGenre = if (genreFilter == null) byList else byList.filter { genreFilter in Genres.parse(it.genres) }
        val byStatus = if (filter == null) byGenre else byGenre.filter { it.status == filter }
        // titleKey ya viene sin mayúsculas, espacios ni símbolos: la búsqueda los ignora también.
        val key = titleKeyOf(query.trim())
        val bySearch = if (query.isBlank()) byStatus else byStatus.filter { it.titleKey.contains(key) }
        sortItems(bySearch, sort)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 4.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(brandBrush())
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            HeroStat("${all.count { it.status == Status.READING }}", "Leyendo")
            HeroStat("${all.count { it.status == Status.COMPLETED }}", "Terminados")
            HeroStat("${all.count { it.status == Status.PLAN }}", "Pendientes")
            HeroStat("${all.size}", "Total")
        }
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

        // Listas personalizadas
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Listas:", style = MaterialTheme.typography.labelLarge)
            lists.forEach { l ->
                FilterChip(
                    selected = listFilter == l.id,
                    onClick = { listFilter = if (listFilter == l.id) null else l.id },
                    label = { Text(l.name) }
                )
            }
            AssistChip(onClick = { newList = true }, label = { Text("＋ Nueva") })
        }
        if (activeList != null) {
            Row(Modifier.padding(horizontal = 12.dp)) {
                TextButton(onClick = { renaming = true }) { Text("Renombrar “${activeList.name}”") }
                TextButton(onClick = { deleting = true }) { Text("Eliminar lista") }
            }
        }

        // Géneros (vienen de AniList)
        if (genreCounts.isNotEmpty()) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Géneros:", style = MaterialTheme.typography.labelLarge)
                genreCounts.forEach { (g, n) ->
                    FilterChip(
                        selected = genreFilter == g,
                        onClick = { genreFilter = if (genreFilter == g) null else g },
                        label = { Text("${Genres.label(g)} ($n)") }
                    )
                }
            }
        }

        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    when {
                        all.isEmpty() ->
                            "Aún no tienes nada.\n\nVe a Más → Importar copia de Komikku y elige el archivo .tachibk, o usa el botón + para agregar uno a mano."
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

    if (newList) {
        NameDialog("Nueva lista", "", "Crear", { newList = false }) { name ->
            vm.createList(name)
            newList = false
        }
    }
    if (renaming && activeList != null) {
        NameDialog("Renombrar lista", activeList.name, "Guardar", { renaming = false }) { name ->
            vm.renameList(activeList.id, name)
            renaming = false
        }
    }
    if (deleting && activeList != null) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("¿Eliminar la lista?") },
            text = { Text("Se borrará “${activeList.name}”. Tus manhwas no se tocan.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteList(activeList.id)
                    deleting = false
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancelar") } }
        )
    }
}

/** Pide un nombre (para crear o renombrar una lista). */
@Composable
fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text) }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun HeroStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
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

    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(m, onCoverMissing, Modifier.width(64.dp).height(92.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    m.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(statusColor(m.status))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(Status.label(m.status), style = MaterialTheme.typography.labelMedium)
                    if (m.rating > 0) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "★ ${m.rating}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = ratingColor(m.rating)
                        )
                    }
                }
                val progress = if (m.totalChapters > 0)
                    "Cap. ${fmt(m.lastChapter)} de ${m.totalChapters}"
                else
                    "Cap. ${fmt(m.lastChapter)}"
                Text(progress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (m.totalChapters > 0) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (m.lastChapter / m.totalChapters).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                        color = statusColor(m.status),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
            FilledTonalIconButton(onClick = onPlusOne) {
                Icon(Icons.Filled.Add, contentDescription = "Leí un capítulo más")
            }
        }
    }
}

/** Tarjeta de la vista en cuadrícula: portada grande con el título encima y el progreso abajo. */
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
        Box {
            Cover(m, onCoverMissing, Modifier.fillMaxWidth().aspectRatio(2f / 3f))

            // Degradado oscuro para que el texto se lea sobre cualquier portada.
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            1f to Color(0xE6000000)
                        )
                    )
            )

            if (m.rating > 0) {
                Text(
                    "★ ${m.rating}",
                    color = ratingColor(m.rating),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(statusColor(m.status))
            )

            Row(
                Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        m.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "Cap. ${fmt(m.lastChapter)}" + if (m.totalChapters > 0) " / ${m.totalChapters}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
                FilledTonalIconButton(onClick = onPlusOne, modifier = Modifier.size(36.dp)) {
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
            .clip(RoundedCornerShape(12.dp))
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
    val allManhwas by vm.items.collectAsState()
    val all = allManhwas
    val allLists by vm.lists.collectAsState()
    val allEntries by vm.entries.collectAsState()
    val scope = rememberCoroutineScope()
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
    var addingWord by remember { mutableStateOf(false) }
    var newListHere by remember { mutableStateOf(false) }
    var similar by remember(item.id) { mutableStateOf<List<AniList.Similar>?>(null) }
    var loadingSimilar by remember(item.id) { mutableStateOf(false) }
    var similarError by remember(item.id) { mutableStateOf<String?>(null) }

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

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mi calificación", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        if (rating > 0) "★ $rating / 10" else "Sin calificar",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (rating > 0) ratingColor(rating) else MaterialTheme.colorScheme.outline
                    )
                }
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
            }
        }

        // ---- Géneros
        val genreList = Genres.parse(item.genres)
        if (genreList.isNotEmpty()) {
            Text("Géneros", style = MaterialTheme.typography.labelLarge)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                genreList.forEach { g -> AssistChip(onClick = {}, label = { Text(Genres.label(g)) }) }
            }
        }

        // ---- Listas personalizadas
        Text("Mis listas", style = MaterialTheme.typography.labelLarge)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            allLists.forEach { l ->
                val inIt = allEntries.any { it.listId == l.id && it.titleKey == item.titleKey }
                FilterChip(
                    selected = inIt,
                    onClick = { vm.toggleInList(l.id, item.titleKey, !inIt) },
                    label = { Text(l.name) }
                )
            }
            AssistChip(onClick = { newListHere = true }, label = { Text("＋ Nueva lista") })
        }

        // ---- Parecidos (AniList)
        HorizontalDivider()
        Text("Parecidos", style = MaterialTheme.typography.titleSmall)
        val sims = similar
        when {
            loadingSimilar -> Text("Buscando…", style = MaterialTheme.typography.bodySmall)
            sims == null -> {
                OutlinedButton(
                    onClick = {
                        loadingSimilar = true
                        similarError = null
                        scope.launch {
                            val result = vm.loadSimilar(item)
                            if (result == null) similarError = "No pude conectar con AniList. Revisa tu internet."
                            else similar = result
                            loadingSimilar = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Ver manhwas parecidos") }
                similarError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
            sims.isEmpty() -> Text("AniList no tiene recomendaciones para este título.", style = MaterialTheme.typography.bodySmall)
            else -> Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                sims.forEach { s ->
                    val have = all.any { it.titleKey == titleKeyOf(s.title) }
                    Column(Modifier.width(110.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            Modifier.width(110.dp).height(160.dp).clip(RoundedCornerShape(14.dp))
                        ) {
                            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceVariant) {}
                            if (!s.cover.isNullOrBlank()) {
                                AsyncImage(
                                    model = s.cover,
                                    contentDescription = s.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            s.score?.let {
                                Text(
                                    "★ ${it / 10.0}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(0xCC000000))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(s.title, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (have) {
                            Text("✓ Ya lo tienes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        } else {
                            TextButton(onClick = { vm.addPlanned(s.title, s.cover) }) { Text("＋ Pendiente") }
                        }
                    }
                }
            }
        }

        HorizontalDivider()
        Text(
            "Palabras de este manhwa (${mine.size})",
            style = MaterialTheme.typography.titleSmall
        )
        Button(onClick = { addingWord = true }, modifier = Modifier.fillMaxWidth()) {
            Text("＋ Guardar una palabra de este manhwa")
        }
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

    if (newListHere) {
        NameDialog("Nueva lista", "", "Crear y agregar", { newListHere = false }) { name ->
            vm.createList(name, addKey = item.titleKey)
            newListHere = false
        }
    }

    if (addingWord) {
        AddWordDialog(
            manhwas = allManhwas,
            initialManhwa = item.title,
            initialChapter = if (item.lastChapter > 0) fmt(item.lastChapter) else "",
            onDismiss = { addingWord = false },
            onConfirm = { text, manhwa, chapter, note ->
                vm.addWord(text, manhwa, chapter, note)
                addingWord = false
            }
        )
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
