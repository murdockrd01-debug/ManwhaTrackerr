package com.manhwatracker.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.manhwatracker.app.data.ReaderProgress
import com.manhwatracker.app.ext.ExtensionHost
import com.manhwatracker.app.ext.OnlineStore
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Explorar: las fuentes de las extensiones instaladas → sus manhwas → capítulos → lectura en línea. */
class ExploreActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeState.load(this)
        setContent { ManhwaTheme { Surface(Modifier.fillMaxSize()) { ExploreRoot { finish() } } } }
    }

    companion object {
        fun open(ctx: Context) = ctx.startActivity(Intent(ctx, ExploreActivity::class.java))
    }
}

private sealed class Screen {
    object Home : Screen()
    class Browse(val src: Source) : Screen()
    class Detail(val src: Source, val manga: SManga) : Screen()
    class Read(val src: Source, val manga: SManga, val chapters: List<SChapter>, val index: Int) : Screen()
}

private fun copyText(ctx: Context, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("error", text))
    Toast.makeText(ctx, tr("Diagnóstico copiado"), Toast.LENGTH_SHORT).show()
}

private fun Throwable.report(): String = (generateSequence(this) { it.cause }.last().let { "${it.javaClass.simpleName}: ${it.message}" }) + "\n\n" + stackTraceToString().take(3000)

@Composable
private fun ExploreRoot(onExit: () -> Unit) {
    var stack by remember { mutableStateOf(listOf<Screen>(Screen.Home)) }
    fun push(s: Screen) { stack = stack + s }
    fun pop() { if (stack.size > 1) stack = stack.dropLast(1) else onExit() }
    BackHandler { pop() }
    when (val s = stack.last()) {
        is Screen.Home -> HomeScreen(onExit = onExit, onOpen = { push(Screen.Browse(it)) })
        is Screen.Browse -> BrowseScreen(s.src, onBack = { pop() }, onOpen = { push(Screen.Detail(s.src, it)) })
        is Screen.Detail -> DetailScreen(s.src, s.manga, onBack = { pop() }, onRead = { list, i -> push(Screen.Read(s.src, s.manga, list, i)) })
        is Screen.Read -> OnlineReader(s.src, s.manga, s.chapters, s.index, onBack = { pop() })
    }
}

// ---------------- Inicio: fuentes instaladas ----------------

private class Row2(val pkg: String, val result: ExtensionHost.Result)

