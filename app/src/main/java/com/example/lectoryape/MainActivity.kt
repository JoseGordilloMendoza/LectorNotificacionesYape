package com.example.lectoryape

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import com.example.lectoryape.auth.GoogleAuthManager
import com.example.lectoryape.databinding.ActivityMainBinding
import com.example.lectoryape.firebase.FirebaseUploader
import com.example.lectoryape.models.YapeDisplayItem
import com.example.lectoryape.service.YapeNotificationListenerService
import com.example.lectoryape.storage.YapeNotificationStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var storage: YapeNotificationStorage
    private lateinit var authManager: GoogleAuthManager
    private lateinit var firebaseUploader: FirebaseUploader
    private lateinit var yapeosAdapter: YapeosAdapter
    
    // BroadcastReceiver para escuchar cuando se guardan notificaciones
    private val notificationReceiver = object : BroadcastReceiver() {
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
        
        //user esta logged
        authManager = GoogleAuthManager(this)
        if (!authManager.isSignedIn()) {
            navigateToLogin()
            return
        }
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        storage = YapeNotificationStorage(this)
        firebaseUploader = FirebaseUploader(this)
        
        setupUI()
        
        try {
            android.util.Log.d("MainActivity", "🔧 Inicializando RecyclerView...")
            setupRecyclerView()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "❌ Error al inicializar RecyclerView: ${e.message}", e)
        }
        
        displayUserInfo()
        checkNotificationPermission()
        updateNotificationCount()
        
        try {
            loadYapeos()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "❌ Error al cargar yapeos: ${e.message}", e)
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                logout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun displayUserInfo() {
        val userEmail = authManager.getUserEmail() ?: "Usuario"
        supportActionBar?.subtitle = userEmail
    }
    
    private fun logout() {
        lifecycleScope.launch {
            authManager.signOut {
                navigateToLogin()
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
        binding.btnEnableNotifications.setOnClickListener {
            openNotificationSettings()
        }
        
        // Botón para exportar CSV
        binding.btnExportCsv.setOnClickListener {
            exportCsv()
        }
        
        // Botón para ver CSV
        binding.btnViewCsv.setOnClickListener {
            viewCsv()
        }
        
        // Botón para limpiar datos
        binding.btnClearData.setOnClickListener {
            confirmClearData()
        }
    }
    
    /**
     * Actualiza el contador de notificaciones en la UI (desde Firebase)
     */
    private fun updateNotificationCount() {
        lifecycleScope.launch {
            try {
                // Obtener conteo desde Firebase (filtrado por usuario)
                val count = withContext(Dispatchers.IO) {
                    firebaseUploader.getUserYapeos().size
                }
                
                binding.tvTransactionCount.text = count.toString()
                
                // Mostrar/ocultar botones según haya datos
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
                
                android.util.Log.d("MainActivity", "📊 Contador actualizado: $count yapeos en Firebase")
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error al actualizar contador: ${e.message}", e)
                // Fallback a conteo local si falla Firebase
                val count = storage.getNotificationCount()
                binding.tvTransactionCount.text = count.toString()
            }
        }
    }
    
    /**
     * Exporta el CSV usando el diálogo de compartir de Android
     */
    private fun exportCsv() {
        if (!storage.fileExists()) {
            Toast.makeText(this, "No hay datos para exportar", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val csvFile = File(storage.getFilePath())
            val uri: Uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                csvFile
            )
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Notificaciones de Yape")
                putExtra(Intent.EXTRA_TEXT, "Archivo CSV con ${storage.getNotificationCount()} notificaciones de Yape")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(shareIntent, "Exportar CSV"))
            
        } catch (e: Exception) {
            Toast.makeText(this, "Error al exportar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Muestra el contenido del CSV en un diálogo
     */
    private fun viewCsv() {
        if (!storage.fileExists()) {
            Toast.makeText(this, "No hay datos para ver", Toast.LENGTH_SHORT).show()
            return
        }
        
        val content = storage.getAllNotificationsAsText()
        val lines = content.lines()
        
        // Mostrar solo las primeras líneas para no saturar la UI
        val preview = if (lines.size > 20) {
            lines.take(20).joinToString("\n") + "\n\n... (${lines.size - 20} líneas más)"
        } else {
            content
        }
        
        AlertDialog.Builder(this)
            .setTitle("📄 Contenido del CSV (${storage.getNotificationCount()} notificaciones)")
            .setMessage(preview)
            .setPositiveButton("Cerrar", null)
            .setNeutralButton("Exportar") { _, _ ->
                exportCsv()
            }
            .show()
    }
    
    /**
     * Confirma antes de limpiar todos los datos
     */
    private fun confirmClearData() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Confirmar")
            .setMessage("¿Estás seguro de eliminar todas las ${storage.getNotificationCount()} notificaciones guardadas?\n\nEsta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                clearData()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    /**
     * Elimina todos los datos
     */
    private fun clearData() {
        val success = storage.clearAll()
        if (success) {
            Toast.makeText(this, "✅ Datos eliminados", Toast.LENGTH_SHORT).show()
            updateNotificationCount()
        } else {
            Toast.makeText(this, "❌ Error al eliminar datos", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Verifica si el servicio de notificaciones está habilitado
     */
    private fun checkNotificationPermission() {
        val isEnabled = isNotificationServiceEnabled()
        
        if (isEnabled) {
            // if para cuando este habilitado
            binding.tvPermissionStatus.text = "Servicio con permiso "
            binding.statusIndicator.backgroundTintList = 
                ContextCompat.getColorStateList(this, android.R.color.holo_green_dark)
            binding.btnEnableNotifications.isEnabled = false
            binding.btnEnableNotifications.text = "Acceso Habilitado"
            binding.btnEnableNotifications.alpha = 0.6f
            binding.instrucciones.isVisible = false
        } else {
            // if para cuando no este habilitado
            binding.tvPermissionStatus.text = "Servicio sin permiso "
            binding.statusIndicator.backgroundTintList = 
                ContextCompat.getColorStateList(this, android.R.color.holo_red_dark)
            binding.btnEnableNotifications.isEnabled = true
            binding.btnEnableNotifications.text = "⚙Habilitar Acceso a Notificaciones"
            binding.btnEnableNotifications.alpha = 1.0f
        }
    }
    
    /**
     * Verifica si nuestro NotificationListenerService está habilitado
     */
    private fun isNotificationServiceEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        )
        
        if (enabledListeners.isNullOrEmpty()) {
            return false
        }
        
        val packageName = packageName
        return enabledListeners.contains(packageName)
    }
    
    /**
     abrir los settings
     */
    private fun openNotificationSettings() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
            
            Toast.makeText(
                this,
                "Busca 'Lector Yape' y activa el switch",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Error al abrir configuración: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
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
                
                val yapeos = withContext(Dispatchers.IO) {
                    firebaseUploader.getUserYapeos()
                }
                
                android.util.Log.d("MainActivity", "📊 Yapeos recibidos: ${yapeos.size}")
                yapeos.forEach { data ->
                    android.util.Log.d("MainActivity", "  - ${data["userEmail"]} | ${data["text"]}")
                }

                val displayItems = yapeos.map { data ->
                    val timestamp = data["timestamp"] as? Long ?: 0L
                    val text = data["text"] as? String ?: ""

                    YapeDisplayItem(
                        monto = com.example.lectoryape.utils.DateFormatter.extractMonto(text),
                        texto = text,
                        fecha = com.example.lectoryape.utils.DateFormatter.formatTimestamp(timestamp),
                        timestamp = timestamp
                    )
                }

                yapeosAdapter.updateYapeos(displayItems)
                
                android.util.Log.d("MainActivity", "✅ RecyclerView actualizado con ${displayItems.size} items")

            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "❌ Error al cargar yapeos: ${e.message}", e)
                Toast.makeText(this@MainActivity, "Error al cargar yapeos: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}