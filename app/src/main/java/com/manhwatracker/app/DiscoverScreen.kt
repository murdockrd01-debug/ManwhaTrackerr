package com.manhwatracker.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.manhwatracker.app.data.AniList
import com.manhwatracker.app.data.Genres
import com.manhwatracker.app.data.titleKeyOf

private val discoverTabs = listOf("FORYOU" to "Para ti", "TREND" to "Tendencias", "POPULAR" to "Populares", "TOP" to "Mejor valorados")

/** Géneros que se pueden filtrar (nombre de AniList). */
private val discoverGenres = listOf(
    "Action", "Fantasy", "Romance", "Comedy", "Drama", "Horror", "Mystery",
    "Supernatural", "Sci-Fi", "Sports", "Psychological", "Adventure", "Slice of Life", "Thriller"
)

/** Descubrir: manhwas que podrían gustarte, según lo que lees, y listas de tendencias. Todo viene de AniList. */
@Composable
fun DiscoverScreen(vm: MainViewModel) {
    val mine by vm.items.collectAsState()
    var tab by rememberSaveable { mutableStateOf("FORYOU") }
    var genre by rememberSaveable { mutableStateOf<String?>(null) }
    var picks by remember { mutableStateOf<List<AniList.Similar>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<AniList.Similar?>(null) }

    LaunchedEffect(tab, genre, reload) {
        loading = true
        error = null
        val result = vm.discover(tab, if (tab == "FORYOU") null else genre, force = reload > 0)
        picks = result.items
        error = result.error
        loading = false
    }

    val ownedKeys = remember(mine) { mine.map { it.titleKey }.toSet() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            discoverTabs.forEach { (id, label) ->
                FilterChip(selected = tab == id, onClick = { tab = id }, label = { Text(label) })
            }
        }
        if (tab != "FORYOU") {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = genre == null, onClick = { genre = null }, label = { Text("Todos") })
                discoverGenres.forEach { g ->
                    FilterChip(
                        selected = genre == g,
                        onClick = { genre = if (genre == g) null else g },
                        label = { Text(Genres.label(g)) }
                    )
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            when {
                loading -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (tab == "FORYOU") "Buscando según lo que lees…" else "Cargando…",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                error != null -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(error ?: "", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { reload++ }) { Text("Actualizar") }
                }

                picks.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        if (tab == "FORYOU") "Aún no hay sugerencias. Califica o lee más manhwas y vuelve."
                        else "No hay resultados con ese filtro.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(picks, key = { it.id }) { s ->
                        DiscoverCard(s, have = titleKeyOf(s.title) in ownedKeys, onClick = { selected = s }, onAdd = { vm.addPlanned(s.title, s.cover) })
                    }
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        TextButton(onClick = { reload++ }, modifier = Modifier.fillMaxWidth()) { Text("Actualizar") }
                    }
                }
            }
        }
    }

    selected?.let { s ->
        val have = titleKeyOf(s.title) in ownedKeys
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(s.title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.score?.let { Text("★ ${it / 10.0} en AniList", style = MaterialTheme.typography.labelLarge) }
                    if (s.genres.isNotEmpty()) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            s.genres.forEach { g -> AssistChip(onClick = {}, label = { Text(Genres.label(g)) }) }
                        }
                    }
                    if (s.because.isNotEmpty()) {
                        Text("Porque lees: " + s.because.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(s.description ?: "AniList no tiene sinopsis para este título.", style = MaterialTheme.typography.bodyMedium)
                    ReviewsSection(resetKey = s.id) { vm.loadReviewsById(s.id) }
                }
            },
            confirmButton = {
                if (have) {
                    TextButton(onClick = { selected = null }) { Text("✓ Ya lo tienes") }
                } else {
                    Button(onClick = {
                        vm.addPlanned(s.title, s.cover)
                        selected = null
                    }) { Text("＋ Agregar a pendientes") }
                }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Cerrar") } }
        )
    }
}

@Composable
private fun DiscoverCard(s: AniList.Similar, have: Boolean, onClick: () -> Unit, onAdd: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
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
                            .padding(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xCC000000))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(s.title, style = MaterialTheme.typography.labelLarge, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (s.because.isNotEmpty()) {
                    Text(
                        "Porque lees " + s.because.first(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (have) {
                    Text("✓ Ya lo tienes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                } else {
                    TextButton(onClick = onAdd, contentPadding = PaddingValues(0.dp)) { Text("＋ Pendiente") }
                }
            }
        }
    }
}
