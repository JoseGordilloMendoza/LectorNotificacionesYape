package com.example.lectoryape.firebase

import android.content.Context
import android.util.Log
import com.example.lectoryape.auth.FirebaseAuthManager
import com.example.lectoryape.models.YapeNotificationRaw
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Maneja la subida de notificaciones a Firebase Firestore
 */
class FirebaseUploader(private val context: Context) {
    
    companion object {
        private const val TAG = "FirebaseUploader"
        private const val COLLECTION_NAME = "yape_notifications"
    }
    
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val authManager = FirebaseAuthManager(context)
    
    /**
     * Sube una notificación a Firestore con estructura simplificada
     */
    suspend fun uploadNotification(notification: YapeNotificationRaw): Boolean {
        return try {
            // Obtener usuario logueado REAL
            val currentUser = authManager.getCurrentUser()
            val userEmail = currentUser?.email ?: "unknown"
            val userId = currentUser?.uid ?: "unknown"
            
            // Formatear fecha y hora usando DateFormatters específicos
            val dateDate = java.util.Date(notification.timestamp)
            
            // Formato Fecha: "2026-01-25"
            val sdfFecha = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val fecha = sdfFecha.format(dateDate)
            
            // Formato Hora: "12:30:45"
            val sdfHora = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            val hora = sdfHora.format(dateDate)
            
            // Crear documento simplificado para Firestore
            val data = hashMapOf(
                "senderName" to notification.name,       // Nombre del emisor
                "amount" to notification.amount,         // Monto
                "fecha" to fecha,                        // Ej: "2026-01-25"
                "hora" to hora,                          // Ej: "12:30:45"
                "status" to "pending",                   // pending
                "branchId" to null,                      // ID de sucursal
                "branchName" to null,                    // Nombre de sucursal
                "timestamp" to notification.timestamp,   // Timestamp original
                "userEmail" to userEmail                 // Email del usuario
            )
            
            // Subir a Firestore (A la subcolección del usuario específico)
            firestore.collection("users").document(userId).collection(COLLECTION_NAME)
                .add(data)
                .await()
            
            Log.d(TAG, "✅ Notificación subida a Firebase: ${notification.name} - S/${notification.amount}")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al subir a Firebase: ${e.message}", e)
            false
        }
    }
    
    /**
     * Sube múltiples notificaciones (para sincronizar pendientes)
     */
    suspend fun uploadMultiple(notifications: List<YapeNotificationRaw>): Int {
        var successCount = 0
        
        notifications.forEach { notification ->
            if (uploadNotification(notification)) {
                successCount++
            }
        }
        
        Log.d(TAG, "📊 Subidas: $successCount/${notifications.size}")
        return successCount
    }
    
    /**
     * Cuenta los yapeos del usuario actual en Firebase usando Aggregate Query
     */
    suspend fun countUserYapeos(): Int {
        return try {
            val userId = authManager.getCurrentUser()?.uid
            if (userId == null) return 0
            
            // Contar todos los documentos dentro de su propia subcolección
            val snapshot = firestore.collection("users").document(userId).collection(COLLECTION_NAME)
                .count()
                .get(com.google.firebase.firestore.AggregateSource.SERVER)
                .await()
                
            snapshot.count.toInt()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al contar yapeos del usuario: ${e.message}", e)
            0
        }
    }
    
    /**
     * Obtiene los yapeos del usuario actual de su subcolección
     */
    suspend fun getUserYapeos(): List<Map<String, Any>> {
        return try {
            val userId = authManager.getCurrentUser()?.uid
            Log.d(TAG, "🔍 Buscando yapeos para userId: $userId")
            
            if (userId == null) {
                Log.w(TAG, "⚠️ No hay usuario logueado o userId es nulo")
                return emptyList()
            }
            
            // Ya no hace falta whereEqualTo, sabemos que estamos dentro del documento del usuario correcto
            val snapshot = firestore.collection("users").document(userId).collection(COLLECTION_NAME)
                .limit(100)
                .get()
                .await()
            
            Log.d(TAG, "📊 Documentos encontrados: ${snapshot.size()} en la subcolección de $userId")
            
            if (snapshot.isEmpty) {
                Log.w(TAG, "⚠️ La consulta devolvió 0 documentos. Verifica mayúsculas/minúsculas en Firebase.")
            }
            
            val results = snapshot.documents.mapNotNull { doc ->
                // Loguear estructura del primer documento para debug
                // if (snapshot.documents.indexOf(doc) == 0) Log.d(TAG, "Estructura doc ejemplo: ${doc.data}")
                doc.data
            }.sortedByDescending { data ->
                (data["timestamp"] as? Long) ?: 0L
            }
            
            results
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al obtener yapeos del usuario: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Guarda o actualiza el usuario en la colección 'users'
     */
    /**
     * Guarda o actualiza el usuario en la colección 'users'
     * Sincronizado con la lógica de la Web App (Vue.js):
     * - Si no existe: Crea perfil con rol 'owner' y Suscripción 'trial' (7 días).
     * - Si existe: Solo actualiza lastLogin.
     */
    /**
     * Guarda el usuario en 'users' SOLO si es la primera vez (Igual que la web)
     */
    suspend fun saveUser(user: com.google.firebase.auth.FirebaseUser) {
        try {
            val userRef = firestore.collection("users").document(user.uid)
            val snapshot = userRef.get().await()
            
            if (!snapshot.exists()) {
                // Generar fechas en formato ISO 8601 (Compatibilidad Web)
                val now = java.util.Date()
                val isoFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                isoFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                
                val createdAt = isoFormat.format(now)
                
                // Calcular fin de prueba (7 días)
                val calendar = java.util.Calendar.getInstance()
                calendar.time = now
                calendar.add(java.util.Calendar.DAY_OF_YEAR, 7)
                val trialEndDate = isoFormat.format(calendar.time)

                // Estructura EXACTA al código Vue.js proporcionado
                val subscription = hashMapOf(
                    "isActive" to true,
                    "status" to "trial",
                    "planName" to "Prueba Gratuita",
                    "limitSucursales" to 3,
                    "trialEndDate" to trialEndDate,
                    "nextBillingDate" to null
                )

                val userData = hashMapOf(
                    "email" to (user.email ?: ""),
                    "displayName" to (user.displayName ?: "Usuario"),
                    "role" to "owner",
                    "createdAt" to createdAt,
                    "subscription" to subscription
                )

                userRef.set(userData).await()
                Log.d(TAG, "✅ Perfil NUEVO creado (Sync Web): ${user.email}")
                
            } else {
                Log.d(TAG, "ℹ️ El usuario ya existe, no se toca nada (Sync Web): ${user.email}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al sincronizar usuario: ${e.message}", e)
        }
    }
    
    /**
     * Envía un "Latido" a la base de datos para avisar a la web si la app
     * está encendida y escuchando notificaciones.
     */
    suspend fun sendHeartbeat(isOnline: Boolean) {
        try {
            val userId = authManager.getCurrentUser()?.uid ?: return
            
            val data = hashMapOf(
                "deviceOnline" to isOnline,
                "lastHeartbeat" to com.google.firebase.firestore.FieldValue.serverTimestamp() // Usamos la hora oficial del servidor de Firebase
            )
            
            // merge() asegura que solo se actualicen estos campos sin borrar el email, perfil o subscripción
            firestore.collection("users").document(userId)
                .set(data, com.google.firebase.firestore.SetOptions.merge())
                .await()
                
            Log.d(TAG, "💓 Heartbeat enviado: isOnline=$isOnline")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al enviar heartbeat: ${e.message}", e)
        }
    }
}
