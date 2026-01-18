package com.example.lectoryape.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateFormatter {

    // PATRÓN CONSTANTE
    private const val DATE_PATTERN = "yyyy-MM-dd HH:mm:ss"

    fun formatTimestamp(timestamp: Long): String {
        // 1. Validación: Si el tiempo es 0 o negativo, devolvemos un placeholder
        if (timestamp <= 0L) {
            return "Fecha inválida"
        }

        try {
            // 2. IMPORTANTE: Instanciamos el SimpleDateFormat DENTRO de la función.
            // Esto evita errores de concurrencia (crash) cuando la app y el servicio lo usan a la vez.
            val dateFormat = SimpleDateFormat(DATE_PATTERN, Locale.getDefault())

            // Opcional: Forzar zona horaria de Perú si el celular estuviera en otra zona
            // dateFormat.timeZone = TimeZone.getTimeZone("America/Lima")

            return dateFormat.format(Date(timestamp))
        } catch (e: Exception) {
            return "Error formato"
        }
    }

    /**
     * Extrae solo la hora para mostrar en listas simples (ej: "14:30")
     */
    fun getOnlyTime(timestamp: Long): String {
        if (timestamp <= 0L) return "--:--"
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}