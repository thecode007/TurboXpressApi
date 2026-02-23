package com.thecode007.turboxpress.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

data class DeliveryGuyResponse(
    val phoneNumber: String,
    val username: String,
    val fullName: String,
    val profilePictureUrl: String?,
    @get:JsonProperty("isActive")
    val isActive: Boolean,
    val createdAt: Instant
)
