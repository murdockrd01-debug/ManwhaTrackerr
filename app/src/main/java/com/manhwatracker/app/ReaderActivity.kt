package com.manhwatracker.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.manhwatracker.app.data.AppDatabase
import com.manhwatracker.app.data.Manhwa
import com.manhwatracker.app.data.ReaderIndex
import com.manhwatracker.app.data.Status
import com.manhwatracker.app.data.titleKeyOf
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.manhwatracker.app.data.Prefs
import com.manhwatracker.app.data.ReaderLogic
import com.manhwatracker.app.data.ReaderProgress
import com.manhwatracker.app.data.ReaderSource
import com.manhwatracker.app.data.ReaderStore
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lector integrado: lee capítulos desde una carpeta o un archivo .cbz/.zip del teléfono. */
class ReaderActivity : ComponentActivity() {
    companion object {
        private const val X_TITLE = "manhwa_title"
        private const val X_CHAPTER = "manhwa_chapter"

        /** Abre el lector directo en el capítulo que toca de ese manhwa (desde su ficha en la biblioteca). */
        fun continueIntent(ctx: Context, title: String, lastChapter: Double): Intent =
            Intent(ctx, ReaderActivity::class.java).putExtra(X_TITLE, title).putExtra(X_CHAPTER, lastChapter)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeState.load(this)
        Features.load(this)
        Tr.lang = Prefs.language(this)
        val startTitle = intent?.getStringExtra(X_TITLE)
        val startChapter = intent?.getDoubleExtra(X_CHAPTER, 0.0) ?: 0.0
        setContent {
            ManhwaTheme {
                Surface(Modifier.fillMaxSize()) { ReaderRoot(startTitle, startChapter, onExit = { finish() }) }
            }
        }
    }
}

/** Un capítulo abierto: sus páginas, sus proporciones y los capítulos vecinos para pasar al siguiente. */
private class Book(
    val title: String,
    val pages: List<Any>,
    val ratios: List<Float>,
    val key: String,
    val tree: Uri?,
    val dirId: String,
    val siblings: List<ReaderSource.Node>,
    val index: Int,
    /** Nombre de la carpeta del manhwa (para sumar el avance en la biblioteca); null si no se sabe. */
    val manga: String?,
    /** Título exacto del manhwa si llegaste desde su ficha; así el avance se suma a ese y no se adivina por la carpeta. */
    val linkTitle: String?
)

