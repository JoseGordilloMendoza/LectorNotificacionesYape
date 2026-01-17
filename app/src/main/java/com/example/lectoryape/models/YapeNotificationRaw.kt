package com.example.lectoryape.models

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Representa una notificación de Yape capturada en su formato crudo (sin parsear)
 * 
 * @property timestamp Momento en que se recibió la notificación (epoch millis)
 * @property name El nombre del emisor del pago
 * @property amount Monto de la operación
 * @property securityCode Representa el codigo de seguridad entre operaciones de yape a yape
 * @property notificationId ID único de la notificación en el sistema
 */
data class YapeNotificationRaw(
    val title: String,
    val name: String,
    val amount: Double,
    val timestamp: Long,
    val securityCode: String,
    val notificationId: Int
) {
    /**
     * Convierte la notificación a formato CSV
     * Escapa las comas y comillas para evitar problemas con el formato CSV
     */
    fun toCsvLine(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val fechaFormateada = dateFormat.format(Date(timestamp))

        return buildString {
            append(fechaFormateada)
            append(",")
            append(escapeCsv(title))
            append(",")
            append(escapeCsv(name))
            append(",")
            append("%.2f".format(java.util.Locale.US, amount))
            append(",")
            append(escapeCsv(securityCode))
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
        const val CSV_HEADER = "datetime,title,name,amount,securityCode,notificationId"
    }
}
