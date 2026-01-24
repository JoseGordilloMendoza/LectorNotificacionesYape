package com.example.lectoryape.firebase

import android.content.Context
import android.util.Log
import com.example.lectoryape.auth.AccountPickerManager
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
    private val accountManager = AccountPickerManager(context)
    
    /**
     * Sube una notificación a Firestore con estructura simplificada
     */
    suspend fun uploadNotification(notification: YapeNotificationRaw): Boolean {
        return try {
            // Obtener email del usuario logueado
            val userEmail = accountManager.getUserEmail() ?: "unknown"
            
            // Formatear fecha y hora por separado
            val fechaHora = com.example.lectoryape.utils.DateFormatter.formatTimestamp(notification.timestamp)
            
            // Separar fecha y hora usando el formato: "21 de enero de 2026 a las 11:49:35 p.m. UTC-5"
            val partes = fechaHora.split(" a las ")
            val fecha = partes.getOrNull(0) ?: fechaHora
            val hora = partes.getOrNull(1) ?: ""
            
            // Crear documento simplificado para Firestore
            val data = hashMapOf(
                "senderName" to notification.name,       // Nombre del emisor
                "amount" to notification.amount,         // Monto
                "fecha" to fecha,                        // Ej: "21 de enero de 2026"
                "hora" to hora,                          // Ej: "11:49:35 p.m. UTC-5"
                "status" to false,                        // Boolean: true = procesado
                "timestamp" to notification.timestamp,   // Timestamp original (para ordenar)
                "userEmail" to userEmail                 // Email del usuario
            )
            
            // Subir a Firestore
            firestore.collection(COLLECTION_NAME)
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
     * Obtiene el número total de notificaciones en Firestore
     */
    suspend fun getTotalCount(): Int {
        return try {
            val snapshot = firestore.collection(COLLECTION_NAME)
                .get()
                .await()
            
            snapshot.size()
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener conteo: ${e.message}", e)
            0
        }
    }
    
    /**
     * Obtiene los yapeos del usuario actual (filtrado por email)
     */
    suspend fun getUserYapeos(): List<Map<String, Any>> {
        return try {
            val userEmail = accountManager.getUserEmail()
            Log.d(TAG, "🔍 Buscando yapeos para email: $userEmail")
            
            if (userEmail == null) {
                Log.w(TAG, "⚠️ No hay usuario logueado")
                return emptyList()
            }
            
            Log.d(TAG, "📡 Ejecutando consulta a Firebase...")
            val snapshot = firestore.collection(COLLECTION_NAME)
                .whereEqualTo("userEmail", userEmail)
                .limit(100) // Limitar a últimos 100 yapeos
                .get()
                .await()
            
            Log.d(TAG, "📊 Documentos encontrados: ${snapshot.size()}")
            
            val results = snapshot.documents.mapNotNull { doc ->
                doc.data
            }.sortedByDescending { data ->
                // Ordenar por timestamp descendente (más recientes primero)
                (data["timestamp"] as? Long) ?: 0L
            }
            
            Log.d(TAG, "✅ Retornando ${results.size} yapeos")
            results
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al obtener yapeos del usuario: ${e.message}", e)
            emptyList()
        }
    }
}
