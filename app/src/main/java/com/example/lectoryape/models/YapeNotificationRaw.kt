package com.example.lectoryape.models

/**
 * notificación de Yape o plin capturada en su formato crudo
 * 
 *  timestamp  millis
 * name nombre  emisor 
 *  amount Monto de la operación
 *  securityCode codigo de seguridad 
 *  notificationId ID único de la notificación en el sistema
 */
data class YapeNotificationRaw(
    val title: String,
    val name: String,
    val amount: Double,
    val timestamp: Long,
    val securityCode: String,
    val notificationId: Int,
    val walletType: String = "YAPE"
) {
    // csv, solo para local
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
        // si hay comas, comillas o saltos de línea, reemplazar con comillas
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
    
    companion object {
        const val CSV_HEADER = "fecha,nombre,monto,codigoSeguridad"
    }
}
