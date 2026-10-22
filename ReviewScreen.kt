package com.manhwatracker.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.manhwatracker.app.data.Word

/**
 * Repaso con tarjetas: se ve la palabra en inglés, la piensas, tocas para ver la respuesta
 * y dices si la sabías. Las que fallas vuelven a salir al final de la ronda.
 */
@Composable
fun ReviewScreen(vm: MainViewModel, onExit: () -> Unit) {
    val words by vm.words.collectAsState()

    // Cada vez que cambia [session] se arma una ronda nueva.
    var session by remember { mutableIntStateOf(0) }
    var practiceAll by remember { mutableStateOf(false) }

    var loaded by remember { mutableStateOf(false) }
    var queue by remember { mutableStateOf<List<Word>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var known by remember { mutableIntStateOf(0) }
    var missed by remember { mutableIntStateOf(0) }
    var flipped by remember { mutableStateOf(false) }

    LaunchedEffect(session) {
        loaded = false
        val list = vm.reviewQueue(practiceAll)
        queue = list
        total = list.size
        known = 0
        missed = 0
        flipped = false
        loaded = true
    }

    fun answer(isKnown: Boolean) {
        val current = queue.firstOrNull() ?: return
        vm.reviewed(current, isKnown)
        // Si la fallas, vuelve a salir al final de esta ronda.
        queue = if (isKnown) queue.drop(1) else queue.drop(1) + current
        if (isKnown) known++ else missed++
        flipped = false
    }

    val now = System.currentTimeMillis()
    val dueNow = words.count { !it.learned && it.dueAt <= now }
    val unlearned = words.count { !it.learned }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when {
            !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Preparando tarjetas…")
            }

            total == 0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("No hay tarjetas para repasar", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (unlearned == 0)
                            "Guarda algunas palabras en Estudio y vuelve aquí."
                        else
                            "Ya repasaste todo lo de hoy. Las palabras vuelven a salir cuando les toca.",
                        textAlign = TextAlign.Center
                    )
                    if (unlearned > 0) {
                        Button(onClick = {
                            practiceAll = true
                            session++
                        }) { Text("Practicar de todos modos ($unlearned)") }
                    }
                    OutlinedButton(onClick = onExit) { Text("Volver a Estudio") }
                }
            }

            queue.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("¡Ronda terminada!", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Repasaste $total ${if (total == 1) "palabra" else "palabras"}" +
                            if (missed > 0) " y fallaste $missed ${if (missed == 1) "vez" else "veces"}." else " sin fallar ninguna.",
                        textAlign = TextAlign.Center
                    )
                    if (dueNow > 0) {
                        Button(onClick = {
                            practiceAll = false
                            session++
                        }) { Text("Seguir repasando ($dueNow)") }
                    }
                    OutlinedButton(onClick = onExit) { Text("Volver a Estudio") }
                }
            }

            else -> {
                val w = queue.first()

                LinearProgressIndicator(
                    progress = { if (total == 0) 0f else known.toFloat() / total },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text("Quedan ${queue.size}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))

                Card(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clickable { flipped = !flipped }
                ) {
                    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                        Column(
                            Modifier.verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                w.text,
                                style = MaterialTheme.typography.headlineMedium,
                                textAlign = TextAlign.Center
                            )
                            val tag = listOfNotNull(w.manhwaTitle, w.chapter?.let { "cap. $it" })
                                .joinToString(" · ")
                            if (tag.isNotBlank()) {
                                Text(
                                    tag,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center
                                )
                            }

                            if (!flipped) {
                                Text(
                                    "Piénsala y toca la tarjeta para ver la respuesta",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                HorizontalDivider()
                                Text(
                                    w.translation
                                        ?: if (!w.lookupDone) "Traducción pendiente (se busca cuando haya internet)" else "—",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center
                                )
                                w.definition?.let {
                                    Text("English", style = MaterialTheme.typography.labelMedium)
                                    Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                                }
                                w.definitionEs?.let {
                                    Text("Español", style = MaterialTheme.typography.labelMedium)
                                    Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                                }
                                w.example?.let {
                                    Text(
                                        "Ej: $it",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontStyle = FontStyle.Italic,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                w.note?.let {
                                    Text("Nota: $it", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (!flipped) {
                    Button(onClick = { flipped = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Mostrar respuesta")
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(onClick = { answer(false) }, modifier = Modifier.weight(1f)) {
                            Text("No la sabía")
                        }
                        Button(onClick = { answer(true) }, modifier = Modifier.weight(1f)) {
                            Text("La sabía")
                        }
                    }
                }
            }
        }
    }
}
