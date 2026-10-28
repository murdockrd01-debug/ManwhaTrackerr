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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.manhwatracker.app.data.Typing
import com.manhwatracker.app.data.Word
import com.manhwatracker.app.data.titleKeyOf

private const val MODE_CARDS = "CARDS"
private const val MODE_WRITE = "WRITE"

/**
 * Repaso de palabras, con dos modos:
 *  - Tarjetas: ves la palabra en inglés, la piensas, tocas para ver la respuesta y dices si la sabías.
 *  - Escritura: ves la traducción en español y escribes la palabra en inglés.
 *
 * Las que fallas vuelven a salir al final de la ronda.
 *
 * @param titleKey si no es null, solo se repasan las palabras de ese manhwa.
 * @param filterLabel nombre del manhwa para mostrarlo (solo texto).
 */
@Composable
fun ReviewScreen(vm: MainViewModel, titleKey: String?, filterLabel: String?, onExit: () -> Unit) {
    val words by vm.words.collectAsState()
    val speaker = rememberSpeaker()

    var mode by rememberSaveable { mutableStateOf(MODE_CARDS) }

    // Cada vez que cambia [session] o [mode] se arma una ronda nueva.
    var session by remember { mutableIntStateOf(0) }
    var practiceAll by remember { mutableStateOf(false) }

    var loaded by remember { mutableStateOf(false) }
    var queue by remember { mutableStateOf<List<Word>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var known by remember { mutableIntStateOf(0) }
    var missed by remember { mutableIntStateOf(0) }
    var flipped by remember { mutableStateOf(false) }

    // Sube con cada respuesta; hace que lo escrito y la pista se reinicien aunque salga la misma palabra.
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(session, mode) {
        loaded = false
        val list = vm.reviewQueue(practiceAll, titleKey, mode == MODE_WRITE)
        queue = list
        total = list.size
        known = 0
        missed = 0
        flipped = false
        attempt++
        loaded = true
    }

    fun answer(isKnown: Boolean) {
        val current = queue.firstOrNull() ?: return
        vm.reviewed(current, isKnown)
        // Si la fallas, vuelve a salir al final de esta ronda.
        queue = if (isKnown) queue.drop(1) else queue.drop(1) + current
        if (isKnown) known++ else missed++
        flipped = false
        attempt++
    }

    val now = System.currentTimeMillis()
    val mine = words.filter { w ->
        !w.learned && (titleKey == null || w.manhwaTitle?.let { titleKeyOf(it) } == titleKey)
    }
    val writable = mine.filter { !it.translation.isNullOrBlank() }
    val relevant = if (mode == MODE_WRITE) writable else mine
    val dueNow = relevant.count { it.dueAt <= now }
    val unlearned = relevant.size

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = mode == MODE_CARDS,
                onClick = { mode = MODE_CARDS },
                label = { Text("Tarjetas") }
            )
            FilterChip(
                selected = mode == MODE_WRITE,
                onClick = { mode = MODE_WRITE },
                label = { Text("Escritura") }
            )
        }
        if (filterLabel != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Solo palabras de: $filterLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(8.dp))

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
                        when {
                            mode == MODE_WRITE && mine.isNotEmpty() && writable.isEmpty() ->
                                "El modo escritura necesita palabras con traducción. Las tuyas aún no la tienen (se busca cuando hay internet)."
                            unlearned == 0 && titleKey != null ->
                                "No tienes palabras sin aprender de este manhwa."
                            unlearned == 0 ->
                                "Guarda algunas palabras en Estudio y vuelve aquí."
                            else ->
                                "Ya repasaste todo lo de hoy. Las palabras vuelven a salir cuando les toca."
                        },
                        textAlign = TextAlign.Center
                    )
                    if (unlearned > 0) {
                        Button(onClick = {
                            practiceAll = true
                            session++
                        }) { Text("Practicar de todos modos ($unlearned)") }
                    }
                    OutlinedButton(onClick = onExit) { Text("Volver") }
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
                    OutlinedButton(onClick = onExit) { Text("Volver") }
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

                if (mode == MODE_WRITE) {
                    WriteCard(
                        w = w,
                        attempt = attempt,
                        speaker = speaker,
                        onUnavailable = { vm.showMessage(SPEAKER_UNAVAILABLE) },
                        onDone = { correct -> answer(correct) },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    FlipCard(
                        w = w,
                        flipped = flipped,
                        onFlip = { flipped = !flipped },
                        speaker = speaker,
                        onUnavailable = { vm.showMessage(SPEAKER_UNAVAILABLE) },
                        modifier = Modifier.weight(1f)
                    )

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
}

// ---------------------------------------------------------------- Modo tarjetas

@Composable
private fun FlipCard(
    w: Word,
    flipped: Boolean,
    onFlip: () -> Unit,
    speaker: Speaker,
    onUnavailable: () -> Unit,
    modifier: Modifier
) {
    Card(modifier.fillMaxWidth().clickable { onFlip() }) {
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
                SpeakButton(w.text, speaker, onUnavailable)
                WordTag(w)

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
                    WordDetails(w)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Modo escritura

@Composable
private fun WriteCard(
    w: Word,
    attempt: Int,
    speaker: Speaker,
    onUnavailable: () -> Unit,
    onDone: (correct: Boolean) -> Unit,
    modifier: Modifier
) {
    // Se reinician con cada palabra nueva (y con cada intento, por si sale la misma otra vez).
    var typed by remember(w.id, attempt) { mutableStateOf("") }
    var result by remember(w.id, attempt) { mutableStateOf<Typing.Result?>(null) }
    var gaveUp by remember(w.id, attempt) { mutableStateOf(false) }
    var showHint by remember(w.id, attempt) { mutableStateOf(false) }

    fun check() {
        if (result != null || typed.isBlank()) return
        result = Typing.check(typed, w.text)
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth().weight(1f)) {
            Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "¿Cómo se dice en inglés?",
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        w.translation.orEmpty(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    WordTag(w)

                    if (showHint && result == null) {
                        Text(
                            "Pista: ${Typing.hint(w.text)}",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                    }

                    val r = result
                    if (r != null) {
                        HorizontalDivider()
                        when {
                            gaveUp -> Text(
                                "Se escribe: ${w.text}",
                                style = MaterialTheme.typography.titleLarge,
                                textAlign = TextAlign.Center
                            )

                            r == Typing.Result.EXACT -> Text(
                                "¡Correcto! ${w.text}",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )

                            r == Typing.Result.CLOSE -> Text(
                                "¡Casi! Se escribe: ${w.text}\nCuenta como acierto, pero fíjate en la ortografía.",
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )

                            else -> Text(
                                "Era: ${w.text}\nTú escribiste: ${typed.trim()}",
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                        SpeakButton(w.text, speaker, onUnavailable)
                        WordDetails(w)
                    }
                }
            }
        }

        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it },
            enabled = result == null,
            label = { Text("Escríbela en inglés") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { check() }),
            modifier = Modifier.fillMaxWidth()
        )

        val r = result
        if (r == null) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { showHint = true }, enabled = !showHint) { Text("Pista") }
                OutlinedButton(
                    onClick = {
                        gaveUp = true
                        result = Typing.Result.WRONG
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("No la sé") }
                Button(
                    onClick = { check() },
                    enabled = typed.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text("Comprobar") }
            }
        } else {
            Button(
                onClick = { onDone(r != Typing.Result.WRONG) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Continuar") }
        }
    }
}

// ---------------------------------------------------------------- Partes comunes

@Composable
private fun WordTag(w: Word) {
    val tag = listOfNotNull(w.manhwaTitle, w.chapter?.let { "cap. $it" }).joinToString(" · ")
    if (tag.isNotBlank()) {
        Text(
            tag,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun WordDetails(w: Word) {
    w.definition?.let {
        Text("English", style = MaterialTheme.typography.labelMedium)
        Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
    w.definitionEs?.let {
        Text("Español", style = MaterialTheme.typography.labelMedium)
        Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
    w.example?.split("\n")?.filter { it.isNotBlank() }?.take(2)?.forEach { line ->
        val (en, es) = com.manhwatracker.app.data.Utility.splitExample(line)
        Text(
            "Ej: $en",
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center
        )
        es?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
    w.note?.let {
        Text("Nota: $it", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}
