package com.example.lectoryape.network.models

import com.google.gson.annotations.SerializedName

data class SubscriptionResponse(
    val id: String?,
    @SerializedName("plan_type") val planType: String,
    @SerializedName("plan_name") val planName: String?,
    val status: String,
    @SerializedName("status_display") val statusDisplay: String?,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("start_date") val startDate: String,
    @SerializedName("end_date") val endDate: String
)
