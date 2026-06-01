package com.example.kajaapp.network.models

import com.google.gson.annotations.SerializedName

data class BootstrapResponse(
    val user: CurrentUser,
    val memberships: List<Membership>,
    @SerializedName("requires_onboarding") val requiresOnboarding: Boolean
)

data class CurrentUser(
    @SerializedName("first_name") val firstName: String,
    val email: String
)

data class Membership(
    val role: String,
    val tenant: Tenant
)

data class Tenant(
    val id: String,
    val name: String
)
