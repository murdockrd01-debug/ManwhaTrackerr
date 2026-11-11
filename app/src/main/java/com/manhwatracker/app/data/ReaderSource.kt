package com.manhwatracker.app.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * De dónde salen las páginas: carpetas elegidas con el selector de Android (SAF) o archivos .cbz/.zip.
 * Todo aquí es bloqueante: se llama desde un hilo de fondo.
 * Las páginas son [Uri] (carpetas) o [File] (.cbz ya extraído a la caché); Coil carga ambos.
 */
object ReaderSource {

    class Node(val name: String, val docId: String, val isDir: Boolean, val isArchive: Boolean)

    sealed class Opened {
        class Pages(val pages: List<Any>) : Opened()
        class Children(val nodes: List<Node>) : Opened()
        object Empty : Opened()
    }

    /** Contenido de una carpeta del árbol elegido. */
    fun list(ctx: Context, tree: Uri, dirDocId: String): List<Node> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, dirDocId)
        val out = ArrayList<Node>()
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        ctx.contentResolver.query(children, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val isDir = c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                out.add(Node(name, id, isDir, !isDir && ReaderLogic.isArchive(name)))
            }
        }
        return out
    }

    /** Carpetas y archivos .cbz de la lista, en orden natural: son los "capítulos". */
    fun chapterNodes(nodes: List<Node>): List<Node> =
        nodes.filter { it.isDir || it.isArchive }
            .sortedWith(Comparator { a, b -> ReaderLogic.compareNatural(a.name, b.name) })

    /** Las imágenes de una carpeta, en orden de lectura. */
    fun imagePages(tree: Uri, nodes: List<Node>): List<Uri> =
        nodes.filter { !it.isDir && ReaderLogic.isImage(it.name) }
            .sortedWith(Comparator { a, b -> ReaderLogic.compareNatural(a.name, b.name) })
            .map { DocumentsContract.buildDocumentUriUsingTree(tree, it.docId) }

    /** La carpeta de un manhwa: ruta de carpetas (desde la raíz) y su contenido. */
    class Found(val path: List<String>, val kids: List<Node>)

    /**
     * Busca la carpeta de un manhwa dentro de las descargas (la raíz puede ser las descargas, una fuente o el propio manhwa).
     * Mira hasta dos niveles: fuente/manhwa. null si no la encuentra.
     */
    fun findManhwaDir(ctx: Context, tree: Uri, rootId: String, title: String): Found? {
        val top = list(ctx, tree, rootId)
        if (ReaderProgress.folderIsManhwa(title, ReaderLogic.nameFromDocId(rootId))) return Found(listOf(rootId), top)
        val dirs = top.filter { it.isDir }
        dirs.firstOrNull { ReaderProgress.folderIsManhwa(title, it.name) }?.let {
            return Found(listOf(rootId, it.docId), list(ctx, tree, it.docId))
        }
        for (src in dirs) {
            val m = list(ctx, tree, src.docId).firstOrNull { it.isDir && ReaderProgress.folderIsManhwa(title, it.name) } ?: continue
            return Found(listOf(rootId, src.docId, m.docId), list(ctx, tree, m.docId))
        }
        return null
    }

    /** Abre un elemento: un .cbz, una carpeta con imágenes, o una carpeta que a su vez tiene capítulos. */
    fun open(ctx: Context, tree: Uri, n: Node): Opened {
        if (n.isArchive) {
            val uri = DocumentsContract.buildDocumentUriUsingTree(tree, n.docId)
            val files = extract(ctx, uri, uri.toString())
            return if (files.isEmpty()) Opened.Empty else Opened.Pages(files)
        }
        val kids = list(ctx, tree, n.docId)
        val imgs = imagePages(tree, kids)
        return when {
            imgs.isNotEmpty() -> Opened.Pages(imgs)
            kids.isNotEmpty() -> Opened.Children(kids)
            else -> Opened.Empty
        }
    }

    /** Páginas de un .cbz suelto. */
    fun openArchive(ctx: Context, uri: Uri): List<File> = extract(ctx, uri, uri.toString())

    fun displayName(ctx: Context, uri: Uri): String {
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.let { return it }
            }
        } catch (_: Exception) {
        }
        return uri.lastPathSegment ?: ""
    }

    /**
     * Copia el .cbz a la caché y saca sus imágenes en orden (00000.jpg, 00001.jpg…).
     * Si ya se había extraído, reutiliza lo que hay. Solo guarda un capítulo a la vez para no llenar el teléfono.
     */
    private fun extract(ctx: Context, uri: Uri, key: String): List<File> {
        val root = File(ctx.cacheDir, "reader")
        root.mkdirs()
        val id = sha(key)
        val dir = File(root, id)
        val ok = File(dir, ".ok")
        if (!ok.exists()) {
            root.listFiles()?.forEach { it.deleteRecursively() }
            dir.mkdirs()
            val tmp = File(root, "$id.tmp")
            val input = ctx.contentResolver.openInputStream(uri) ?: return emptyList()
            input.use { ins -> tmp.outputStream().use { out -> ins.copyTo(out) } }
            ZipFile(tmp).use { zip ->
                val entries = zip.entries().toList()
                    .filter { !it.isDirectory && ReaderLogic.isImage(it.name) }
                    .sortedWith(Comparator { a, b -> ReaderLogic.compareNatural(a.name, b.name) })
                entries.forEachIndexed { i, e ->
                    val ext = e.name.substringAfterLast('.').lowercase()
                    val f = File(dir, String.format("%05d.%s", i, ext))
                    zip.getInputStream(e).use { ins -> f.outputStream().use { out -> ins.copyTo(out) } }
                }
            }
            tmp.delete()
            ok.writeText("1")
        }
        return dir.listFiles().orEmpty().filter { ReaderLogic.isImage(it.name) }.sortedBy { it.name }
    }

    /** Proporción ancho/alto de una página sin cargarla entera (null si no se pudo medir). */
    fun ratio(ctx: Context, model: Any): Float? {
        val o = BitmapFactory.Options()
        o.inJustDecodeBounds = true
        try {
            when (model) {
                is File -> BitmapFactory.decodeFile(model.path, o)
                is Uri -> ctx.contentResolver.openInputStream(model)?.use { BitmapFactory.decodeStream(it, null, o) }
            }
        } catch (_: Exception) {
            return null
        }
        return ReaderLogic.safeRatio(o.outWidth, o.outHeight)
    }

    private fun sha(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
}
