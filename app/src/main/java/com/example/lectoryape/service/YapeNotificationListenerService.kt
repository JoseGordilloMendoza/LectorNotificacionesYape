package com.example.lectoryape.service

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.annotation.RequiresApi
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
    private val firebaseUploader by lazy { FirebaseUploader(applicationContext) }

    companion object {
        private const val TAG = "YapeNotificationListener"
        // debuggeo xd
        private const val DEBUG_MODE = false  // ← Cambiado para testing
        
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
            
            // En DEBUG_MODE: capturar TODAS las notificaciones
            // En modo normal: solo YAPE
            if (DEBUG_MODE || notification.packageName == YAPE_PACKAGE) {
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
        try {
            val yapePayment = YapeParser.parse(sbn)

            if (yapePayment == null) {
                Log.w(TAG, "Formato de notificación no reconocido: ${sbn.notification.extras.getString("android.text")}")
                return
            }

            logYapePayment(yapePayment)

            // Guardar local
            val saved = storage.saveNotification(yapePayment)
            if (saved) {
                sendBroadcast(Intent(ACTION_NOTIFICATION_SAVED))
            }

            // Subir a Firebase con un try-catch interno para que no rompa el resto
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    firebaseUploader.uploadNotification(yapePayment)
                } catch (e: Exception) {
                    Log.e(TAG, "Error subiendo a Firebase: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error crítico procesando notificación: ${e.message}")
        }
    }

    private fun logYapePayment(payment: YapeNotificationRaw) {
        val fecha = com.example.lectoryape.utils.DateFormatter.formatTimestamp(payment.timestamp)
        Log.d(TAG, """
        ═══ PAGO RECIBIDO ═══
        Cliente: ${payment.name}
        Monto:   S/ ${"%.2f".format(payment.amount)}
        Código:  ${payment.securityCode}
        Fecha:   $fecha
        ════════════════════════
        """.trimIndent())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "✅ Servicio de notificaciones CONECTADO y listo para recibir pagos")
        Log.d(TAG, "📁 Archivo CSV: ${storage.getFilePath()}")
        Log.d(TAG, "📱 Versión Android: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
    }
    
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "⚠️ Servicio de notificaciones DESCONECTADO - Intentando reconectar...")
        
        // Intentar reconectar automáticamente el servicio (API 24+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                requestRebind(ComponentName(this, YapeNotificationListenerService::class.java))
                Log.d(TAG, "✅ Solicitud de reconexión enviada")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error al solicitar reconexión: ${e.message}")
            }
        } else {
            Log.w(TAG, "⚠️ requestRebind() requiere Android 7.0+ (API 24)")
        }
    }
}
