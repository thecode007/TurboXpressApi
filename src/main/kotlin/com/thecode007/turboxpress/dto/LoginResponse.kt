package com.thecode007.turboxpress.dto

data class LoginResponse(
    val token: String,
    val type: String = "Bearer",
    val userId: String,
    val fullName: String,
    val phoneNumber: String,
    val roles: Set<String>,
    val permissions: Set<String>,
    val context: Map<String, Any>
)
