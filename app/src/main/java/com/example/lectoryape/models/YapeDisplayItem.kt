package com.example.lectoryape.models

/**
 * Modelo simplificado para mostrar yapeos en la UI
 */
data class YapeDisplayItem(
    val monto: String,          // Ej: "S/ 50.00"
    val texto: String,          // Texto de la notificación
    val fecha: String,          // Ej: "Hoy 10:30 AM"
    val timestamp: Long         // Para ordenar
)
