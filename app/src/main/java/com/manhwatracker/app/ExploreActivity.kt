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
        val title = intent.getStringExtra("title")
        val chapter = intent.getDoubleExtra("chapter", -1.0)
        setContent { ManhwaTheme { Surface(Modifier.fillMaxSize()) { ExploreRoot(title, chapter) { finish() } } } }
    }

    companion object {
        fun open(ctx: Context) = ctx.startActivity(Intent(ctx, ExploreActivity::class.java))

        /** "Continuar leyendo" de un manhwa de tu lista: abre su ficha en la fuente (o la busca) y salta al capítulo siguiente. */
        fun continueIntent(ctx: Context, title: String, chapterNow: Double): Intent =
            Intent(ctx, ExploreActivity::class.java).putExtra("title", title).putExtra("chapter", chapterNow)
    }
}

private sealed class Screen {
    object Home : Screen()
    class Browse(val src: Source, val pkg: String, val initialQuery: String? = null) : Screen()
    class Detail(val src: Source, val pkg: String, val manga: SManga, val linkTitle: String? = null, val startAfter: Double? = null) : Screen()
    class Read(val src: Source, val manga: SManga, val chapters: List<SChapter>, val index: Int, val linkTitle: String? = null) : Screen()
}

private fun copyText(ctx: Context, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("error", text))
    Toast.makeText(ctx, tr("Diagnóstico copiado"), Toast.LENGTH_SHORT).show()
}

/** Recuerda la última fuente y el último manhwa abiertos, para volver ahí sin empezar de cero. */
private object ExploreMemory {
    private fun p(c: Context) = c.getSharedPreferences("explore", Context.MODE_PRIVATE)
    fun saveSource(c: Context, pkg: String, id: Long) = p(c).edit().putString("pkg", pkg).putLong("src", id).apply()
    fun norm(t: String) = t.lowercase().filter { it.isLetterOrDigit() }
    fun saveManga(c: Context, pkg: String, id: Long, m: SManga, linkTitle: String? = null) {
        val e = p(c).edit().putString("pkg", pkg).putLong("src", id).putString("url", m.url).putString("title", m.title).putString("thumb", m.thumbnail_url)
        // Recuerda qué manhwa de la fuente corresponde a cada título de tu lista (para "Continuar leyendo").
        val rec = listOf(pkg, id.toString(), m.url, m.title.replace("\n", " "), m.thumbnail_url.orEmpty()).joinToString("\u001F")
        e.putString("map_" + norm(m.title), rec)
        if (!linkTitle.isNullOrBlank()) e.putString("map_" + norm(linkTitle), rec)
        e.apply()
    }
    /** El manhwa de la fuente que ya vinculaste con [title], si lo hay. */
    fun forTitle(c: Context, title: String): Last? {
        val f = p(c).getString("map_" + norm(title), null)?.split("\u001F") ?: return null
        if (f.size < 5) return null
        val m = SManga.create().apply { url = f[2]; this.title = f[3]; thumbnail_url = f[4].ifEmpty { null } }
        return Last(f[0], f[1].toLongOrNull() ?: return null, m)
    }
    class Last(val pkg: String, val srcId: Long, val manga: SManga?)
    fun read(c: Context): Last? {
        val sp = p(c)
        val pkg = sp.getString("pkg", null) ?: return null
        val url = sp.getString("url", null)
        val manga = if (url != null) SManga.create().apply { this.url = url; title = sp.getString("title", "") ?: ""; thumbnail_url = sp.getString("thumb", null) } else null
        return Last(pkg, sp.getLong("src", 0L), manga)
    }
}

private fun Throwable.report(): String = (generateSequence(this) { it.cause }.last().let { "${it.javaClass.simpleName}: ${it.message}" }) + "\n\n" + stackTraceToString().take(3000)

