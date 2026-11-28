package com.manhwatracker.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manhwatracker.app.data.GrammarDrill
import com.manhwatracker.app.data.Structures

private const val ROUND = 10

/** Rondas de ejercicios de gramática: completar frases y reconocer la estructura. Vive dentro de la pestaña Estudio. */
@Composable
fun ExercisesPanel() {
    var mode by remember { mutableStateOf(GrammarDrill.Mode.MIXED) }
    var session by remember { mutableStateOf<List<GrammarDrill.Question>?>(null) }
    var index by remember { mutableStateOf(0) }
    var correct by remember { mutableStateOf(0) }
    var missed by remember { mutableStateOf(listOf<String>()) }

    val qs = session
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            when {
                qs == null -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Ejercicios de gramática", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Rondas de 10 preguntas. Practicas escribiendo y reconociendo estructuras, no solo leyendo.", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = mode == GrammarDrill.Mode.MIXED, onClick = { mode = GrammarDrill.Mode.MIXED }, label = { Text("Mezcla") })
                        FilterChip(selected = mode == GrammarDrill.Mode.FILL, onClick = { mode = GrammarDrill.Mode.FILL }, label = { Text("Completar") })
                        FilterChip(selected = mode == GrammarDrill.Mode.IDENTIFY, onClick = { mode = GrammarDrill.Mode.IDENTIFY }, label = { Text("¿Qué estructura es?") })
                    }
                    Button(onClick = { session = GrammarDrill.build(ROUND, mode); index = 0; correct = 0; missed = emptyList() }) { Text("Empezar ronda") }
                }
                index >= qs.size -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("¡Ronda terminada!", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("$correct / ${qs.size}", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    val weak = missed.distinct().mapNotNull { Structures.byId(it) }
                    if (weak.isEmpty()) Text("¡Sin errores!")
                    else {
                        Text("Repasa estas estructuras (pestaña Estructuras):", fontWeight = FontWeight.SemiBold)
                        weak.forEach { Text("📐 ${it.title} — ${it.pattern}", style = MaterialTheme.typography.bodyMedium) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { session = GrammarDrill.build(ROUND, mode); index = 0; correct = 0; missed = emptyList() }) { Text("Otra ronda") }
                        OutlinedButton(onClick = { session = null }) { Text("Cambiar modo") }
                    }
                }
                else -> {
                    val q = qs[index]
                    QuestionCard(
                        key = index, q = q, number = index + 1, total = qs.size,
                        onAnswered = { ok -> if (ok) correct++ else missed = missed + q.structureId },
                        onNext = { index++ }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuestionCard(key: Int, q: GrammarDrill.Question, number: Int, total: Int, onAnswered: (Boolean) -> Unit, onNext: () -> Unit) {
    var typed by remember(key) { mutableStateOf("") }
    var verdict by remember(key) { mutableStateOf<Boolean?>(null) }
    var chosen by remember(key) { mutableStateOf<String?>(null) }
    fun finish(ok: Boolean) { if (verdict == null) { verdict = ok; onAnswered(ok) } }

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LinearProgressIndicator(progress = { (number - 1f) / total }, modifier = Modifier.fillMaxWidth())
            Text("$number / $total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (q.kind == GrammarDrill.Kind.FILL) {
                Text("Completa la frase", fontWeight = FontWeight.SemiBold)
                Text(q.prompt, fontSize = 18.sp)
                OutlinedTextField(
                    value = typed, onValueChange = { typed = it }, singleLine = true, enabled = verdict == null,
                    modifier = Modifier.fillMaxWidth(), placeholder = { Text("Escribe la respuesta") }
                )
                if (verdict == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = typed.isNotBlank(), onClick = { finish(GrammarDrill.check(q.answer, typed)) }) { Text("Comprobar") }
                        TextButton(onClick = { finish(false) }) { Text("Ver respuesta") }
                    }
                }
            } else {
                Text("¿Qué estructura usa esta frase?", fontWeight = FontWeight.SemiBold)
                Text(q.prompt, fontSize = 18.sp)
                q.options.forEach { opt ->
                    val picked = chosen == opt
                    val state = verdict
                    OutlinedButton(
                        enabled = state == null,
                        onClick = { chosen = opt; finish(opt == q.answer) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(opt + if (state != null && opt == q.answer) "  ✓" else if (picked && state == false) "  ✗" else "") }
                }
            }
            verdict?.let { ok ->
                Text(
                    if (ok) "¡Correcto!" else "Respuesta: " + (if (q.kind == GrammarDrill.Kind.FILL) GrammarDrill.shown(q.answer) else q.answer),
                    color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold
                )
                Structures.byId(q.structureId)?.let { st ->
                    Text("📐 ${st.title}: ${st.pattern}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(st.tip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = onNext) { Text(if (number == total) "Ver resultado" else "Siguiente") }
            }
        }
    }
}
