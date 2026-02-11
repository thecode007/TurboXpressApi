package com.thecode007.turboxpress.dto

import java.time.Instant
import java.util.*

data class UserResponse(
    val id: UUID,
    val fullName: String,
    val phoneNumber: String,
    val isActive: Boolean,
    val createdAt: Instant,
    val roles: Set<String>
)
