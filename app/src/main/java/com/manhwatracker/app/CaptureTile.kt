package com.manhwatracker.app

import android.content.Intent
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Mosaico del panel rápido: "Guardar texto". Toma la captura y abre el selector de texto. */
class CaptureTile : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "Guardar texto"
            updateTile()
        }
    }

    override fun onClick() {
        val service = CaptureService.instance
        if (service != null) {
            service.start()
        } else {
            // Todavía no está activado como servicio de accesibilidad: abre esos ajustes.
            @Suppress("DEPRECATION")
            startActivityAndCollapse(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
