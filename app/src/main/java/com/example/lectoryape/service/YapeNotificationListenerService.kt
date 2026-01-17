package com.example.lectoryape.service

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.lectoryape.firebase.FirebaseUploader
import com.example.lectoryape.models.YapeNotificationRaw
import com.example.lectoryape.storage.YapeNotificationStorage
import com.example.lectoryape.utils.YapeParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class YapeNotificationListenerService : NotificationListenerService() {
    // se usa para seguridad con posibles problemas de arranque
    private val storage by lazy { YapeNotificationStorage(applicationContext) }
    private lateinit var firebaseUploader: FirebaseUploader

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
        val title = extras.getString("android.title") ?: "Sin título"
        val text = extras.getString("android.text") ?: "Sin texto"
        // bigText suele ser CharSequence, lo forzamos a String
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: "No hay informacion detallada"

        Log.d(TAG, """
        ══ DEBUG NOTIFICACIÓN ══
        App: ${sbn.packageName}
        ID: ${sbn.id}
        Title: $title
        Text: $text
        BigText: $bigText
        ══════════════════════════
    """.trimIndent())
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

        // Subir a Firebase (nube)
        CoroutineScope(Dispatchers.IO).launch {
            val uploaded = firebaseUploader.uploadNotification(yapePayment)
            if (uploaded) {
                Log.i(TAG, "Notificación subida a Firebase exitosamente")
            } else {
                Log.w(TAG, "No se pudo subir a Firebase (se quedó en CSV local)")
            }
        }

        // Enviar broadcast para notificar a MainActivity
        sendBroadcast(Intent(ACTION_NOTIFICATION_SAVED))

        // TODO: Aquí aplicaremos el regex para extraer datos estructurados
    }

    private fun logYapePayment(payment: YapeNotificationRaw) {
        Log.d(TAG, """
        ═══ PAGO RECIBIDO ═══
        Cliente: ${payment.name}
        Monto:   S/ ${"%.2f".format(payment.amount)}
        Código:  ${payment.securityCode}
        Time:    ${payment.timestamp}
        ════════════════════════
    """.trimIndent())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Servicio de notificaciones CONECTADO")
        Log.d(TAG, "Archivo CSV: ${storage.getFilePath()}")
    }
    
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Servicio de notificaciones DESCONECTADO")
    }
}
