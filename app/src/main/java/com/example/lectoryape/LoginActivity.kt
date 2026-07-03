package com.example.kajaapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.kajaapp.auth.SupabaseAuthManager
import com.example.kajaapp.auth.SupabaseManager
import com.example.kajaapp.databinding.ActivityLoginBinding
import com.example.kajaapp.network.RetrofitClient
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import io.github.jan.supabase.gotrue.SessionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var supabaseAuthManager: SupabaseAuthManager
    private lateinit var googleSignInClient: GoogleSignInClient

    companion object {
        private const val TAG = "LoginActivity"
        private const val RC_SIGN_IN = 9001

        const val PREF_APP_MODE = "app_mode"
        const val MODE_POS = "modo_pos"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supabaseAuthManager = SupabaseAuthManager()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Always POS mode
        getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            .edit().putString(PREF_APP_MODE, MODE_POS).apply()

        binding.btnGoogleSignIn.setOnClickListener {
            showLoading(true)
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_SIGN_IN)
        }

        // Esperar a que Supabase cargue la sesión del almacenamiento (async)
        // Si se llama currentSessionOrNull() de forma síncrona aquí, siempre
        // devuelve null porque la carga del disco aún no terminó.
        lifecycleScope.launch {
            val status = SupabaseManager.auth.sessionStatus
                .first { it !is SessionStatus.LoadingFromStorage }

            if (status is SessionStatus.Authenticated) {
                Log.d(TAG, "Sesión restaurada desde almacenamiento — saltando login")
                navigateToMain()
            }
            // Si no es Authenticated, la pantalla ya está visible con el botón habilitado
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            lifecycleScope.launch {
                try {
                    val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                    val account = task.getResult(ApiException::class.java)

                    val idToken = account.idToken
                    if (idToken == null) {
                        showLoading(false)
                        Toast.makeText(this@LoginActivity, "No se recibió el Token de Google", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    Log.d(TAG, "Google Sign-In exitoso, autenticando con Supabase...")
                    val supabaseSuccess = supabaseAuthManager.loginWithGoogleIdToken(idToken)

                    if (!supabaseSuccess) {
                        showLoading(false)
                        Toast.makeText(this@LoginActivity, "Fallo al conectar con Supabase", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    // Leer el token directamente de la sesión recién creada para
                    // evitar que el interceptor de Retrofit lo lea antes de que
                    // supabase-kt lo haya guardado en memoria.
                    val accessToken = SupabaseManager.auth.currentSessionOrNull()?.accessToken
                    if (accessToken == null) {
                        Log.e(TAG, "Sesión de Supabase no disponible después del login")
                        showLoading(false)
                        Toast.makeText(this@LoginActivity, "Error de sesión, intenta de nuevo", Toast.LENGTH_SHORT).show()
                        supabaseAuthManager.signOut()
                        return@launch
                    }

                    Log.d(TAG, "Token de Supabase obtenido, verificando empresa en backend...")
                    binding.tvStatus.text = "Verificando empresa..."

                    val prefs = getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                    try {
                        val response = RetrofitClient.api.getBootstrap("Bearer $accessToken")
                        if (response.isSuccessful) {
                            val bootstrap = response.body()
                            if (bootstrap?.requiresOnboarding == true) {
                                showLoading(false)
                                Toast.makeText(this@LoginActivity, "Debes crear tu empresa en la web primero", Toast.LENGTH_LONG).show()
                                supabaseAuthManager.signOut()
                                googleSignInClient.signOut()
                            } else {
                                val firstTenantId = bootstrap?.memberships?.firstOrNull()?.tenant?.id
                                if (firstTenantId != null) {
                                    val user = bootstrap.user
                                    val role = bootstrap.memberships.firstOrNull()?.role ?: "MEMBER"
                                    val tenantName = bootstrap.memberships.firstOrNull()?.tenant?.name?.takeIf { it.isNotBlank() } ?: "Negocio"
                                    val photoUrl = account.photoUrl?.toString() ?: ""
                                    
                                    val userName = account.displayName?.takeIf { it.isNotBlank() } 
                                        ?: user.firstName?.takeIf { it.isNotBlank() } 
                                        ?: "Usuario"
                                    
                                    prefs.edit()
                                        .putString("tenant_id", firstTenantId)
                                        .putString("user_name", userName)
                                        .putString("user_email", user.email)
                                        .putString("user_role", role)
                                        .putString("tenant_name", tenantName)
                                        .putString("user_photo_url", photoUrl)
                                        .apply()
                                    Log.d(TAG, "Perfil guardado: $userName - $tenantName")
                                } else {
                                    Log.w(TAG, "Bootstrap sin tenantId — notificaciones no serán enviadas al backend")
                                }
                                Toast.makeText(this@LoginActivity, "✅ Sesión iniciada", Toast.LENGTH_SHORT).show()
                                navigateToMain()
                            }
                        } else {
                            Log.e(TAG, "Backend respondió con error ${response.code()}: ${response.errorBody()?.string()}")
                            Toast.makeText(this@LoginActivity, "Error del servidor: ${response.code()}", Toast.LENGTH_SHORT).show()
                            navigateToMain()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Backend no alcanzable, entrando en modo offline: ${e.message}")
                        Toast.makeText(this@LoginActivity, "Sin conexión al servidor, modo offline", Toast.LENGTH_SHORT).show()
                        navigateToMain()
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
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun showLoading(loading: Boolean) {
        binding.progressBar.isVisible = loading
        binding.tvStatus.isVisible = loading
        binding.btnGoogleSignIn.isEnabled = !loading
        if (loading) binding.tvStatus.text = "Conectando con Supabase..."
    }
}
