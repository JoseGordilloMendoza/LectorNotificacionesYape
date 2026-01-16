package com.example.lectoryape.service

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.lectoryape.models.YapeNotificationRaw
import com.example.lectoryape.storage.YapeNotificationStorage

class YapeNotificationListenerService : NotificationListenerService() {
    
    private lateinit var storage: YapeNotificationStorage
    
    companion object {
        private const val TAG = "YapeNotificationListener"
        
        // debuggeo xd
        private const val DEBUG_MODE = false
        
        // pakeich de yape xd
        private const val YAPE_PACKAGE = "com.bcp.innovacxion.yapeapp"
        
        // Acción del broadcast para notificar a MainActivity
        const val ACTION_NOTIFICATION_SAVED = "com.example.lectoryape.NOTIFICATION_SAVED"
    }
    
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        
        sbn?.let { notification ->
            if (DEBUG_MODE) {
                // MODO DEBUG: Logea TODAS las notificaciones con información detallada
                logNotificationDetails(notification)
            }
            
            // Filtrar solo notificaciones de YAPE
            if (notification.packageName == YAPE_PACKAGE) {
                processYapeNotification(notification)
            }
        }
    }
    
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        // Por ahora no hacemos nada cuando se remueve una notificación
    }
    
    /**
     * cualquier noti, comentarlo dsps
     */
    private fun logNotificationDetails(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        
        Log.d(TAG, "╔═══════════════════════════════════════════")
        Log.d(TAG, "║ NUEVA NOTIFICACIÓN")
        Log.d(TAG, "╠═══════════════════════════════════════════")
        Log.d(TAG, "║ Package: ${sbn.packageName}")
        Log.d(TAG, "║ ID: ${sbn.id}")
        Log.d(TAG, "║ Timestamp: ${sbn.postTime}")
        Log.d(TAG, "║ ---")
        Log.d(TAG, "║ Title: ${extras.getString("android.title")}")
        Log.d(TAG, "║ Text: ${extras.getString("android.text")}")
        Log.d(TAG, "║ SubText: ${extras.getString("android.subText")}")
        Log.d(TAG, "║ BigText: ${extras.getCharSequence("android.bigText")}")
        Log.d(TAG, "║ InfoText: ${extras.getString("android.infoText")}")
        Log.d(TAG, "║ Summary: ${extras.getString("android.summaryText")}")
        Log.d(TAG, "╚═══════════════════════════════════════════")
    }
    
    /**
     * Procesa notificaciones específicas de Yape
     */
    private fun processYapeNotification(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getString("android.text") ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""
        
        Log.w(TAG, "🟢 ═══ NOTIFICACIÓN DE YAPE DETECTADA ═══")
        Log.w(TAG, "🟢 Timestamp: ${sbn.postTime}")
        Log.w(TAG, "🟢 Título: $title")
        Log.w(TAG, "🟢 Texto: $text")
        Log.w(TAG, "🟢 BigText: $bigText")
        Log.w(TAG, "🟢 ════════════════════════════════════════")
        
        // Crear objeto de notificación cruda
        val notification = YapeNotificationRaw(
            timestamp = sbn.postTime,
            title = title,
            text = text,
            bigText = bigText,
            notificationId = sbn.id
        )
        
        // Guardar en CSV
        val saved = storage.saveNotification(notification)
        if (saved) {
            Log.i(TAG, "💾 Notificación guardada exitosamente. Total: ${storage.getNotificationCount()}")
            
            // Enviar broadcast para notificar a MainActivity
            sendBroadcast(Intent(ACTION_NOTIFICATION_SAVED))
        } else {
            Log.e(TAG, "❌ Error al guardar notificación")
        }
        
        // TODO: Aquí aplicaremos el regex para extraer datos estructurados
    }
    
    override fun onListenerConnected() {
        super.onListenerConnected()
        storage = YapeNotificationStorage(applicationContext)
        Log.d(TAG, "Servicio de notificaciones CONECTADO")
        Log.d(TAG, "📂 Archivo CSV: ${storage.getFilePath()}")
    }
    
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Servicio de notificaciones DESCONECTADO")
    }
}
