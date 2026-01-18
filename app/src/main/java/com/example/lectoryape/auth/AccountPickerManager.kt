package com.example.lectoryape.auth

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.common.AccountPicker

/**
 * Maneja la selección de cuenta Google usando Account Picker nativo
 * NO requiere OAuth Client ID ni SHA-1
 */
class AccountPickerManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AccountPickerManager"
        private const val PREFS_NAME = "YapeAuthPrefs"
        private const val KEY_EMAIL = "user_email"
        private const val KEY_NAME = "user_name"
        const val REQUEST_CODE_PICK_ACCOUNT = 1000
    }
    
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Muestra el selector de cuentas Google del dispositivo
     */
    fun showAccountPicker(activity: Activity) {
        try {
            val intent = AccountPicker.newChooseAccountIntent(
                null,                           // Cuenta seleccionada previamente
                null,                           // Lista de cuentas a mostrar (null = todas)
                arrayOf(GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE), // Solo cuentas Google
                false,                          // No permitir añadir cuenta nueva
                null,                           // Descripción
                null,                           // Sugerencia de email
                null,                           // Características requeridas
                null                            // Opciones
            )
            
            activity.startActivityForResult(intent, REQUEST_CODE_PICK_ACCOUNT)
            Log.d(TAG, "Account Picker lanzado")
        } catch (e: Exception) {
            Log.e(TAG, "Error al mostrar Account Picker: ${e.message}", e)
        }
    }
    
    /**
     * Procesa el resultado de Account Picker
     */
    fun handleAccountPickerResult(data: Intent?): String? {
        if (data == null) {
            Log.w(TAG, "No se seleccionó cuenta")
            return null
        }
        
        val email = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        val accountType = data.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE)
        
        Log.d(TAG, "Cuenta seleccionada: $email (tipo: $accountType)")
        
        if (email != null) {
            saveEmail(email)
        }
        
        return email
    }
    
    /**
     * Guarda el email del usuario en SharedPreferences
     */
    private fun saveEmail(email: String) {
        prefs.edit().apply {
            putString(KEY_EMAIL, email)
            // Extraer nombre del email (parte antes del @)
            val name = email.substringBefore("@")
            putString(KEY_NAME, name)
            apply()
        }
        Log.d(TAG, "Email guardado: $email")
    }
    
    /**
     * Verifica si hay un usuario logueado
     */
    fun isSignedIn(): Boolean {
        val email = getUserEmail()
        return !email.isNullOrEmpty()
    }
    
    /**
     * Obtiene el email del usuario guardado
     */
    fun getUserEmail(): String? {
        return prefs.getString(KEY_EMAIL, null)
    }
    
    /**
     * Obtiene el nombre del usuario guardado
     */
    fun getUserName(): String? {
        return prefs.getString(KEY_NAME, null)
    }
    
    /**
     * Cierra sesión (limpia SharedPreferences)
     */
    fun signOut() {
        prefs.edit().clear().apply()
        Log.d(TAG, "Sesión cerrada - SharedPreferences limpiado")
    }
    
    /**
     * Obtiene todas las cuentas Google disponibles en el dispositivo
     */
    fun getAvailableGoogleAccounts(): List<Account> {
        return try {
            val accountManager = AccountManager.get(context)
            accountManager.getAccountsByType(GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE).toList()
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener cuentas: ${e.message}", e)
            emptyList()
        }
    }
}
