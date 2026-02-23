package com.thecode007.turboxpress.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty

data class UpdateUserRequest(
    @field:NotBlank(message = "Full name is required")
    val fullName: String,
    
    @field:NotBlank(message = "Phone number is required")
    val phoneNumber: String,
    
    val isActive: Boolean,
    
    @field:NotEmpty(message = "At least one role is required")
    val roleIds: List<Int>
)
