package com.thecode007.turboxpress.dto

import jakarta.validation.constraints.NotBlank

data class UpdateDeliveryGuyRequest(
    @field:NotBlank(message = "Username is required")
    val username: String,
    
    @field:NotBlank(message = "Full name is required")
    val fullName: String,
    
    val profilePictureUrl: String? = null,
    
    val isActive: Boolean
)
