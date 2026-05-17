package com.example.lectoryape

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.lectoryape.auth.FirebaseAuthManager
import com.example.lectoryape.databinding.ActivityMainBinding
import com.example.lectoryape.firebase.FirebaseUploader
import com.example.lectoryape.service.YapeNotificationListenerService
import com.example.lectoryape.storage.YapeNotificationStorage
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
// Coil - carga de imágenes (foto de perfil Google)
import coil.load
import coil.transform.CircleCropTransformation

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var storage: YapeNotificationStorage
    private lateinit var authManager: FirebaseAuthManager
    private lateinit var firebaseUploader: FirebaseUploader
    private lateinit var appUpdater: com.example.lectoryape.utils.AppUpdater
    
    // SharedPreferences para guardar la preferencia del switch
    private val prefs by lazy {
        getSharedPreferences("yape_listener_prefs", Context.MODE_PRIVATE)
    }

    // BroadcastReceiver para escuchar cuando se guardan notificaciones
    private val notificationReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    // Actualizar contador
                    updateNotificationCount()
                }
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // modo claro, al pepo se le distorsiona xd
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // Verificar Login
        authManager = FirebaseAuthManager(this)
        
        if (!authManager.isSignedIn()) {
            navigateToLogin()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configurar Toolbar como ActionBar
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        storage = YapeNotificationStorage(this)
        firebaseUploader = FirebaseUploader(this)
        appUpdater = com.example.lectoryape.utils.AppUpdater(this)

        // Verificar suscripción ANTES de inicializar la app
        checkSubscription()
    }

    /* Verificacion de suscripcion de user
    Si la prueba expiró o está inactiva, muestra un diálogo bloqueante.
     */
    private fun checkSubscription() {
        val user = authManager.getCurrentUser()
        if (user == null) {
            navigateToLogin()
            return
        }

        // Mostrar loading mientras verificamos
        binding.root.alpha = 0.5f

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("users").document(user.uid)
            .get()
            .addOnSuccessListener { document ->
                binding.root.alpha = 1.0f

                if (document != null && document.exists()) {
                    val subMap = document.get("subscription") as? Map<String, Any>
                    val isActive = subMap?.get("isActive") as? Boolean ?: false
                    val status = subMap?.get("status") as? String ?: "expired"

                    if (isActive && status != "expired") {
                        // ✅ Suscripción válida → Inicializar app
                        android.util.Log.d("MainActivity", "sub activa: $status")
                        initializeApp()
                    } else {
                        // ❌ Suscripción expirada → Bloquear
                        android.util.Log.w("MainActivity", "sub expirada: isActive=$isActive, status=$status")
                        showSubscriptionExpiredDialog()
                    }
                } else {
                    // No tiene documento de usuario → tratar como expirado
                    showSubscriptionExpiredDialog()
                }
            }
            .addOnFailureListener { e ->
                binding.root.alpha = 1.0f
                android.util.Log.e("MainActivity", "Error verificando suscripción: ${e.message}")
                // En caso de error de red, permitir uso (para no bloquear sin internet)
                Toast.makeText(this, "⚠️ No se pudo verificar suscripción", Toast.LENGTH_SHORT).show()
                initializeApp()
            }
    }

    private fun initializeApp() {
        setupUI()

        checkNotificationPermission()
        updateNotificationCount()
        setupNotificationSwitch()
        
        // Configurar Barra Inferior
        setupBottomNavigation()
        // Inicializar datos del perfil
        setupProfileScreen()
        
        // Verificar y solicitar exención de optimización de batería
        checkBatteryOptimization()

        // Verificar si hay una actualización OTA disponible (silencioso, no bloquea la UI)
        appUpdater.checkForUpdates()
    }

    /**
     * Muestra un diálogo bloqueante cuando la suscripción ha expirado.
     * Ofrece ir a la web para renovar o cerrar sesión.
     */
    private fun showSubscriptionExpiredDialog() {
        AlertDialog.Builder(this)
            .setTitle("⏰ Suscripción Expirada")
            .setMessage("Tu período de prueba o suscripción ha finalizado.\n\nPara seguir usando la app, renueva tu plan desde nuestra página web.")
            .setPositiveButton("🌐 Ir a la Web") { _, _ ->
                openWebPortal()
                // Después de abrir el navegador, cerrar sesión
                performLogout()
            }
            .setNegativeButton("Cerrar Sesión") { _, _ ->
                performLogout()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Abre el portal web de Yape Visualizer en el navegador
     */
    private fun openWebPortal() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://yapevisualizer.onrender.com"))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir el navegador", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Verifica si la app está exenta de optimización de batería
     * Si no lo está, muestra un diálogo para solicitarlo
     * También verifica AutoStart en Xiaomi
     */
    /**
     * Verifica si la app está exenta de optimización de batería
     * Delega la lógica al BatteryOptimizationHelper
     */
    private fun checkBatteryOptimization() {
        com.example.lectoryape.utils.BatteryOptimizationHelper.checkAndRequest(this)
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavigation)
        val containerHome = findViewById<android.widget.ScrollView>(R.id.containerHome)
        val containerUpdates = findViewById<android.widget.ScrollView>(R.id.containerUpdates)
        val containerProfile = findViewById<android.widget.ScrollView>(R.id.containerProfile)

        bottomNav.setOnItemSelectedListener { item ->
            // Ocultar todos
            containerHome.visibility = android.view.View.GONE
            containerUpdates.visibility = android.view.View.GONE
            containerProfile.visibility = android.view.View.GONE

            when (item.itemId) {
                R.id.nav_home -> {
                    containerHome.visibility = android.view.View.VISIBLE
                    updateToolbarTitle(R.id.nav_home)
                    true
                }
                R.id.nav_updates -> {
                    containerUpdates.visibility = android.view.View.VISIBLE
                    updateToolbarTitle(R.id.nav_updates)
                    appUpdater.setupUpdatesScreen() // Actualizar info
                    true
                }
                R.id.nav_profile -> {
                    containerProfile.visibility = android.view.View.VISIBLE
                    updateToolbarTitle(R.id.nav_profile)
                    true
                }
                else -> false
            }
        }
        
        // Establecer título inicial
        updateToolbarTitle(R.id.nav_home)
    }

    private fun updateToolbarTitle(navId: Int) {
        val user = authManager.getCurrentUser()
        val firstName = user?.displayName?.split(" ")?.firstOrNull() ?: "Usuario"
        
        val title = when (navId) {
            R.id.nav_home     -> "HOLA, ${firstName.uppercase()}"
            R.id.nav_updates  -> "NOVEDADES"
            R.id.nav_profile  -> "MI CUENTA"
            else              -> "KAJA"
        }
        // Escribir en el TextView custom del toolbar
        supportActionBar?.title = ""
        findViewById<android.widget.TextView>(R.id.tvToolbarTitle)?.text = title
    }

    private fun setupProfileScreen() {
        // Referencias a vistas ya integradas en activity_main.xml (a través de <include>)

        val ivPhoto        = findViewById<android.widget.ImageView>(R.id.ivProfilePhoto)
        val tvName         = findViewById<android.widget.TextView>(R.id.tvProfileName)
        val tvEmail        = findViewById<android.widget.TextView>(R.id.tvProfileEmail)
        val tvStatus       = findViewById<android.widget.TextView>(R.id.tvProfileStatus)
        val tvRole         = findViewById<android.widget.TextView>(R.id.tvProfileRole)
        val tvCreatedAt    = findViewById<android.widget.TextView>(R.id.tvProfileCreatedAt)
        val tvTrialEnd     = findViewById<android.widget.TextView>(R.id.tvProfileTrialEnd)
        val btnLogout      = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnProfileLogout)

        val user = authManager.getCurrentUser()

        if (user != null) {
            // Datos básicos desde Firebase Auth
            tvName.text  = user.displayName ?: "Usuario"
            tvEmail.text = user.email ?: "—"

            // Cargar foto de Google con Coil (si existe)
            val photoUrl = user.photoUrl
            if (photoUrl != null) {
                ivPhoto.load(photoUrl) {
                    crossfade(true)
                    placeholder(R.drawable.ic_person)
                    error(R.drawable.ic_person)
                    transformations(CircleCropTransformation())
                }
            }

            // Datos de Firestore (suscripción, rol, fechas)
            tvStatus.text = "Cargando..."
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(user.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val subMap   = document.get("subscription") as? Map<String, Any>
                        val status   = subMap?.get("status")   as? String ?: "expired"
                        val planName = subMap?.get("planName") as? String ?: "Sin plan"
                        val trialEnd = subMap?.get("trialEndDate") as? String
                        val role     = document.getString("role") ?: "owner"
                        val createdAt = document.getString("createdAt")

                        // Badge de suscripción
                        val statusDisplay = status.replaceFirstChar { it.uppercaseChar() }
                        tvStatus.text = "$planName · $statusDisplay"
                        val badgeColor = if (status == "trial" || status == "active") "#4CAF50" else "#D32F2F"
                        tvStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.parseColor(badgeColor)
                        )

                        // Rol
                        tvRole.text = role.replaceFirstChar { it.uppercaseChar() }

                        // Fecha de registro (solo la fecha, sin la hora)
                        tvCreatedAt.text = createdAt?.take(10) ?: "—"

                        // Fecha de vencimiento de prueba
                        tvTrialEnd.text = trialEnd?.take(10) ?: "—"

                    } else {
                        tvStatus.text = "Sin Plan"
                        tvRole.text   = "—"
                    }
                }
                .addOnFailureListener {
                    tvStatus.text = "Error al cargar"
                }
        } else {
            tvStatus.text = "No autenticado"
        }

        // Botón Cerrar Sesión
        btnLogout.setOnClickListener {
            // dialog.dismiss() -- Ya no es diálogo
            logout()
        }
    }


    private fun logout() {
        // Mostrar confirmación antes de cerrar sesión
        AlertDialog.Builder(this)
                .setTitle("Cerrar sesión")
                .setMessage("¿Estás seguro que deseas cerrar sesión?")
                .setPositiveButton("Sí") { _, _ -> performLogout() }
                .setNegativeButton("Cancelar", null)
                .show()
    }

    private fun performLogout() {
        try {
            authManager.signOut()
            Toast.makeText(this@MainActivity, "Sesión cerrada", Toast.LENGTH_SHORT).show()
            navigateToLogin()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error al cerrar sesión: ${e.message}", e)
            Toast.makeText(this@MainActivity, "Error al cerrar sesión", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onResume() {
        super.onResume()
        // Registrar el receiver para escuchar broadcasts
        val filter = IntentFilter(YapeNotificationListenerService.ACTION_NOTIFICATION_SAVED)
        registerReceiver(notificationReceiver, filter, RECEIVER_NOT_EXPORTED)

        // Verificar permiso cada vez que la app regresa al foreground
        checkNotificationPermission()
        updateNotificationCount()

        // Forzar reconexión del servicio si está habilitado (API 24+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isNotificationServiceEnabled()) {
            requestServiceRebindIfNeeded()
        }
        
        // ServiceWatchdog: si el switch está ON y el servicio pudo haber muerto,
        // re-enviar el broadcast de activación. El receiver en el servicio lo atenderá
        // si sigue vivo; si no, el requestRebind anterior lo despertará y luego onListenerConnected
        // lo re-registrará para futuros cambios de switch.
        val isSwitchOn = prefs.getBoolean(YapeNotificationListenerService.PREF_SHOW_NOTIFICATION, false)
        if (isSwitchOn && isNotificationServiceEnabled()) {
            val watchdogIntent = Intent(YapeNotificationListenerService.ACTION_TOGGLE_NOTIFICATION)
            watchdogIntent.setPackage(packageName)
            sendBroadcast(watchdogIntent)
            android.util.Log.d("MainActivity", "🐕 Watchdog: broadcast de activación enviado en onResume")
        }
    }

    override fun onPause() {
        super.onPause()
        // Desregistrar el receiver para evitar memory leaks
        try {
            unregisterReceiver(notificationReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver no estaba registrado, ignorar
        }
    }

    private fun setupUI() {
        // Botón para abrir configuración de notificaciones genérica
        binding.btnEnableNotifications.setOnClickListener { openNotificationSettings() }

        // Botón específico para la pantalla de Información de la App (Ajustes Restringidos)
        binding.btnOpenAppInfo.setOnClickListener { openAppInfoSettings() }

        // Botón para abrir el portal web
        binding.btnOpenWeb.setOnClickListener { openWebPortal() }
    }
    
    /**
     * Abre la pantalla de "Información de la Aplicación" específica de Lector Yape.
     * Aquí el usuario puede acceder a los 3 puntitos para "Permitir ajustes restringidos".
     */
    private fun openAppInfoSettings() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = android.net.Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error abriendo App Info: ${e.message}")
            android.widget.Toast.makeText(this, "No se pudo abrir la configuración", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    /**
     * Configura el switch de notificación persistente / servicio activo
     */
    private fun setupNotificationSwitch() {
        val showNotification = prefs.getBoolean(
            YapeNotificationListenerService.PREF_SHOW_NOTIFICATION,
            false
        )
        binding.switchPersistentNotification.isChecked = showNotification
        
        binding.switchPersistentNotification.setOnCheckedChangeListener { _, isChecked ->
            val message = if (isChecked) {
                "✅ Activando servicio..."
            } else {
                "🔕 Desactivando servicio..."
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            
            lifecycleScope.launch(Dispatchers.IO) {
                prefs.edit()
                    .putBoolean(YapeNotificationListenerService.PREF_SHOW_NOTIFICATION, isChecked)
                    .commit()
                
                withContext(Dispatchers.Main) {
                    // Broadcast con paquete explícito para garantizar entrega en Android 14+/OEMs
                    val toggleIntent = Intent(YapeNotificationListenerService.ACTION_TOGGLE_NOTIFICATION)
                    toggleIntent.setPackage(packageName)
                    sendBroadcast(toggleIntent)
                    
                    if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        try {
                            requestServiceRebindIfNeeded()
                            android.util.Log.d("MainActivity", "⚡ Switch ON: Forzando Rebind del servicio")
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Error intentando Rebind: ${e.message}")
                        }
                    }
                }
            }
            
            android.util.Log.d("MainActivity", "Switch cambiado: ${if (isChecked) "ON" else "OFF"}")
        }
    }

    /** Actualiza el contador de notificaciones y la suma en Soles en la UI */
    private fun updateNotificationCount() {
        lifecycleScope.launch {
            try {
                // Obtener conteo y sumatoria general 
                // Para no hacer que la app se vuelva pesada pidiendo Firebase a cada rato, 
                // contamos los valores usando el storage local que está siempre sincronizado
                val (count, sum) = withContext(Dispatchers.IO) { calculateLocalYapeosTotals() }

                val formattedSum = String.format(Locale.getDefault(), "S/ %.2f", sum)
                val sumTextView = findViewById<android.widget.TextView>(R.id.tvTransactionSum)
                val countTextView = findViewById<android.widget.TextView>(R.id.tvTransactionCount)
                val totalCountTextView = findViewById<android.widget.TextView>(R.id.tvTransactionTotalCount)

                // Suma y conteo local de HOY
                sumTextView?.text = formattedSum
                countTextView?.text = "$count Yapeos de Hoy"
                
                // Conteo Histórico Global en la Nube (Firebase Aggregate Query)
                val totalCount = withContext(Dispatchers.IO) { firebaseUploader.countUserYapeos() }
                totalCountTextView?.text = "Histórico: $totalCount Yapeos en la nube"

                android.util.Log.d(
                        "MainActivity",
                        "📊 Hero Section: Hoy -> $count ($formattedSum) | Histórico -> $totalCount"
                )
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error al actualizar contador: ${e.message}", e)
                val sumTextView = findViewById<android.widget.TextView>(R.id.tvTransactionSum)
                val countTextView = findViewById<android.widget.TextView>(R.id.tvTransactionCount)
                val totalCountTextView = findViewById<android.widget.TextView>(R.id.tvTransactionTotalCount)
                sumTextView?.text = "S/ 0.00"
                countTextView?.text = "0 Yapeos de Hoy"
                totalCountTextView?.text = "Histórico: 0 Yapeos en la nube"
            }
        }
    }
    
    private fun calculateLocalYapeosTotals(): Pair<Int, Double> {
        return try {
            val fileContent = storage.getAllNotificationsAsText()
            if (fileContent.isBlank()) return Pair(0, 0.0)

            val lines = fileContent.split("\n").filter { it.isNotBlank() }
            if (lines.size <= 1) return Pair(0, 0.0) // solo hay cabecera

            // Obtener fecha de hoy para filtrar (DEBE COINCIDIR CON DateFormatter 'yyyy-MM-dd')
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val todayStr = sdf.format(java.util.Date())

            var countToday = 0
            var sumToday = 0.0

            // Omitir cabecera (lines[0])
            for (i in 1 until lines.size) {
                // Formato CSV local: fecha,nombre,monto,codigoSeguridad
                val parts = lines[i].split(",")
                if (parts.size >= 3) {
                    val fechaHoraStr = parts[0]
                    if (fechaHoraStr.startsWith(todayStr)) {
                        countToday++
                        val amount = parts[2].toDoubleOrNull() ?: 0.0
                        sumToday += amount
                    }
                }
            }
            Pair(countToday, sumToday)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error procesando CSV local", e)
            Pair(0, 0.0)
        }
    }



    /** Verifica si el servicio de notificaciones está habilitado */
    private fun checkNotificationPermission() {
        val isEnabled = isNotificationServiceEnabled()

        if (isEnabled) {
            binding.tvPermissionStatus.text = "Servicio con permiso "
            binding.statusIndicator.backgroundTintList =
                    ContextCompat.getColorStateList(this, android.R.color.holo_green_dark)
            binding.btnEnableNotifications.isEnabled = false
            binding.btnEnableNotifications.text = "Acceso Habilitado"
            binding.btnEnableNotifications.alpha = 0.6f
            binding.cardRestrictedSettings.isVisible = false

            // Activar la animación de pulso
            val pulseAnim = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.pulse_animation)
            binding.statusIndicator.startAnimation(pulseAnim)
        } else {
            binding.tvPermissionStatus.text = "Servicio sin permiso "
            binding.statusIndicator.backgroundTintList =
                    ContextCompat.getColorStateList(this, android.R.color.holo_red_dark)
            binding.btnEnableNotifications.isEnabled = true
            binding.btnEnableNotifications.text = "⚙Habilitar Acceso a Notificaciones"
            binding.btnEnableNotifications.alpha = 1.0f
            binding.cardRestrictedSettings.isVisible = true

            // Detener animación si no hay permiso
            binding.statusIndicator.clearAnimation()
        }
    }

    /** Verifica si nuestro NotificationListenerService está habilitado */
    private fun isNotificationServiceEnabled(): Boolean {
        val enabledListeners =
                Settings.Secure.getString(contentResolver, "enabled_notification_listeners")

        if (enabledListeners.isNullOrEmpty()) {
            return false
        }

        val packageName = packageName
        return enabledListeners.contains(packageName)
    }

    /** abrir los settings */
    private fun openNotificationSettings() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)

            Toast.makeText(this, "Busca 'Lector Yape' y activa el switch", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error al abrir configuración: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
        }
    }

    /**
     * Solicita la reconexión del servicio de notificaciones si está desconectado Esto asegura que
     * el servicio esté activo cada vez que el usuario abre la app
     */
    @RequiresApi(Build.VERSION_CODES.N)
    private fun requestServiceRebindIfNeeded() {
        try {
            val componentName =
                    android.content.ComponentName(this, YapeNotificationListenerService::class.java)
            NotificationListenerService.requestRebind(componentName)
            android.util.Log.d("MainActivity", "🔄 Solicitud de reconexión del servicio enviada")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "❌ Error al solicitar reconexión: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Limpiar el receiver del AppUpdater para evitar memory leaks
        if (::appUpdater.isInitialized) {
            appUpdater.cleanup()
        }
    }

}