@Composable
private fun HomeScreen(onExit: () -> Unit, onOpen: (Source) -> Unit) {
    val ctx = LocalContext.current
    var rows by remember { mutableStateOf<List<Row2>?>(null) }
    var reload by remember { mutableStateOf(0) }
    var trusting by remember { mutableStateOf<ExtensionHost.Result.NeedsTrust?>(null) }
    LaunchedEffect(reload) {
        rows = null
        rows = withContext(Dispatchers.IO) {
            ExtensionHost.installedExtensions(ctx).map { Row2(it.packageName, ExtensionHost.load(ctx, it.packageName)) }
        }
    }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onExit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Volver")) }
                Column(Modifier.weight(1f)) {
                    Text("Explorar", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("Fuentes de tus extensiones instaladas", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val list = rows
        if (list == null) item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        else if (list.isEmpty()) item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No hay extensiones instaladas", fontWeight = FontWeight.SemiBold)
                    Text("Agrega un repositorio e instala una extensión en Más › Fuentes, y vuelve aquí.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { SourcesActivity.open(ctx) }, modifier = Modifier.fillMaxWidth()) { Text("Ir a Fuentes") }
                }
            }
        }
        list?.forEach { row ->
            when (val r = row.result) {
                is ExtensionHost.Result.Ok -> {
                    item(key = row.pkg + "h") { Text(r.ext.name + " · v" + r.ext.versionName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(r.ext.sources, key = { row.pkg + it.id }) { src ->
                        Card(Modifier.fillMaxWidth().clickable { onOpen(src) }) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(src.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(com.manhwatracker.app.data.Sources.langLabel(src.lang.ifBlank { "all" }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("Abrir ›", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                is ExtensionHost.Result.NeedsTrust -> item(key = row.pkg) {
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.name, fontWeight = FontWeight.SemiBold)
                                Text("Necesita tu permiso para usarse", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Button(onClick = { trusting = r }) { Text("Confiar") }
                        }
                    }
                }
                is ExtensionHost.Result.Fail -> item(key = row.pkg) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(row.pkg.substringAfterLast('.'), fontWeight = FontWeight.SemiBold)
                            Text("No se pudo cargar: " + r.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { copyText(ctx, r.message + "\n" + r.detail.take(3000)) }) { Text("Copiar diagnóstico") }
                        }
                    }
                }
            }
        }
    }
    trusting?.let { t ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { trusting = null },
            title = { Text("¿Confiar en esta extensión?") },
            text = { Text("${t.name} es código de terceros que se ejecutaría dentro de esta app, con acceso a internet. Confía solo si el repositorio del que la instalaste es de confianza.") },
            confirmButton = { Button(onClick = { ExtensionHost.trust(ctx, t.signatures); trusting = null; reload++ }) { Text("Confiar") } },
            dismissButton = { TextButton(onClick = { trusting = null }) { Text("Cancelar") } }
        )
    }
}

// ---------------- Fuente: populares / recientes / búsqueda ----------------

@Composable
private fun cover(src: Source, url: String?): ImageRequest {
    val ctx = LocalContext.current
    return ImageRequest.Builder(ctx).data(url).apply { (src as? HttpSource)?.headers?.let { headers(it) } }.build()
}

@Composable
private fun BrowseScreen(src: Source, onBack: () -> Unit, onOpen: (SManga) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val items = remember { mutableStateListOf<SManga>() }
    var mode by remember { mutableStateOf(0) }           // 0 populares, 1 recientes, 2 búsqueda
    var query by remember { mutableStateOf("") }
    var applied by remember { mutableStateOf("") }
    var page by remember { mutableStateOf(0) }
    var hasNext by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var gen by remember { mutableStateOf(0) }

    fun loadMore() {
        if (loading || !hasNext) return
        val my = gen
        scope.launch {
            loading = true; failure = null
            try {
                val next = page + 1
                val res = withContext(Dispatchers.IO) {
                    when (mode) {
                        0 -> src.getPopularManga(next)
                        1 -> src.getLatestUpdates(next)
                        else -> src.getSearchManga(next, applied, (src as? CatalogueSource)?.getFilterList() ?: FilterList())
                    }
                }
                if (my == gen) {
                    val have = items.map { it.url }.toSet()
                    items.addAll(res.mangas.filter { it.url !in have })
                    page = next
                    hasNext = res.hasNextPage && res.mangas.isNotEmpty()
                }
            } catch (e: Throwable) {
                if (my == gen) { failure = e.report(); hasNext = false }
            }
            loading = false
        }
    }
    fun restart() { gen++; items.clear(); page = 0; hasNext = true; loading = false; failure = null; loadMore() }
    LaunchedEffect(Unit) { loadMore() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Volver")) }
            Column(Modifier.weight(1f)) {
                Text(src.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(com.manhwatracker.app.data.Sources.langLabel(src.lang.ifBlank { "all" }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == 0, onClick = { mode = 0; restart() }, label = { Text("Populares") })
            if (src.supportsLatest) FilterChip(selected = mode == 1, onClick = { mode = 1; restart() }, label = { Text("Recientes") })
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.weight(1f),
                label = { Text("Buscar en ${src.name}") }
            )
            Spacer(Modifier.width(8.dp))
            Button(enabled = query.isNotBlank(), onClick = { applied = query.trim(); mode = 2; restart() }) { Text("Buscar") }
        }
        failure?.let { f ->
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("La fuente dio un error", fontWeight = FontWeight.SemiBold)
                Text(f.lines().first(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { copyText(ctx, f) }) { Text("Copiar diagnóstico") }
                    OutlinedButton(onClick = { restart() }) { Text("Reintentar") }
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(items, key = { _, m -> m.url }) { i, m ->
                if (i >= items.size - 6) LaunchedEffect(items.size) { loadMore() }
                Column(Modifier.clickable { onOpen(m) }) {
                    Box(Modifier.fillMaxWidth().aspectRatio(0.7f).clip(RoundedCornerShape(8.dp))) {
                        AsyncImage(model = cover(src, m.thumbnail_url), imageLoader = ExtensionHost.imageLoader(ctx), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Text(m.title, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
            }
            if (loading) item(span = { GridItemSpan(3) }) { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            if (!loading && items.isEmpty() && failure == null) item(span = { GridItemSpan(3) }) { Text("No hay resultados", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

// ---------------- Ficha del manhwa y capítulos ----------------

private fun statusLabel(s: Int) = when (s) {
    SManga.ONGOING -> "En emisión"; SManga.COMPLETED -> "Completado"; SManga.LICENSED -> "Licenciado"
    SManga.PUBLISHING_FINISHED -> "Publicación terminada"; SManga.CANCELLED -> "Cancelado"; SManga.ON_HIATUS -> "En pausa"; else -> "Estado desconocido"
}

@Composable
private fun DetailScreen(src: Source, manga: SManga, onBack: () -> Unit, onRead: (List<SChapter>, Int) -> Unit) {
    val ctx = LocalContext.current
    var detail by remember { mutableStateOf(manga) }
    var chapters by remember { mutableStateOf<List<SChapter>?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(reload) {
        failure = null; chapters = null
        try {
            val up = withContext(Dispatchers.IO) { src.getMangaUpdate(manga, emptyList(), true, true) }
            detail = up.manga.also { if (it.title.isBlank()) it.title = manga.title }
            chapters = up.chapters
        } catch (e: Throwable) { failure = e.report() }
    }
    // Para leer: del más viejo al más nuevo. Las fuentes suelen devolver el más nuevo primero.
    val asc = remember(chapters) {
        chapters?.let { l -> if (l.size > 1 && l.first().chapter_number > l.last().chapter_number) l.reversed() else if (l.size > 1 && l.first().chapter_number == l.last().chapter_number) l.reversed() else l }
    }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Volver")) }
                Text(src.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.width(110.dp).aspectRatio(0.7f).clip(RoundedCornerShape(10.dp))) {
                    AsyncImage(model = cover(src, detail.thumbnail_url), imageLoader = ExtensionHost.imageLoader(ctx), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(detail.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    detail.author?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text(statusLabel(detail.status), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    detail.genre?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        detail.description?.takeIf { it.isNotBlank() }?.let { d -> item { Text(d, style = MaterialTheme.typography.bodyMedium, maxLines = 8, overflow = TextOverflow.Ellipsis) } }
        failure?.let { f ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("No se pudieron cargar los datos", fontWeight = FontWeight.SemiBold)
                    Text(f.lines().first(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { copyText(ctx, f) }) { Text("Copiar diagnóstico") }
                        OutlinedButton(onClick = { reload++ }) { Text("Reintentar") }
                    }
                }
            }
        }
        if (chapters == null && failure == null) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        chapters?.let { list ->
            item { Text("${list.size} capítulos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            items(list, key = { it.url }) { ch ->
                Card(Modifier.fillMaxWidth().clickable {
                    val a = asc ?: list
                    onRead(a, a.indexOfFirst { it.url == ch.url }.coerceAtLeast(0))
                }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(ch.name, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val sub = listOfNotNull(
                            ch.date_upload.takeIf { it > 0 }?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) },
                            ch.scanlator?.takeIf { it.isNotBlank() }
                        ).joinToString(" · ")
                        if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ---------------- Lector en línea ----------------

private sealed class PageState {
    object Loading : PageState()
    class Ready(val file: File, val ratio: Float) : PageState()
    class Err(val msg: String) : PageState()
}

@Composable
private fun OnlineReader(src: Source, manga: SManga, chapters: List<SChapter>, startIndex: Int, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var index by remember { mutableStateOf(startIndex) }
    val chapter = chapters[index]
    var pages by remember(index) { mutableStateOf<List<Page>?>(null) }
    var failure by remember(index) { mutableStateOf<String?>(null) }
    val states = remember(index) { mutableStateMapOf<Int, PageState>() }
    val listState = rememberLazyListState()
    var recorded by remember(index) { mutableStateOf(false) }
    val key = src.id.toString() + "|" + manga.url + "|" + chapter.url

    LaunchedEffect(index) {
        try { pages = withContext(Dispatchers.IO) { src.getPageList(chapter).sortedBy { it.index } } }
        catch (e: Throwable) { failure = e.report() }
    }
    LaunchedEffect(index) { listState.scrollToItem(0) }

    // Al llegar a la última página, el avance se guarda en tus manhwas (si ese manhwa está en tu lista).
    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
    val total = pages?.size ?: 0
    if (!recorded && total > 0 && lastVisible >= total - 1) {
        recorded = true
        scope.launch {
            val saved = ReaderProgress.record(ctx, manga.title, chapter.name)
            if (saved != null) Toast.makeText(ctx, "${saved.title}: capítulo ${ReaderProgress.fmt(saved.chapter)} guardado", Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Volver")) }
            Column(Modifier.weight(1f)) {
                Text(manga.title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(chapter.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(enabled = index > 0, onClick = { index-- }) { Text("‹ Anterior") }
            TextButton(enabled = index < chapters.lastIndex, onClick = { index++ }) { Text("Siguiente ›") }
        }
        val list = pages
        when {
            failure != null -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("No se pudieron cargar las páginas", fontWeight = FontWeight.SemiBold)
                Text(failure!!.lines().first(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = { copyText(ctx, failure!!) }) { Text("Copiar diagnóstico") }
            }
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Text("Este capítulo no tiene páginas", modifier = Modifier.padding(16.dp))
            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(list.size, key = { index.toString() + "-" + it }) { i ->
                    val st = states[i]
                    LaunchedEffect(i, index) {
                        if (states[i] == null) {
                            states[i] = PageState.Loading
                            states[i] = try {
                                val f = OnlineStore.pageFile(ctx, src, key, list[i])
                                PageState.Ready(f, com.manhwatracker.app.data.ReaderSource.ratio(ctx, f) ?: 0.7f)
                            } catch (e: Throwable) { PageState.Err(e.message ?: e.javaClass.simpleName) }
                        }
                    }
                    when (st) {
                        is PageState.Ready -> AsyncImage(model = st.file, contentDescription = null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth().aspectRatio(st.ratio))
                        is PageState.Err -> Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Página ${i + 1}: " + st.msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { states.remove(i) }) { Text("Reintentar") }
                        }
                        else -> Box(Modifier.fillMaxWidth().height(360.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                }
                item(key = "end") {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Fin de ${chapter.name}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (index < chapters.lastIndex) Button(onClick = { index++ }) { Text("Siguiente capítulo") }
                        else Text("Es el último capítulo disponible", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
