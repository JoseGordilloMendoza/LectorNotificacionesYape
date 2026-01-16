package com.example.lectoryape.storage

import android.content.Context
import android.util.Log
import com.example.lectoryape.models.YapeNotificationRaw
import java.io.File
import java.io.FileWriter
import java.io.IOException

class YapeNotificationStorage(private val context: Context) {
    
    companion object {
        private const val TAG = "YapeNotificationStorage"
        private const val FILE_NAME = "yape_notifications.csv"
    }
    
    private val csvFile: File by lazy {
        File(context.getExternalFilesDir(null), FILE_NAME)
    }
    
    /**
     * Guarda una notificación en el archivo CSV
     * Crea el archivo con encabezado si no existe
     */
    @Synchronized
    fun saveNotification(notification: YapeNotificationRaw): Boolean {
        return try {
            // si no hay archivo archivo no existe, crear con encabezado
            if (!csvFile.exists()) {
                createFileWithHeader()
            }
            
            // Append la notificación
            FileWriter(csvFile, true).use { writer ->
                writer.append(notification.toCsvLine())
                writer.append("\n")
            }
            
            Log.d(TAG, "✅ Notificación guardada: ${notification.name}")
            true
        } catch (e: IOException) {
            Log.e(TAG, "❌ Error al guardar notificación: ${e.message}", e)
            false
        }
    }
    
    /**
     * Crea el archivo CSV con el encabezado
     */
    private fun createFileWithHeader() {
        try {
            FileWriter(csvFile).use { writer ->
                writer.write(YapeNotificationRaw.CSV_HEADER)
                writer.write("\n")
            }
            Log.d(TAG, "📄 Archivo CSV creado: ${csvFile.absolutePath}")
        } catch (e: IOException) {
            Log.e(TAG, "❌ Error al crear archivo CSV: ${e.message}", e)
        }
    }
    
    /**
     * Obtiene todas las notificaciones guardadas como texto
     */
    fun getAllNotificationsAsText(): String {
        return try {
            if (csvFile.exists()) {
                csvFile.readText()
            } else {
                ""
            }
        } catch (e: IOException) {
            Log.e(TAG, "❌ Error al leer archivo CSV: ${e.message}", e)
            ""
        }
    }
    
    /**
     * Cuenta cuántas notificaciones hay guardadas
     */
    fun getNotificationCount(): Int {
        return try {
            if (csvFile.exists()) {
                // Restar 1 por el encabezado
                maxOf(0, csvFile.readLines().size - 1)
            } else {
                0
            }
        } catch (e: IOException) {
            Log.e(TAG, "❌ Error al contar notificaciones: ${e.message}", e)
            0
        }
    }
    
    /**
     * Elimina todas las notificaciones guardadas
     */
    fun clearAll(): Boolean {
        return try {
            if (csvFile.exists()) {
                csvFile.delete()
                Log.d(TAG, "🗑️ Archivo CSV eliminado")
                true
            } else {
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al eliminar archivo CSV: ${e.message}", e)
            false
        }
    }
    
    /**
     * Obtiene la ruta del archivo CSV
     */
    fun getFilePath(): String = csvFile.absolutePath
    
    /**
     * Verifica si el archivo existe
     */
    fun fileExists(): Boolean = csvFile.exists()
}
