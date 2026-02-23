package com.thecode007.turboxpress.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateOwnerRequest(
    @field:NotBlank(message = "Phone number is required")
    val phoneNumber: String,
    
    @field:NotBlank(message = "Full name is required")
    val fullName: String,
    
    @field:NotBlank(message = "Password is required")
    @field:Size(min = 8, message = "Password must be at least 8 characters")
    val password: String,
    
    val profilePictureUrl: String? = null
)
