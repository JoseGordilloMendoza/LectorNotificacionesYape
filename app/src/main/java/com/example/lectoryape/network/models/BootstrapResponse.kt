package com.example.lectoryape.network.models

import com.google.gson.annotations.SerializedName

data class BootstrapResponse(
    val memberships: List<Membership>,
    @SerializedName("requires_onboarding") val requiresOnboarding: Boolean
)

data class Membership(
    val tenant: Tenant
)

data class Tenant(
    val id: String,
    val name: String
)
