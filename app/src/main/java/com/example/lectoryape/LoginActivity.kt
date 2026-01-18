package com.example.lectoryape

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import com.example.lectoryape.auth.AccountPickerManager
import com.example.lectoryape.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityLoginBinding
    private lateinit var accountManager: AccountPickerManager
    
    companion object {
        private const val TAG = "LoginActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Forzar modo claro (light mode)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        accountManager = AccountPickerManager(this)
        
        // Si ya está logueado, ir directo a MainActivity
        if (accountManager.isSignedIn()) {
            navigateToMain()
            return
        }
        
        setupUI()
    }
    
    private fun setupUI() {
        binding.btnGoogleSignIn.setOnClickListener {
            showAccountPicker()
        }
    }
    
    /**
     * Muestra el selector de cuentas Google
     */
    private fun showAccountPicker() {
        showLoading(true)
        
        try {
            accountManager.showAccountPicker(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error al mostrar Account Picker: ${e.message}", e)
            showLoading(false)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Maneja el resultado del Account Picker
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == AccountPickerManager.REQUEST_CODE_PICK_ACCOUNT) {
            showLoading(false)
            
            if (resultCode == Activity.RESULT_OK) {
                val email = accountManager.handleAccountPickerResult(data)
                
                if (email != null) {
                    Log.d(TAG, "Cuenta seleccionada: $email")
                    Toast.makeText(this, "✅ Sesión iniciada: $email", Toast.LENGTH_SHORT).show()
                    navigateToMain()
                } else {
                    Toast.makeText(this, "No se pudo obtener el email", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Usuario canceló
                Log.d(TAG, "Selección de cuenta cancelada")
                Toast.makeText(this, "Selección cancelada", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * Navega a MainActivity
     */
    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    /**
     * Muestra/oculta el indicador de carga
     */
    private fun showLoading(loading: Boolean) {
        binding.progressBar.isVisible = loading
        binding.tvStatus.isVisible = loading
        binding.btnGoogleSignIn.isEnabled = !loading
    }
}
