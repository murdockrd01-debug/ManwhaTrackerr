package com.manhwatracker.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
        if (uri != null) vm.setKomikkuFolder(uri)
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
                    "En Komikku activa la copia de seguridad automática y elige una carpeta (por ejemplo Documentos/Manwhas). " +
                        "Aquí elige esa misma carpeta una vez. Cada vez que abras la app, leerá la copia más reciente " +
                        "y actualizará tus manhwas sola. No hace falta exportar nada a mano.",
                    style = MaterialTheme.typography.bodySmall
                )

                val folder = info.komikkuFolder
                if (folder == null) {
                    Text("Todavía no elegiste carpeta.", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { komikkuPicker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Elegir carpeta de Komikku")
                    }
                } else {
                    Text("Carpeta: $folder", style = MaterialTheme.typography.bodyMedium)
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
                        Text("Cambiar carpeta")
                    }
                    TextButton(onClick = { vm.clearKomikkuFolder() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Dejar de usar esta carpeta")
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
                        "Se hace sola cuando sales de la app (como máximo una vez por hora), una por día, " +
                        "y se conservan las 7 más recientes. Elige una carpeta que sincronice Google Drive para tenerla también en la nube.",
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

        Spacer(Modifier.height(16.dp))
    }
}
