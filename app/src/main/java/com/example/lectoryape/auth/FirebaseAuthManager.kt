package com.example.lectoryape.auth

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.lectoryape.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

/**
 * Maneja la autenticación REAL con Firebase y Google
 */
class FirebaseAuthManager(private val context: Context) {

    companion object {
        private const val TAG = "FirebaseAuthManager"
        // Este ID suele estar en google-services.json bajo "oauth_client" con type 3
        // Pero Android lo genera automáticamente como recurso.
    }

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val googleSignInClient: GoogleSignInClient

    init {
        // Configurar Google Sign In
        // IMPORTANTE: requestIdToken es vital para Firebase
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(context, gso)
    }

    /**
     * Intent para lanzar la actividad de Google Sign In
     */
    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    /**
     * Procesa el resultado del intent de Google y autentica en Firebase
     */
    suspend fun firebaseAuthWithGoogle(data: Intent?): FirebaseUser? {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            
            Log.d(TAG, "Google Sign In exitoso: ${account.email}")
            
            val idToken = account.idToken
            if (idToken == null) {
                Log.e(TAG, "ID Token es nulo! Revisa el google-services.json")
                return null
            }

            val credential = GoogleAuthProvider.getCredential(idToken, null)
            
            // Autenticar en Firebase
            val authResult = auth.signInWithCredential(credential).await()
            val user = authResult.user
            
            Log.d(TAG, "Firebase Auth exitoso. UID: ${user?.uid}")
            return user

        } catch (e: Exception) {
            Log.e(TAG, "Error en autenticación: ${e.message}", e)
            throw e
        }
    }

    /**
     * Usuario actual de Firebase
     */
    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }
    
    fun isSignedIn(): Boolean {
        return auth.currentUser != null
    }

    fun getUserEmail(): String? {
        return auth.currentUser?.email
    }

    /**
     * Cerrar sesión completa
     */
    fun signOut() {
        // Sign out from Firebase
        auth.signOut()
        // Sign out from Google (para que pueda elegir cuenta de nuevo)
        googleSignInClient.signOut()
    }
}
