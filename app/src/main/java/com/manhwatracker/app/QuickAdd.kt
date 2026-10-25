package com.manhwatracker.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.manhwatracker.app.data.Prefs
import com.manhwatracker.app.data.WordRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Notificación fija con un campo de texto para guardar palabras sin salir de la app de lectura. */
object QuickAdd {
    private const val CHANNEL = "quick_add"
    private const val NOTIFICATION_ID = 1
    const val KEY_TEXT = "word_text"

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun show(context: Context, status: String? = null) {
        if (!canNotify(context)) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Guardar palabras", NotificationManager.IMPORTANCE_LOW)
            )
        }

        val mutable = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val replyIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, QuickAddReceiver::class.java).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or mutable
        )
        val openIntent = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pickIntent = PendingIntent.getActivity(
            context,
            2,
            Intent(context, PickManhwaActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val remoteInput = RemoteInput.Builder(KEY_TEXT).setLabel("Palabra o frase").build()
        val action = NotificationCompat.Action.Builder(R.drawable.ic_notif, "Guardar palabra", replyIntent)
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(false)
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Estudio de inglés")
            .setContentText(status ?: ("En: " + (Prefs.lastManhwa(context) ?: "ningún manhwa") + " — toca “Guardar palabra”"))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(action)
            .addAction(R.drawable.ic_notif, "Cambiar manhwa", pickIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Sin permiso de notificaciones: no hacemos nada.
        }
    }

    fun hide(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}

class QuickAddReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val text = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(QuickAdd.KEY_TEXT)?.toString()?.trim().orEmpty()

        if (text.isEmpty()) {
            QuickAdd.show(app)
            return
        }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Si no hay internet o tarda, la palabra queda guardada y se completa después.
                val result = withTimeoutOrNull(8_000) {
                    WordRepo.save(app, text, Prefs.lastManhwa(app), Prefs.lastChapter(app), null)
                }
                val where = Prefs.lastManhwa(app)?.let { " en $it" }.orEmpty()
                val status = if (result != null && !result.added) {
                    "Ya la tenías: $text"
                } else {
                    "Guardada: $text$where"
                }
                QuickAdd.show(app, status)
            } finally {
                pending.finish()
            }
        }
    }
}
