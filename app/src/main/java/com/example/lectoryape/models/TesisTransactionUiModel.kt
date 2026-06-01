package com.example.kajaapp.models

data class TesisTransactionUiModel(
    val id: String,
    val puesto: String,
    val senderName: String,
    val amount: Double,
    val walletType: String,
    val hourLabel: String,
    val status: String,
    val description: String?,
    val helperName: String
)
