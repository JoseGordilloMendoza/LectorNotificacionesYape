package com.example.kajaapp.utils

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Helper para manejar las restricciones de batería en dispositivos OEM (Honor, Xiaomi, Huawei, etc.)
 */
object BatteryOptimizationHelper {
    private const val TAG = "BatteryHelper"
    private const val PREF_NAME = "battery_opt_prefs"
    private const val KEY_AUTOSTART_ASKED = "autostart_asked_v2" 

    /**
     * Verifica y solicita los permisos necesarios según el fabricante
     */
    fun checkAndRequest(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        
        // 1. Verificar Optimización de Batería (Doze Mode) - Estándar Android
        if (!isIgnoringBatteryOptimizations(context)) {
            showBatteryOptimizationDialog(context)
            return // Vamos uno por uno para no abrumar
        }

        // 2. Verificar Inicio Automático (AutoStart) - OEMs Específicos
        if (!prefs.getBoolean(KEY_AUTOSTART_ASKED, false)) {
            val intent = getAutoStartIntent(context)
            if (intent != null) {
                showAutoStartDialog(context, intent, prefs)
            }
        }
    }

    /**
     * Verifica si la app está en la lista blanca de optimización de batería
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }
        return true
    }

    /**
     * Solicita al sistema ignorar optimizaciones de batería
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:${context.packageName}")
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error abriendo settings de batería: ${e.message}")
            }
        }
    }

    private fun showBatteryOptimizationDialog(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("⚡ Configuración Necesaria")
            .setMessage("Para que la lectura de notificaciones no se detenga, necesitas seleccionar 'Sin Restricciones' o 'No Optimizar' en la siguiente pantalla.")
            .setPositiveButton("Configurar") { _, _ ->
                requestIgnoreBatteryOptimizations(context)
            }
            .setNegativeButton("Más tarde", null)
            .show()
    }

    private fun showAutoStartDialog(context: Context, intent: Intent, prefs: SharedPreferences) {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        
        AlertDialog.Builder(context)
            .setTitle("📱 Configuración $manufacturer")
            .setMessage("En dispositivos $manufacturer, es OBLIGATORIO activar:\n\n1. Inicio Automático\n2. Ejecutar en segundo plano\n\nSi no lo haces, la app dejará de leer notificaciones cuando apagues la pantalla.")
            .setPositiveButton("Ir a Configuración") { _, _ ->
                try {
                    context.startActivity(intent)
                    prefs.edit().putBoolean(KEY_AUTOSTART_ASKED, true).apply()
                } catch (e: Exception) {
                    Log.e(TAG, "Error abriendo intent de AutoStart: ${e.message}")
                    // Fallback a configuración de aplicación general
                    openAppInfo(context)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun openAppInfo(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:${context.packageName}")
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo abrir info de la app", e)
        }
    }

    /**
     * Retorna el Intent específico para la pantalla de inicio automático según el fabricante
     */
    private fun getAutoStartIntent(context: Context): Intent? {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val intent = Intent()

        try {
            when {
                "xiaomi" in manufacturer -> {
                    intent.component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                }
                "redmi" in manufacturer -> {
                    intent.component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                }
                "honor" in manufacturer || "huawei" in manufacturer -> {
                    // Honor/Huawei suelen usar Phone Manager
                    intent.component = ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
                }
                "oppo" in manufacturer -> {
                    intent.component = ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
                }
                "vivo" in manufacturer -> {
                    intent.component = ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
                }
                else -> return null
            }
            return intent
        } catch (e: Exception) {
            Log.e(TAG, "Error construyendo intent: ${e.message}")
            return null
        }
    }
}
