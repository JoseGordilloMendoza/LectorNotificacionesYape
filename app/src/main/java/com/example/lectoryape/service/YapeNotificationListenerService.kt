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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class YapeNotificationListenerService : NotificationListenerService() {
    // se usa para seguridad con posibles problemas de arranque
    private val storage by lazy { YapeNotificationStorage(applicationContext) }
    private val firebaseUploader by lazy { FirebaseUploader(applicationContext) }
    private val notificationHelper by lazy { NotificationHelper(applicationContext) }
    
    //hash / set :V para evitar duplicados
    private val processedNotifications = mutableSetOf<String>()
    
    // SharedPreferences para leer la preferencia del usuario
    private val prefs: SharedPreferences by lazy {
        applicationContext.getSharedPreferences("yape_listener_prefs", Context.MODE_PRIVATE)
    }
    
    // Wake lock para mantener el servicio activo
    private var wakeLock: PowerManager.WakeLock? = null
    
    // Tarea encargada de enviar el Heartbeat a Firebase
    private var heartbeatJob: Job? = null
    
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
                        startHeartbeat()
                    } else {
                        stopForegroundService()
                        releaseWakeLock()
                        stopHeartbeatAndSendOffline()
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "YapeNotificationListener"
        // debuggeo xd
        private const val DEBUG_MODE = true
        
        // package de yape
        private const val YAPE_PACKAGE = "com.bcp.innovacxion.yapeapp"
        private const val PLIN_PACKAGE = "pe.com.interbank.mobilebanking"
        
        // broadcast notifica a MainActivity
        const val ACTION_NOTIFICATION_SAVED = "com.example.lectoryape.NOTIFICATION_SAVED"
        
        // broadcast controla la notificación persistente
        const val ACTION_TOGGLE_NOTIFICATION = "com.example.lectoryape.TOGGLE_NOTIFICATION"
        
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
    
    // only yapey plin
    private fun processYapeNotification(sbn: StatusBarNotification) {
        try {
            // Deduplicación: evitar reprocesar la misma notificación
            val notifKey = "${sbn.packageName}_${sbn.id}_${sbn.postTime}"
            if (!processedNotifications.add(notifKey)) {
                Log.d(TAG, "notificacion duplicada, ignorando ps: $notifKey")
                return
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
                sendBroadcast(Intent(ACTION_NOTIFICATION_SAVED))
            }

            // subir a Firebase
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    firebaseUploader.uploadNotification(yapePlinPayment)
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
        
        // Empezar el heartbeat si el servicio está habilitado
        if (isServiceEnabled()) {
            startHeartbeat()
        }
    }
    
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, " servicio de notificaciones DESCONECTADO - se intenta reconectar")
        
        // Detener Heartbeat e informar offline
        stopHeartbeatAndSendOffline()
        
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
    }
    
    // se detiene el servicio
    private fun stopForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        Log.d(TAG, "foreground service detenido")
    }
    
    /**
     * Maneja el cambio de preferencia del usuario (activar/desactivar notificación)
     */
    fun toggleNotification(show: Boolean) {
        if (show) {
            startForegroundService()
            acquireWakeLock()
            startHeartbeat()
        } else {
            stopForegroundService()
            releaseWakeLock()
            stopHeartbeatAndSendOffline()
        }
    }
    
    /**
     * Inicia un ciclo que envía un "pulso" a Firebase cada 60 segundos
     */
    private fun startHeartbeat() {
        if (heartbeatJob?.isActive == true) return
        
        heartbeatJob = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                Log.d(TAG, "💓 Enviando pulso de vida (Heartbeat) a Firebase...")
                firebaseUploader.sendHeartbeat(isOnline = true)
                delay(60_000) // Esperar 60 segundos
            }
        }
    }
    
    /**
     * Detiene el Heartbeat e informa a Firebase que el servicio se cerró
     */
    private fun stopHeartbeatAndSendOffline() {
        heartbeatJob?.cancel()
        CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "💔 Servicio detenido, avisando a Firebase (Offline)...")
            firebaseUploader.sendHeartbeat(isOnline = false)
        }
    }
    
    // wake lock para mantener el CPU activo
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
                Log.d(TAG, "wake lock adquirido")
            }
        } catch (e: Exception) {
            Log.e(TAG, "wake lock error: ${e.message}", e)
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
        releaseWakeLock()
        stopHeartbeatAndSendOffline()
    }
}
