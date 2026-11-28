package com.manhwatracker.app

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient

/** Imprime (o guarda como PDF) una página HTML con el sistema de impresión de Android. */
object StudyPrint {
    // Se guarda para que el WebView no se libere antes de terminar de preparar la impresión.
    private var keep: WebView? = null

    fun print(ctx: Context, html: String) {
        val wv = WebView(ctx)
        keep = wv
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val pm = view.context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                pm.print("Lectoon - Estudio", view.createPrintDocumentAdapter("Lectoon-estudio"), PrintAttributes.Builder().build())
            }
        }
        wv.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }
}
