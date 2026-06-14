package com.thecode007.turboxpress.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

/**
 * Request body for the Firebase login endpoint.
 *
 * @param firebaseToken Raw Firebase ID token from the mobile client (from FirebaseAuth.getIdToken()).
 * @param targetRole    The app the user is logging into: CUSTOMER | COURIER | MERCHANT | ADMIN
 */
data class FirebaseLoginRequest(
    @field:NotBlank(message = "firebaseToken must not be blank")
    val firebaseToken: String? = null,

    @field:NotBlank(message = "targetRole must not be blank")
    @field:Pattern(
        regexp = "CUSTOMER|COURIER|MERCHANT|ADMIN",
        flags = [Pattern.Flag.CASE_INSENSITIVE],
        message = "targetRole must be one of: CUSTOMER, COURIER, MERCHANT, ADMIN"
    )
    val targetRole: String? = null
)
