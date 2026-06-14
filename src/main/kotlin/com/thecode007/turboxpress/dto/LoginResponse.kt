package com.thecode007.turboxpress.dto

data class LoginResponse(
    val token: String,
    val type: String = "Bearer",
    val userId: String,
    val fullName: String,
    val phoneNumber: String,
    val roles: Set<String>,
    val permissions: Set<String>,
    val context: Map<String, Any>,

    /**
     * The single role this JWT session is scoped to.
     * Null for legacy admin-panel tokens (backward compat).
     * One of: CUSTOMER | COURIER | MERCHANT | ADMIN
     */
    val activeRole: String? = null,

    /**
     * UUID of the role-specific profile row (customer_profiles, driver_profiles, etc.).
     * Null for legacy tokens.
     */
    val profileId: String? = null,

    /**
     * True if the user has filled their onboarding profile (submitted docs/names).
     */
    val isProfileComplete: Boolean = false,

    /**
     * The current verification status of the profile.
     */
    val verificationStatus: String = "PENDING"
)
