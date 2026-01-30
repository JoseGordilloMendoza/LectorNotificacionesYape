package com.example.lectoryape

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.lectoryape.auth.FirebaseAuthManager
import com.example.lectoryape.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

import com.example.lectoryape.firebase.FirebaseUploader // Importar

class LoginActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityLoginBinding
    private lateinit var authManager: FirebaseAuthManager
    private lateinit var firebaseUploader: FirebaseUploader // Nuevo
    
    companion object {
        private const val TAG = "LoginActivity"
        private const val RC_SIGN_IN = 9001
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Forzar modo claro (light mode)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Inicializar managers
        authManager = FirebaseAuthManager(this)
        firebaseUploader = FirebaseUploader(this) // Nuevo
        
        // Si ya está logueado en Firebase, ir directo a MainActivity
        if (authManager.isSignedIn()) {
            Log.d(TAG, "Usuario ya logueado: ${authManager.getUserEmail()}")
            navigateToMain()
            return
        }
        
        setupUI()
    }
    
    private fun setupUI() {
        binding.btnGoogleSignIn.setOnClickListener {
            performGoogleSignIn()
        }
    }
    
    /**
     * Inicia el flujo de Google Sign-In REAL
     */
    private fun performGoogleSignIn() {
        showLoading(true)
        val signInIntent = authManager.getSignInIntent()
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }
    
    /**
     * Maneja el resultado del Google Sign-In
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == RC_SIGN_IN) {
            // Usar coroutine para operaciones asíncronas de Firebase
            lifecycleScope.launch {
                try {
                    val user = authManager.firebaseAuthWithGoogle(data)
                    
                    if (user != null) {
                        Log.d(TAG, "Login exitoso: ${user.email} (${user.uid})")
                        
                        // Guardar usuario en Firestore
                        binding.tvStatus.text = "Guardando perfil..."
                        firebaseUploader.saveUser(user)
                        
                        Toast.makeText(this@LoginActivity, "✅ Bienvenido ${user.displayName}", Toast.LENGTH_SHORT).show()
                        navigateToMain()
                    } else {
                        showLoading(false)
                        Toast.makeText(this@LoginActivity, "Fallo en autenticación", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error en login", e)
                    showLoading(false)
                    Toast.makeText(this@LoginActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    private fun showLoading(loading: Boolean) {
        binding.progressBar.isVisible = loading
        binding.tvStatus.isVisible = loading
        binding.btnGoogleSignIn.isEnabled = !loading
        
        if (loading) {
            binding.tvStatus.text = "Conectando con Google..."
        }
    }
}
