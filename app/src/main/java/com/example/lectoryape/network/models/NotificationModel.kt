package com.example.kajaapp.network.models

import com.google.gson.annotations.SerializedName

data class NotificationModel(
    val id: String,
    val amount: String,
    @SerializedName("sender_name") val senderName: String,
    @SerializedName("reference_code") val referenceCode: String,
    @SerializedName("timestamp_ms") val timestampMs: Long,
    val status: String
)
