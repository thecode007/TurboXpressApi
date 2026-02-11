package com.thecode007.turboxpress.dto

data class ImpersonateResponse(
    val token: String,
    val type: String = "Bearer",
    val impersonation: Boolean = true,
    val adminId: String,
    val targetUser: UserResponse,
    val expiresIn: Long
)
