package com.example.lectoryape.models

/**
 yapeos en la UI    
 */
data class YapeDisplayItem(
    val monto: String,          // cantidad soles
    val texto: String,          // texto de notificación
    val fecha: String,          // date"
    val timestamp: Long         // timestamp para ordenar
)
