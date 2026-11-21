package com.manhwatracker.app.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

object ReaderSource {
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
}
