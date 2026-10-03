package com.kitsune.core.background

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GenerationNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    val foregroundNotificationId = NOTIFICATION_ID_PROGRESS

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Générations en arrière-plan",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications de progression et de fin des générations IA"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun buildProgressNotification() = NotificationCompat.Builder(context, CHANNEL_ID)
        .setContentTitle("Génération en cours")
        .setContentText("Kitsune génère du contenu en arrière-plan…")
        .setSmallIcon(android.R.drawable.ic_popup_sync)
        .setOngoing(true)
        .setSilent(true)
        .build()

    fun showCompleteNotification(jobId: String, succeeded: Boolean) {
        val title = if (succeeded) "Génération terminée" else "Génération échouée"
        val text = if (succeeded) {
            "Une génération est prête. Ouvrez Kitsune pour la consulter."
        } else {
            "Une génération en arrière-plan a échoué. Réessayez depuis l'application."
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setContentIntent(context.openAppPendingIntent(jobId))
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(jobId.hashCode(), notification)
    }

    private companion object {
        const val CHANNEL_ID = "kitsune_generation_worker"
        const val NOTIFICATION_ID_PROGRESS = 0x4b17
    }
}
