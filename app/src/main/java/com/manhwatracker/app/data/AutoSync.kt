package com.manhwatracker.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Sincronización con carpetas elegidas por el usuario (sin permisos especiales, con el selector de carpetas de Android):
 *  - Komikku: lee la copia de seguridad más reciente de la carpeta donde Komikku guarda las suyas.
 *  - Copia propia: guarda una copia de los datos de esta app en otra carpeta, una por día.
 */
object AutoSync {

    /** Un archivo de la carpeta, con la dirección para abrirlo. */
    class Entry(val uri: Uri, val info: SyncLogic.FileInfo)

    sealed class KomikkuCheck {
        /** La carpeta no tiene ninguna copia de Komikku. */
        object NoFiles : KomikkuCheck()

        /** La copia más reciente ya se importó. */
        object UpToDate : KomikkuCheck()

        /** Hay una copia nueva por importar. [key] se guarda al terminar para no repetirla. */
        class Pending(val uri: Uri, val name: String, val key: String) : KomikkuCheck()
    }

    /** Como mínimo pasa una hora entre dos copias automáticas. */
    private const val BACKUP_MIN_INTERVAL = 60 * 60 * 1000L

    /** Cuántas copias automáticas se guardan (una por día). Las más viejas se borran. */
    private const val BACKUP_KEEP = 7

    private val backupMutex = Mutex()

    // ---------------------------------------------------------------- Carpetas

    /** Android olvida el permiso de una carpeta al cerrar la app, salvo que se pida conservarlo. */
    fun takePermission(context: Context, tree: Uri, write: Boolean) {
        var flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (write) flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(tree, flags)
        } catch (e: SecurityException) {
            // Si no se puede conservar, la lectura falla después y se avisa en la pantalla de sincronización.
        }
    }

    /** Nombre legible de la carpeta, ej. "Almacenamiento interno/Documents/Manwhas". */
    fun folderLabel(tree: Uri): String {
        val id = try {
            DocumentsContract.getTreeDocumentId(tree)
        } catch (e: Exception) {
            return tree.toString()
        }
        val volume = id.substringBefore(':')
        val path = id.substringAfter(':', "")
        val base = if (volume == "primary") "Almacenamiento interno" else volume
        return if (path.isEmpty()) base else "$base/$path"
    }

    /** Archivos (no carpetas) que hay dentro de la carpeta elegida. */
    fun list(context: Context, tree: Uri): List<Entry> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            tree, DocumentsContract.getTreeDocumentId(tree)
        )
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val out = mutableListOf<Entry>()
        context.contentResolver.query(children, columns, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(4) == DocumentsContract.Document.MIME_TYPE_DIR) continue
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                out += Entry(
                    DocumentsContract.buildDocumentUriUsingTree(tree, id),
                    SyncLogic.FileInfo(name, c.getLong(2), c.getLong(3))
                )
            }
        }
        return out
    }

    // ---------------------------------------------------------------- Komikku

    /** Mira la carpeta de Komikku y dice si hay una copia nueva. Puede lanzar excepción si la carpeta ya no se puede leer. */
    fun checkKomikku(context: Context): KomikkuCheck {
        val tree = Prefs.komikkuFolder(context)?.let { Uri.parse(it) } ?: return KomikkuCheck.NoFiles
        val entries = list(context, tree)
        val newest = SyncLogic.newestKomikku(entries.map { it.info }) ?: return KomikkuCheck.NoFiles
        val key = SyncLogic.fingerprint(newest)
        if (key == Prefs.lastKomikkuKey(context)) return KomikkuCheck.UpToDate
        val entry = entries.first { it.info == newest }
        return KomikkuCheck.Pending(entry.uri, newest.name, key)
    }

    // ---------------------------------------------------------------- Copia propia

    private fun createFile(context: Context, tree: Uri, name: String): Uri? {
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        return DocumentsContract.createDocument(context.contentResolver, parent, "application/json", name)
    }

    /**
     * Guarda la copia de hoy en la carpeta elegida, si ya toca (o si [force]).
     * Devuelve el nombre del archivo, o null si no se hizo nada. Puede lanzar excepción si la carpeta falla.
     */
    suspend fun backupIfDue(context: Context, force: Boolean = false): String? =
        backupMutex.withLock {
            withContext(Dispatchers.IO) {
                val tree = Prefs.backupFolder(context)?.let { Uri.parse(it) } ?: return@withContext null
                val now = System.currentTimeMillis()
                if (!force && !SyncLogic.isDue(Prefs.lastBackupAt(context), now, BACKUP_MIN_INTERVAL)) {
                    return@withContext null
                }

                val name = SyncLogic.autoName(LocalDate.now())
                val existing = list(context, tree).firstOrNull { it.info.name == name }
                val target = existing?.uri
                    ?: createFile(context, tree, name)
                    ?: throw BackupFormatException("No se pudo crear el archivo en esa carpeta")
                OwnBackup.write(context, target)

                // Borra solo copias automáticas viejas (nunca otros archivos de la carpeta).
                try {
                    val now2 = list(context, tree)
                    val old = SyncLogic.toDelete(now2.map { it.info }, BACKUP_KEEP).toSet()
                    for (e in now2) {
                        if (e.info in old) DocumentsContract.deleteDocument(context.contentResolver, e.uri)
                    }
                } catch (e: Exception) {
                    // La copia de hoy ya quedó guardada; limpiar es opcional.
                }

                Prefs.setLastBackup(context, now, name)
                Prefs.setBackupError(context, null)
                name
            }
        }

    /** Se llama al salir de la app: hace la copia en segundo plano si ya toca. No molesta si falla. */
    fun backupInBackground(context: Context) {
        val app = context.applicationContext
        if (Prefs.backupFolder(app) == null) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                backupIfDue(app)
            } catch (e: Exception) {
                Prefs.setBackupError(app, "No pude guardar la copia en la carpeta. Elígela otra vez.")
            }
        }
    }
}