@Composable
private fun ReaderRoot(startTitle: String?, startChapter: Double, onExit: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tree by remember { mutableStateOf<Uri?>(null) }
    var stack by remember { mutableStateOf<List<String>>(emptyList()) }
    var nodes by remember { mutableStateOf<List<ReaderSource.Node>>(emptyList()) }
    var book by remember { mutableStateOf<Book?>(null) }
    var busy by remember { mutableStateOf(false) }
    var last by remember { mutableStateOf(ReaderStore.last(ctx)) }
    /** true si llegaste desde la ficha de un manhwa: "atrás" vuelve directo a la biblioteca. */
    var direct by remember { mutableStateOf(false) }
    val vm: MainViewModel = viewModel()
    val libFlow = remember { AppDatabase.get(ctx).dao().observeAll() }
    /** Tu biblioteca, en vivo: lo que el lector guarda aparece aquí al instante. */
    val library by libFlow.collectAsState(initial = emptyList())
    /** Título exacto del manhwa cuando llegaste desde su ficha. */
    var link by remember { mutableStateOf<String?>(null) }
    var index by remember { mutableStateOf(ReaderStore.index(ctx)) }
    var scanning by remember { mutableStateOf(false) }
    var downloads by remember { mutableStateOf(ReaderStore.downloads(ctx)) }

    fun toast(s: String) = Toast.makeText(ctx, tr(s), Toast.LENGTH_SHORT).show()

    fun closeBook() {
        book = null
        last = ReaderStore.last(ctx)
    }

    suspend fun showBook(
        t: Uri?, dirId: String, item: String, name: String,
        pages: List<Any>, sibs: List<ReaderSource.Node>, idx: Int
    ) {
        val ratios = withContext(Dispatchers.IO) {
            pages.map { ReaderSource.ratio(ctx, it) ?: ReaderLogic.FALLBACK_RATIO }
        }
        val place = ReaderLogic.Place(t?.toString() ?: "", dirId, item, name)
        ReaderStore.setLast(ctx, place)
        // Solo se sabe de qué manhwa es si el capítulo está dentro de la carpeta del manhwa (no si abriste el capítulo suelto).
        val manga = if (t != null && dirId != item) ReaderLogic.nameFromDocId(dirId) else null
        book = Book(name, pages, ratios, "${place.tree}|$item", t, dirId, sibs, idx, manga, link)
    }

    /** Abre un capítulo (o entra a la carpeta si en vez de páginas tiene más capítulos). */
    fun openNode(t: Uri, dirId: String, sibs: List<ReaderSource.Node>, n: ReaderSource.Node) {
        scope.launch {
            busy = true
            try {
                val res = withContext(Dispatchers.IO) {
                    try { ReaderSource.open(ctx, t, n) } catch (e: Exception) { ReaderSource.Opened.Empty }
                }
                when (res) {
                    is ReaderSource.Opened.Pages ->
                        showBook(t, dirId, n.docId, n.name, res.pages, sibs, sibs.indexOfFirst { it.docId == n.docId })
                    is ReaderSource.Opened.Children -> {
                        stack = stack + n.docId
                        nodes = ReaderSource.chapterNodes(res.nodes)
                    }
                    else -> toast("No se encontraron imágenes aquí")
                }
            } finally {
                busy = false
            }
        }
    }

    /** Muestra el contenido de una carpeta. Si la carpeta es en sí un capítulo (solo imágenes), la abre. */
    fun browse(t: Uri, dirIds: List<String>) {
        scope.launch {
            busy = true
            try {
                val dirId = dirIds.last()
                val kids = withContext(Dispatchers.IO) {
                    try { ReaderSource.list(ctx, t, dirId) } catch (e: Exception) { emptyList() }
                }
                val imgs = ReaderSource.imagePages(t, kids)
                val chapters = ReaderSource.chapterNodes(kids)
                tree = t
                stack = dirIds
                nodes = chapters
                if (imgs.isNotEmpty() && chapters.isEmpty()) {
                    showBook(t, dirId, dirId, ReaderLogic.nameFromDocId(dirId), imgs, emptyList(), 0)
                } else if (chapters.isEmpty()) {
                    toast("Esta carpeta no tiene capítulos")
                }
            } finally {
                busy = false
            }
        }
    }

    fun openLoose(uri: Uri) {
        scope.launch {
            busy = true
            try {
                val files = withContext(Dispatchers.IO) {
                    try { ReaderSource.openArchive(ctx, uri) } catch (e: Exception) { emptyList() }
                }
                if (files.isEmpty()) toast("No se pudo abrir el archivo")
                else {
                    val name = withContext(Dispatchers.IO) { ReaderSource.displayName(ctx, uri) }
                    showBook(null, "", uri.toString(), name, files, emptyList(), 0)
                }
            } finally {
                busy = false
            }
        }
    }

    fun resume(p: ReaderLogic.Place) {
        link = null
        if (p.tree.isEmpty()) {
            openLoose(Uri.parse(p.item))
            return
        }
        val t = Uri.parse(p.tree)
        val root = try { DocumentsContract.getTreeDocumentId(t) } catch (e: Exception) { null }
        if (root == null) {
            toast("No se pudo abrir la carpeta")
            return
        }
        val ids = if (p.dir == root) listOf(root) else listOf(root, p.dir)
        if (p.item == p.dir) {
            browse(t, ids)
            return
        }
        scope.launch {
            busy = true
            try {
                val kids = withContext(Dispatchers.IO) {
                    try { ReaderSource.list(ctx, t, p.dir) } catch (e: Exception) { emptyList() }
                }
                val sibs = ReaderSource.chapterNodes(kids)
                tree = t
                stack = ids
                nodes = sibs
                val n = sibs.firstOrNull { it.docId == p.item }
                if (n != null) openNode(t, p.dir, sibs, n) else toast("No se encontró el capítulo")
            } finally {
                busy = false
            }
        }
    }

    /** Desde la ficha de un manhwa: busca su carpeta en las descargas y abre el capítulo que toca. */
    fun continueManhwa(title: String, lastRead: Double, path: List<String>? = null) {
        val saved = ReaderStore.downloads(ctx)
        if (saved == null) {
            toast("Elige primero la carpeta de descargas de Komikku")
            return
        }
        val t = Uri.parse(saved)
        val root = try { DocumentsContract.getTreeDocumentId(t) } catch (e: Exception) { null }
        if (root == null) {
            toast("No se pudo abrir la carpeta")
            return
        }
        scope.launch {
            busy = true
            try {
                val found = withContext(Dispatchers.IO) {
                    try {
                        // Si el índice ya sabe dónde está la carpeta, no hace falta buscarla otra vez.
                        if (path != null) ReaderSource.Found(path, ReaderSource.list(ctx, t, path.last()))
                        else ReaderSource.findManhwaDir(ctx, t, root, title)
                    } catch (e: Exception) { null }
                }
                if (found == null) {
                    toast("No encontré «$title» en tu carpeta de descargas")
                    return@launch
                }
                val chapters = ReaderSource.chapterNodes(found.kids)
                if (chapters.isEmpty()) {
                    toast("Esa carpeta no tiene capítulos")
                    return@launch
                }
                tree = t
                stack = found.path
                nodes = chapters
                direct = true
                link = title
                val partial = chapters.map { ReaderStore.inProgress(ctx, "$t|${it.docId}") }
                val i = ReaderProgress.pickChapter(chapters.map { it.name }, partial, lastRead)
                if (i == null) {
                    toast("Ya leíste todo lo descargado de «$title». Descarga el siguiente en Komikku.")
                    return@launch
                }
                openNode(t, found.path.last(), chapters, chapters[i])
            } finally {
                busy = false
            }
        }
    }

    fun goUp() {
        val t = tree ?: return
        if (stack.size > 1) browse(t, stack.dropLast(1))
        else {
            tree = null
            nodes = emptyList()
            stack = emptyList()
        }
    }

    /** Abre el capítulo número [i] de la carpeta (para "Anterior" y para cuando ya no hay más cargados). */
    fun goIndex(b: Book, i: Int) {
        val t = b.tree ?: return
        val n = b.siblings.getOrNull(i) ?: return
        openNode(t, b.dirId, b.siblings, n)
    }

    /** Vuelve a recorrer las descargas y actualiza cuántos capítulos hay por leer de cada manhwa. */
    fun rescan() {
        val saved = ReaderStore.downloads(ctx) ?: return
        if (scanning) return
        val t = Uri.parse(saved)
        val root = try { DocumentsContract.getTreeDocumentId(t) } catch (e: Exception) { null } ?: return
        scope.launch {
            scanning = true
            try {
                val found = withContext(Dispatchers.IO) {
                    try { ReaderSource.scanDownloads(ctx, t, root) } catch (e: Exception) { null }
                }
                if (found != null) {
                    index = found
                    ReaderStore.setIndex(ctx, ReaderIndex.encode(found), System.currentTimeMillis())
                }
            } finally {
                scanning = false
            }
        }
    }

    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            // La carpeta que eliges se recuerda: "Continuar leyendo" la usa para encontrar el capítulo de cada manhwa.
            ReaderStore.setDownloads(ctx, uri.toString())
            downloads = uri.toString()
            direct = false
            link = null
            rescan()
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            openLoose(uri)
        }
    }

    LaunchedEffect(Unit) {
        if (startTitle != null) {
            // Desde la ficha: si el índice conoce la carpeta de este manhwa, se abre directo.
            val e = ReaderIndex.entryFor(index, startTitle)
            continueManhwa(startTitle, startChapter, e?.path)
        }
        // Al abrir el panel, se actualiza el índice si no existe o tiene más de 10 minutos.
        if (ReaderStore.downloads(ctx) != null && (index.isEmpty() || System.currentTimeMillis() - ReaderStore.indexAt(ctx) > 10 * 60_000L)) rescan()
    }

    BackHandler {
        when {
            book != null -> closeBook()
            tree != null -> if (direct) onExit() else goUp()
            else -> onExit()
        }
    }

    Box(Modifier.fillMaxSize()) {
        val b = book
        val t = tree
        if (b != null) {
            key(b.key) {
                ReaderScreen(
                    book = b,
                    onClose = { closeBook() },
                    onOpenIndex = { i -> goIndex(b, i) }
                )
            }
        } else if (t == null) {
            ReaderHome(
                library = library,
                last = last,
                index = index,
                scanning = scanning,
                downloadsName = downloads?.let { d -> runCatching { ReaderLogic.nameFromDocId(DocumentsContract.getTreeDocumentId(Uri.parse(d))) }.getOrNull() },
                onResume = { last?.let { resume(it) } },
                onOpenManhwa = { m, e -> continueManhwa(m.title, m.lastChapter, e.path) },
                onKomikku = { m -> if (!ReaderLink.open(ctx, m.title)) toast("No encontré ninguna app lectora instalada en este teléfono") },
                onPickFolder = { treePicker.launch(null) },
                onBrowse = {
                    val u = downloads?.let { Uri.parse(it) }
                    val r = u?.let { runCatching { DocumentsContract.getTreeDocumentId(it) }.getOrNull() }
                    if (u != null && r != null) { direct = false; link = null; browse(u, listOf(r)) }
                },
                onRescan = { rescan() },
                onFile = { filePicker.launch(arrayOf("*/*")) },
                onExit = onExit
            )
        } else {
            val folder = ReaderLogic.nameFromDocId(stack.lastOrNull() ?: "")
            val bm = link?.let { lt -> library.firstOrNull { it.titleKey == titleKeyOf(lt) } } ?: ReaderProgress.match(library, folder)
            ReaderBrowse(
                title = folder,
                nodes = nodes,
                tree = t,
                manhwa = bm,
                library = library,
                onBack = { if (direct) onExit() else goUp() },
                onOpen = { n -> openNode(t, stack.last(), nodes, n) },
                onAdd = { vm.add(folder, 0.0, 0) }
            )
        }
        if (busy) {
            Box(
                Modifier.fillMaxSize().background(Color(0x88000000)).pointerInput(Unit) { detectTapGestures { } },
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        }
    }
}

