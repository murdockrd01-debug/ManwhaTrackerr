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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.manhwatracker.app.data.Utility
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
        ModeSwitch(mode) { mode = it }
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
                Text("Preparando tarjetas…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            total == 0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("🗂️", fontSize = 56.sp)
                    Text("No hay tarjetas para repasar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
                    Text(if (missed == 0) "🎉" else "💪", fontSize = 64.sp)
                    Text("¡Ronda terminada!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile("Palabras", total.toString(), MaterialTheme.colorScheme.primary)
                        StatTile("Fallos", missed.toString(), if (missed == 0) Color(0xFF4CC38A) else Color(0xFFE5736A))
                    }
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

                ProgressHeader(total = total, known = known, missed = missed, left = queue.size)
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
                        onSwipe = { isKnown -> answer(isKnown) },
                        speaker = speaker,
                        onUnavailable = { vm.showMessage(SPEAKER_UNAVAILABLE) },
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(Modifier.height(12.dp))

                    if (!flipped) {
                        Button(
                            onClick = { flipped = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) { Text("Mostrar respuesta", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                    } else {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { answer(false) },
                                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5736A), contentColor = Color.White)
                            ) { Text("✗  No la sabía", fontWeight = FontWeight.SemiBold) }
                            Button(
                                onClick = { answer(true) },
                                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2FA36B), contentColor = Color.White)
                            ) { Text("✓  La sabía", fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Partes de la pantalla

private val Good = Color(0xFF2FA36B)
private val Bad = Color(0xFFE5736A)
private val Warn = Color(0xFFF0A93B)

/** Selector de modo: dos botones dentro de una cápsula. */
@Composable
private fun ModeSwitch(mode: String, onMode: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(4.dp)) {
            listOf(MODE_CARDS to "🗂️  Tarjetas", MODE_WRITE to "✍️  Escritura").forEach { (id, label) ->
                val sel = mode == id
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { onMode(id) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/** Barra de progreso con contadores: cuántas faltan, cuántas sabías y cuántas fallaste. */
@Composable
private fun ProgressHeader(total: Int, known: Int, missed: Int, left: Int) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else known.toFloat() / total },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Quedan $left", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text("✓ $known", color = Good, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Text("✗ $missed", color = Bad, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, color: Color) {
    Surface(shape = RoundedCornerShape(20.dp), color = color.copy(alpha = 0.14f)) {
        Column(Modifier.padding(horizontal = 28.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Tarjeta con franja de color arriba (utilidad de la palabra y manhwa de origen). */
@Composable
private fun CardShell(w: Word, modifier: Modifier, content: @Composable () -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            val tier = Utility.tier(w.freq, w.text)
            val tag = listOfNotNull(w.manhwaTitle, w.chapter?.let { "cap. $it" }).joinToString(" · ")
            Row(
                Modifier.fillMaxWidth().background(brandBrush()).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(tierColor(tier)))
                Spacer(Modifier.width(8.dp))
                Text(tier.label, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                if (tag.isNotBlank()) {
                    Text(
                        tag,
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 12.dp)
                    )
                }
            }
            Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) { content() }
        }
    }
}

// ---------------------------------------------------------------- Modo tarjetas

@Composable
private fun FlipCard(
    w: Word,
    flipped: Boolean,
    onFlip: () -> Unit,
    onSwipe: (known: Boolean) -> Unit,
    speaker: Speaker,
    onUnavailable: () -> Unit,
    modifier: Modifier
) {
    val rot by animateFloatAsState(if (flipped) 180f else 0f, tween(380), label = "flip")
    val density = LocalDensity.current.density
    val threshold = 120f * density
    var dx by remember(w.id, flipped) { mutableFloatStateOf(0f) }

    Box(
        modifier
            .graphicsLayer {
                translationX = dx
                rotationZ = dx / (30f * density)
            }
            .pointerInput(w.id, flipped) {
                if (flipped) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val d = dx
                            dx = 0f
                            if (d > threshold) onSwipe(true) else if (d < -threshold) onSwipe(false)
                        },
                        onDragCancel = { dx = 0f }
                    ) { _, delta -> dx += delta }
                }
            }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationY = rot
                    cameraDistance = 14f * density
                }
                .clickable { onFlip() }
        ) {
            if (rot <= 90f) {
                CardShell(w, Modifier.fillMaxSize()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            w.text,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        SpeakButton(w.text, speaker, onUnavailable)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Piénsala y toca la tarjeta para ver la respuesta",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) {
                    CardShell(w, Modifier.fillMaxSize()) {
                        Column(
                            Modifier.verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(w.text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                w.translation
                                    ?: if (!w.lookupDone) "Traducción pendiente (se busca cuando haya internet)" else "—",
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                            SpeakButton(w.text, speaker, onUnavailable)
                            WordDetails(w)
                            Text(
                                "Desliza la tarjeta o usa los botones",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
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
        CardShell(w, Modifier.weight(1f)) {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "¿Cómo se dice en inglés?",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Text(
                    w.translation.orEmpty(),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                if (showHint && result == null) {
                    Surface(shape = RoundedCornerShape(14.dp), color = Warn.copy(alpha = 0.16f)) {
                        Text(
                            "Pista: ${Typing.hint(w.text)}",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                val r = result
                if (r != null) {
                    val (tint, msg) = when {
                        gaveUp -> Bad to "Se escribe: ${w.text}"
                        r == Typing.Result.EXACT -> Good to "¡Correcto! ${w.text}"
                        r == Typing.Result.CLOSE -> Warn to "¡Casi! Se escribe: ${w.text}\nCuenta como acierto, pero fíjate en la ortografía."
                        else -> Bad to "Era: ${w.text}\nTú escribiste: ${typed.trim()}"
                    }
                    Surface(shape = RoundedCornerShape(16.dp), color = tint.copy(alpha = 0.16f)) {
                        Text(
                            msg,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    SpeakButton(w.text, speaker, onUnavailable)
                    WordDetails(w)
                }
            }
        }

        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it },
            enabled = result == null,
            label = { Text("Escríbela en inglés") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
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
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("No la sé") }
                Button(
                    onClick = { check() },
                    enabled = typed.isNotBlank(),
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("Comprobar") }
            }
        } else {
            Button(
                onClick = { onDone(r != Typing.Result.WRONG) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(18.dp)
            ) { Text("Continuar", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

// ---------------------------------------------------------------- Partes comunes

@Composable
private fun DetailBlock(label: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun WordDetails(w: Word) {
    w.definition?.let {
        DetailBlock("English") { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
    w.definitionEs?.let {
        DetailBlock("Español") { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
    val examples = w.example?.split("\n")?.filter { it.isNotBlank() }?.take(2).orEmpty()
    if (examples.isNotEmpty()) {
        DetailBlock("Ejemplos") {
            examples.forEach { line ->
                val (en, es) = Utility.splitExample(line)
                Text(en, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
                es?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
    w.note?.let {
        DetailBlock("Nota") { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}
