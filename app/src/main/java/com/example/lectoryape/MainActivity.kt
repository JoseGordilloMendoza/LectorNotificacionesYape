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
import com.example.lectoryape.adapters.YapeosAdapter
import com.example.lectoryape.auth.FirebaseAuthManager
import com.example.lectoryape.databinding.ActivityMainBinding
import com.example.lectoryape.firebase.FirebaseUploader
import com.example.lectoryape.models.YapeDisplayItem
import com.example.lectoryape.service.YapeNotificationListenerService
import com.example.lectoryape.storage.YapeNotificationStorage
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var storage: YapeNotificationStorage
    private lateinit var authManager: FirebaseAuthManager
    private lateinit var firebaseUploader: FirebaseUploader
    private lateinit var yapeosAdapter: YapeosAdapter
    
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

                    // Recargar lista de yapeos automáticamente
                    loadYapeos()
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

        setupUI()

        try {
            android.util.Log.d("MainActivity", "🔧 Inicializando RecyclerView...")
            setupRecyclerView()
        } catch (e: Exception) {
            android.util.Log.e(
                    "MainActivity",
                    "❌ Error al inicializar RecyclerView: ${e.message}",
                    e
            )
        }

        // displayUserInfo() call removed as it's now handled by Profile Dialog
        checkNotificationPermission()
        updateNotificationCount()
        setupNotificationSwitch()
        
        // Verificar y solicitar exención de optimización de batería
        checkBatteryOptimization()

        try {
            loadYapeos()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "❌ Error al cargar yapeos: ${e.message}", e)
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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_profile -> {
                showProfileDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showProfileDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_profile, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        // Referencias a vistas
        val ivPhoto = dialogView.findViewById<android.widget.ImageView>(R.id.ivProfilePhoto)
        val tvName = dialogView.findViewById<android.widget.TextView>(R.id.tvProfileName)
        val tvEmail = dialogView.findViewById<android.widget.TextView>(R.id.tvProfileEmail)
        val tvStatus = dialogView.findViewById<android.widget.TextView>(R.id.tvProfileStatus)
        val btnLogout = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnProfileLogout)
        
        // Cargar datos básicos de Auth (Usando nuestro Manager)
        val user = authManager.getCurrentUser()
        if (user != null) {
            tvName.text = user.displayName ?: "Usuario"
            tvEmail.text = user.email
            // Como no tenemos Glide, dejamos el icono por defecto
        }
        
        // Obtener estado de suscripción desde Firestore
        if (user != null) {
            tvStatus.text = "Cargando..."
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(user.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val subMap = document.get("subscription") as? Map<String, Any>
                        val status = subMap?.get("status") as? String ?: "Free"
                        val planName = subMap?.get("planName") as? String ?: "Básico"
                        
                        // Capitalizar primera letra
                        val statusDisplay = status.substring(0, 1).uppercase() + status.substring(1)
                        tvStatus.text = "$planName ($statusDisplay)"
                        
                        // Color según estado
                        if (status == "trial" || status == "active") {
                            tvStatus.setBackgroundColor(android.graphics.Color.parseColor("#4CAF50")) // Verde
                        } else {
                            tvStatus.setBackgroundColor(android.graphics.Color.parseColor("#D32F2F")) // Rojo
                        }
                    } else {
                        tvStatus.text = "Sin Plan"
                    }
                }
                .addOnFailureListener {
                    tvStatus.text = "Error al cargar"
                }
        } else {
             tvStatus.text = "No autenticado"
        }

        // Configurar Botón Salir
        btnLogout.setOnClickListener {
            dialog.dismiss()
            logout() // Reutilizamos el método logout existente que pide confirmación
        }
        
        dialog.show()
        // Fondo transparente necesario para el CardView
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
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


        // Botón para abrir configuración de notificaciones
        binding.btnEnableNotifications.setOnClickListener { openNotificationSettings() }

        // Botón para exportar CSV
        binding.btnExportCsv.setOnClickListener { exportCsv() }

        // Botón para ver CSV
        binding.btnViewCsv.setOnClickListener { viewCsv() }

        // Botón para limpiar datos
        binding.btnClearData.setOnClickListener { confirmClearData() }
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

    /** Actualiza el contador de notificaciones en la UI (desde Firebase) */
    private fun updateNotificationCount() {
        lifecycleScope.launch {
            try {
                // Obtener conteo desde Firebase (filtrado por usuario)
                val count = withContext(Dispatchers.IO) { firebaseUploader.getUserYapeos().size }

                binding.tvTransactionCount.text = count.toString()

                val hasData = count > 0
                binding.btnExportCsv.isEnabled = hasData
                binding.btnViewCsv.isEnabled = hasData
                binding.btnClearData.isEnabled = hasData

                if (!hasData) {
                    binding.btnExportCsv.alpha = 0.5f
                    binding.btnViewCsv.alpha = 0.5f
                    binding.btnClearData.alpha = 0.5f
                } else {
                    binding.btnExportCsv.alpha = 1.0f
                    binding.btnViewCsv.alpha = 1.0f
                    binding.btnClearData.alpha = 1.0f
                }

                android.util.Log.d(
                        "MainActivity",
                        "📊 Contador actualizado: $count yapeos en Firebase"
                )
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error al actualizar contador: ${e.message}", e)
                val count = storage.getNotificationCount()
                binding.tvTransactionCount.text = count.toString()
            }
        }
    }

    /** Exporta el CSV usando el diálogo de compartir de Android */
    private fun exportCsv() {
        if (!storage.fileExists()) {
            Toast.makeText(this, "No hay datos para exportar", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val csvFile = File(storage.getFilePath())
            val uri: Uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", csvFile)

            val shareIntent =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "Notificaciones de Yape")
                        putExtra(
                                Intent.EXTRA_TEXT,
                                "Archivo CSV con ${storage.getNotificationCount()} notificaciones de Yape"
                        )
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

            startActivity(Intent.createChooser(shareIntent, "Exportar CSV"))
        } catch (e: Exception) {
            Toast.makeText(this, "Error al exportar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /** Muestra el contenido del CSV en un diálogo */
    private fun viewCsv() {
        if (!storage.fileExists()) {
            Toast.makeText(this, "No hay datos para ver", Toast.LENGTH_SHORT).show()
            return
        }

        val content = storage.getAllNotificationsAsText()
        val lines = content.lines()

        val preview =
                if (lines.size > 20) {
                    lines.take(20).joinToString("\n") + "\n\n... (${lines.size - 20} líneas más)"
                } else {
                    content
                }

        AlertDialog.Builder(this)
                .setTitle("📄 Contenido del CSV (${storage.getNotificationCount()} notificaciones)")
                .setMessage(preview)
                .setPositiveButton("Cerrar", null)
                .setNeutralButton("Exportar") { _, _ -> exportCsv() }
                .show()
    }

    /** Confirma antes de limpiar todos los datos */
    private fun confirmClearData() {
        AlertDialog.Builder(this)
                .setTitle("⚠️ Confirmar")
                .setMessage(
                        "¿Estás seguro de eliminar todas las ${storage.getNotificationCount()} notificaciones guardadas?\n\nEsta acción no se puede deshacer."
                )
                .setPositiveButton("Eliminar") { _, _ -> clearData() }
                .setNegativeButton("Cancelar", null)
                .show()
    }

    /** Elimina todos los datos */
    private fun clearData() {
        val success = storage.clearAll()
        if (success) {
            Toast.makeText(this, "✅ Datos eliminados", Toast.LENGTH_SHORT).show()
            updateNotificationCount()
        } else {
            Toast.makeText(this, "❌ Error al eliminar datos", Toast.LENGTH_SHORT).show()
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
            binding.instrucciones.isVisible = false
        } else {
            binding.tvPermissionStatus.text = "Servicio sin permiso "
            binding.statusIndicator.backgroundTintList =
                    ContextCompat.getColorStateList(this, android.R.color.holo_red_dark)
            binding.btnEnableNotifications.isEnabled = true
            binding.btnEnableNotifications.text = "⚙Habilitar Acceso a Notificaciones"
            binding.btnEnableNotifications.alpha = 1.0f
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

    private fun setupRecyclerView() {
        yapeosAdapter = YapeosAdapter()
        binding.rvYapeos.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = yapeosAdapter
        }
    }
    
    private fun loadYapeos() {
        lifecycleScope.launch {
            try {
                android.util.Log.d("MainActivity", "🔍 Iniciando carga de yapeos...")

                val yapeos = withContext(Dispatchers.IO) { firebaseUploader.getUserYapeos() }

                val displayItems =
                        yapeos.map { data ->
                            // Leer campos del Firebase (Soporte para estructura Nueva y Antigua)
                            val name = (data["senderName"] as? String) ?: (data["text"] as? String) ?: "Desconocido"
                            
                            val amount = when(val a = data["amount"] ?: data["bigText"]) {
                                is Double -> a
                                is Long -> a.toDouble()
                                else -> 0.0
                            }
                            
                            val title = data["title"] as? String ?: "Yape"
                            val fechaStr = (data["fecha"] as? String) ?: (data["timestamp"] as? String) ?: ""

                            YapeDisplayItem(
                                monto = "S/ %.2f".format(java.util.Locale.US, amount), // Formatear como moneda
                                texto = "$name te envió un pago",  // Texto descriptivo
                                fecha = fechaStr,  // Ya viene formateado de Firebase
                                timestamp = 0L  // No necesitamos ordenar, Firebase ya lo hace
                            )
                        }

                yapeosAdapter.updateYapeos(displayItems)
                android.util.Log.d("MainActivity", "✅ RecyclerView actualizado con ${displayItems.size} yapeos")
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "❌ Error al cargar yapeos: ${e.message}", e)
            }
        }
    }
}
