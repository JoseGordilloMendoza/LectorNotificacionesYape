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
        private const val YAPEOS_SUBCOLLECTION = "yape_notifications"
    }
    
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val authManager = FirebaseAuthManager(context)
    
    /**
     subida de yapeo
     */
    suspend fun uploadNotification(notification: YapeNotificationRaw): Boolean {
        return try {
            val currentUser = authManager.getCurrentUser()
            val userId = currentUser?.uid ?: run {
                Log.e(TAG, "usuario no logueado")
                return false
            }
            
            val dateDate = java.util.Date(notification.timestamp)
            
            val sdfFecha = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val fecha = sdfFecha.format(dateDate)
            
            val sdfHora = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            val hora = sdfHora.format(dateDate)
            
            // formaato doc de yapeo
            val data = hashMapOf(
                "senderName" to notification.name,
                "amount" to notification.amount,
                "fecha" to fecha,
                "hora" to hora,
                "status" to "pending",
                "branchId" to null,
                "branchName" to null,
                "timestamp" to notification.timestamp,
                "wallet" to notification.walletType.uppercase()  // "YAPE" o "PLIN"
            )
            
            // id unico y no generado aleatoriamente
            val uniqueId = "${notification.name}_${notification.amount}_${notification.timestamp}_${notification.walletType}"
                .hashCode()
                .toUInt()
                .toString()
            
            // subcolección: user/yape_notifications
            firestore.collection("users")
                .document(userId)
                .collection(YAPEOS_SUBCOLLECTION)
                .document(uniqueId)
                .set(data)
                .await()
            
            Log.d(TAG, "uploaded a users/$userId/yape_notifications [$uniqueId]: ${notification.name} - S/${notification.amount} [${notification.walletType}]")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "error subida a firebase: ${e.message}", e)
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
        
        Log.d(TAG, "uploads: $successCount/${notifications.size}")
        return successCount
    }
    
    /**
    yapeos del usuario actual
     */
    suspend fun getUserYapeos(): List<Map<String, Any>> {
        return try {
            val currentUser = authManager.getCurrentUser()
            val userId = currentUser?.uid ?: run {
                Log.w(TAG, " usuario no logueado")
                return emptyList()
            }
            
            Log.d(TAG, "debug buscando yapeos en users/$userId/yape_notifications")
            
            // Leer directamente de la subcolección del usuario
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection(YAPEOS_SUBCOLLECTION)
                .limit(100)
                .get()
                .await()
            
            Log.d(TAG, "docs encontrados: ${snapshot.size()}")
            
            snapshot.documents.mapNotNull { doc ->
                doc.data
            }.sortedByDescending { data ->
                (data["timestamp"] as? Long) ?: 0L
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error OBTENIENDO yapeos: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * creacion o actualizacion en la colección 'users'
     */
    /**
     * - no existe: Crea perfil con rol 'owner' y Suscripción 'trial' (7 días).
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
                Log.d(TAG, "nuevo perfil: ${user.email}")
                
            } else {
                Log.d(TAG, "usuario existente: ${user.email}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "error en sincronizacion usuario: ${e.message}", e)
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
