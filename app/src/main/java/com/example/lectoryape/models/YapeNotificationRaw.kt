package com.example.lectoryape.models

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
    val notificationId: Int,
    val walletType: String = "Yape" // "Yape" o "Plin"
) {
    /**
     * A formato csv, esta es solo para el local
     */
    fun toCsvLine(): String {
        val fechaLegible = com.example.lectoryape.utils.DateFormatter.formatTimestamp(timestamp)
        return buildString {
            append(fechaLegible)
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
        const val CSV_HEADER = "fecha,nombre,monto,codigoSeguridad"
    }
}
