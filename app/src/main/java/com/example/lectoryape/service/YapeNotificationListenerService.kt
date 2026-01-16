package com.example.lectoryape.service

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.lectoryape.models.YapeNotificationRaw
import com.example.lectoryape.storage.YapeNotificationStorage
import com.example.lectoryape.utils.YapeParser

class YapeNotificationListenerService : NotificationListenerService() {
    // se usa para seguridad con posibles problemas de arranque
    private val storage by lazy { YapeNotificationStorage(applicationContext) }
    
    companion object {
        private const val TAG = "YapeNotificationListener"
        // debuggeo xd
        private const val DEBUG_MODE = false
        
        // pakeich de yape xd
        private const val YAPE_PACKAGE = "com.bcp.innovabcp.yapeapp"
        
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
        // usamos el parser
        val yapePayment = YapeParser.parse(sbn)

        if (yapePayment == null) {
            Log.w(TAG, "Notificación de Yape recibida pero no es un pago válido o formato desconocido")
            return
        }

        // log estructurado del pago ya procesado
        logYapePayment(yapePayment)

        // persistencia
        val saved = storage.saveNotification(yapePayment)

        if (saved) {
            Log.i(TAG, "Pago de ${yapePayment.name} guardado. Total: ${storage.getNotificationCount()}")
            sendBroadcast(Intent(ACTION_NOTIFICATION_SAVED))
        } else {
            Log.e(TAG, "Error al guardar en CSV")
        }
    }

    private fun logYapePayment(payment: YapeNotificationRaw) {
        Log.d(TAG, """
        🟢 ═══ PAGO RECIBIDO ═══
        👤 Cliente: ${payment.name}
        💰 Monto:   S/ ${"%.2f".format(payment.amount)}
        🔑 Código:  ${payment.securityCode}
        ⏰ Time:    ${payment.timestamp}
        ════════════════════════
    """.trimIndent())
    }

    // para debugging
    private fun logRawDetails(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        Log.v(TAG, "Raw Text: ${extras.getString("android.text")}")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Servicio de notificaciones CONECTADO")
        Log.d(TAG, "📂 Archivo CSV: ${storage.getFilePath()}")
    }
    
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Servicio de notificaciones DESCONECTADO")
    }
}
