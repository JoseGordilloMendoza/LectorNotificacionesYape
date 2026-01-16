package com.example.lectoryape.models

/**
 * Representa una notificación de Yape capturada en su formato crudo (sin parsear)
 * 
 * @property timestamp Momento en que se recibió la notificación (epoch millis)
 * @property title Título de la notificación (ej: "Confirmación de Pago")
 * @property text Texto principal de la notificación
 * @property bigText Texto expandido (usualmente igual que text)
 * @property notificationId ID único de la notificación en el sistema
 */
data class YapeNotificationRaw(
    val timestamp: Long,
    val title: String,
    val text: String,
    val bigText: String,
    val notificationId: Int
) {
    /**
     * Convierte la notificación a formato CSV
     * Escapa las comas y comillas para evitar problemas con el formato CSV
     */
    fun toCsvLine(): String {
        return buildString {
            append(timestamp)
            append(",")
            append(escapeCsv(title))
            append(",")
            append(escapeCsv(text))
            append(",")
            append(escapeCsv(bigText))
            append(",")
            append(notificationId)
        }
    }
    
    private fun escapeCsv(value: String): String {
        // Si contiene comas, comillas o saltos de línea, envolver en comillas
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
    
    companion object {
        /**
         * Encabezado del archivo CSV
         */
        const val CSV_HEADER = "timestamp,title,text,bigText,notificationId"
    }
}
