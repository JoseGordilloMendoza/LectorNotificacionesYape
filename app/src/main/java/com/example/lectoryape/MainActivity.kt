package com.example.lectoryape

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.example.lectoryape.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        checkNotificationPermission()
    }
    
    override fun onResume() {
        super.onResume()
        // Verificar permiso cada vez que la app regresa al foreground
        checkNotificationPermission()
    }
    
    private fun setupUI() {
        // Botón para abrir configuración de notificaciones
        binding.btnEnableNotifications.setOnClickListener {
            openNotificationSettings()
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
            binding.btnEnableNotifications.text = ":V Acceso Habilitado"
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
}