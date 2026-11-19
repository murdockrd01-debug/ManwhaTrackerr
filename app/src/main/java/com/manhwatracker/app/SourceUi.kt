package com.manhwatracker.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.TwoStatePreference
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter

// ---------------- Filtros de búsqueda de una fuente ----------------

/** Los filtros que la fuente ofrece (géneros, estado, orden...). Cada cambio se guarda en el propio filtro y sube [version]. */
@Composable
internal fun FilterEditor(filters: List<Filter<*>>, version: Int, bump: () -> Unit) {
    filters.forEach { FilterRow(it, version, bump) }
}

@Composable
private fun FilterRow(f: Filter<*>, version: Int, bump: () -> Unit) {
    when (f) {
        is Filter.Header -> Text(f.name, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        is Filter.Separator -> HorizontalDivider(Modifier.padding(vertical = 6.dp))
        is Filter.Select<*> -> Column {
            Text(f.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                f.values.forEachIndexed { i, v ->
                    FilterChip(selected = f.state == i, onClick = { f.state = i; bump() }, label = { Text(v.toString()) })
                }
            }
        }
        is Filter.Text -> OutlinedTextField(
            value = f.state, onValueChange = { f.state = it; bump() }, singleLine = true,
            label = { Text(f.name) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        is Filter.CheckBox -> Row(Modifier.fillMaxWidth().clickable { f.state = !f.state; bump() }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = f.state, onCheckedChange = null)
            Text(f.name, modifier = Modifier.padding(start = 10.dp, top = 8.dp, bottom = 8.dp))
        }
        is Filter.TriState -> Row(Modifier.fillMaxWidth().clickable { f.state = (f.state + 1) % 3; bump() }, verticalAlignment = Alignment.CenterVertically) {
            Text(when (f.state) { Filter.TriState.STATE_INCLUDE -> "✓"; Filter.TriState.STATE_EXCLUDE -> "✗"; else -> "☐" }, modifier = Modifier.padding(horizontal = 6.dp))
            Text(f.name, modifier = Modifier.padding(start = 6.dp, top = 8.dp, bottom = 8.dp))
        }
        is Filter.Group<*> -> {
            var open by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 8.dp)) {
                Text((if (open) "▼ " else "▶ ") + f.name, fontWeight = FontWeight.SemiBold)
            }
            if (open) Column(Modifier.padding(start = 12.dp)) {
                (f.state as List<*>).filterIsInstance<Filter<*>>().forEach { FilterRow(it, version, bump) }
            }
        }
        is Filter.Sort -> Column {
            Text(f.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                f.values.forEachIndexed { i, v ->
                    val sel = f.state
                    val on = sel?.index == i
                    FilterChip(
                        selected = on,
                        onClick = { f.state = if (on) Filter.Sort.Selection(i, !sel!!.ascending) else Filter.Sort.Selection(i, false); bump() },
                        label = { Text(v + if (on) (if (sel!!.ascending) " ↑" else " ↓") else "") }
                    )
                }
            }
        }
        else -> {}
    }
}

// ---------------- Ajustes de una fuente (ConfigurableSource) ----------------

@Composable
internal fun SourceSettingsDialog(src: ConfigurableSource, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val built = remember(src) {
        runCatching {
            val pm = androidx.preference.PreferenceManager(ctx)
            pm.sharedPreferencesName = "source_${(src as eu.kanade.tachiyomi.source.Source).id}"
            pm.createPreferenceScreen(ctx).also { src.setupPreferenceScreen(it) }
        }
    }
    var tick by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf<Preference?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Ajustes de la fuente") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val screen = built.getOrNull()
                if (screen == null) Text("Esta fuente no pudo mostrar sus ajustes: " + (built.exceptionOrNull()?.message ?: ""), color = MaterialTheme.colorScheme.error)
                else if (screen.preferenceCount == 0) Text("Esta fuente no tiene ajustes.")
                else PrefItems(screen, tick, { tick++ }, { editing = it })
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Listo") } }
    )
    editing?.let { p -> PrefEditDialog(p, onDone = { editing = null; tick++ }) }
}

@Composable
private fun PrefItems(group: PreferenceGroup, tick: Int, bump: () -> Unit, edit: (Preference) -> Unit) {
    for (i in 0 until group.preferenceCount) {
        val p = group.getPreference(i)
        if (!p.isVisible) continue
        val title = p.title?.toString().orEmpty()
        when (p) {
            is PreferenceCategory -> {
                Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp))
                PrefItems(p, tick, bump, edit)
            }
            is TwoStatePreference -> Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title)
                    p.summary?.toString()?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Switch(checked = p.isChecked, enabled = p.isEnabled, onCheckedChange = { if (p.callChangeListener(it)) p.isChecked = it; bump() })
            }
            is ListPreference, is EditTextPreference, is MultiSelectListPreference -> Column(
                Modifier.fillMaxWidth().clickable(enabled = p.isEnabled) { edit(p) }.padding(vertical = 8.dp)
            ) {
                Text(title)
                val summary = when (p) {
                    is ListPreference -> p.entry?.toString() ?: p.summary?.toString()
                    is EditTextPreference -> p.text ?: p.summary?.toString()
                    is MultiSelectListPreference -> p.values.size.toString() + " seleccionados"
                    else -> null
                }
                summary?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            else -> if (title.isNotBlank()) Text(title, modifier = Modifier.padding(vertical = 6.dp))
        }
    }
}

@Composable
private fun PrefEditDialog(p: Preference, onDone: () -> Unit) {
    val title = p.title?.toString().orEmpty()
    when (p) {
        is ListPreference -> AlertDialog(
            onDismissRequest = onDone, title = { Text(title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    val entries = p.entries ?: emptyArray()
                    val values = p.entryValues ?: emptyArray()
                    entries.forEachIndexed { i, e ->
                        val v = values.getOrNull(i)?.toString() ?: return@forEachIndexed
                        Row(Modifier.fillMaxWidth().clickable { if (p.callChangeListener(v)) p.value = v; onDone() }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = p.value == v, onClick = null)
                            Text(e.toString(), modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 10.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDone) { Text("Cerrar") } }
        )
        is EditTextPreference -> {
            var text by remember { mutableStateOf(p.text.orEmpty()) }
            AlertDialog(
                onDismissRequest = onDone, title = { Text(title) },
                text = { OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth()) },
                confirmButton = { TextButton(onClick = { if (p.callChangeListener(text)) p.text = text; onDone() }) { Text("Guardar") } },
                dismissButton = { TextButton(onClick = onDone) { Text("Cancelar") } }
            )
        }
        is MultiSelectListPreference -> {
            var chosen by remember { mutableStateOf(p.values.toSet()) }
            AlertDialog(
                onDismissRequest = onDone, title = { Text(title) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        val entries = p.entries ?: emptyArray()
                        val values = p.entryValues ?: emptyArray()
                        entries.forEachIndexed { i, e ->
                            val v = values.getOrNull(i)?.toString() ?: return@forEachIndexed
                            Row(Modifier.fillMaxWidth().clickable { chosen = if (v in chosen) chosen - v else chosen + v }, verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = v in chosen, onCheckedChange = null)
                                Text(e.toString(), modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 10.dp))
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { if (p.callChangeListener(chosen)) p.values = chosen; onDone() }) { Text("Guardar") } },
                dismissButton = { TextButton(onClick = onDone) { Text("Cancelar") } }
            )
        }
        else -> onDone()
    }
}