@Composable
private fun ExploreRoot(openTitle: String?, openChapter: Double, onExit: () -> Unit) {
    val ctx = LocalContext.current
    var stack by remember { mutableStateOf(listOf<Screen>(Screen.Home)) }
    var resume by remember { mutableStateOf<Pair<Screen.Browse, Screen.Detail?>?>(null) }
    // Al abrir: vuelve a la última fuente (y deja un acceso al último manhwa en el inicio).
    LaunchedEffect(Unit) {
        // Si vienes de "Continuar leyendo" de un manhwa de tu lista: abre su ficha en la fuente, o lo busca.
        if (!openTitle.isNullOrBlank()) {
            val linked = ExploreMemory.forTitle(ctx, openTitle)
            val known = linked ?: ExploreMemory.read(ctx)
            val r = known?.let { withContext(Dispatchers.IO) { ExtensionHost.load(ctx, it.pkg) } }
            val src = (r as? ExtensionHost.Result.Ok)?.ext?.sources?.firstOrNull { it.id == known?.srcId }
            if (known != null && src != null) {
                val browse = Screen.Browse(src, known.pkg, if (linked == null) openTitle else null)
                stack = if (linked?.manga != null) listOf(Screen.Home, browse, Screen.Detail(src, known.pkg, linked.manga, openTitle, openChapter.takeIf { it >= 0 }))
                else listOf(Screen.Home, browse)
            }
            return@LaunchedEffect
        }
        val last = ExploreMemory.read(ctx) ?: return@LaunchedEffect
        val r = withContext(Dispatchers.IO) { ExtensionHost.load(ctx, last.pkg) }
        val src = (r as? ExtensionHost.Result.Ok)?.ext?.sources?.firstOrNull { it.id == last.srcId } ?: return@LaunchedEffect
        val browse = Screen.Browse(src, last.pkg)
        resume = browse to last.manga?.let { Screen.Detail(src, last.pkg, it) }
        if (stack.size == 1) stack = listOf(Screen.Home, browse)
    }
    fun push(s: Screen) {
        when (s) {
            is Screen.Browse -> ExploreMemory.saveSource(ctx, s.pkg, s.src.id)
            is Screen.Detail -> ExploreMemory.saveManga(ctx, s.pkg, s.src.id, s.manga, s.linkTitle)
            else -> {}
        }
        stack = stack + s
    }
    fun pop() { if (stack.size > 1) stack = stack.dropLast(1) else onExit() }
    BackHandler { pop() }
    when (val s = stack.last()) {
        is Screen.Home -> HomeScreen(
            onExit = onExit, onOpen = { src, pkg -> push(Screen.Browse(src, pkg)) },
            continueTitle = resume?.second?.manga?.title,
            onContinue = { resume?.let { (b, d) -> stack = listOfNotNull(Screen.Home, b, d) } }
        )
        is Screen.Browse -> BrowseScreen(s.src, s.initialQuery, onBack = { pop() }, onOpen = { push(Screen.Detail(s.src, s.pkg, it, s.initialQuery)) })
        is Screen.Detail -> DetailScreen(s.src, s.manga, s.startAfter, onBack = { pop() }, onRead = { list, i -> push(Screen.Read(s.src, s.manga, list, i, s.linkTitle)) })
        is Screen.Read -> OnlineReader(s.src, s.manga, s.chapters, s.index, s.linkTitle, onBack = { pop() })
    }
}

// ---------------- Inicio: fuentes instaladas ----------------

private class Row2(val pkg: String, val result: ExtensionHost.Result)

@Composable
private fun HomeScreen(onExit: () -> Unit, onOpen: (Source, String) -> Unit, continueTitle: String?, onContinue: () -> Unit) {
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
                    Text("Leer", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("Lee en línea con tus extensiones", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { SourcesActivity.open(ctx) }) { Text("Fuentes") }
            }
        }
        if (!continueTitle.isNullOrBlank()) item {
            Card(
                Modifier.fillMaxWidth().clickable(onClick = onContinue),
                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Continuar donde te quedaste", style = MaterialTheme.typography.labelMedium)
                        Text(continueTitle, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text("▶", fontSize = 22.sp)
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
                        Card(Modifier.fillMaxWidth().clickable { onOpen(src, row.pkg) }) {
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
        item(key = "downloads") {
            TextButton(onClick = { ctx.startActivity(Intent(ctx, ReaderActivity::class.java)) }, modifier = Modifier.fillMaxWidth()) {
                Text("Leer archivos descargados (Komikku, .cbz)")
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
private fun BrowseScreen(src: Source, initialQuery: String?, onBack: () -> Unit, onOpen: (SManga) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val items = remember { mutableStateListOf<SManga>() }
    var mode by remember { mutableStateOf(if (initialQuery.isNullOrBlank()) 0 else 2) }   // 0 populares, 1 recientes, 2 búsqueda
    var query by remember { mutableStateOf(initialQuery.orEmpty()) }
    var applied by remember { mutableStateOf(initialQuery.orEmpty()) }
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
private fun DetailScreen(src: Source, manga: SManga, startAfter: Double?, onBack: () -> Unit, onRead: (List<SChapter>, Int) -> Unit) {
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
    // Desde "Continuar leyendo": salta al primer capítulo después del último que leíste (una sola vez).
    var auto by remember { mutableStateOf(startAfter) }
    LaunchedEffect(asc) {
        val after = auto ?: return@LaunchedEffect
        val list = asc ?: return@LaunchedEffect
        auto = null
        val i = list.indexOfFirst { it.chapter_number >= 0 && it.chapter_number > after + 0.0001 }
        if (i >= 0) onRead(list, i)
        else if (list.any { it.chapter_number >= 0 }) Toast.makeText(ctx, "Ya estás al día en esta fuente", Toast.LENGTH_LONG).show()
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
private fun OnlineReader(src: Source, manga: SManga, chapters: List<SChapter>, startIndex: Int, linkTitle: String?, onBack: () -> Unit) {
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
            val saved = ReaderProgress.record(ctx, linkTitle ?: manga.title, chapter.name, preferTitle = linkTitle)
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
