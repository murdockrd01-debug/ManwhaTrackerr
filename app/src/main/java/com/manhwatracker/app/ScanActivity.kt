package com.manhwatracker.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.manhwatracker.app.data.Prefs
import com.manhwatracker.app.data.ScanLogic
import com.manhwatracker.app.data.WordRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Aparece al compartir una imagen (por ejemplo una captura de pantalla): lee el texto,
 * dejas marcar lo que quieres y lo guarda en tu vocabulario con el manhwa y capítulo actuales.
 */
class ScanActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeState.load(this)
        Features.load(this)
        Tr.lang = Prefs.language(this)

        val uri = if (intent?.action == Intent.ACTION_SEND)
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) else null
        if (uri == null) {
            Toast.makeText(this, tr("No se pudo abrir el archivo"), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        setContent {
            ManhwaTheme {
                Surface(Modifier.fillMaxSize()) { ScanScreen(uri) { finish() } }
            }
        }
    }
}

private fun loadBitmap(context: Context, uri: Uri): Bitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    }
} catch (e: Exception) {
    null
}

private suspend fun recognize(bmp: Bitmap): List<ScanLogic.Line>? = suspendCancellableCoroutine { cont ->
    try {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { t ->
                val out = t.textBlocks.flatMap { it.lines }.mapNotNull { l ->
                    l.boundingBox?.let { b ->
                        ScanLogic.Line(l.text, b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
                    }
                }
                if (cont.isActive) cont.resume(out)
            }
            .addOnFailureListener { if (cont.isActive) cont.resume(null) }
    } catch (e: Exception) {
        if (cont.isActive) cont.resume(null)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScanScreen(uri: Uri, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var lines by remember { mutableStateOf<List<ScanLogic.Line>>(emptyList()) }
    var state by remember { mutableStateOf("LOADING") } // LOADING, READY, ERROR
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var text by remember { mutableStateOf("") }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragNow by remember { mutableStateOf<Offset?>(null) }
    var saved by remember { mutableStateOf<Set<String>>(emptySet()) }
    var message by remember { mutableStateOf<String?>(null) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val manhwa = remember { Prefs.lastManhwa(ctx) }
    val chapter = remember { Prefs.lastChapter(ctx) }

    LaunchedEffect(uri) {
        val bmp = withContext(Dispatchers.IO) { loadBitmap(ctx, uri) }
        if (bmp == null) {
            state = "ERROR"
            return@LaunchedEffect
        }
        bitmap = bmp
        val found = recognize(bmp)
        if (found == null) {
            state = "ERROR"
        } else {
            lines = found
            state = "READY"
        }
    }

    fun select(newSel: Set<Int>) {
        selected = newSel
        text = ScanLogic.join(lines, newSel.sorted())
    }

    fun save(t: String) {
        val clean = t.trim()
        if (clean.isEmpty()) return
        saved = saved + clean.lowercase()
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                WordRepo.save(ctx.applicationContext, clean, manhwa, chapter, null)
            }
            message = if (r.added) "Guardada: $clean" else "Ya la tenías guardada"
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Guardar texto de la imagen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (manhwa != null) "En: $manhwa" else "En: ningún manhwa",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onClose) { Text("Cerrar") }
        }

        // ---- Imagen con el texto detectado
        Box(
            Modifier.weight(1f).fillMaxWidth().onSizeChanged { boxSize = it },
            contentAlignment = Alignment.Center
        ) {
            val bmp = bitmap
            if (bmp != null && boxSize.width > 0) {
                val scale = minOf(boxSize.width / bmp.width.toFloat(), boxSize.height / bmp.height.toFloat())
                val ox = (boxSize.width - bmp.width * scale) / 2f
                val oy = (boxSize.height - bmp.height * scale) / 2f
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                val primary = MaterialTheme.colorScheme.primary
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(lines, boxSize, selected) {
                            detectTapGestures { p ->
                                val idx = ScanLogic.lineAt(lines, (p.x - ox) / scale, (p.y - oy) / scale)
                                if (idx != null) select(if (idx in selected) selected - idx else selected + idx)
                            }
                        }
                        .pointerInput(lines, boxSize) {
                            detectDragGestures(
                                onDragStart = { dragStart = it; dragNow = it },
                                onDrag = { change, _ -> dragNow = change.position },
                                onDragEnd = {
                                    val a = dragStart
                                    val b = dragNow
                                    if (a != null && b != null) {
                                        val hit = ScanLogic.pick(
                                            lines,
                                            (a.x - ox) / scale, (a.y - oy) / scale,
                                            (b.x - ox) / scale, (b.y - oy) / scale
                                        )
                                        if (hit.isNotEmpty()) select(hit.toSet())
                                    }
                                    dragStart = null
                                    dragNow = null
                                },
                                onDragCancel = { dragStart = null; dragNow = null }
                            )
                        }
                ) {
                    lines.forEachIndexed { i, ln ->
                        val tl = Offset(ln.left * scale + ox, ln.top * scale + oy)
                        val sz = Size((ln.right - ln.left) * scale, (ln.bottom - ln.top) * scale)
                        if (i in selected) {
                            drawRect(primary.copy(alpha = 0.35f), tl, sz)
                            drawRect(primary, tl, sz, style = Stroke(width = 3f))
                        } else {
                            drawRect(Color.White.copy(alpha = 0.9f), tl, sz, style = Stroke(width = 2f))
                        }
                    }
                    val a = dragStart
                    val b = dragNow
                    if (a != null && b != null) {
                        drawRect(
                            primary, Offset(minOf(a.x, b.x), minOf(a.y, b.y)),
                            Size(kotlin.math.abs(a.x - b.x), kotlin.math.abs(a.y - b.y)), style = Stroke(width = 4f)
                        )
                    }
                }
            }
            if (state == "LOADING") Text("Leyendo el texto…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // ---- Panel de abajo
        Surface(tonalElevation = 3.dp, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when {
                    state == "ERROR" -> Text("No pude leer la imagen.", color = MaterialTheme.colorScheme.error)
                    state == "LOADING" -> Text("Leyendo el texto…")
                    lines.isEmpty() -> Text("No encontré texto en la imagen.")
                    selected.isEmpty() -> {
                        Text("Toca una línea o arrastra el dedo para marcar el texto.", style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = { select(lines.indices.toSet()) }) { Text("Seleccionar todo") }
                    }
                    else -> {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text("Texto (puedes corregirlo)") },
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { save(text) },
                                enabled = text.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            ) { Text(if (text.trim().lowercase() in saved) "✓ Frase guardada" else "Guardar frase") }
                            OutlinedButton(onClick = { select(emptySet()) }) { Text("Limpiar") }
                        }
                        val ws = ScanLogic.words(text)
                        if (ws.isNotEmpty()) {
                            Text("O guarda palabras sueltas:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ws.forEach { w ->
                                    val done = w.lowercase() in saved
                                    AssistChip(onClick = { save(w) }, label = { Text(if (done) "✓ $w" else w) })
                                }
                            }
                        }
                    }
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            }
        }
    }
}
