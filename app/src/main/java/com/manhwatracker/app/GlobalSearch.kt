package com.manhwatracker.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import coil.request.ImageRequest
import com.manhwatracker.app.data.SearchRank
import com.manhwatracker.app.data.Sources
import com.manhwatracker.app.ext.ExtensionHost
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Lo que una fuente encontró para la búsqueda. */
private class Hit(val src: Source, val pkg: String, val items: List<SManga>)

/** Busca [query] en todas las fuentes instaladas a la vez y muestra en cuáles está. */
@Composable
internal fun GlobalSearchScreen(query: String, onBack: () -> Unit, onOpen: (Source, String, SManga) -> Unit) {
    val ctx = LocalContext.current
    val hits = remember(query) { mutableStateListOf<Hit>() }
    var total by remember(query) { mutableIntStateOf(0) }
    var done by remember(query) { mutableIntStateOf(0) }
    var failed by remember(query) { mutableIntStateOf(0) }
    var finished by remember(query) { mutableStateOf(false) }

    LaunchedEffect(query) {
        val hiddenKeys = com.manhwatracker.app.data.Prefs.hiddenSources(ctx)
        val sources = withContext(Dispatchers.IO) {
            ExtensionHost.installedExtensions(ctx).flatMap { info ->
                (ExtensionHost.load(ctx, info.packageName) as? ExtensionHost.Result.Ok)?.ext?.sources
                    ?.filterIsInstance<CatalogueSource>()?.map { it as Source to info.packageName } ?: emptyList()
            }.filter { (s, pkg) -> com.manhwatracker.app.data.SourceOrder.key(pkg, s.id) !in hiddenKeys }
        }
        total = sources.size
        // De 4 en 4, para no saturar el teléfono ni las páginas; cada fuente tiene 20 segundos.
        val gate = Semaphore(4)
        coroutineScope {
            sources.forEach { (src, pkg) ->
                launch {
                    gate.withPermit {
                        val found: List<SManga>? = try {
                            withTimeout(20_000) {
                                withContext(Dispatchers.IO) {
                                    val cs = src as CatalogueSource
                                    cs.getSearchManga(1, query, cs.getFilterList()).mangas
                                }
                            }
                        } catch (e: TimeoutCancellationException) { null }
                        catch (e: CancellationException) { throw e }
                        catch (e: Throwable) { null }
                        done++
                        if (found == null) failed++
                        else if (found.isNotEmpty()) hits.add(Hit(src, pkg, SearchRank.sortTitles(query, found) { it.title }))
                    }
                }
            }
        }
        finished = true
    }

    // Las fuentes con la mejor coincidencia primero.
    val ordered = remember(hits.size) {
        hits.sortedWith(
            compareByDescending<Hit> { SearchRank.best(query, it.items.map { m -> m.title }) }
                .thenBy { it.src.name.lowercase() }
        )
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Volver")) }
            Column(Modifier.weight(1f)) {
                Text("Buscando “$query”", fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        total == 0 && !finished -> "Cargando tus fuentes…"
                        total == 0 -> "No hay fuentes instaladas"
                        !finished -> "Buscando en $done de $total fuentes…"
                        else -> "Está en ${hits.size} de $total fuentes" + if (failed > 0) " · $failed no respondieron" else ""
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!finished && total > 0) LinearProgressIndicator(progress = { done.toFloat() / total }, modifier = Modifier.fillMaxWidth())

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ordered.isNotEmpty()) item(key = "summary") {
                Text(
                    "Dónde está: " + ordered.joinToString(", ") { it.src.name },
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary
                )
            }
            items(ordered, key = { it.pkg + it.src.id }) { hit ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(hit.src.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Sources.langLabel(hit.src.lang.ifBlank { "all" }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(hit.items.take(12), key = { it.url }) { m ->
                            Column(Modifier.width(96.dp).clickable { onOpen(hit.src, hit.pkg, m) }) {
                                Box(Modifier.width(96.dp).aspectRatio(0.7f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface)) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(ctx).data(m.thumbnail_url).apply { (hit.src as? HttpSource)?.headers?.let { headers(it) } }.build(),
                                        imageLoader = ExtensionHost.imageLoader(ctx), contentDescription = null,
                                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Text(m.title, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }
            if (finished && ordered.isEmpty()) item(key = "none") {
                Text(
                    if (total == 0) "Instala una extensión en Fuentes para poder buscar." else "Ninguna de tus fuentes tiene “$query”. Prueba con otro nombre o en inglés.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
