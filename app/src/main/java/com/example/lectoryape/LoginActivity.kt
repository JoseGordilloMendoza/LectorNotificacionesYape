package com.example.lectoryape

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import com.example.lectoryape.auth.GoogleAuthManager
import com.example.lectoryape.databinding.ActivityLoginBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException

class LoginActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityLoginBinding
    private lateinit var authManager: GoogleAuthManager
    
    companion object {
        private const val TAG = "LoginActivity"
    }
    
    // Launcher para el resultado de Google Sign-In
    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleSignInResult(result.data)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Forzar modo claro (light mode)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        authManager = GoogleAuthManager(this)
        
        // Si ya está logueado, ir directo a MainActivity
        if (authManager.isSignedIn()) {
            navigateToMain()
            return
        }
        
        setupUI()
    }
    
    private fun setupUI() {
        binding.btnGoogleSignIn.setOnClickListener {
            signIn()
        }
    }
    
    /**
     * Inicia el flujo de Google Sign-In
     */
    private fun signIn() {
        showLoading(true)
        
        try {
            val signInIntent = authManager.getSignInIntent()
            signInLauncher.launch(signInIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar sign-in: ${e.message}", e)
            showLoading(false)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Maneja el resultado del flujo de Google Sign-In
     */
    private fun handleSignInResult(data: Intent?) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            
            Log.d(TAG, "Sign-in exitoso: ${account.email}")
            
            // Login exitoso, navegar a MainActivity
            Toast.makeText(this, "✅ Sesión iniciada: ${account.email}", Toast.LENGTH_SHORT).show()
            navigateToMain()
            
        } catch (e: ApiException) {
            Log.e(TAG, "Error en sign-in. Code: ${e.statusCode}", e)
            showLoading(false)
            
            when (e.statusCode) {
                12501 -> {
                    // Usuario canceló
                    Toast.makeText(this, "Inicio de sesión cancelado", Toast.LENGTH_SHORT).show()
                }
                10 -> {
                    // Configuración incorrecta
                    Toast.makeText(
                        this,
                        "Error de configuración. Verifica el Client ID y SHA-1",
                        Toast.LENGTH_LONG
                    ).show()
                }
                else -> {
                    Toast.makeText(
                        this,
                        "Error al iniciar sesión: ${e.statusCode}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inesperado: ${e.message}", e)
            showLoading(false)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
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
