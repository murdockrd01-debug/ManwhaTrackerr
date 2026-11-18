package com.manhwatracker.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
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
import com.manhwatracker.app.data.ChapterPick
import com.manhwatracker.app.data.LibraryAdd
import com.manhwatracker.app.data.Status
import com.manhwatracker.app.data.ReaderProgress
import com.manhwatracker.app.ext.ExtensionHost
import com.manhwatracker.app.ext.OfflineDownloads
import com.manhwatracker.app.ext.OfflineStore
import com.manhwatracker.app.ext.OnlineStore
import com.manhwatracker.app.data.OfflinePlan
import kotlinx.coroutines.CancellationException
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
    class Browse(val src: Source, val pkg: String, val initialQuery: String? = null) : Screen() { var autoOpened = false }
    class Detail(val src: Source, val pkg: String, val manga: SManga, val linkTitle: String? = null, var startAfter: Double? = null) : Screen()
    class Read(val src: Source, val pkg: String, val manga: SManga, val chapters: List<SChapter>, val index: Int, val linkTitle: String? = null, val startPos: Pair<Int, Int>? = null) : Screen()
}

/** Devuelve una función que pide el permiso de notificaciones (Android 13+) si aún no lo tienes; sirve para ver el avance de las descargas. */
@Composable
private fun rememberNotifAsk(): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    return {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
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
    /** Por dónde ibas en un capítulo: página (elemento de la lista) y desplazamiento. */
    fun savePos(c: Context, title: String, chapterUrl: String, index: Int, offset: Int, done: Boolean = false) =
        p(c).edit().putString("pos_" + norm(title), listOf(chapterUrl, index.toString(), offset.toString(), if (done) "1" else "0").joinToString("\u001F")).apply()
    class LastPos(val url: String, val done: Boolean)
    /** Último capítulo abierto de [title] y si llegaste a su final. */
    fun readLast(c: Context, title: String): LastPos? {
        val f = p(c).getString("pos_" + norm(title), null)?.split("\u001F") ?: return null
        if (f.isEmpty() || f[0].isEmpty()) return null
        return LastPos(f[0], f.getOrNull(3) == "1")
    }
    fun readPos(c: Context, title: String, chapterUrl: String): Pair<Int, Int>? {
        val f = p(c).getString("pos_" + norm(title), null)?.split("\u001F") ?: return null
        if (f.size < 3 || f[0] != chapterUrl) return null
        return (f[1].toIntOrNull() ?: return null) to (f[2].toIntOrNull() ?: 0)
    }
    fun lastReadUrl(c: Context, title: String): String? =
        p(c).getString("pos_" + norm(title), null)?.split("\u001F")?.firstOrNull()
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

private fun Throwable.report(): String = (generateSequence(this) { it.cause }.last().let { "${it.javaClass.simpleName}: ${it.message}" }) +
    "\n\nÚltimas peticiones de red:\n" + eu.kanade.tachiyomi.network.interceptor.NetworkLog.dump() +
    "\n\n" + stackTraceToString().take(2500)

@Composable
private fun ExploreRoot(openTitle: String?, openChapter: Double, onExit: () -> Unit) {
    val ctx = LocalContext.current
    var stack by remember { mutableStateOf(listOf<Screen>(Screen.Home)) }
    var resume by remember { mutableStateOf<Pair<Screen.Browse, Screen.Detail?>?>(null) }
    // Retoma las descargas que quedaron a medias si la app se cerró.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            OfflineDownloads.pending(ctx).groupBy { it[0] + "|" + it[1] + "|" + it[2] }.values.forEach { group ->
                val (pkg, sid, murl) = group.first()
                val srcId = sid.toLongOrNull() ?: return@forEach
                val saved = OfflineStore.loadManga(ctx, srcId, murl) ?: return@forEach
                val src = (ExtensionHost.load(ctx, pkg) as? ExtensionHost.Result.Ok)?.ext?.sources?.firstOrNull { it.id == srcId } ?: return@forEach
                val urls = group.map { it[3] }.toSet()
                OfflineDownloads.enqueue(ctx, src, pkg, saved.manga, saved.chapters.filter { it.url in urls })
            }
        }
    }
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
            onOpenSaved = { src, pkg, m -> push(Screen.Detail(src, pkg, m)) },
            continueTitle = resume?.second?.manga?.title,
            onContinue = { resume?.let { (b, d) -> stack = listOfNotNull(Screen.Home, b, d) } }
        )
        is Screen.Browse -> BrowseScreen(s.src, s.initialQuery, s.autoOpened, onAutoOpened = { s.autoOpened = true }, onBack = { pop() }, onOpen = { push(Screen.Detail(s.src, s.pkg, it, s.initialQuery)) })
        is Screen.Detail -> DetailScreen(s.src, s.pkg, s.manga, s.linkTitle, s.startAfter, onAutoJumped = { s.startAfter = null }, onBack = { pop() }, onRead = { list, i -> push(Screen.Read(s.src, s.pkg, s.manga, list, i, s.linkTitle, ExploreMemory.readPos(ctx, s.linkTitle ?: s.manga.title, list[i].url))) })
        is Screen.Read -> OnlineReader(s.src, s.pkg, s.manga, s.chapters, s.index, s.linkTitle, s.startPos, onBack = { pop() })
    }
}