private class ReadRow(val m: Manhwa, val e: ReaderIndex.Entry?, val unread: Int, val next: Double?)

@Composable
private fun CoverBox(url: String?, title: String, w: Dp = 56.dp, h: Dp = 80.dp) {
    Box(
        Modifier.size(w, h).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text(title.take(1).uppercase(), fontWeight = FontWeight.Bold)
    }
}

private fun readLine(m: Manhwa): String = when {
    m.lastChapter <= 0.0 -> "Sin empezar"
    m.totalChapters > 0 -> "Leído hasta el cap. ${ReaderProgress.fmt(m.lastChapter)} de ${m.totalChapters}"
    else -> "Leído hasta el cap. ${ReaderProgress.fmt(m.lastChapter)}"
}

/** La ventana "Leer": qué seguir leyendo, qué tienes descargado sin leer y a dónde volver, todo según tu biblioteca. */
@Composable
private fun ReaderHome(
    library: List<Manhwa>,
    last: ReaderLogic.Place?,
    index: List<ReaderIndex.Entry>,
    scanning: Boolean,
    downloadsName: String?,
    onResume: () -> Unit,
    onOpenManhwa: (Manhwa, ReaderIndex.Entry) -> Unit,
    onKomikku: (Manhwa) -> Unit,
    onPickFolder: () -> Unit,
    onBrowse: () -> Unit,
    onRescan: () -> Unit,
    onFile: () -> Unit,
    onExit: () -> Unit
) {
    val rows = remember(library, index) {
        library.filter { it.status != Status.COMPLETED }.map { m ->
            val e = ReaderIndex.entryFor(index, m.title)
            ReadRow(m, e, if (e != null) ReaderIndex.unread(e, m.lastChapter) else 0, e?.let { ReaderIndex.next(it, m.lastChapter) })
        }
    }
    val toRead = remember(rows) {
        rows.filter { it.unread > 0 }.sortedWith(compareBy<ReadRow>({ if (it.m.status == Status.READING) 0 else 1 }, { -it.m.updatedAt }))
    }
    val upToDate = remember(rows) { rows.filter { it.unread == 0 && it.m.status == Status.READING }.sortedByDescending { it.m.updatedAt } }
    val unreadTotal = toRead.sumOf { it.unread }
    val lastManhwa = remember(library, last) {
        last?.takeIf { it.tree.isNotEmpty() && it.dir != it.item }?.let { ReaderProgress.match(library, ReaderLogic.nameFromDocId(it.dir)) }
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onExit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Volver")) }
                Column(Modifier.weight(1f)) {
                    Text("Leer", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            downloadsName == null -> "Conecta tus descargas de Komikku"
                            scanning -> "Buscando capítulos descargados…"
                            toRead.isEmpty() -> "No tienes capítulos descargados por leer"
                            else -> "${toRead.size} manhwas con capítulos por leer · $unreadTotal capítulos"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (downloadsName != null) {
                    if (scanning) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    else IconButton(onClick = onRescan) { Icon(Icons.Filled.Refresh, contentDescription = tr("Actualizar")) }
                }
            }
        }

        if (last != null) {
            item {
                Card(
                    Modifier.fillMaxWidth().clickable(onClick = onResume),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (lastManhwa != null) { CoverBox(lastManhwa.coverUrl, lastManhwa.title); Spacer(Modifier.width(12.dp)) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Continuar donde te quedaste", style = MaterialTheme.typography.labelMedium)
                            Text(lastManhwa?.title ?: last.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (lastManhwa != null) Text(last.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text("▶", fontSize = 22.sp)
                    }
                }
            }
        }

        item {
            if (downloadsName == null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Elige tu carpeta de descargas", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Es la carpeta donde Komikku guarda los capítulos descargados (normalmente «Komikku/downloads»). Así la app sabe qué tienes por leer de cada manhwa y suma tu avance sola.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = onPickFolder, modifier = Modifier.fillMaxWidth()) { Text("Elegir carpeta de descargas") }
                    }
                }
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("📁 $downloadsName", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = onBrowse) { Text("Explorar") }
                        TextButton(onClick = onPickFolder) { Text("Cambiar") }
                    }
                }
            }
        }

        if (toRead.isNotEmpty()) {
            item { Text("Por leer", style = MaterialTheme.typography.titleMedium) }
            items(toRead, key = { "r" + it.m.id }) { r ->
                ReadRowCard(r, onOpen = { r.e?.let { e -> onOpenManhwa(r.m, e) } }, onKomikku = { onKomikku(r.m) })
            }
        } else if (downloadsName != null && !scanning && index.isEmpty()) {
            item {
                Text(
                    "No encontré capítulos en esa carpeta. Elige la carpeta «downloads» de Komikku: la que tiene una subcarpeta por cada fuente.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (upToDate.isNotEmpty()) {
            item { Text("Al día (${upToDate.size})", style = MaterialTheme.typography.titleMedium) }
            items(upToDate.take(40), key = { "u" + it.m.id }) { r ->
                ReadRowCard(r, onOpen = { r.e?.let { e -> onOpenManhwa(r.m, e) } }, onKomikku = { onKomikku(r.m) })
            }
        }

        item { OutlinedButton(onClick = onFile, modifier = Modifier.fillMaxWidth()) { Text("Abrir un archivo .cbz o .zip suelto") } }
    }
}

