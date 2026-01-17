package com.example.lectoryape.firebase

import android.content.Context
import android.util.Log
import com.example.lectoryape.auth.GoogleAuthManager
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
    private val authManager = GoogleAuthManager(context)
    
    /**
     * Sube una notificación a Firestore
     */
    suspend fun uploadNotification(notification: YapeNotificationRaw): Boolean {
        return try {
            // Obtener email del usuario logueado
            val userEmail = authManager.getUserEmail() ?: "unknown"
            
            // Crear documento para Firestore
            val data = hashMapOf(
                "timestamp" to notification.timestamp,
                "title" to notification.title,
                "text" to notification.text,
                "bigText" to notification.bigText,
                "notificationId" to notification.notificationId,
                "userEmail" to userEmail,
                "uploadedAt" to System.currentTimeMillis()
            )
            
            // Subir a Firestore
            firestore.collection(COLLECTION_NAME)
                .add(data)
                .await()
            
            Log.d(TAG, "✅ Notificación subida exitosamente a Firebase")
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
}
