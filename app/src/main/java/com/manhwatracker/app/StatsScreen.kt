package com.manhwatracker.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.manhwatracker.app.data.ActivityKind
import com.manhwatracker.app.data.Stats

/** Estadísticas: racha de repaso, palabras aprendidas por semana, capítulos por mes y obras con más palabras. */
@Composable
fun StatsScreen(vm: MainViewModel) {
    val words by vm.words.collectAsState()
    val activity by vm.activity.collectAsState()

    val today = remember { Stats.dayOf(System.currentTimeMillis()) }

    val reviewDays = remember(activity) {
        activity.filter { it.kind == ActivityKind.REVIEW && it.amount > 0 }.map { it.day }
    }
    val streak = remember(reviewDays, today) { Stats.streak(reviewDays, today) }
    val reviewedToday = activity.firstOrNull { it.day == today && it.kind == ActivityKind.REVIEW }?.amount ?: 0

    val learnedWithDate = remember(words) { words.filter { it.learned && it.learnedAt > 0 } }
    val weekly = remember(learnedWithDate, today) {
        Stats.weeklyCounts(learnedWithDate.map { Stats.dayOf(it.learnedAt) }, today, 8)
    }
    val learnedTotal = words.count { it.learned }
    val learnedNoDate = learnedTotal - learnedWithDate.size

    val chapters = remember(activity, today) {
        Stats.monthlySums(
            activity.filter { it.kind == ActivityKind.CHAPTER }.map { it.day to it.amount },
            today,
            6
        )
    }
    val perManhwa = remember(words) { Stats.wordsPerManhwa(words, 5) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---------------- Racha
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Racha de repaso", style = MaterialTheme.typography.titleSmall)
                Text(
                    "🔥 ${streak.current} ${if (streak.current == 1) "día" else "días"}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    when {
                        streak.reviewedToday -> "¡Hoy ya repasaste! ($reviewedToday ${if (reviewedToday == 1) "tarjeta" else "tarjetas"})"
                        streak.current > 0 -> "Repasa hoy para no perder tu racha."
                        else -> "Repasa hoy para empezar una racha."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Text("Mejor racha: ${streak.best} ${if (streak.best == 1) "día" else "días"}", style = MaterialTheme.typography.bodySmall)
            }
        }

        // ---------------- Resumen
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Palabras", words.size.toString(), Modifier.weight(1f))
            StatTile("Aprendidas", learnedTotal.toString(), Modifier.weight(1f))
            StatTile("Por repasar", (words.size - learnedTotal).toString(), Modifier.weight(1f))
        }

        // ---------------- Aprendidas por semana
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Palabras aprendidas por semana", style = MaterialTheme.typography.titleSmall)
                if (weekly.all { it.value == 0 }) {
                    Text(
                        "Todavía no hay datos. Aparecerán cuando una palabra llegue a “aprendida” en el repaso o la marques tú.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    BarChart(weekly)
                    Text("Cada barra es una semana (de lunes a domingo); la etiqueta es el día en que empieza.", style = MaterialTheme.typography.bodySmall)
                }
                if (learnedNoDate > 0) {
                    Text(
                        "$learnedNoDate ${if (learnedNoDate == 1) "palabra aprendida" else "palabras aprendidas"} antes de esta versión no tienen fecha y no salen aquí.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // ---------------- Capítulos por mes
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Capítulos leídos por mes", style = MaterialTheme.typography.titleSmall)
                if (chapters.all { it.value == 0 }) {
                    Text(
                        "Todavía no hay datos. Se cuentan los capítulos que sumas con el botón + , los que cambias a mano y los que traen tus backups de Komikku.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    BarChart(chapters)
                    Text("Se cuenta desde que instalaste esta versión.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // ---------------- Obras con más palabras
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("De qué obras aprendes más", style = MaterialTheme.typography.titleSmall)
                if (perManhwa.isEmpty()) {
                    Text(
                        "Aún no hay palabras con manhwa. Al guardar una palabra, indica de cuál manhwa viene.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    val max = perManhwa.maxOf { it.value }
                    perManhwa.forEach { b ->
                        Column {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    b.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text("${b.value}", style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(Modifier.height(2.dp))
                            LinearProgressIndicator(
                                progress = { b.value.toFloat() / max },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}

/** Gráfica de barras simple: una barra por elemento, con el número arriba y la etiqueta abajo. */
@Composable
private fun BarChart(data: List<Stats.Bucket>) {
    val max = maxOf(1, data.maxOfOrNull { it.value } ?: 0)
    val color = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().height(140.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        data.forEach { b ->
            val barHeight = if (b.value == 0) 1f else maxOf(3f, 90f * b.value / max)
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(if (b.value > 0) b.value.toString() else "", style = MaterialTheme.typography.labelSmall)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(barHeight.dp)
                        .background(color, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                )
                Text(b.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}
