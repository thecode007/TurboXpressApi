package com.thecode007.turboxpress.dto

import jakarta.validation.constraints.NotBlank

data class LoginRequest(
    @field:NotBlank(message = "Phone number or username is required")
    val identifier: String? = null,

    @field:NotBlank(message = "Password is required")
    val password: String? = null
)
