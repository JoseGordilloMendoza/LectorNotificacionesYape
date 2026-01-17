package com.example.lectoryape.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * ESTO EN TEORIA PUEDE SER REEMPLAZADO, POR ELREGEX , ASI QUE PUEDE BORRARSE
 */
object DateFormatter {
    
    fun formatTimestamp(timestamp: Long): String {
        val now = Calendar.getInstance()
        val notificationTime = Calendar.getInstance().apply {
            timeInMillis = timestamp
        }
        
        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeStr = dateFormat.format(Date(timestamp))
        
        return when {
            // Hoy
            now.get(Calendar.DAY_OF_YEAR) == notificationTime.get(Calendar.DAY_OF_YEAR) &&
            now.get(Calendar.YEAR) == notificationTime.get(Calendar.YEAR) -> {
                "Hoy $timeStr"
            }
            // Ayer
            now.get(Calendar.DAY_OF_YEAR) - 1 == notificationTime.get(Calendar.DAY_OF_YEAR) &&
            now.get(Calendar.YEAR) == notificationTime.get(Calendar.YEAR) -> {
                "Ayer $timeStr"
            }
            // Otro día
            else -> {
                val fullDateFormat = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
                fullDateFormat.format(Date(timestamp))
            }
        }
    }
    
    fun extractMonto(text: String): String {
        val montoRegex = """S/\s*(\d+\.?\d*)""".toRegex()
        val match = montoRegex.find(text)
        return if (match != null) {
            "S/ ${match.groupValues[1]}"
        } else {
            "S/ --"
        }
    }
}
