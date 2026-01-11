package com.example.lectoryape.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class YapeNotificationListenerService : NotificationListenerService() {
    
    companion object {
        private const val TAG = "YapeNotificationListener"
        // Nombre del paquete de la app YAPE (puede variar)
        private const val YAPE_PACKAGE = "com.bcp.bank.bcp" // Este es un ejemplo, puede ser diferente
    }
    
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        
        sbn?.let { notification ->
            // Filtrar solo notificaciones de YAPE
            if (notification.packageName == YAPE_PACKAGE) {
                processYapeNotification(notification)
            }
            
            // TODO: Eliminar esto después de pruebas - esto logea TODAS las notificaciones
            // para ayudarte a encontrar el paquete correcto de YAPE
            Log.d(TAG, "Notificación recibida de: ${notification.packageName}")
            Log.d(TAG, "Título: ${notification.notification.extras.getString("android.title")}")
            Log.d(TAG, "Texto: ${notification.notification.extras.getString("android.text")}")
        }
    }
    
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        // Por ahora no hacemos nada cuando se remueve una notificación
    }
    
    private fun processYapeNotification(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getString("android.text") ?: ""
        
        Log.d(TAG, "=== NOTIFICACIÓN YAPE ===")
        Log.d(TAG, "Título: $title")
        Log.d(TAG, "Texto: $text")
        Log.d(TAG, "========================")
        
        // TODO: Aquí aplicaremos el regex para extraer datos
        // TODO: Aquí guardaremos en CSV
    }
    
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Servicio de notificaciones CONECTADO")
    }
    
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Servicio de notificaciones DESCONECTADO")
    }
}
