package com.example.kajaapp.network.models

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

data class DeviceNotificationPayload(
    @SerializedName("device_id") val deviceId: String,
    val amount: Double,
    val sender_name: String,
    val reference_code: String,
    val wallet_type: String,
    val raw_payload: Map<String, Any>
)
