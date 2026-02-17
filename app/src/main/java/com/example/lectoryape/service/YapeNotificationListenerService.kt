package com.example.lectoryape.service

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
import androidx.annotation.RequiresApi
import com.example.lectoryape.firebase.FirebaseUploader
import com.example.lectoryape.models.YapeNotificationRaw
import com.example.lectoryape.storage.YapeNotificationStorage
import com.example.lectoryape.utils.NotificationHelper
import com.example.lectoryape.utils.YapeParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class YapeNotificationListenerService : NotificationListenerService() {
    // se usa para seguridad con posibles problemas de arranque
    private val storage by lazy { YapeNotificationStorage(applicationContext) }
    private val firebaseUploader by lazy { FirebaseUploader(applicationContext) }
    private val notificationHelper by lazy { NotificationHelper(applicationContext) }
    
    // SharedPreferences para leer la preferencia del usuario
    private val prefs: SharedPreferences by lazy {
        applicationContext.getSharedPreferences("yape_listener_prefs", Context.MODE_PRIVATE)
    }
    
    // Wake lock para mantener el servicio activo
    private var wakeLock: PowerManager.WakeLock? = null
    
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
        // debuggeo xd
        private const val DEBUG_MODE = true  // ← ACTIVADO para debugging
        
// pakeich de yape xd
        private const val YAPE_PACKAGE = "com.bcp.innovacxion.yapeapp"
        private const val PLIN_PACKAGE = "pe.com.interbank.mobilebanking"
        
        // Acción del broadcast para notificar a MainActivity
        const val ACTION_NOTIFICATION_SAVED = "com.example.lectoryape.NOTIFICATION_SAVED"
        
        // Acción del broadcast para controlar la notificación persistente
        const val ACTION_TOGGLE_NOTIFICATION = "com.example.lectoryape.TOGGLE_NOTIFICATION"
        
        // Key para SharedPreferences
        const val PREF_SHOW_NOTIFICATION = "show_persistent_notification"
    }
    
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        
        // Verificar si el servicio está habilitado por el usuario
        if (!isServiceEnabled()) {
            Log.d(TAG, "Servicio deshabilitado por el usuario - notificación ignorada")
            return
        }
        
        sbn?.let { notification ->
            if (DEBUG_MODE) {
                // MODO DEBUG: Logea TODAS las notificaciones con información detallada
                logNotificationDetails(notification)
            }
            
            // En DEBUG_MODE: capturar TODAS las notificaciones
            // En modo normal: solo YAPE
            // En DEBUG_MODE: capturar TODAS las notificaciones
            // En modo normal: solo YAPE y PLIN
            if (DEBUG_MODE || notification.packageName == YAPE_PACKAGE || notification.packageName == PLIN_PACKAGE) {
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
        
        // Registrar BroadcastReceiver para escuchar cambios del switch
        registerToggleReceiver()
        
        // Inicializar foreground service
        initializeForegroundService()
    }
    
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "⚠️ Servicio de notificaciones DESCONECTADO - Intentando reconectar...")
        
        // Desregistrar el receiver para evitar memory leaks
        try {
            unregisterReceiver(toggleReceiver)
            Log.d(TAG, "📻 BroadcastReceiver desregistrado")
        } catch (e: IllegalArgumentException) {
            // Receiver no estaba registrado
        }
        
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // START_STICKY: Si el sistema mata el servicio, lo recrea automáticamente
        Log.d(TAG, "🔄 onStartCommand ejecutado - Servicio marcado como STICKY")
        return START_STICKY
    }
    
    /**
     * Inicializa el servicio como Foreground Service si el usuario lo tiene habilitado
     */
    private fun initializeForegroundService() {
        try {
            // Crear el canal de notificación primero (necesario para Android 8.0+)
            notificationHelper.createNotificationChannel()
            
            // Verificar si el usuario tiene el servicio habilitado
            if (isServiceEnabled()) {
                startForegroundService()
                acquireWakeLock()
                Log.d(TAG, "🔔 Foreground service iniciado - Escuchando notificaciones")
            } else {
                releaseWakeLock()
                Log.d(TAG, "🔕 Servicio deshabilitado por el usuario - No se escucharán notificaciones")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al inicializar foreground service: ${e.message}", e)
        }
    }
    
    /**
     * Verifica si el servicio está habilitado por el usuario (switch ON)
     */
    private fun isServiceEnabled(): Boolean {
        return prefs.getBoolean(PREF_SHOW_NOTIFICATION, false)  // Default: OFF
    }
    
    /**
     * Registra el BroadcastReceiver para escuchar cambios del switch
     * Usamos RECEIVER_EXPORTED porque el broadcast viene de nuestra propia app (con setPackage)
     */
    private fun registerToggleReceiver() {
        val filter = IntentFilter(ACTION_TOGGLE_NOTIFICATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(toggleReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(toggleReceiver, filter)
        }
        Log.d(TAG, "📻 BroadcastReceiver registrado")
    }
    
    /**
     * Inicia el foreground service con la notificación persistente
     */
    private fun startForegroundService() {
        val notification = notificationHelper.buildServiceNotification()
        startForeground(NotificationHelper.NOTIFICATION_ID, notification)
        Log.d(TAG, "🚀 Foreground service iniciado")
    }
    
    /**
     * Detiene el foreground service (oculta la notificación)
     */
    private fun stopForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        Log.d(TAG, "🛑 Foreground service detenido (notificación oculta)")
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
    
    /**
     * Adquiere un wake lock para mantener el CPU activo
     */
    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "YapeLector::NotificationWakeLock"
                )
                wakeLock?.setReferenceCounted(false)
            }
            
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire()
                Log.d(TAG, "🔋 Wake lock adquirido")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al adquirir wake lock: ${e.message}", e)
        }
    }
    
    /**
     * Libera el wake lock
     */
    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "🔌 Wake lock liberado")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al liberar wake lock: ${e.message}", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
    }
}
