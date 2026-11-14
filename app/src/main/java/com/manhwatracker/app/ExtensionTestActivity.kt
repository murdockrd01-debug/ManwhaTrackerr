package com.manhwatracker.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.manhwatracker.app.ext.ExtensionHost
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.MangasPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Prueba del motor de extensiones: carga una extensión instalada y muestra los populares de su primera fuente.
 * Si algo falla, muestra el error completo para copiarlo.
 */
class ExtensionTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeState.load(this)
        val pkg = intent.getStringExtra("pkg").orEmpty()
        setContent { ManhwaTheme { Surface(Modifier.fillMaxSize()) { TestScreen(pkg) { finish() } } } }
    }

    companion object {
        fun open(ctx: Context, pkg: String) = ctx.startActivity(Intent(ctx, ExtensionTestActivity::class.java).putExtra("pkg", pkg))
    }
}

private fun copy(ctx: Context, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("error", text))
    Toast.makeText(ctx, tr("Diagnóstico copiado"), Toast.LENGTH_SHORT).show()
}

@Composable
private fun TestScreen(pkg: String, onExit: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<ExtensionHost.Result?>(null) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(pkg, reload) {
        result = null
        result = withContext(Dispatchers.IO) { ExtensionHost.load(ctx, pkg) }
    }
    var sel by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf<MangasPage?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun fetch(src: Source) {
        scope.launch {
            loading = true; page = null; failure = null
            try {
                page = withContext(Dispatchers.IO) { src.getPopularManga(1) }
            } catch (e: Throwable) {
                failure = e.stackTraceToString().take(3500)
            }
            loading = false
        }
    }

    val ok = result as? ExtensionHost.Result.Ok
    LaunchedEffect(ok, sel) { ok?.ext?.sources?.getOrNull(sel)?.let { fetch(it) } }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onExit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Volver")) }
            Column(Modifier.weight(1f)) {
                Text(ok?.ext?.name ?: "Probar extensión", fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Prueba del motor (experimental)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when (val r = result) {
            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is ExtensionHost.Result.NeedsTrust -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("¿Confiar en esta extensión?", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text(
                    "${r.name} es código de terceros que se ejecutaría dentro de esta app, con acceso a internet. Confía solo si el repositorio del que la instalaste es de confianza.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = { ExtensionHost.trust(ctx, r.signatures); reload++ }, modifier = Modifier.fillMaxWidth()) { Text("Confiar y probar") }
                OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
            }
            is ExtensionHost.Result.Fail -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("No se pudo cargar", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text(r.message)
                OutlinedButton(onClick = { copy(ctx, r.message + "\n" + r.detail.take(3500)) }, modifier = Modifier.fillMaxWidth()) { Text("Copiar diagnóstico") }
            }
            is ExtensionHost.Result.Ok -> {
                val sources = r.ext.sources
                if (sources.size > 1) {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        sources.forEachIndexed { i, s ->
                            FilterChip(selected = i == sel, onClick = { sel = i }, label = { Text(s.name + (s as? eu.kanade.tachiyomi.source.CatalogueSource)?.lang?.let { " ($it)" }.orEmpty()) })
                        }
                    }
                }
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    failure != null -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("La fuente dio un error", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        Text(failure!!.lines().take(4).joinToString("\n"), style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { copy(ctx, failure!!) }, modifier = Modifier.fillMaxWidth()) { Text("Copiar diagnóstico") }
                    }
                    page != null -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item { Text("${page!!.mangas.size} populares", style = MaterialTheme.typography.titleMedium) }
                        items(page!!.mangas) { m ->
                            Card(Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(width = 54.dp, height = 76.dp).clip(RoundedCornerShape(8.dp))) {
                                        m.thumbnail_url?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(m.title, fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
