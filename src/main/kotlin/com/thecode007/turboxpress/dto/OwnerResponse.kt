package com.thecode007.turboxpress.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.thecode007.turboxpress.entity.Owner
import java.time.Instant

data class OwnerResponse(
    val phoneNumber: String,
    val fullName: String,
    val profilePictureUrl: String?,
    @get:JsonProperty("isActive")
    val isActive: Boolean,
    val createdAt: Instant
) {
    companion object {
        fun from(owner: Owner): OwnerResponse {
            return OwnerResponse(
                phoneNumber = owner.phoneNumber,
                fullName = owner.fullName,
                profilePictureUrl = owner.profilePictureUrl,
                isActive = owner.isActive,
                createdAt = owner.createdAt
            )
        }
    }
}