@Composable
private fun ReadRowCard(r: ReadRow, onOpen: () -> Unit, onKomikku: () -> Unit) {
    val m = r.m
    Card(Modifier.fillMaxWidth().clickable { if (r.unread > 0) onOpen() else onKomikku() }) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverBox(m.coverUrl, m.title)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(m.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(readLine(m), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (m.totalChapters > 0) {
                    LinearProgressIndicator(progress = { (m.lastChapter / m.totalChapters).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                }
                if (r.unread > 0 && r.next != null) {
                    Text("${r.unread} por leer · sigue el cap. ${ReaderProgress.fmt(r.next)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                } else {
                    Text(if (r.e != null) "Al día con lo descargado" else "Sin descargas encontradas", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(8.dp))
            if (r.unread > 0) FilledTonalButton(onClick = onOpen) { Text("Leer") }
            else TextButton(onClick = onKomikku) { Text("Komikku") }
        }
    }
}

/** Capítulos de una carpeta, marcados según tu biblioteca: leído, a medias, el que sigue o sin leer. */
@Composable
private fun ReaderBrowse(
    title: String,
    nodes: List<ReaderSource.Node>,
    tree: Uri,
    manhwa: Manhwa?,
    library: List<Manhwa>,
    onBack: () -> Unit,
    onOpen: (ReaderSource.Node) -> Unit,
    onAdd: () -> Unit
) {
    val ctx = LocalContext.current
    val names = nodes.map { it.name }
    val chapterLevel = manhwa != null || ReaderProgress.looksLikeChapters(names, nodes.map { it.isArchive })
    val lastRead = manhwa?.lastChapter ?: -1.0
    val partial = nodes.map { ReaderStore.inProgress(ctx, "$tree|${it.docId}") }
    val states = if (chapterLevel) {
        ReaderProgress.states(names, nodes.map { ReaderStore.done(ctx, "$tree|${it.docId}") }, partial, lastRead)
    } else emptyList()
    val target = if (chapterLevel) ReaderProgress.pickChapter(names, partial, lastRead) else null
    val listState = rememberLazyListState()
    LaunchedEffect(target, nodes.size) {
        // El primer elemento es la cabecera: así el capítulo que sigue queda a la vista con uno arriba.
        if (target != null && target > 1) listState.scrollToItem(target)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Volver")) }
            Text(manhwa?.title ?: title, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        if (nodes.isEmpty()) {
            Text("Esta carpeta está vacía", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            if (chapterLevel) {
                item {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (manhwa != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CoverBox(manhwa.coverUrl, manhwa.title)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(readLine(manhwa), fontWeight = FontWeight.SemiBold)
                                        if (manhwa.totalChapters > 0) {
                                            LinearProgressIndicator(progress = { (manhwa.lastChapter / manhwa.totalChapters).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                                        }
                                        Text("En tu biblioteca · " + tr(Status.label(manhwa.status)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (target != null) {
                                    val num = ReaderProgress.chapterNumber(names[target])
                                    Button(onClick = { onOpen(nodes[target]) }, modifier = Modifier.fillMaxWidth()) {
                                        Text(if (partial[target]) "▶  Seguir donde te quedaste" else if (num != null) "▶  Continuar · cap. ${ReaderProgress.fmt(num)}" else "▶  Continuar")
                                    }
                                } else {
                                    Text("Ya leíste todo lo descargado. Descarga el siguiente en Komikku.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                Text("«$title» no está en tu biblioteca, así que el avance no se guardará.", style = MaterialTheme.typography.bodyMedium)
                                Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("Agregar a mi biblioteca") }
                            }
                        }
                    }
                }
            }
            itemsIndexed(nodes) { i, n ->
                val st = states.getOrNull(i)
                val highlight = st == ReaderProgress.ChapterState.NEXT || st == ReaderProgress.ChapterState.PARTIAL
                val faded = st == ReaderProgress.ChapterState.READ
                val lib = if (!chapterLevel && n.isDir) ReaderProgress.match(library, n.name) else null
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (highlight) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { onOpen(n) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (chapterLevel) when (st) {
                            ReaderProgress.ChapterState.READ -> "✓"
                            ReaderProgress.ChapterState.PARTIAL -> "▶"
                            ReaderProgress.ChapterState.NEXT -> "●"
                            else -> "○"
                        } else if (n.isDir) "📁" else "📖",
                        fontSize = 18.sp,
                        color = if (faded) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(
                            n.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            color = if (faded) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f) else MaterialTheme.colorScheme.onSurface
                        )
                        val sub = when {
                            st == ReaderProgress.ChapterState.PARTIAL -> "A medias · pág. ${ReaderStore.position(ctx, "$tree|${n.docId}") + 1}"
                            st == ReaderProgress.ChapterState.NEXT -> "Siguiente"
                            lib != null -> readLine(lib)
                            else -> null
                        }
                        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/** Un capítulo dentro de la lectura continua. [index] es su posición entre los capítulos de la carpeta (-1 si no hay). */
private class Seg(
    val title: String,
    val pages: List<Any>,
    val ratios: List<Float>,
    val key: String,
    val index: Int,
    val docId: String
)

/**
 * Lector de páginas en lectura continua: al bajar, el capítulo siguiente se carga solo debajo del actual,
 * sin pulsar nada. Cada capítulo cuenta como leído (y se suma a tu biblioteca) al llegar a su final.
 */
@Composable
private fun ReaderScreen(
    book: Book,
    onClose: () -> Unit,
    onOpenIndex: (Int) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val firstTotal = book.pages.size
    val start = if (ReaderStore.done(ctx, book.key)) 0 else ReaderLogic.clampPage(ReaderStore.position(ctx, book.key), firstTotal)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = start)
    val segs = remember {
        mutableStateListOf(Seg(book.title, book.pages, book.ratios, book.key, book.index, book.siblings.getOrNull(book.index)?.docId ?: ""))
    }
    val counts = segs.map { it.pages.size }
    var bars by remember { mutableStateOf(true) }
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(0f) }
    var loadingNext by remember { mutableStateOf(false) }
    var noMore by remember { mutableStateOf(false) }
    val saved = remember { mutableStateMapOf<String, ReaderProgress.Saved>() }
    val undone = remember { mutableStateMapOf<String, Boolean>() }

    // Dónde estás ahora: capítulo y página dentro de ese capítulo.
    val loc = ReaderLogic.locate(counts, listState.firstVisibleItemIndex)
    val curSeg = loc.first.coerceIn(0, segs.size - 1)
    val cur = segs[curSeg]
    val total = cur.pages.size
    val current = ReaderLogic.clampPage(loc.second, total)

    // Guarda por dónde vas en el capítulo que estás leyendo y recuerda cuál es (para "Continuar").
    LaunchedEffect(listState) {
        snapshotFlow { ReaderLogic.locate(segs.map { it.pages.size }, listState.firstVisibleItemIndex) }.collect { (s, p) ->
            val seg = segs.getOrNull(s) ?: return@collect
            ReaderStore.setPosition(ctx, seg.key, ReaderLogic.clampPage(p, seg.pages.size))
            val t = book.tree
            if (t != null && seg.docId.isNotEmpty()) ReaderStore.setLast(ctx, ReaderLogic.Place(t.toString(), book.dirId, seg.docId, seg.title))
        }
    }

    // Un capítulo cuenta como leído al llegar a su final (aunque lo cruces rápido), y se suma a tu biblioteca.
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
            val c = segs.map { it.pages.size }
            for (si in ReaderLogic.finished(c, last)) {
                val seg = segs.getOrNull(si) ?: continue
                if (ReaderStore.done(ctx, seg.key)) continue
                ReaderStore.setDone(ctx, seg.key, true)
                val manga = book.manga ?: continue
                val app = ctx.applicationContext
                // Fuera de la pantalla: si cierras el lector enseguida, igual se guarda.
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    val sv = try { ReaderProgress.record(app, manga, seg.title, preferTitle = book.linkTitle) } catch (e: Exception) { null }
                    if (sv != null) withContext(Dispatchers.Main) { saved[seg.key] = sv }
                }
            }
        }
    }

    // Lectura continua: al acercarte al final, carga el capítulo siguiente y lo pone debajo.
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
            val t = book.tree ?: return@collect
            val tail = segs.last()
            if (loadingNext || noMore || tail.index < 0) return@collect
            if (!ReaderLogic.shouldLoadNext(segs.map { it.pages.size }, last)) return@collect
            val node = book.siblings.getOrNull(tail.index + 1)
            if (node == null) { noMore = true; return@collect }
            loadingNext = true
            try {
                val res = withContext(Dispatchers.IO) {
                    try { ReaderSource.open(ctx, t, node) } catch (e: Exception) { ReaderSource.Opened.Empty }
                }
                if (res is ReaderSource.Opened.Pages) {
                    val ratios = withContext(Dispatchers.IO) { res.pages.map { ReaderSource.ratio(ctx, it) ?: ReaderLogic.FALLBACK_RATIO } }
                    segs.add(Seg(node.name, res.pages, ratios, "$t|${node.docId}", tail.index + 1, node.docId))
                } else noMore = true
            } finally {
                loadingNext = false
            }
        }
    }

    // Pantalla completa mientras lees; al salir se devuelven las barras del sistema.
    DisposableEffect(Unit) {
        val window = (ctx as Activity).window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        onDispose {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        }
    }
    LaunchedEffect(bars) {
        val window = (ctx as Activity).window
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (bars) c.show(WindowInsetsCompat.Type.systemBars()) else c.hide(WindowInsetsCompat.Type.systemBars())
    }

    val hasPrev = book.tree != null && cur.index > 0
    val hasNext = curSeg < segs.size - 1 || (book.tree != null && cur.index >= 0 && cur.index < book.siblings.size - 1)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wDp = maxWidth
            val wPx = with(LocalDensity.current) { maxWidth.toPx() }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    // Pellizco para acercar y arrastre lateral con zoom; el scroll vertical queda libre.
                    .pointerInput(wPx) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val pressed = event.changes.count { it.pressed }
                                if (pressed >= 2) {
                                    val zc = event.calculateZoom()
                                    val pc = event.calculatePan()
                                    if (zc != 1f || pc != Offset.Zero) {
                                        zoom = ReaderLogic.clampZoom(zoom * zc)
                                        pan = ReaderLogic.clampPan(pan + pc.x, wPx, zoom)
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    }
                                } else if (zoom > 1f) {
                                    val pc = event.calculatePan()
                                    if (pc.x != 0f && abs(pc.x) > abs(pc.y)) {
                                        pan = ReaderLogic.clampPan(pan + pc.x, wPx, zoom)
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .pointerInput(Unit) { detectTapGestures { bars = !bars } }
            ) {
                segs.forEachIndexed { si, seg ->
                    itemsIndexed(seg.pages, key = { i, _ -> "${seg.key}#$i" }) { i, page ->
                        AsyncImage(
                            model = page,
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .graphicsLayer { translationX = pan }
                                .requiredWidth(wDp * zoom)
                                .background(Color(0xFF1A1A1A))
                                .aspectRatio(seg.ratios.getOrElse(i) { ReaderLogic.FALLBACK_RATIO })
                        )
                    }
                    item(key = "${seg.key}#end") {
                        val next = segs.getOrNull(si + 1)
                        val sv = saved[seg.key]
                        val isUndone = undone[seg.key] == true
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Fin de «${seg.title}»", color = Color(0xFFB0B0B0), fontSize = 13.sp)
                            if (sv != null) {
                                Text(
                                    if (isUndone) "Avance deshecho" else "✓ Guardado en tu biblioteca: «${sv.title}» · cap. ${ReaderProgress.fmt(sv.chapter)}",
                                    color = Color(0xFFB0B0B0), fontSize = 13.sp
                                )
                                if (!isUndone) TextButton(onClick = {
                                    undone[seg.key] = true
                                    val app = ctx.applicationContext
                                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch { try { ReaderProgress.undo(app, sv) } catch (_: Exception) { } }
                                }) { Text("Deshacer") }
                            }
                            if (next != null) {
                                Text("Sigue: ${next.title}", color = Color.White, fontWeight = FontWeight.SemiBold)
                            } else if (loadingNext && si == segs.size - 1) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                Text("Cargando el siguiente capítulo…", color = Color.White)
                            } else {
                                val more = book.tree != null && seg.index >= 0 && seg.index < book.siblings.size - 1
                                if (more) Button(onClick = { onOpenIndex(seg.index + 1) }) { Text("Siguiente capítulo") }
                                else Text("No hay más capítulos descargados", color = Color(0xFFB0B0B0))
                                OutlinedButton(onClick = onClose) { Text("Cerrar") }
                                Spacer(Modifier.navigationBarsPadding())
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(bars, Modifier.align(Alignment.TopCenter), enter = fadeIn(), exit = fadeOut()) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xCC000000)).statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onClose) { Text("‹ Atrás", color = Color.White) }
                Text(cur.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }

        AnimatedVisibility(bars, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
            Column(Modifier.fillMaxWidth().background(Color(0xCC000000)).navigationBarsPadding().padding(8.dp)) {
                if (total > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${current + 1}/$total", color = Color.White, fontSize = 12.sp)
                        Slider(
                            value = current.toFloat(),
                            onValueChange = { v ->
                                val t = v.roundToInt().coerceIn(0, total - 1)
                                scope.launch { listState.scrollToItem(ReaderLogic.flatIndex(segs.map { it.pages.size }, curSeg, t)) }
                            },
                            valueRange = 0f..(total - 1).toFloat(),
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onOpenIndex(cur.index - 1) }, enabled = hasPrev) {
                        Text("‹ Anterior", color = if (hasPrev) Color.White else Color.Gray)
                    }
                    Text("${ReaderLogic.percent(current, total)}%", color = Color.White)
                    TextButton(
                        onClick = {
                            if (curSeg < segs.size - 1) scope.launch { listState.scrollToItem(ReaderLogic.flatIndex(segs.map { it.pages.size }, curSeg + 1, 0)) }
                            else onOpenIndex(cur.index + 1)
                        },
                        enabled = hasNext
                    ) {
                        Text("Siguiente ›", color = if (hasNext) Color.White else Color.Gray)
                    }
                }
            }
        }
    }
}
