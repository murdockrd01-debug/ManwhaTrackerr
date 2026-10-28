package com.manhwatracker.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.manhwatracker.app.data.AniList
import com.manhwatracker.app.data.Translator
import kotlinx.coroutines.launch

/**
 * Reseñas de otros lectores (AniList). Se cargan cuando se toca el botón, con [load].
 * [resetKey] reinicia la sección al cambiar de manhwa.
 */
@Composable
fun ReviewsSection(resetKey: Any, load: suspend () -> List<AniList.Review>?) {
    val scope = rememberCoroutineScope()
    var reviews by remember(resetKey) { mutableStateOf<List<AniList.Review>?>(null) }
    var loading by remember(resetKey) { mutableStateOf(false) }
    var error by remember(resetKey) { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Reseñas de otros lectores", style = MaterialTheme.typography.titleSmall)
        val list = reviews
        when {
            loading -> Text("Buscando…", style = MaterialTheme.typography.bodySmall)
            list == null -> {
                OutlinedButton(
                    onClick = {
                        loading = true
                        error = null
                        scope.launch {
                            val result = load()
                            if (result == null) error = "No pude conectar con AniList. Revisa tu internet." else reviews = result
                            loading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Ver reseñas") }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
            list.isEmpty() -> Text("Todavía no hay reseñas de este título en AniList.", style = MaterialTheme.typography.bodySmall)
            else -> list.forEach { r -> ReviewCard(r) }
        }
    }
}

@Composable
private fun ReviewCard(r: AniList.Review) {
    val scope = rememberCoroutineScope()
    var translated by remember(r) { mutableStateOf<String?>(null) }
    var working by remember(r) { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.user, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                r.score?.let { Text("★ ${it / 10.0}", style = MaterialTheme.typography.labelLarge, color = ratingColor((it / 10.0).toInt())) }
            }
            Text(translated ?: r.summary, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (r.helpful > 0) {
                    Text("👍 ${r.helpful}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                } else {
                    Text("", modifier = Modifier.weight(1f))
                }
                TextButton(
                    enabled = !working,
                    onClick = {
                        if (translated != null) {
                            translated = null
                        } else {
                            working = true
                            scope.launch {
                                translated = Translator.toSpanish(r.summary) ?: "No pude traducirla ahora (límite diario o sin internet)."
                                working = false
                            }
                        }
                    }
                ) { Text(if (translated != null) "Ver original" else if (working) "Traduciendo…" else "Traducir al español") }
            }
        }
    }
}
