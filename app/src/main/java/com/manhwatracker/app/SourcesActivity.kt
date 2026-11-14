package com.manhwatracker.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.manhwatracker.app.data.RepoExt
import com.manhwatracker.app.data.Sources
import com.manhwatracker.app.data.SourcesStore
import com.manhwatracker.app.data.UpdateCheck
import kotlinx.coroutines.launch

/** Fuentes: repositorios de extensiones (los mismos de Komikku/Mihon) y sus extensiones. La app no trae ninguno. */
class SourcesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeState.load(this)
        setContent { ManhwaTheme { Surface(Modifier.fillMaxSize()) { SourcesScreen { finish() } } } }
    }

    companion object {
        fun open(ctx: Context) = ctx.startActivity(Intent(ctx, SourcesActivity::class.java))
    }
}

private fun ago(ms: Long): String {
    if (ms <= 0) return "nunca"
    val m = (System.currentTimeMillis() - ms) / 60_000
    return when {
        m < 1 -> "ahora mismo"
        m < 60 -> "hace $m min"
        m < 60 * 24 -> "hace ${m / 60} h"
        else -> "hace ${m / (60 * 24)} d"
    }
}

@Composable
private fun SourcesScreen(onExit: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var repos by remember { mutableStateOf(SourcesStore.repos(ctx)) }
    var all by remember { mutableStateOf(Sources.merge(repos.flatMap { SourcesStore.cached(ctx, it) })) }
    // De qué repositorio viene cada extensión (para bajar el APK de ahí).
    val origin = remember(repos, all) {
        val m = HashMap<String, String>()
        repos.forEach { r -> SourcesStore.cached(ctx, r).forEach { e -> val cur = all.firstOrNull { it.pkg == e.pkg }; if (cur != null && cur.code == e.code) m.putIfAbsent(e.pkg, r) } }
        m
    }
    var tick by remember { mutableStateOf(0) }              // sube al volver del instalador, para releer lo instalado
    var query by remember { mutableStateOf("") }
    var lang by remember { mutableStateOf<String?>(null) }
    var showNsfw by remember { mutableStateOf(SourcesStore.showNsfw(ctx)) }
    var refreshing by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    val progress = remember { mutableStateMapOf<String, Float>() }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, ev -> if (ev == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    fun reload() {
        repos = SourcesStore.repos(ctx)
        all = Sources.merge(repos.flatMap { SourcesStore.cached(ctx, it) })
    }

    fun refreshAll() {
        if (refreshing || repos.isEmpty()) return
        scope.launch {
            refreshing = true
            var failed = 0
            for (r in repos) if (SourcesStore.refresh(ctx, r).list == null) failed++
            reload()
            refreshing = false
            if (failed > 0) Toast.makeText(ctx, tr("No pude actualizar $failed repositorio(s)"), Toast.LENGTH_SHORT).show()
        }
    }

    // Al abrir: si hay repositorios guardados con más de una hora, se actualizan.
    LaunchedEffect(Unit) {
        if (repos.isNotEmpty() && repos.any { System.currentTimeMillis() - SourcesStore.fetchedAt(ctx, it) > 60 * 60_000L }) refreshAll()
    }

    fun install(e: RepoExt) {
        val base = origin[e.pkg] ?: return
        if (progress.containsKey(e.pkg)) return
        scope.launch {
            progress[e.pkg] = 0f
            val f = SourcesStore.downloadApk(ctx, base, e) { progress[e.pkg] = it }
            progress.remove(e.pkg)
            if (f == null) {
                Toast.makeText(ctx, tr("No pude bajar la extensión"), Toast.LENGTH_SHORT).show()
            } else if (UpdateCheck.install(ctx, f) == UpdateCheck.InstallResult.NEEDS_PERMISSION) {
                Toast.makeText(ctx, tr("Permite instalar apps desde esta app y vuelve a intentarlo"), Toast.LENGTH_LONG).show()
            }
        }
    }

    fun uninstall(e: RepoExt) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${e.pkg}"))) }
    }

    val langs = remember(all) { Sources.languages(all) }
    val shown = remember(all, query, lang, showNsfw) { Sources.filter(all, query, lang, showNsfw) }
    // Se vuelve a leer lo instalado al cambiar tick (volver del instalador).
    val installed = remember(all, tick) { all.associate { it.pkg to SourcesStore.installedCode(ctx, it.pkg) } }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onExit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Volver")) }
                Column(Modifier.weight(1f)) {
                    Text("Fuentes", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("Repositorios y extensiones", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (refreshing) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else if (repos.isNotEmpty()) IconButton(onClick = { refreshAll() }) { Icon(Icons.Filled.Refresh, contentDescription = tr("Actualizar")) }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Repositorios", fontWeight = FontWeight.SemiBold)
                    if (repos.isEmpty()) {
                        Text(
                            "Las fuentes son extensiones compatibles con Komikku y Mihon. Esta app no incluye ningún repositorio: pega la dirección del que quieras usar (la misma que pondrías en Komikku, en Más › Ajustes › Navegar › Repositorios de extensiones). Tú eres responsable de lo que uses con ellos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    repos.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(Sources.repoName(r), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val n = SourcesStore.cached(ctx, r).size
                                Text("$n extensiones · actualizado ${ago(SourcesStore.fetchedAt(ctx, r))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { SourcesStore.remove(ctx, r); reload() }) { Text("Quitar") }
                        }
                    }
                    Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text("Agregar repositorio") }
                }
            }
        }

        if (all.isNotEmpty()) {
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text("Buscar extensión o fuente") }, modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = lang == null, onClick = { lang = null }, label = { Text("Todos") })
                    langs.take(14).forEach { l ->
                        FilterChip(selected = lang == l, onClick = { lang = if (lang == l) null else l }, label = { Text(Sources.langLabel(l)) })
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mostrar contenido adulto (+18)", Modifier.weight(1f))
                    Switch(checked = showNsfw, onCheckedChange = { showNsfw = it; SourcesStore.setShowNsfw(ctx, it) })
                }
            }
            item { Text("${shown.size} extensiones", style = MaterialTheme.typography.titleMedium) }
            items(shown, key = { it.pkg }) { e ->
                ExtRow(
                    e = e,
                    iconUrl = origin[e.pkg]?.let { Sources.iconUrl(it, e) },
                    state = Sources.state(e, installed[e.pkg]),
                    progress = progress[e.pkg],
                    onInstall = { install(e) },
                    onUninstall = { uninstall(e) }
                )
            }
        }
    }

    if (adding) AddRepoDialog(
        onDismiss = { adding = false },
        onAdded = { adding = false; reload() }
    )
}

