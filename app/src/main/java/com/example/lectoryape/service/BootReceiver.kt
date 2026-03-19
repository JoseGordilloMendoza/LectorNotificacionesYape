package com.example.lectoryape.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d(TAG, "🟢 Dispositivo reiniciado. Verificando estado del servicio Lector Yape...")

            val prefs = context.getSharedPreferences("yape_listener_prefs", Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean(YapeNotificationListenerService.PREF_SHOW_NOTIFICATION, false)

            if (isEnabled) {
                Log.d(TAG, "⚡ El servicio estaba activo antes del reinicio. Solicitando Rebind...")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    try {
                        val component = android.content.ComponentName(context, YapeNotificationListenerService::class.java)
                        android.service.notification.NotificationListenerService.requestRebind(component)
                        Log.d(TAG, "✅ requestRebind enviado exitosamente")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error al solicitar rebind en el arranque: ${e.message}")
                    }
                }
            } else {
                Log.d(TAG, "🔕 El servicio estaba apagado. No se tomará ninguna acción.")
            }
        }
    }
}
