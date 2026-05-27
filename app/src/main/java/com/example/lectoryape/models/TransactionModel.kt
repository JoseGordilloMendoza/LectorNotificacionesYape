package com.example.lectoryape.models

import kotlinx.serialization.Serializable

@Serializable
data class TransactionModel(
    val owner_id: String,
    val monto: Double,
    val nombre_remitente: String,
    val codigo_referencia: String
)
