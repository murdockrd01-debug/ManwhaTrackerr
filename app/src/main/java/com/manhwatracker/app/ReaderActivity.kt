package com.manhwatracker.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
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
    val manga: String?
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
        book = Book(name, pages, ratios, "${place.tree}|$item", t, dirId, sibs, idx, manga)
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
    fun continueManhwa(title: String, lastRead: Double) {
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
                    try { ReaderSource.findManhwaDir(ctx, t, root, title) } catch (e: Exception) { null }
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

    fun goChapter(b: Book, delta: Int) {
        val t = b.tree ?: return
        val n = b.siblings.getOrNull(b.index + delta) ?: return
        openNode(t, b.dirId, b.siblings, n)
    }

    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            // La carpeta que eliges se recuerda: "Continuar leyendo" la usa para encontrar el capítulo de cada manhwa.
            ReaderStore.setDownloads(ctx, uri.toString())
            direct = false
            browse(uri, listOf(DocumentsContract.getTreeDocumentId(uri)))
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
        if (startTitle != null) continueManhwa(startTitle, startChapter)
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
                    onPrev = if (b.tree != null && b.index > 0) ({ goChapter(b, -1) }) else null,
                    onNext = if (b.tree != null && b.index >= 0 && b.index < b.siblings.size - 1) ({ goChapter(b, 1) }) else null
                )
            }
        } else if (t == null) {
            ReaderHome(
                last = last,
                onFolder = { treePicker.launch(null) },
                onFile = { filePicker.launch(arrayOf("*/*")) },
                onResume = { last?.let { resume(it) } },
                onExit = onExit
            )
        } else {
            ReaderBrowse(
                title = ReaderLogic.nameFromDocId(stack.lastOrNull() ?: ""),
                nodes = nodes,
                tree = t,
                onBack = { if (direct) onExit() else goUp() },
                onOpen = { n -> openNode(t, stack.last(), nodes, n) }
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

@Composable
private fun ReaderHome(
    last: ReaderLogic.Place?,
    onFolder: () -> Unit,
    onFile: () -> Unit,
    onResume: () -> Unit,
    onExit: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onExit) { Text("‹ Volver") }
        Text("📖 Lector", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            "Lee tus capítulos descargados: elige una carpeta con tus manhwas (cada subcarpeta o archivo .cbz es un capítulo) o abre un .cbz suelto.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (last != null) {
            Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) { Text("Continuar: ${last.name}", maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        OutlinedButton(onClick = onFolder, modifier = Modifier.fillMaxWidth()) { Text("Elegir carpeta") }
        Text(
            "La carpeta que elijas se recuerda para «Continuar leyendo» en la ficha de cada manhwa.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = onFile, modifier = Modifier.fillMaxWidth()) { Text("Abrir archivo .cbz o .zip") }
    }
}

@Composable
private fun ReaderBrowse(
    title: String,
    nodes: List<ReaderSource.Node>,
    tree: Uri,
    onBack: () -> Unit,
    onOpen: (ReaderSource.Node) -> Unit
) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Atrás") }
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        if (nodes.isEmpty()) {
            Text("Esta carpeta está vacía", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(nodes) { n ->
                val done = ReaderStore.done(ctx, "$tree|${n.docId}")
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(n) }.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (n.isDir) "📁" else "📖", fontSize = 20.sp)
                    Text(n.name, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (done) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ReaderScreen(
    book: Book,
    onClose: () -> Unit,
    onPrev: (() -> Unit)?,
    onNext: (() -> Unit)?
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val total = book.pages.size
    val start = if (ReaderStore.done(ctx, book.key)) 0 else ReaderLogic.clampPage(ReaderStore.position(ctx, book.key), total)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = start)
    var bars by remember { mutableStateOf(true) }
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(0f) }
    var current by remember { mutableStateOf(start) }

    // Guarda por dónde vas y marca el capítulo como terminado al llegar a la última página.
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { i ->
            val p = ReaderLogic.clampPage(i, total)
            current = p
            ReaderStore.setPosition(ctx, book.key, p)
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { i ->
            if (i >= total - 1 && !ReaderStore.done(ctx, book.key)) {
                ReaderStore.setDone(ctx, book.key, true)
                val manga = book.manga
                if (manga != null) {
                    val app = ctx.applicationContext
                    // Fuera de la pantalla: si cierras el lector enseguida, igual se guarda.
                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                        val saved = try { ReaderProgress.record(app, manga, book.title) } catch (e: Exception) { null }
                        if (saved != null) {
                            val n = saved.chapter.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                Toast.makeText(app, tr("Cap. $n guardado en") + " " + saved.title, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
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
                itemsIndexed(book.pages) { i, page ->
                    AsyncImage(
                        model = page,
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .graphicsLayer { translationX = pan }
                            .requiredWidth(wDp * zoom)
                            .background(Color(0xFF1A1A1A))
                            .aspectRatio(book.ratios.getOrElse(i) { ReaderLogic.FALLBACK_RATIO })
                    )
                }
                item {
                    Column(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Fin del capítulo", color = Color.White)
                        if (onNext != null) Button(onClick = onNext) { Text("Siguiente capítulo") }
                        OutlinedButton(onClick = onClose) { Text("Cerrar") }
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
                Text(book.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
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
                                val t = v.roundToInt()
                                current = t
                                scope.launch { listState.scrollToItem(t) }
                            },
                            valueRange = 0f..(total - 1).toFloat(),
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onPrev?.invoke() }, enabled = onPrev != null) {
                        Text("‹ Anterior", color = if (onPrev != null) Color.White else Color.Gray)
                    }
                    Text("${ReaderLogic.percent(current, total)}%", color = Color.White)
                    TextButton(onClick = { onNext?.invoke() }, enabled = onNext != null) {
                        Text("Siguiente ›", color = if (onNext != null) Color.White else Color.Gray)
                    }
                }
            }
        }
    }
}