@Composable
private fun ExtRow(e: RepoExt, iconUrl: String?, state: Sources.State, progress: Float?, onInstall: () -> Unit, onUninstall: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                if (iconUrl != null) AsyncImage(model = iconUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(Sources.displayName(e), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    Sources.langLabel(e.lang) + " · v" + e.version + (if (e.nsfw != 0) " · +18" else "") +
                        (if (e.sources.size > 1) " · ${e.sources.size} fuentes" else ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (progress != null) {
                    if (progress >= 0f) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else if (state == Sources.State.INSTALLED) {
                    Text("Instalada", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                } else if (state == Sources.State.UPDATE) {
                    Text("Hay una versión nueva", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                }
            }
            Spacer(Modifier.width(8.dp))
            when {
                progress != null -> Unit
                state == Sources.State.NOT_INSTALLED -> FilledTonalButton(onClick = onInstall) { Text("Instalar") }
                state == Sources.State.UPDATE -> FilledTonalButton(onClick = onInstall) { Text("Actualizar") }
                else -> TextButton(onClick = onUninstall) { Text("Quitar") }
            }
        }
    }
}

@Composable
private fun AddRepoDialog(onDismiss: () -> Unit, onAdded: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Agregar repositorio") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Pega la dirección del repositorio (termina en index.min.json o es su carpeta).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text, onValueChange = { text = it; error = null }, singleLine = false, maxLines = 3,
                    label = { Text("Dirección") }, isError = error != null, modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(enabled = !busy && text.isNotBlank(), onClick = {
                val base = Sources.normalizeRepoUrl(text)
                if (base == null) { error = "Eso no parece una dirección web válida."; return@Button }
                scope.launch {
                    busy = true
                    val r = SourcesStore.refresh(ctx, base)
                    busy = false
                    if (r.list == null) error = r.error
                    else { SourcesStore.add(ctx, base); onAdded() }
                }
            }) { Text("Agregar") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") } }
    )
}
