package com.thecode007.turboxpress.dto

import jakarta.validation.constraints.NotBlank

data class LoginRequest(
    @field:NotBlank(message = "Username or phone number is required")
    val identifier: String,

    @field:NotBlank(message = "Password is required")
    val password: String
)
