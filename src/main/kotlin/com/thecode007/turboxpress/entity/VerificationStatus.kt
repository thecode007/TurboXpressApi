package com.thecode007.turboxpress.entity

/**
 * Lifecycle status of a user profile submitted for administrative review.
 *
 * State machine:
 *   PENDING  -  APPROVED  (admin accepts the profile)
 *   PENDING  -  REJECTED  (admin rejects; user must resubmit docs)
 *   REJECTED -  PENDING   (user resubmits; profile goes back for review)
 *   APPROVED -  REJECTED  (admin can revoke approval, e.g. for fraud)
 */
enum class VerificationStatus {
    /** Profile has been submitted and is awaiting admin review. */
    PENDING,

    /** Admin has reviewed and approved the profile. Grants full role access. */
    APPROVED,

    /** Admin has rejected the profile. User must correct and resubmit. */
    REJECTED
}
