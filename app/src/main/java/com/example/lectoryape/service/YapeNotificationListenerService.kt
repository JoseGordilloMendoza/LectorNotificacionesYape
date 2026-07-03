package com.example.kajaapp.service

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.kajaapp.network.RetrofitClient
import com.example.kajaapp.models.YapeNotificationRaw
import com.example.kajaapp.storage.YapeNotificationStorage
import com.example.kajaapp.utils.NotificationHelper
import com.example.kajaapp.utils.YapeParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class YapeNotificationListenerService : NotificationListenerService() {
    // se usa para seguridad con posibles problemas de arranque
    private val storage by lazy { YapeNotificationStorage(applicationContext) }
    private val notificationHelper by lazy { NotificationHelper(applicationContext) }
    
    //hash / set :V para evitar duplicados
    private val processedNotifications = mutableSetOf<String>()
    
    // SharedPreferences para leer la preferencia del usuario
    private val prefs: SharedPreferences by lazy {
        applicationContext.getSharedPreferences("yape_listener_prefs", Context.MODE_PRIVATE)
    }

    // Wake lock para mantener el servicio activo
    private var wakeLock: PowerManager.WakeLock? = null
    
    private var heartbeatJob: kotlinx.coroutines.Job? = null
    
    // Scope con lifecycle: se cancela en onDestroy para evitar memory leaks
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // BroadcastReceiver para escuchar cuando el usuario cambia el switch
    private val toggleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_TOGGLE_NOTIFICATION -> {
                    val isEnabled = isServiceEnabled()
                    Log.d(TAG, "📻 Broadcast recibido - Switch: ${if (isEnabled) "ON" else "OFF"}")
                    
                    // Ejecutar en segundo plano para no bloquear UI
                    if (isEnabled) {
                        acquireWakeLock()
                        startForegroundService()
                    } else {
                        stopForegroundService()
                        releaseWakeLock()
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "YapeNotificationListener"
        // Cambiar a true solo para sesiones de debugging con ADB
        private const val DEBUG_MODE = false
        
        // package de yape
        private const val YAPE_PACKAGE = "com.bcp.innovacxion.yapeapp"
        private const val PLIN_PACKAGE = "pe.com.interbank.mobilebanking"
        
        // broadcast notifica a MainActivity
        const val ACTION_NOTIFICATION_SAVED = "com.example.kajaapp.NOTIFICATION_SAVED"
        
        // broadcast controla la notificación persistente
        const val ACTION_TOGGLE_NOTIFICATION = "com.example.kajaapp.TOGGLE_NOTIFICATION"
        
        // Key para SharedPreferences
        const val PREF_SHOW_NOTIFICATION = "show_persistent_notification"
    }
    
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        
        // verificar servicio activo
        if (!isServiceEnabled()) {
            Log.d(TAG, "Servicio deshabilitado por el usuario - notificación ignorada")
            return
        }
        
        sbn?.let { notification ->
            if (DEBUG_MODE) {
                // MODO DEBUG: Logea TODAS las notificaciones
                logNotificationDetails(notification)
            }
            
            if (DEBUG_MODE || notification.packageName == YAPE_PACKAGE || notification.packageName == PLIN_PACKAGE) {
                processYapeNotification(notification)
            }
        }
    }
    
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        //dumb
    }
    
    /**
     * cualquier noti, comentarlo dsps
     */
    private fun logNotificationDetails(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: "Sin título"
        val text = extras.getString("android.text") ?: "Sin texto"
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: "No hay informacion detallada"

        Log.d(TAG, """
        /. notifacion capturada .\
        App: ${sbn.packageName}
        ID: ${sbn.id}
        Title: $title
        Text: $text
        BigText: $bigText
        ══════════════════════════
    """.trimIndent())
    }
    
    // Solo Yape y Plin (cuando DEBUG_MODE=false)
    private fun processYapeNotification(sbn: StatusBarNotification) {
        try {
            // Deduplicación: evitar reprocesar la misma notificación
            val notifKey = "${sbn.packageName}_${sbn.id}_${sbn.postTime}"
            if (!processedNotifications.add(notifKey)) {
                Log.d(TAG, "notificacion duplicada, ignorando: $notifKey")
                return
            }
            
            // Prevenir memory leak: limpiar entradas antiguas si supera 200
            if (processedNotifications.size > 200) {
                val oldest = processedNotifications.take(100)
                processedNotifications.removeAll(oldest.toSet())
                Log.d(TAG, "Cache de notificaciones limpiada (200 → 100)")
            }

            val yapePlinPayment = YapeParser.parse(sbn)

            if (yapePlinPayment == null) {
                Log.w(TAG, "Formato de notificación no reconocido: ${sbn.notification.extras.getString("android.text")}")
                return
            }

            logYapePayment(yapePlinPayment)

            // csv
            val saved = storage.saveNotification(yapePlinPayment)
            if (saved) {
                val intent = Intent(ACTION_NOTIFICATION_SAVED)
                intent.setPackage(packageName)
                sendBroadcast(intent)
            }

            // Subir al backend Django (autenticado por device_id, sin JWT)
            serviceScope.launch {
                try {
                    val deviceId = applicationContext.getSharedPreferences("yape_listener_prefs", Context.MODE_PRIVATE)
                        .getString("device_id", "") ?: ""
                    if (deviceId.isBlank()) {
                        Log.w(TAG, "deviceId no configurado — pago no enviado al backend")
                        return@launch
                    }
                    val rawJson = sbn.notification.extras.getString("android.text") ?: ""
                    val payload = com.example.kajaapp.network.models.DeviceNotificationPayload(
                        deviceId = deviceId,
                        amount = yapePlinPayment.amount,
                        sender_name = yapePlinPayment.name,
                        reference_code = yapePlinPayment.securityCode,
                        wallet_type = yapePlinPayment.walletType,
                        raw_payload = mapOf("text" to rawJson)
                    )
                    val response = RetrofitClient.api.sendNotificationFromDevice(payload)
                    if (response.isSuccessful) {
                        Log.i(TAG, "✅ Enviado al backend: ${yapePlinPayment.name} S/${"%.2f".format(yapePlinPayment.amount)}")
                    } else if (response.code() == 409) {
                        Log.d(TAG, "⚠️ Notificación duplicada ignorada (code: ${yapePlinPayment.securityCode})")
                    } else {
                        Log.e(TAG, "❌ Error backend ${response.code()}: ${response.errorBody()?.string()}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Sin conexión al backend: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error crítico procesando notificación: ${e.message}")
        }
    }

    private fun logYapePayment(payment: YapeNotificationRaw) {
        val fecha = com.example.kajaapp.utils.DateFormatter.formatTimestamp(payment.timestamp)
        Log.d(TAG, """
        ═══ pago yape ═══
        Cliente: ${payment.name}
        Monto:   S/ ${"%.2f".format(payment.amount)}
        Código:  ${payment.securityCode}
        Fecha:   $fecha
        ════════════════════════
        """.trimIndent())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Servicio de notificaciones CONECTADO y listo para recibir pagos")
        Log.d(TAG, "csv: ${storage.getFilePath()}")
        Log.d(TAG, "android version: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
        
        // BroadcastReceiver para escuchar cambios del switch
        registerToggleReceiver()
        
        // foreground service
        initializeForegroundService()
    }
    
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, " servicio de notificaciones DESCONECTADO - se intenta reconectar")
        
        // Desregistrar el receiver para evitar memory leaks
        try {
            unregisterReceiver(toggleReceiver)
            Log.d(TAG, "BroadcastReceiver desregistrado")
        } catch (e: IllegalArgumentException) {
            // Receiver no estaba registrado
        }
        
        // reconectar automáticamente el servicio (API 24+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                requestRebind(ComponentName(this, YapeNotificationListenerService::class.java))
                Log.d(TAG, "Solicitud de reconexión enviada")
            } catch (e: Exception) {
                Log.e(TAG, "error reconexión: ${e.message}")
            }
        } else {
            Log.w(TAG, " requestRebind() requiere Android 7.0+ (API 24)")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // START_STICKY: Si el sistema mata el servicio, lo recrea automáticamente
        Log.d(TAG, "onStartCommand ejecutado - Servicio marcado como STICKY")
        return START_STICKY
    }
    
    // servicio se inicia como Foreground Service si se activa
    private fun initializeForegroundService() {
        try {
            //canal de notificación
            notificationHelper.createNotificationChannel()
             
            //verificar si el servicio habilitado
            if (isServiceEnabled()) {
                startForegroundService()
                acquireWakeLock()
                Log.d(TAG, " service iniciado - escuchando notificaciones")
            } else {
                releaseWakeLock()
                Log.d(TAG, "servicio deshabilitado - sin escuchar notificaciones")
            }
        } catch (e: Exception) {
            Log.e(TAG, "foreground service error: ${e.message}", e)
        }
    }
    
    // switch on
    private fun isServiceEnabled(): Boolean {
        return prefs.getBoolean(PREF_SHOW_NOTIFICATION, false)  // Default: OFF
    }
    
    //  BroadcastReceiver para escuchar cambios del switch

    private fun registerToggleReceiver() {
        val filter = IntentFilter(ACTION_TOGGLE_NOTIFICATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(toggleReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(toggleReceiver, filter)
        }
        Log.d(TAG, "📻 BroadcastReceiver registrado")
    }
    
    // servicio se inicia con la noti persistente
    private fun startForegroundService() {
        val notification = notificationHelper.buildServiceNotification()
        startForeground(NotificationHelper.NOTIFICATION_ID, notification)
        Log.d(TAG, "foreground serv iniciado")
        startHeartbeat()
    }
    
    // se detiene el servicio
    private fun stopForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopHeartbeat()
        Log.d(TAG, "foreground service detenido")
    }
    
    /**
     * Maneja el cambio de preferencia del usuario (activar/desactivar notificación)
     */
    fun toggleNotification(show: Boolean) {
        if (show) {
            startForegroundService()
            acquireWakeLock()
        } else {
            stopForegroundService()
            releaseWakeLock()
        }
    }
    
    private fun startHeartbeat() {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = serviceScope.launch {
            // Envío inmediato al arrancar — el backend sabe que el dispositivo está online
            sendHeartbeatNow()
            while (isActive) {
                // Esperar 4 minutos antes del siguiente latido
                kotlinx.coroutines.delay(4 * 60 * 1000L)
                sendHeartbeatNow()
                // Renovar wake lock en cada ciclo (timeout configurado en 20 min)
                acquireWakeLock()
            }
        }
    }

    /**
     * Refresca el token de Supabase si está próximo a expirar o es nulo.
     * El cliente tiene autoLoadFromStorage pero no auto-refresca en background.
     * Decodifica el JWT manualmente (sin librerías externas) para verificar el exp.
     */
    private suspend fun ensureFreshToken() {
        try {
            val token = com.example.kajaapp.auth.SupabaseManager.auth
                .currentSessionOrNull()?.accessToken
            if (token == null || isTokenExpiringSoon(token)) {
                Log.d(TAG, "Token nulo o próximo a expirar — refrescando")
                com.example.kajaapp.auth.SupabaseManager.auth.refreshCurrentSession()
                Log.d(TAG, "Token refrescado correctamente")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Token refresh fallido: ${e.message}")
        }
    }

    /** Devuelve true si el token JWT expira en menos de 5 minutos o no se puede leer. */
    private fun isTokenExpiringSoon(accessToken: String): Boolean {
        return try {
            val payload = accessToken.split(".").getOrNull(1) ?: return true
            val decoded = String(
                android.util.Base64.decode(
                    payload,
                    android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING
                )
            )
            val exp = org.json.JSONObject(decoded).getLong("exp") // segundos Unix
            val nowPlusFiveMin = System.currentTimeMillis() / 1000 + 300
            exp < nowPlusFiveMin
        } catch (e: Exception) {
            true // si no se puede leer, mejor refrescar
        }
    }

    private suspend fun sendHeartbeatNow() {
        ensureFreshToken()
        try {
            val prefs = applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val tenantId = prefs.getString("tenant_id", "") ?: ""
            val deviceId = applicationContext.getSharedPreferences("yape_listener_prefs", Context.MODE_PRIVATE)
                .getString("device_id", "") ?: ""

            if (tenantId.isBlank() || tenantId == "null" || deviceId.isBlank()) {
                Log.w(TAG, "Heartbeat omitido: tenantId o deviceId no configurados")
                return
            }
            val payload = com.example.kajaapp.network.models.HeartbeatPayload(
                tenantId = tenantId,
                deviceId = deviceId,
                timestamp = System.currentTimeMillis()
            )
            val hbResponse = RetrofitClient.api.sendHeartbeat(payload)
            if (hbResponse.code() == 404) {
                // El device no existe en la BD (ej. BD limpiada) — forzar re-registro al próximo inicio
                applicationContext.getSharedPreferences("yape_listener_prefs", Context.MODE_PRIVATE)
                    .edit().putBoolean("is_device_registered", false).apply()
                Log.w(TAG, "💓 Device no encontrado en backend — se re-registrará al próximo inicio")
            } else {
                Log.d(TAG, "💓 Heartbeat enviado (device: $deviceId)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al enviar heartbeat: ${e.message}")
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        Log.d(TAG, "💓 Heartbeat detenido")
    }
    
    // wake lock para mantener el CPU activo
    // Timeout de 10 minutos: el heartbeat (cada 4 min) lo renueva antes que expire
    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "KAJAapp::ServiceWakeLock"
                )
                wakeLock?.setReferenceCounted(false)
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(10 * 60 * 1000L) // 10 minutos — renovado por heartbeat cada 4
                Log.d(TAG, "Wake lock adquirido (timeout: 20min)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Wake lock error: ${e.message}", e)
        }
    }
    
    // wake lock liberado
    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "Wake lock liberado")
            }
        } catch (e: Exception) {
            Log.e(TAG, " wake lock error liberacion: ${e.message}", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Desregistrar el receiver para evitar IllegalArgumentException
        try {
            unregisterReceiver(toggleReceiver)
        } catch (e: IllegalArgumentException) {
            // Ya estaba desregistrado (p. ej. si onListenerDisconnected lo hizo antes)
        }
        releaseWakeLock()
        // Cancelar el scope del servicio: libera todos los coroutines activos
        serviceScope.cancel()
        Log.d(TAG, "🛑 Servicio destruido: recursos liberados")
    }
}
