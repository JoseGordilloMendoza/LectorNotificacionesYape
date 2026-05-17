package com.example.lectoryape.auth

import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.gotrue.providers.builtin.IDToken
import io.github.jan.supabase.gotrue.providers.Google
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SupabaseAuthManager {

    /**
     * Inicia sesión con Correo y Contraseña
     */
    suspend fun loginWithEmail(emailUser: String, passwordUser: String): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                SupabaseManager.auth.signInWith(Email) {
                    email = emailUser
                    password = passwordUser
                }
            }
            true // Retorna true si tuvo éxito
        } catch (e: Exception) {
            android.util.Log.e("SupabaseAuth", "Error al iniciar sesión: ${e.message}")
            false
        }
    }

    /**
     * Inicia sesión usando el ID Token nativo de Google
     */
    suspend fun loginWithGoogleIdToken(googleIdToken: String): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                SupabaseManager.auth.signInWith(IDToken) {
                    provider = Google
                    idToken = googleIdToken
                }
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("SupabaseAuth", "Error en login Google: ${e.message}")
            false
        }
    }

    /**
     * Verifica si el usuario ya está logueado y tiene un token válido
     */
    fun isUserSignedIn(): Boolean {
        return SupabaseManager.auth.currentSessionOrNull() != null
    }

    /**
     * Obtiene el ID (UUID) del usuario autenticado
     */
    fun getCurrentUserId(): String? {
        return SupabaseManager.auth.currentUserOrNull()?.id
    }

    /**
     * Cierra la sesión y borra el token del celular
     */
    suspend fun signOut() {
        try {
            withContext(Dispatchers.IO) {
                SupabaseManager.auth.signOut()
            }
        } catch (e: Exception) {
            android.util.Log.e("SupabaseAuth", "Error al cerrar sesión: ${e.message}")
        }
    }
}
