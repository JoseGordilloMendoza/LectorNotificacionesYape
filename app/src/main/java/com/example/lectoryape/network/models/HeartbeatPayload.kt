package com.example.kajaapp.network.models

import com.google.gson.annotations.SerializedName

data class HeartbeatPayload(
    @SerializedName("tenant_id") val tenantId: String,
    @SerializedName("device_id") val deviceId: String,
    val timestamp: Long
)
