package com.example.lectoryape.service

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.util.Log

// FIX basicamente Si el usuario tenía el switch de escucha activado, solicita al sistema 
// que reconecte el NotificationListenerService automáticamente

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.i(TAG, "📱 BOOT_COMPLETED recibido — verificando si el servicio debe reconectarse")

        // Verificar si el usuario tenía el switch activado antes del reinicio
        val prefs = context.getSharedPreferences("yape_listener_prefs", Context.MODE_PRIVATE)
        val wasEnabled = prefs.getBoolean(YapeNotificationListenerService.PREF_SHOW_NOTIFICATION, false)

        if (!wasEnabled) {
            Log.d(TAG, "🔕 Switch estaba OFF — no se reinicia el servicio")
            return
        }

        Log.i(TAG, "✅ Switch estaba ON — solicitando reconexión del NotificationListenerService")

        // requestRebind() pide al sistema Android que reconecte el servicio.
        // Es seguro llamarlo en boot: si el sistema aún no está listo, Android lo
        // reintenta internamente. No lanza excepciones ni crashea.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val componentName = ComponentName(context, YapeNotificationListenerService::class.java)
                NotificationListenerService.requestRebind(componentName)
                Log.i(TAG, "🔄 requestRebind() enviado exitosamente")
            } catch (e: Exception) {
                // En la práctica esto no falla, pero lo capturamos por seguridad
                Log.e(TAG, "❌ Error al solicitar rebind: ${e.message}")
            }
        } else {
            // Android < 7.0: requestRebind no existe. El servicio se reconecta
            // automáticamente cuando el usuario abre la app (onResume lo maneja).
            Log.w(TAG, "⚠️ requestRebind() requiere Android 7.0+ — el servicio se restaurará al abrir la app")
        }
    }
}
