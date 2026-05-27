package com.example.lectoryape.network.models

import com.google.gson.annotations.SerializedName

data class DeviceRegistrationRequest(
    val tenant: String,
    @SerializedName("device_id") val deviceId: String,
    val name: String
)

data class DeviceRegistrationResponse(
    val id: String,
    val name: String,
    @SerializedName("device_id") val deviceId: String,
    val tenant: String,
    @SerializedName("last_heartbeat") val lastHeartbeat: String?
)