// ---------------- Inicio: fuentes instaladas ----------------

private class Row2(val pkg: String, val result: ExtensionHost.Result)

@Composable
private fun HomeScreen(onExit: () -> Unit, onOpen: (Source, String) -> Unit, onOpenSaved: (Source, String, SManga) -> Unit, continueTitle: String?, onContinue: () -> Unit) {
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
    val homeScope = rememberCoroutineScope()
    val dlVersion = OfflineDownloads.version
    var saved by remember { mutableStateOf<List<OfflineStore.Entry>>(emptyList()) }
    var confirmDelete by remember { mutableStateOf<OfflineStore.Entry?>(null) }
    LaunchedEffect(dlVersion) { saved = withContext(Dispatchers.IO) { OfflineStore.downloadedManga(ctx) } }
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
        item(key = "wifi") {
            var wifiOnly by remember { mutableStateOf(com.manhwatracker.app.data.Prefs.wifiOnly(ctx)) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Bajar solo con Wi-Fi", fontWeight = FontWeight.Medium)
                    Text("Las descargas esperan si estás con datos móviles.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.Switch(checked = wifiOnly, onCheckedChange = { wifiOnly = it; com.manhwatracker.app.data.Prefs.setWifiOnly(ctx, it) })
            }
        }
        if (saved.isNotEmpty()) {
            item(key = "dlh") { Text("Descargados (sin conexión) · " + com.manhwatracker.app.data.OfflinePlan.size(saved.sumOf { it.bytes }), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(saved.size, key = { "dl" + saved[it].saved.srcId + saved[it].saved.manga.url }) { i ->
                val ent = saved[i]
                val sv = ent.saved
                val n = ent.chapters
                Card(Modifier.fillMaxWidth().clickable {
                    homeScope.launch {
                        val r = withContext(Dispatchers.IO) { ExtensionHost.load(ctx, sv.pkg) }
                        val src = (r as? ExtensionHost.Result.Ok)?.ext?.sources?.firstOrNull { it.id == sv.srcId }
                        if (src != null) onOpenSaved(src, sv.pkg, sv.manga)
                        else Toast.makeText(ctx, "La extensión de este manhwa no está disponible", Toast.LENGTH_LONG).show()
                    }
                }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        val cf = OfflineStore.coverFile(ctx, sv.srcId, sv.manga.url)
                        if (cf.exists()) {
                            AsyncImage(model = cf, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.width(44.dp).height(62.dp).clip(RoundedCornerShape(8.dp)))
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(sv.manga.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("$n capítulos · " + com.manhwatracker.app.data.OfflinePlan.size(ent.bytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { confirmDelete = ent }) { Text("🗑") }
                    }
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
    confirmDelete?.let { ent ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("¿Borrar descargas?") },
            text = { Text("Se borran los ${ent.chapters} capítulos bajados de ${ent.saved.manga.title} (${com.manhwatracker.app.data.OfflinePlan.size(ent.bytes)}). Tu progreso no cambia y podrás volver a bajarlos.") },
            confirmButton = { Button(onClick = { OfflineStore.deleteManga(ctx, ent.saved); OfflineDownloads.refresh(); confirmDelete = null }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancelar") } }
        )
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
private fun BrowseScreen(src: Source, initialQuery: String?, alreadyAutoOpened: Boolean, onAutoOpened: () -> Unit, onBack: () -> Unit, onOpen: (SManga) -> Unit) {
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
    var autoDone by remember { mutableStateOf(initialQuery.isNullOrBlank() || alreadyAutoOpened) }

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
                    // Desde "Continuar leyendo": si un resultado se llama igual que tu manhwa, se abre solo.
                    if (!autoDone && mode == 2) {
                        autoDone = true
                        val want = ExploreMemory.norm(initialQuery.orEmpty())
                        val exact = items.filter { ExploreMemory.norm(it.title) == want }
                        if (exact.size == 1 || (exact.isNotEmpty() && items.first() === exact.first())) { onAutoOpened(); onOpen(exact.first()) }
                    }
                }
            } catch (e: CancellationException) { loading = false; throw e
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
private fun DetailScreen(src: Source, pkg: String, manga: SManga, linkTitle: String?, startAfter: Double?, onAutoJumped: () -> Unit, onBack: () -> Unit, onRead: (List<SChapter>, Int) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf(manga) }
    var chapters by remember { mutableStateOf<List<SChapter>?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var offlineMode by remember { mutableStateOf(false) }
    val startTitle = remember { manga.title }
    val askNotif = rememberNotifAsk()
    LaunchedEffect(reload) {
        failure = null; chapters = null; offlineMode = false
        try {
            val up = withContext(Dispatchers.IO) { src.getMangaUpdate(manga, emptyList(), true, true) }
            detail = up.manga.also { if (it.title.isBlank()) it.title = manga.title }
            chapters = up.chapters
            withContext(Dispatchers.IO) { OfflineStore.saveManga(ctx, pkg, src.id, detail, up.chapters) }
        } catch (e: CancellationException) { throw e
        } catch (e: Throwable) {
            // Sin conexión: si ya la abriste antes, se muestra lo guardado (y lo que bajaste se puede leer).
            val saved = withContext(Dispatchers.IO) { OfflineStore.loadManga(ctx, src.id, manga.url) }
            if (saved != null) { detail = saved.manga; chapters = saved.chapters; offlineMode = true }
            else failure = e.report()
        }
    }
    // Para leer: del más viejo al más nuevo. Las fuentes suelen devolver el más nuevo primero.
    val asc = remember(chapters) {
        chapters?.let { l ->
            val nums = l.map { ChapterPick.number(it.chapter_number, it.name) }
            if (ChapterPick.newestFirst(nums)) l.reversed() else l
        }
    }
    // Capítulos ya bajados para leer sin conexión (se refresca cuando termina o se borra una descarga).
    val dlVer = OfflineDownloads.version
    var have by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(chapters, dlVer) {
        val l = chapters ?: return@LaunchedEffect
        have = withContext(Dispatchers.IO) { l.filter { OfflineStore.pageCount(ctx, OfflineStore.key(src.id, manga.url, it.url)) > 0 }.map { it.url }.toSet() }
    }
    // Desde "Continuar leyendo": salta al primer capítulo después del último que leíste (una sola vez).
    var auto by remember { mutableStateOf(startAfter) }
    LaunchedEffect(asc) {
        val after = auto ?: return@LaunchedEffect
        val list = asc ?: return@LaunchedEffect
        auto = null
        onAutoJumped()
        val nums = list.map { ChapterPick.number(it.chapter_number, it.name) }
        val last = ExploreMemory.readLast(ctx, linkTitle ?: manga.title)
        val i = ChapterPick.resumeIndex(list.map { it.url }, nums, last?.url, last?.done ?: false, after)
        if (i >= 0) onRead(list, i)
        else if (nums.any { it != null }) Toast.makeText(ctx, "Ya estás al día en esta fuente", Toast.LENGTH_LONG).show()
        else Toast.makeText(ctx, "Esta fuente no numera sus capítulos: elige uno de la lista", Toast.LENGTH_LONG).show()
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
                    AsyncImage(model = if (offlineMode) (OfflineStore.coverFile(ctx, src.id, manga.url).takeIf { it.exists() } ?: cover(src, detail.thumbnail_url)) else cover(src, detail.thumbnail_url), imageLoader = ExtensionHost.imageLoader(ctx), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(detail.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    detail.author?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text(statusLabel(detail.status), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    detail.genre?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        item {
            val inLib = remember { mutableStateOf<Boolean?>(null) }
            LaunchedEffect(manga.title) { inLib.value = LibraryAdd.contains(ctx, manga.title) }
            when (inLib.value) {
                true -> Text("✓ Está en tus manhwas", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                false -> Button(
                    onClick = {
                        scope.launch {
                            if (LibraryAdd.ensure(ctx, detail.title.ifBlank { manga.title }, detail.thumbnail_url ?: manga.thumbnail_url, Status.PLAN)) {
                                Toast.makeText(ctx, "Agregado a tus manhwas (Pendiente)", Toast.LENGTH_SHORT).show()
                            }
                            inLib.value = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("➕ Agregar a mis manhwas") }
                null -> {}
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
        if (offlineMode) item {
            Text("Sin conexión: se muestra lo guardado. Puedes leer los capítulos marcados ✓.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = { reload++ }) { Text("Reintentar") }
        }
        if (chapters == null && failure == null) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        chapters?.let { list ->
            item { Text("${list.size} capítulos · ${have.size} sin conexión", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            if (!offlineMode) item {
                val a = asc ?: list
                val from = ExploreMemory.lastReadUrl(ctx, startTitle).let { u -> a.indexOfFirst { it.url == u }.let { if (it < 0) 0 else it } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val idx = OfflinePlan.pick(a.map { it.url }, have, from, 5)
                        if (idx.isEmpty()) Toast.makeText(ctx, "Ya tienes esos capítulos", Toast.LENGTH_SHORT).show()
                        else { askNotif(); OfflineDownloads.enqueue(ctx, src, pkg, detail, idx.map { a[it] }); Toast.makeText(ctx, "Bajando ${idx.size} capítulos", Toast.LENGTH_SHORT).show() }
                    }) { Text("⬇ Siguientes 5") }
                    OutlinedButton(onClick = {
                        val idx = OfflinePlan.pick(a.map { it.url }, have, 0, 0)
                        if (idx.isEmpty()) Toast.makeText(ctx, "Ya tienes todos", Toast.LENGTH_SHORT).show()
                        else { askNotif(); OfflineDownloads.enqueue(ctx, src, pkg, detail, idx.map { a[it] }); Toast.makeText(ctx, "Bajando ${idx.size} capítulos", Toast.LENGTH_SHORT).show() }
                    }) { Text("⬇ Todos") }
                }
            }
            item {
                if (OfflineDownloads.active.isNotEmpty()) OutlinedButton(onClick = { OfflineDownloads.cancelAll(ctx) }) {
                    Text("✕ Cancelar descargas (${OfflineDownloads.active.size})")
                }
            }
            items(list, key = { it.url }) { ch ->
                val ckey = OfflineStore.key(src.id, manga.url, ch.url)
                Card(Modifier.fillMaxWidth().clickable {
                    val a = asc ?: list
                    onRead(a, a.indexOfFirst { it.url == ch.url }.coerceAtLeast(0))
                }) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.padding(12.dp).weight(1f)) {
                        Text(ch.name, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val sub = listOfNotNull(
                            ch.date_upload.takeIf { it > 0 }?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) },
                            ch.scanlator?.takeIf { it.isNotBlank() }
                        ).joinToString(" · ")
                        if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OfflineDownloads.errors[ckey]?.let { Text("No se pudo bajar: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, maxLines = 2) }
                    }
                    val pr = OfflineDownloads.active[ckey]
                    when {
                        pr != null -> Text(if (pr < 0f) "…" else "${(pr * 100).toInt()}%", modifier = Modifier.padding(end = 14.dp), color = MaterialTheme.colorScheme.primary)
                        ch.url in have -> TextButton(onClick = { OfflineDownloads.delete(ctx, ckey) }) { Text("✓ 🗑") }
                        !offlineMode -> TextButton(onClick = { askNotif(); OfflineDownloads.enqueue(ctx, src, pkg, detail, listOf(ch)) }) { Text("⬇") }
                    }
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
private fun OnlineReader(src: Source, pkg: String, manga: SManga, chapters: List<SChapter>, startIndex: Int, linkTitle: String?, startPos: Pair<Int, Int>?, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var index by remember { mutableStateOf(startIndex) }
    val chapter = chapters[index]
    var pages by remember(index) { mutableStateOf<List<Page>?>(null) }
    var failure by remember(index) { mutableStateOf<String?>(null) }
    val states = remember(index) { mutableStateMapOf<Int, PageState>() }
    // Una lista nueva por capítulo: si no, el capítulo siguiente heredaba la posición (al final) del anterior.
    val listState = remember(index) { LazyListState() }
    // true cuando ya se colocó la lista en su lugar (inicio o página guardada); antes de eso no se registra ni se guarda nada.
    var positioned by remember(index) { mutableStateOf(false) }
    val zoom = remember { ZoomState() }
    var recorded by remember(index) { mutableStateOf(false) }
    // Si empiezas a leer algo que no tenías, se agrega a tus manhwas (en "Leyendo").
    LaunchedEffect(Unit) {
        if (LibraryAdd.ensure(ctx, linkTitle ?: manga.title, manga.thumbnail_url)) {
            Toast.makeText(ctx, "Agregado a tus manhwas", Toast.LENGTH_SHORT).show()
        }
    }
    val key = src.id.toString() + "|" + manga.url + "|" + chapter.url

    LaunchedEffect(index) {
        val saved = OfflineStore.pageCount(ctx, key)
        if (saved > 0) { pages = List(saved) { Page(it) }; return@LaunchedEffect }
        try { pages = withContext(Dispatchers.IO) { src.getPageList(chapter).sortedBy { it.index } } }
        catch (e: CancellationException) { throw e }
        catch (e: Throwable) { failure = e.report() }
    }
    // Al abrir: vuelve a la página donde ibas (solo en el capítulo con el que se abrió); si no, al inicio.
    LaunchedEffect(index, pages) {
        if (pages == null) return@LaunchedEffect
        val pos = startPos?.takeIf { index == startIndex }
        if (pos != null) listState.scrollToItem(pos.first.coerceIn(0, pages!!.size), pos.second) else listState.scrollToItem(0)
        positioned = true
    }
    // Guarda por dónde vas (con una pequeña espera para no escribir en cada pixel).
    LaunchedEffect(index, pages, positioned) {
        if (pages == null || !positioned) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collectLatest { (i, o) ->
            delay(500)
            ExploreMemory.savePos(ctx, linkTitle ?: manga.title, chapter.url, i, o, recorded)
        }
    }

    // Al llegar a la última página, el avance se guarda en tus manhwas (si ese manhwa está en tu lista).
    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
    val total = pages?.size ?: 0
    if (!recorded && positioned && total > 0 && lastVisible >= total - 1) {
        recorded = true
        scope.launch {
            ExploreMemory.savePos(ctx, linkTitle ?: manga.title, chapter.url, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, true)
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
            var menu by remember { mutableStateOf(false) }
            val askNotifR = rememberNotifAsk()
            Box {
                IconButton(onClick = { menu = true }) { Text("⋮", fontSize = 20.sp) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Bajar los próximos 5 capítulos") },
                        onClick = {
                            menu = false
                            scope.launch {
                                val have = withContext(Dispatchers.IO) {
                                    chapters.filter { OfflineStore.pageCount(ctx, OfflineStore.key(src.id, manga.url, it.url)) > 0 }.map { it.url }.toSet()
                                }
                                val idx = OfflinePlan.pick(chapters.map { it.url }, have, index + 1, 5)
                                if (idx.isEmpty()) Toast.makeText(ctx, "Ya tienes los siguientes", Toast.LENGTH_SHORT).show()
                                else {
                                    askNotifR()
                                    OfflineDownloads.enqueue(ctx, src, pkg, manga, idx.map { chapters[it] })
                                    Toast.makeText(ctx, "Bajando ${idx.size} capítulos", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Fijar como mi avance") },
                        onClick = {
                            menu = false
                            scope.launch {
                                val r = ReaderProgress.record(ctx, linkTitle ?: manga.title, chapter.name, preferTitle = linkTitle, allowLower = true)
                                Toast.makeText(
                                    ctx,
                                    if (r != null) "Tu avance en ${r.title} ahora es el capítulo ${ReaderProgress.fmt(r.chapter)}" else "Ese ya es tu avance (o el manhwa no está en tu lista)",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    )
                }
            }
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
            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize().zoomable(zoom, listState)) {
                items(list.size, key = { index.toString() + "-" + it }) { i ->
                    val st = states[i]
                    LaunchedEffect(i, index) {
                        if (states[i] == null) {
                            states[i] = PageState.Loading
                            states[i] = try {
                                val f = OnlineStore.pageFile(ctx, src, key, list[i])
                                PageState.Ready(f, com.manhwatracker.app.data.ReaderSource.ratio(ctx, f) ?: 0.7f)
                            } catch (e: CancellationException) {
                                // La página salió de pantalla antes de terminar: no es un error, se pide de nuevo al volver.
                                states.remove(i)
                                throw e
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

// ---------------- Zoom del lector en línea ----------------

/** Zoom horizontal del lector: las páginas se ensanchan (se ven nítidas) y se desplazan de lado; el scroll vertical sigue siendo el de la lista. */
class ZoomState {
    var scale by mutableStateOf(1f)
    var offX by mutableStateOf(0f)
    var viewW by mutableStateOf(0f)

    fun clampX(x: Float): Float = x.coerceIn(-(viewW * scale - viewW).coerceAtLeast(0f), 0f)

    fun zoomAt(factor: Float, cx: Float) {
        val old = scale
        val ns = (old * factor).coerceIn(1f, 4f)
        offX = clampX(cx - (cx - offX) * (ns / old))
        scale = ns
        if (ns == 1f) offX = 0f
    }
}

private fun Modifier.zoomable(z: ZoomState, list: LazyListState): Modifier = this
    .clipToBounds()
    .onSizeChanged { z.viewW = it.width.toFloat() }
    .pointerInput(Unit) {
        detectTapGestures(onDoubleTap = { p -> if (z.scale > 1.01f) z.zoomAt(1f / z.scale, p.x) else z.zoomAt(2.5f, p.x) })
    }
    .pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            do {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = ev.changes.count { it.pressed }
                if (pressed >= 2) {
                    val zoom = ev.calculateZoom()
                    val pan = ev.calculatePan()
                    val c = ev.calculateCentroid(useCurrent = true)
                    if (zoom != 1f || pan != Offset.Zero) {
                        val before = z.scale
                        z.zoomAt(zoom, c.x)
                        z.offX = z.clampX(z.offX + pan.x)
                        list.dispatchRawDelta(-pan.y + (z.scale / before - 1f) * c.y)
                        ev.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                } else if (pressed == 1 && z.scale > 1f) {
                    // Con una página ampliada, arrastrar de lado la mueve; el vertical lo sigue manejando la lista.
                    z.offX = z.clampX(z.offX + ev.changes.first().positionChange().x)
                }
            } while (ev.changes.any { it.pressed })
        }
    }
    .layout { measurable, constraints ->
        val w = (constraints.maxWidth * z.scale).toInt().coerceAtLeast(constraints.maxWidth)
        val p = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
        layout(constraints.maxWidth, p.height) { p.place(z.offX.toInt(), 0) }
    }
