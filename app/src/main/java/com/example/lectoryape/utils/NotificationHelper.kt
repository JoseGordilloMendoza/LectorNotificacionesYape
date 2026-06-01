package com.example.kajaapp.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.kajaapp.MainActivity
import com.example.kajaapp.R

/**
 * Helper class para manejar notificaciones del foreground service
 */
class NotificationHelper(private val context: Context) {
    
    companion object {
        const val CHANNEL_ID = "yape_listener_channel"
        const val NOTIFICATION_ID = 1001
        private const val CHANNEL_NAME = "KAJA POS"
        private const val CHANNEL_DESCRIPTION = "Servicio activo escuchando pagos de Yape y Plin"
    }
    
    private val notificationManager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    
    /**
     * Crea el canal de notificación (necesario para Android 8.0+)
     */
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT // DEFAULT para que sea visible en Xiaomi/Honor
            ).apply {
                description = CHANNEL_DESCRIPTION
                setShowBadge(false) // No mostrar badge en el ícono de la app
                enableLights(false)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Construye la notificación persistente del foreground service
     * Nota: El contador está en la UI de la app, aquí solo mostramos que está escuchando
     */
    fun buildServiceNotification(): Notification {
        // Intent para abrir la app cuando el usuario toque la notificación
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("KAJA activo")
            .setContentText("Escuchando pagos de Yape y Plin")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // No se puede descartar manualmente
            .setPriority(NotificationCompat.PRIORITY_DEFAULT) // DEFAULT para visibilidad en OEMs
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setAutoCancel(false)
            .build()
    }
}
