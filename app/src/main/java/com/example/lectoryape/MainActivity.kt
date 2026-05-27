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

import com.example.lectoryape.auth.SupabaseAuthManager
import com.example.lectoryape.adapters.TesisTransactionAdapter
import com.example.lectoryape.databinding.ActivityMainBinding
import com.example.lectoryape.repository.FakeTesisRepository
import com.example.lectoryape.service.YapeNotificationListenerService
import com.example.lectoryape.storage.YapeNotificationStorage
import com.example.lectoryape.tesis.TesisOwnerActivity
import com.example.lectoryape.tesis.TesisQueueActivity
import com.example.lectoryape.tesis.TesisShiftActivity
import com.example.lectoryape.tesis.TesisTeamActivity
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
    private lateinit var supabaseAuthManager: SupabaseAuthManager
    private lateinit var appUpdater: com.example.lectoryape.utils.AppUpdater
    private lateinit var transactionAdapter: com.example.lectoryape.adapters.TransactionAdapter
    private lateinit var tesisTransactionAdapter: TesisTransactionAdapter
    
    // SharedPreferences para guardar la preferencia del switch
    private val prefs by lazy {
        getSharedPreferences("yape_listener_prefs", Context.MODE_PRIVATE)
    }

    private val modePrefs by lazy {
        getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
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
        supabaseAuthManager = SupabaseAuthManager()
        
        if (!supabaseAuthManager.isUserSignedIn()) {
            navigateToLogin()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configurar Toolbar como ActionBar
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        storage = YapeNotificationStorage(this)
        appUpdater = com.example.lectoryape.utils.AppUpdater(this)

        // Verificar suscripción ANTES de inicializar la app
        checkSubscription()
    }

    /* Verificacion de suscripcion de user
    Si la prueba expiró o está inactiva, muestra un diálogo bloqueante.
     */
    private fun checkSubscription() {
        // En modo Tesis y Híbrido, saltamos la validación de suscripción local de Firebase por ahora.
        initializeApp()
    }

    private fun initializeApp() {
        setupUI()

        // Configurar RecyclerView
        transactionAdapter = com.example.lectoryape.adapters.TransactionAdapter()
        tesisTransactionAdapter = TesisTransactionAdapter()
        val rvTesis = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvTesisTransactions)
        rvTesis?.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        rvTesis?.adapter = tesisTransactionAdapter

        // Verificar el modo actual
        val mode = modePrefs.getString(com.example.lectoryape.LoginActivity.PREF_APP_MODE, com.example.lectoryape.LoginActivity.MODE_POS)
        val layoutModePos = findViewById<android.widget.LinearLayout>(R.id.layoutModePos)
        val layoutModeTesis = findViewById<android.widget.LinearLayout>(R.id.layoutModeTesis)

        if (mode == com.example.lectoryape.LoginActivity.MODE_TESIS) {
            layoutModePos?.visibility = android.view.View.GONE
            layoutModeTesis?.visibility = android.view.View.VISIBLE

            setupTesisMockUI()

            // Botón Compartir
            findViewById<android.view.View>(R.id.btnShareReport)?.setOnClickListener {
                shareDailyReport()
            }
        } else {
            layoutModePos?.visibility = android.view.View.VISIBLE
            layoutModeTesis?.visibility = android.view.View.GONE
        }

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

    private fun shareDailyReport() {
        lifecycleScope.launch(Dispatchers.IO) {
            val mode = modePrefs.getString(
                com.example.lectoryape.LoginActivity.PREF_APP_MODE,
                com.example.lectoryape.LoginActivity.MODE_POS
            )

            if (mode == com.example.lectoryape.LoginActivity.MODE_TESIS) {
                val transactions = FakeTesisRepository.getTransactions()
                val count = FakeTesisRepository.getTotalCount()
                val sum = FakeTesisRepository.getTotalAmount()
                val report = buildString {
                    appendLine("Reporte de Caja (Modo Tesis Demo)")
                    val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    appendLine("Fecha: ${sdf.format(java.util.Date())}")
                    appendLine("Negocio: ${FakeTesisRepository.getBusinessName()}")
                    appendLine("Total recaudado: S/ ${"%.2f".format(sum)}")
                    appendLine("Cantidad: $count operaciones")
                    appendLine()
                    appendLine("Detalle:")
                    transactions.forEach {
                        val description = it.description ?: "Sin descripcion"
                        appendLine("- ${it.puesto} - ${it.senderName} - S/ ${"%.2f".format(it.amount)} - ${it.status} - $description")
                    }
                    appendLine()
                    appendLine("Demo visual de KAJA Tesis")
                }

                withContext(Dispatchers.Main) {
                    shareTextReport(report)
                }
                return@launch
            }

            val (count, sum, transactions) = loadLocalTransactions()
            if (count == 0) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "No hay pagos hoy para reportar", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val report = buildString {
                appendLine("📊 *Reporte de Caja (Modo Tesis)*")
                val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                appendLine("🗓️ Fecha: ${sdf.format(java.util.Date())}")
                appendLine("💰 *Total Recaudado:* S/ ${"%.2f".format(sum)}")
                appendLine("📈 *Cantidad:* $count operaciones")
                appendLine()
                appendLine("🧾 *Detalle:*")
                transactions.forEach {
                    appendLine("• ${it.name} - S/ ${"%.2f".format(it.amount)} (${it.walletType})")
                }
                appendLine()
                appendLine("_Generado automáticamente por KAJA_")
            }

            withContext(Dispatchers.Main) {
                shareTextReport(report)
            }
        }
    }

    private fun shareTextReport(report: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, report)
            type = "text/plain"
            setPackage("com.whatsapp")
        }

        try {
            startActivity(sendIntent)
        } catch (e: Exception) {
            val fallbackIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, report)
                type = "text/plain"
            }
            startActivity(Intent.createChooser(fallbackIntent, "Compartir reporte con..."))
        }
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
        val title = when (navId) {
            R.id.nav_home     -> "HOLA"
            R.id.nav_updates  -> "NOVEDADES"
            R.id.nav_profile  -> "MI CUENTA"
            else              -> "KAJA"
        }
        // Escribir en el TextView custom del toolbar
        supportActionBar?.title = ""
        findViewById<android.widget.TextView>(R.id.tvToolbarTitle)?.text = title
    }

    private fun setupProfileScreen() {
        val ivPhoto        = findViewById<android.widget.ImageView>(R.id.ivProfilePhoto)
        val tvName         = findViewById<android.widget.TextView>(R.id.tvProfileName)
        val tvEmail        = findViewById<android.widget.TextView>(R.id.tvProfileEmail)
        val tvStatus       = findViewById<android.widget.TextView>(R.id.tvProfileStatus)
        val tvRole         = findViewById<android.widget.TextView>(R.id.tvProfileRole)
        val btnLogout      = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnProfileLogout)

        tvName.text  = "Usuario"
        tvEmail.text = "—"
        tvStatus.text = "Modo Híbrido"
        tvRole.text = "Activo"

        // Botón Cerrar Sesión
        btnLogout.setOnClickListener {
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
        lifecycleScope.launch {
            try {
                supabaseAuthManager.signOut()
                Toast.makeText(this@MainActivity, "Sesión cerrada", Toast.LENGTH_SHORT).show()
                navigateToLogin()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error al cerrar sesión: ${e.message}", e)
                Toast.makeText(this@MainActivity, "Error al cerrar sesión", Toast.LENGTH_SHORT).show()
            }
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
                val mode = modePrefs.getString(
                    com.example.lectoryape.LoginActivity.PREF_APP_MODE,
                    com.example.lectoryape.LoginActivity.MODE_POS
                )

                if (mode == com.example.lectoryape.LoginActivity.MODE_TESIS) {
                    updateTesisMockDashboard()
                    return@launch
                }

                // Obtener conteo y sumatoria general 
                // Para no hacer que la app se vuelva pesada pidiendo Firebase a cada rato, 
                // contamos los valores usando el storage local que está siempre sincronizado
                val (count, sum, list) = withContext(Dispatchers.IO) { loadLocalTransactions() }

                val formattedSum = String.format(Locale.getDefault(), "S/ %.2f", sum)
                val sumTextView = findViewById<android.widget.TextView>(R.id.tvTransactionSum)
                val countTextView = findViewById<android.widget.TextView>(R.id.tvTransactionCount)
                val totalCountTextView = findViewById<android.widget.TextView>(R.id.tvTransactionTotalCount)

                // Suma y conteo local de HOY
                sumTextView?.text = formattedSum
                countTextView?.text = "$count Yapeos de Hoy"
                
                // Conteo Histórico Global
                totalCountTextView?.text = "Histórico Nube (Modo Activo)"

                android.util.Log.d(
                        "MainActivity",
                        "📊 Hero Section: Hoy -> $count ($formattedSum) | Histórico -> (Nube Activa)"
                )
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error al actualizar contador: ${e.message}", e)
            }
        }
    }

    private fun setupTesisMockUI() {
        findViewById<android.widget.TextView>(R.id.tvTesisBusinessName)?.text =
            FakeTesisRepository.getBusinessName()
        findViewById<android.widget.TextView>(R.id.tvTesisOwnerWallet)?.text =
            FakeTesisRepository.getOwnerWallet()

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnTesisAddDescription)?.setOnClickListener {
            Toast.makeText(this, "Demo: aqui el ayudante agregaria una descripcion de venta", Toast.LENGTH_SHORT).show()
        }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnTesisConfirmPayment)?.setOnClickListener {
            Toast.makeText(this, "Demo: aqui se marcaria un pago como confirmado", Toast.LENGTH_SHORT).show()
        }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnTesisViewAlerts)?.setOnClickListener {
            Toast.makeText(this, "Demo: aqui verias pagos observados o sin descripcion", Toast.LENGTH_SHORT).show()
        }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnTesisOpenShift)?.setOnClickListener {
            startActivity(Intent(this, TesisShiftActivity::class.java))
        }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnTesisOpenQueue)?.setOnClickListener {
            startActivity(Intent(this, TesisQueueActivity::class.java))
        }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnTesisOpenOwner)?.setOnClickListener {
            startActivity(Intent(this, TesisOwnerActivity::class.java))
        }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnTesisOpenTeam)?.setOnClickListener {
            startActivity(Intent(this, TesisTeamActivity::class.java))
        }

        updateTesisMockDashboard()
    }

    private fun updateTesisMockDashboard() {
        val transactions = FakeTesisRepository.getTransactions()
        val totalAmount = FakeTesisRepository.getTotalAmount()
        val totalCount = FakeTesisRepository.getTotalCount()
        val observedCount = FakeTesisRepository.getObservedCount()
        val pendingCount = FakeTesisRepository.getPendingCount()
        val totalsByPuesto = FakeTesisRepository.getTotalsByPuesto()

        findViewById<android.widget.TextView>(R.id.tvTesisTransactionSum)?.text =
            String.format(Locale.getDefault(), "S/ %.2f", totalAmount)
        findViewById<android.widget.TextView>(R.id.tvTesisTransactionCount)?.text =
            "$totalCount COBROS REGISTRADOS HOY"
        findViewById<android.widget.TextView>(R.id.tvTesisLastPayment)?.text =
            FakeTesisRepository.getLastPaymentLabel()
        findViewById<android.widget.TextView>(R.id.tvTesisPendingCount)?.text =
            "$pendingCount por revisar"
        findViewById<android.widget.TextView>(R.id.tvTesisObservedCount)?.text =
            "$observedCount observados"

        val totals = listOf(
            R.id.tvTesisPuesto1Total,
            R.id.tvTesisPuesto2Total,
            R.id.tvTesisPuesto3Total
        )

        totals.forEachIndexed { index, viewId ->
            val value = totalsByPuesto.getOrNull(index)?.second ?: 0.0
            findViewById<android.widget.TextView>(viewId)?.text =
                String.format(Locale.getDefault(), "S/ %.2f", value)
        }

        if (transactions.isEmpty()) {
            findViewById<android.view.View>(R.id.tvTesisEmptyState)?.visibility = android.view.View.VISIBLE
            findViewById<android.view.View>(R.id.rvTesisTransactions)?.visibility = android.view.View.GONE
        } else {
            findViewById<android.view.View>(R.id.tvTesisEmptyState)?.visibility = android.view.View.GONE
            findViewById<android.view.View>(R.id.rvTesisTransactions)?.visibility = android.view.View.VISIBLE
            tesisTransactionAdapter.setTransactions(FakeTesisRepository.getRecentTransactions())
        }
    }
    
    private fun loadLocalTransactions(): Triple<Int, Double, List<com.example.lectoryape.models.YapeNotificationRaw>> {
        return try {
            val fileContent = storage.getAllNotificationsAsText()
            if (fileContent.isBlank()) return Triple(0, 0.0, emptyList())

            val lines = fileContent.split("\n").filter { it.isNotBlank() }
            if (lines.size <= 1) return Triple(0, 0.0, emptyList()) // solo hay cabecera

            // Obtener fecha de hoy para filtrar (DEBE COINCIDIR CON DateFormatter 'yyyy-MM-dd')
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val todayStr = sdf.format(java.util.Date())

            var countToday = 0
            var sumToday = 0.0
            val transactionsToday = mutableListOf<com.example.lectoryape.models.YapeNotificationRaw>()

            // Omitir cabecera (lines[0])
            for (i in 1 until lines.size) {
                // Formato CSV local: fecha,nombre,monto,codigoSeguridad
                val parts = lines[i].split(",")
                if (parts.size >= 4) {
                    val fechaHoraStr = parts[0] // e.g. "2026-05-24 14:30:00"
                    if (fechaHoraStr.startsWith(todayStr)) {
                        countToday++
                        val amount = parts[2].toDoubleOrNull() ?: 0.0
                        sumToday += amount
                        
                        // Recrear objeto dummy para el adapter
                        // Nota: el CSV no guarda el walletType, pero podemos inferirlo o dejarlo default para UI
                        val timeFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        val timestamp = try { timeFormat.parse(fechaHoraStr)?.time ?: 0L } catch(e:Exception){0L}
                        
                        transactionsToday.add(
                            com.example.lectoryape.models.YapeNotificationRaw(
                                title = "Yape",
                                name = parts[1].replace("\"", ""),
                                amount = amount,
                                timestamp = timestamp,
                                securityCode = parts[3].replace("\"", ""),
                                notificationId = i,
                                walletType = "YAPE" // Default (podría guardarse en el CSV en una version futura)
                            )
                        )
                    }
                }
            }
            Triple(countToday, sumToday, transactionsToday)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error procesando CSV local", e)
            Triple(0, 0.0, emptyList())
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
