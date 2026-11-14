package com.thecode007.turboxpress.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.util.*

data class UserResponse(
    val id: UUID,
    val username: String,
    val fullName: String,
    val phoneNumber: String,
    @get:JsonProperty("isActive")
    val isActive: Boolean,
    val createdAt: Instant,
    val roles: Set<String>,
    val territoryId: Long? = null,
    val territoryName: String? = null
)
