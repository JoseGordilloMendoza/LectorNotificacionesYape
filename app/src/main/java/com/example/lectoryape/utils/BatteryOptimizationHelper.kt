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
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return
        }
        
        if (isIgnoringBatteryOptimizations(context)) {
            Log.d(TAG, "✅ Ya está exento de optimización de batería")
            return
        }
        
        try {
            val intent = Intent().apply {
                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.d(TAG, "📱 Solicitando exención de optimización de batería")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al solicitar exención: ${e.message}", e)
            // Fallback: abrir configuración general de batería
            openBatterySettings(context)
        }
    }
    
    /**
     * Abre la configuración de optimización de batería
     */
    private fun openBatterySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al abrir configuración: ${e.message}", e)
        }
    }
}
