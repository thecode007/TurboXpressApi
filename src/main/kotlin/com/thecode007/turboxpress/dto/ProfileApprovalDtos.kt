package com.thecode007.turboxpress.dto

import jakarta.validation.constraints.NotBlank

/** Request sent by an admin to approve or reject a user's profile. */
data class ApproveProfileRequest(
    @field:NotBlank(message = "userId must not be blank")
    val userId: String,

    /** CUSTOMER | COURIER | MERCHANT | ADMIN */
    @field:NotBlank(message = "role must not be blank")
    val role: String,

    /** Optional note stored on the profile for auditing. Mandatory on rejection. */
    val adminNote: String? = null
)

/** Response returned after an approval or rejection action. */
data class ApproveProfileResponse(
    val userId: String,
    val role: String,
    val status: String,        // "APPROVED" or "REJECTED"
    val approvedBy: String,    // admin's userId
    val message: String        // human-readable outcome
)

/** Lightweight projection used by the admin panel pending queue. */
data class PendingProfileDto(
    val userId: String,
    val role: String,
    val displayName: String?,
    val hasIdDocument: Boolean,
    val hasCriminalRecord: Boolean,
    val submittedAt: String?
)
