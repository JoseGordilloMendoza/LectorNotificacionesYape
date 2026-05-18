package com.example.lectoryape

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.lectoryape.auth.FirebaseAuthManager
import com.example.lectoryape.auth.SupabaseAuthManager
import com.example.lectoryape.databinding.ActivityLoginBinding
import com.example.lectoryape.firebase.FirebaseUploader
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityLoginBinding
    private lateinit var supabaseAuthManager: SupabaseAuthManager
    private lateinit var authManager: FirebaseAuthManager
    private lateinit var firebaseUploader: FirebaseUploader
    private lateinit var googleSignInClient: GoogleSignInClient
    
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
        
        // Inicializar manager de Supabase
        supabaseAuthManager = SupabaseAuthManager()
        
        // Inicializar managers de Firebase
        authManager = FirebaseAuthManager(this)
        firebaseUploader = FirebaseUploader(this)
        
        // Configurar Cliente de Google Sign-In Nativo
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
        
        // Si ya está logueado en Supabase Y Firebase, ir directo a MainActivity
        if (supabaseAuthManager.isUserSignedIn() && authManager.isSignedIn()) {
            Log.d(TAG, "Usuario ya logueado en Supabase y Firebase")
            navigateToMain()
            return
        }
        
        // Si el estado es inconsistente (ej. logueado en Firebase pero no Supabase),
        // forzamos el cierre de sesión silencioso para que vuelva a pedir la cuenta
        if (authManager.isSignedIn() && !supabaseAuthManager.isUserSignedIn()) {
            authManager.signOut()
            googleSignInClient.signOut()
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
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }
    
    /**
     * Maneja el resultado del Google Sign-In
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == RC_SIGN_IN) {
            lifecycleScope.launch {
                try {
                    val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                    val account = task.getResult(ApiException::class.java)
                    
                    val idToken = account.idToken
                    if (idToken != null) {
                        Log.d(TAG, "Google Sign In nativo exitoso. Iniciando doble sesión...")
                        
                        // 1. Iniciar sesión en Supabase
                        val supabaseSuccess = supabaseAuthManager.loginWithGoogleIdToken(idToken)
                        
                        if (supabaseSuccess) {
                            // 2. Iniciar sesión en Firebase (para mantener compatibilidad temporal)
                            binding.tvStatus.text = "Sincronizando plataforma..."
                            val firebaseUser = authManager.firebaseAuthWithGoogle(data)
                            
                            if (firebaseUser != null) {
                                // 3. Guardar en Firestore (Compatibilidad)
                                firebaseUploader.saveUser(firebaseUser)
                                
                                Toast.makeText(this@LoginActivity, "✅ Sesión Híbrida Exitosa", Toast.LENGTH_SHORT).show()
                                navigateToMain()
                            } else {
                                showLoading(false)
                                Toast.makeText(this@LoginActivity, "Fallo al conectar con el servidor antiguo", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            showLoading(false)
                            Toast.makeText(this@LoginActivity, "Fallo al conectar con Supabase", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        showLoading(false)
                        Toast.makeText(this@LoginActivity, "No se recibió el Token de Google", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error en login", e)
                    showLoading(false)
                    Toast.makeText(this@LoginActivity, "Error: Cancelado o Fallido", Toast.LENGTH_LONG).show()
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
            binding.tvStatus.text = "Conectando con Supabase..."
        }
    }
}
