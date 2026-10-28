package com.manhwatracker.app

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manhwatracker.app.data.Manhwa

private object RatingFilter {
    const val ALL = "ALL"
    const val TOP = "TOP"
    const val GOOD = "GOOD"
    const val MID = "MID"
    const val NONE = "NONE"
    val all = listOf(ALL, TOP, GOOD, MID, NONE)

    fun label(f: String) = when (f) {
        ALL -> "Todos"
        TOP -> "9–10 · Favoritos"
        GOOD -> "7–8 · Buenos"
        MID -> "1–6 · Regulares"
        else -> "Sin calificar"
    }

    fun matches(f: String, m: Manhwa) = when (f) {
        TOP -> m.rating >= 9
        GOOD -> m.rating in 7..8
        MID -> m.rating in 1..6
        NONE -> m.rating == 0
        else -> true
    }
}

/** Ranking de tus manhwas por la nota que les diste. */
@Composable
fun RatingsScreen(vm: MainViewModel, onOpen: (Long) -> Unit) {
    val all by vm.items.collectAsState()
    var filter by rememberSaveable { mutableStateOf(RatingFilter.ALL) }

    val rated = remember(all) { all.filter { it.rating > 0 } }
    val average = if (rated.isEmpty()) 0.0 else rated.sumOf { it.rating }.toDouble() / rated.size
    val shown = remember(all, filter) {
        all.filter { RatingFilter.matches(filter, it) }
            .sortedWith(compareByDescending<Manhwa> { it.rating }.thenBy { it.title.lowercase() })
    }

    Column(Modifier.fillMaxSize()) {
        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Summary(if (rated.isEmpty()) "–" else String.format("%.1f", average), "Promedio")
                Summary("${rated.size}", "Calificados")
                Summary("${rated.count { it.rating >= 9 }}", "Favoritos")
            }
        }

        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RatingFilter.all.forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(RatingFilter.label(f)) })
            }
        }

        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (rated.isEmpty()) "Aún no calificaste ninguno.\n\nAbre un manhwa y elige tu nota en “Mi calificación”."
                    else "No hay manhwas en esta categoría."
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(shown, key = { _, m -> m.id }) { index, m ->
                    RatingRow(position = index + 1, m = m, onClick = { onOpen(m.id) }, onCoverMissing = { vm.coverFallback(m) })
                }
            }
        }
    }
}

@Composable
private fun Summary(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun RatingRow(position: Int, m: Manhwa, onClick: () -> Unit, onCoverMissing: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (m.rating > 0) {
                Text(
                    "$position",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.width(28.dp)
                )
            }
            Cover(m, onCoverMissing, Modifier.width(48.dp).height(68.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val note = m.review
                if (!note.isNullOrBlank()) {
                    Text(
                        note,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (m.rating > 0) {
                Spacer(Modifier.width(8.dp))
                Text("★ ${m.rating}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ratingColor(m.rating))
            }
        }
    }
}
