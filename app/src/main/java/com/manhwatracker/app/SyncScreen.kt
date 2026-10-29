package com.manhwatracker.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

private fun formatTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))

/**
 * Sincronización: la app lee sola la copia de Komikku y guarda sola una copia de tus datos.
 * Solo hace falta elegir las carpetas una vez.
 */
@Composable
fun SyncScreen(vm: MainViewModel) {
    val info by vm.sync.collectAsState()

    // Por si se cambió algo mientras la pantalla estaba cerrada (por ejemplo, una copia hecha al salir de la app).
    LaunchedEffect(Unit) { vm.refreshSync() }

    val komikkuPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.addKomikkuFolder(uri)
    }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setBackupFolder(uri)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---------------- Komikku
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Importar de Komikku automáticamente", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Elige la carpeta donde Komikku guarda sus copias de seguridad (por ejemplo Documentos/Manwhas). " +
                        "Sirven las automáticas y las manuales: si agregas varias carpetas, se usa siempre la copia más reciente de todas. " +
                        "Cada vez que abras la app o vuelvas a ella, la leerá y actualizará tus manhwas sola.",
                    style = MaterialTheme.typography.bodySmall
                )

                val folders = info.komikkuFolders
                if (folders.isEmpty()) {
                    Text("Todavía no elegiste carpeta.", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { komikkuPicker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Elegir carpeta de Komikku")
                    }
                } else {
                    folders.forEach { f ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                f.label,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { vm.removeKomikkuFolder(f.uri) }) { Text("Quitar") }
                        }
                    }
                    Text(
                        if (info.komikkuLastName != null)
                            "Última copia importada: ${info.komikkuLastName} (${formatTime(info.komikkuLastAt)})"
                        else
                            "Todavía no se importó ninguna copia.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(onClick = { vm.autoSync(manual = true) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Actualizar ahora")
                    }
                    OutlinedButton(onClick = { komikkuPicker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Agregar otra carpeta")
                    }
                }
            }
        }

        // ---------------- Copia propia
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Copia automática de mis datos", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Guarda una copia de tus manhwas, palabras, notas y estadísticas en la carpeta que elijas. " +
                        "Se hace sola cada vez que sales de la app (con un minuto de margen), " +
                        "se guarda un archivo por día y se conservan los 7 más recientes. Elige una carpeta que sincronice Google Drive para tenerla también en la nube.",
                    style = MaterialTheme.typography.bodySmall
                )

                val folder = info.backupFolder
                if (folder == null) {
                    Text("Todavía no elegiste carpeta.", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { backupPicker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Elegir carpeta para mis copias")
                    }
                } else {
                    Text("Carpeta: $folder", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (info.backupLastName != null)
                            "Última copia: ${info.backupLastName} (${formatTime(info.backupLastAt)})"
                        else
                            "Todavía no se hizo ninguna copia.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    info.backupError?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Button(onClick = { vm.backupNow() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Hacer una copia ahora")
                    }
                    OutlinedButton(onClick = { backupPicker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Cambiar carpeta")
                    }
                    TextButton(onClick = { vm.clearBackupFolder() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Dejar de hacer copias automáticas")
                    }
                }
                Text(
                    "Para recuperar una copia: menú ⋮ → Restaurar mi copia y elige el archivo .json.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // ---------------- AniList
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                var user by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
                var busy by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                Text("Importar desde AniList", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Escribe tu usuario de AniList (tu lista debe ser pública). Se agregan los que no tienes; " +
                        "los que ya tienes solo se completan, sin bajar tu capítulo ni cambiar tu estado o nota. " +
                        "Si usas MyAnimeList, AniList puede importar tu lista desde su web y luego la traes aquí.",
                    style = MaterialTheme.typography.bodySmall
                )
                androidx.compose.material3.OutlinedTextField(
                    value = user, onValueChange = { user = it }, singleLine = true,
                    label = { Text("Usuario de AniList") }, modifier = Modifier.fillMaxWidth()
                )
                Button(enabled = !busy && user.isNotBlank(), onClick = {
                    busy = true
                    vm.importAniList(user) { busy = false }
                }) { Text(if (busy) "Importando…" else "Importar") }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}
