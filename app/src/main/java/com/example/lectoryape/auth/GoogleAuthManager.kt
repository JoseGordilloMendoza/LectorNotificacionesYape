package com.example.lectoryape.auth

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

/**
 * Maneja la autenticación con Google (solo para identificación de usuario)
 */
class GoogleAuthManager(private val context: Context) {
    
    companion object {
        private const val TAG = "GoogleAuthManager"
    }
    
    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        
        GoogleSignIn.getClient(context, gso)
    }
    
    /**
     * Obtiene el Intent para iniciar el flujo de Google Sign-In
     */
    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }
    
    /**
     * Verifica si el usuario ya está logueado
     */
    fun isSignedIn(): Boolean {
        return GoogleSignIn.getLastSignedInAccount(context) != null
    }
    
    /**
     * Obtiene la cuenta de Google actual
     */
    fun getCurrentAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }
    
    /**
     * Obtiene el email del usuario logueado
     */
    fun getUserEmail(): String? {
        return getCurrentAccount()?.email
    }
    
    /**
     * Obtiene el nombre del usuario logueado
     */
    fun getUserName(): String? {
        return getCurrentAccount()?.displayName
    }
    
    /**
     * Cierra sesión
     */
    suspend fun signOut(onComplete: () -> Unit) {
        try {
            googleSignInClient.signOut().addOnCompleteListener {
                Log.d(TAG, "Sesión cerrada exitosamente")
                onComplete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al cerrar sesión: ${e.message}", e)
            onComplete()
        }
    }
}
