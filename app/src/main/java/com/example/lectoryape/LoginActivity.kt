package com.example.lectoryape

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.lectoryape.auth.SupabaseAuthManager
import com.example.lectoryape.databinding.ActivityLoginBinding
import com.example.lectoryape.network.RetrofitClient
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityLoginBinding
    private lateinit var supabaseAuthManager: SupabaseAuthManager
    private lateinit var googleSignInClient: GoogleSignInClient
    private var selectedMode: String = MODE_POS
    
    companion object {
        private const val TAG = "LoginActivity"
        private const val RC_SIGN_IN = 9001
        
        const val PREF_APP_MODE = "app_mode"
        const val MODE_POS = "modo_pos"
        const val MODE_TESIS = "modo_tesis"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Forzar modo claro (light mode)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Inicializar manager de Supabase
        supabaseAuthManager = SupabaseAuthManager()
        

        
        // Configurar Cliente de Google Sign-In Nativo
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
        
        // Si ya está logueado en Supabase, ir directo a MainActivity
        if (supabaseAuthManager.isUserSignedIn()) {
            Log.d(TAG, "Usuario ya logueado en Supabase")
            navigateToMain()
            return
        }
        
        setupUI()
    }
    
    private fun setupUI() {
        // Restaurar selección previa si existe
        val prefs = getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        selectedMode = prefs.getString(PREF_APP_MODE, MODE_POS) ?: MODE_POS
        updateCardSelection()

        binding.cardModePos.setOnClickListener {
            selectedMode = MODE_POS
            updateCardSelection()
        }

        binding.cardModeTesis.setOnClickListener {
            selectedMode = MODE_TESIS
            updateCardSelection()
        }

        binding.btnGoogleSignIn.setOnClickListener {
            // Guardar el modo seleccionado
            prefs.edit().putString(PREF_APP_MODE, selectedMode).apply()
            
            performGoogleSignIn()
        }
    }

    private fun updateCardSelection() {
        val tealColor = getColor(R.color.kaja_teal)
        val tealDarkColor = getColor(R.color.kaja_teal_dark)
        val grayColor = android.graphics.Color.parseColor("#9E9E9E")
        val lightGrayColor = android.graphics.Color.parseColor("#E0E0E0")
        val bgLightColor = getColor(R.color.kaja_bg_light)
        val whiteColor = getColor(R.color.white)

        if (selectedMode == MODE_POS) {
            // Card POS Activo
            binding.cardModePos.strokeWidth = 4
            binding.cardModePos.strokeColor = tealColor
            binding.cardModePos.cardElevation = 8f
            binding.cardModePos.setCardBackgroundColor(whiteColor)
            // TextViews del card no son accesibles directamente por binding si no tienen ID,
            // pero añadimos IDs en el XML para cambiar los colores.
            binding.ivIconPos.setColorFilter(tealColor)
            binding.tvTitlePos.setTextColor(tealDarkColor)
            
            // Card Tesis Inactivo
            binding.cardModeTesis.strokeWidth = 2
            binding.cardModeTesis.strokeColor = lightGrayColor
            binding.cardModeTesis.cardElevation = 0f
            binding.cardModeTesis.setCardBackgroundColor(bgLightColor)
            binding.ivIconTesis.setColorFilter(grayColor)
            binding.tvTitleTesis.setTextColor(grayColor)
            binding.tvSubTesis.setTextColor(grayColor)
        } else {
            // Card Tesis Activo
            binding.cardModeTesis.strokeWidth = 4
            binding.cardModeTesis.strokeColor = tealColor
            binding.cardModeTesis.cardElevation = 8f
            binding.cardModeTesis.setCardBackgroundColor(whiteColor)
            binding.ivIconTesis.setColorFilter(tealColor)
            binding.tvTitleTesis.setTextColor(tealDarkColor)
            binding.tvSubTesis.setTextColor(tealColor)

            // Card POS Inactivo
            binding.cardModePos.strokeWidth = 2
            binding.cardModePos.strokeColor = lightGrayColor
            binding.cardModePos.cardElevation = 0f
            binding.cardModePos.setCardBackgroundColor(bgLightColor)
            binding.ivIconPos.setColorFilter(grayColor)
            binding.tvTitlePos.setTextColor(grayColor)
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
                            val prefs = getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                            val mode = prefs.getString(PREF_APP_MODE, MODE_POS)
                            
                            if (mode == MODE_POS) {
                                // Modo Empresa POS: Verificar en Django
                                binding.tvStatus.text = "Verificando empresa..."
                                try {
                                    val response = RetrofitClient.api.getBootstrap()
                                    if (response.isSuccessful) {
                                        val bootstrap = response.body()
                                        if (bootstrap?.requiresOnboarding == true) {
                                            showLoading(false)
                                            Toast.makeText(this@LoginActivity, "Debes crear tu empresa en la web primero", Toast.LENGTH_LONG).show()
                                            supabaseAuthManager.signOut()
                                            googleSignInClient.signOut()
                                        } else {
                                            // Guardar el tenantId en SharedPreferences para usarlo en el servicio
                                            val firstTenantId = bootstrap?.memberships?.firstOrNull()?.tenant?.id
                                            if (firstTenantId != null) {
                                                prefs.edit().putString("tenant_id", firstTenantId).apply()
                                            }
                                            Toast.makeText(this@LoginActivity, "✅ Sesión POS Exitosa", Toast.LENGTH_SHORT).show()
                                            navigateToMain()
                                        }
                                    } else {
                                        showLoading(false)
                                        Toast.makeText(this@LoginActivity, "Error del servidor: ${response.code()}", Toast.LENGTH_SHORT).show()
                                        navigateToMain()
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error conectando al backend Django", e)
                                    Toast.makeText(this@LoginActivity, "Backend local no responde, usando offline", Toast.LENGTH_SHORT).show()
                                    navigateToMain()
                                }
                            } else {
                                // Modo Microempresa Tesis: Omitir Django
                                Toast.makeText(this@LoginActivity, "✅ Sesión Tesis Exitosa", Toast.LENGTH_SHORT).show()
                                navigateToMain()
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
