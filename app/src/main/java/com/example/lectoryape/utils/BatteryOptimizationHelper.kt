package com.example.lectoryape.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Helper para gestionar optimizaciones de batería y mantener el servicio vivo
 */
object BatteryOptimizationHelper {
    
    private const val TAG = "BatteryOptimization"
    
    /**
     * Verifica si la app está exenta de optimización de batería
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true // No aplica en versiones antiguas
        }
        
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = context.packageName
        val isIgnoring = powerManager.isIgnoringBatteryOptimizations(packageName)
        
        Log.d(TAG, "¿Exento de optimización de batería? $isIgnoring")
        return isIgnoring
    }
    
    /**
     * Solicita al usuario que desactive la optimización de batería para esta app
     */
    /**
     * Intenta abrir la configuración de inicio automático (AutoStart)
     * Crítico para Xiaomi/Redmi/Poco
     */
    fun checkAndRequestAutoStart(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase()
        if ("xiaomi" in manufacturer || "redmi" in manufacturer || "poco" in manufacturer) {
            try {
                // Intent específico para gestión de AutoStart en MIUI
                val intent = Intent()
                intent.component = android.content.ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                Log.d(TAG, "🚀 Abriendo AutoStart para Xiaomi")
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo abrir AutoStart Xiaomi: ${e.message}")
                // Fallback a configuración de aplicación
                openAppDetails(context)
            }
        }
    }

    /**
     * Abre la pantalla de detalles de la app
     */
    private fun openAppDetails(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:${context.packageName}")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error abriendo detalles de app: ${e.message}")
        }
    }

    /**
     * Versión mejorada que intenta evitar el dialog confuso en Xiaomi
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        
        if (isIgnoringBatteryOptimizations(context)) return

        // Para Xiaomi, a veces es mejor ir directo a la configuración de batería
        // que usar el intent estándar que muestra el dialog confuso
        val manufacturer = Build.MANUFACTURER.lowercase()
        if ("xiaomi" in manufacturer || "redmi" in manufacturer) {
            try {
                // Intent para "Ahorro de batería de aplicaciones" en MIUI
                val intent = Intent()
                intent.component = android.content.ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.powercenter.PowerSettings"
                )
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                // Fallback al estándar
            }
        }

        // Método estándar Android
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Último recurso: Configuración general
            openBatterySettings(context)
        }
    }

    /**
     * Abre la configuración de optimización de batería (Genérico)
     */
    private fun openBatterySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al abrir configuración: ${e.message}", e)
            openAppDetails(context)
        }
    }
}
