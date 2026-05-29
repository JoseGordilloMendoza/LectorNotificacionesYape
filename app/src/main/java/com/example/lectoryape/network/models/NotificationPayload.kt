package com.example.lectoryape.network.models

import com.google.gson.annotations.SerializedName

data class NotificationPayload(
    val tenant: String,
    val branch: String?,
    val amount: Double,
    val sender_name: String,
    val reference_code: String,
    val wallet_type: String,
    val raw_payload: Map<String, Any>
)
