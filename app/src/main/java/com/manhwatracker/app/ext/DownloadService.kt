package com.manhwatracker.app.ext

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.manhwatracker.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano con notificación: mientras hay capítulos en la cola, Android no cierra la app
 * aunque la minimices o bloquees el teléfono. Se detiene solo al terminar.
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        val n = notification()
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(ID, n)
        if (job?.isActive != true) {
            job = scope.launch {
                while (OfflineDownloads.active.isNotEmpty()) {
                    (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(ID, notification())
                    delay(1000)
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        val act = OfflineDownloads.active
        val total = act.size
        val current = act.values.firstOrNull { it >= 0f }
        val text = if (total == 0) "Terminando…" else "Quedan $total capítulos" + (current?.let { " · el actual va en ${(it * 100).toInt()}%" } ?: "")
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Descargando para leer sin conexión")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, ((current ?: 0f) * 100).toInt(), current == null)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val m = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (m.getNotificationChannel(CHANNEL) == null) {
            m.createNotificationChannel(NotificationChannel(CHANNEL, "Descargas sin conexión", NotificationManager.IMPORTANCE_LOW))
        }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    companion object {
        private const val CHANNEL = "downloads"
        private const val ID = 4107
        fun start(ctx: Context) {
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, DownloadService::class.java)) }
        }
    }
}
