package com.example.lectoryape.models

data class TesisMemberUiModel(
    val id: String,
    val fullName: String,
    val role: String,
    val defaultStall: String?,
    val status: String
)
