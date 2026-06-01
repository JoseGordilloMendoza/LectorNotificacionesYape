package com.example.kajaapp.models

data class TesisInvitationUiModel(
    val code: String,
    val targetRole: String,
    val stallName: String?,
    val expiresLabel: String,
    val status: String
)
