package com.thecode007.turboxpress.dto

import jakarta.validation.constraints.NotBlank

data class PhoneLoginRequest(
    @field:NotBlank(message = "Phone number is required")
    val phoneNumber: String,
    
    val password: String? = null,
    
    @field:NotBlank(message = "Target role is required")
    val targetRole: String
)

data class SetPasswordRequest(
    @field:NotBlank(message = "Phone number is required")
    val phoneNumber: String,
    
    @field:NotBlank(message = "Password is required")
    val password: String,
    
    @field:NotBlank(message = "Target role is required")
    val targetRole: String
)
