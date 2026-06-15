package com.thecode007.turboxpress.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.springframework.data.domain.Persistable
import java.time.Instant
import java.util.UUID

/**
 * Owner Profile - one per user who registers as a restaurant owner.
 *
 * Verification flow:
 *   1. User submits profile via the Owner app (status = PENDING).
 *      Document URLs may be null initially - admin can approve before upload.
 *   2. Admin reviews - calls AdminProfileService.approveProfile(userId, "MERCHANT").
 *   3. Status - APPROVED; approvedBy is set for the audit trail.
 */
@Entity
@Table(name = "owner_profiles")
data class OwnerProfile(

    @Id
    @Column(name = "user_id", columnDefinition = "VARCHAR(36)")
    val userId: UUID,

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    val user: User,

    @Column(name = "business_name", length = 255)
    var businessName: String? = null,

    @Column(name = "profile_picture_url", length = 500)
    var profilePictureUrl: String? = null,

    @Column(name = "location_description", length = 500)
    var locationDescription: String? = null,

    // --- Document URLs (nullable - enables admin override before upload) -------

    /** Government-issued ID scan. */
    @Column(name = "id_document_url", length = 500)
    var idDocumentUrl: String? = null,

    /** Criminal background check certificate. */
    @Column(name = "criminal_record_url", length = 500)
    var criminalRecordUrl: String? = null,

    // --- Verification / Audit -------------------------------------------------

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    var verificationStatus: VerificationStatus = VerificationStatus.PENDING,

    /** Free-text reason for approval or rejection. */
    @Column(name = "admin_note", columnDefinition = "TEXT")
    var adminNote: String? = null,

    /** UUID of the admin who last changed the verification status. */
    @Column(name = "approved_by", columnDefinition = "VARCHAR(36)")
    var approvedBy: UUID? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null
) : Persistable<UUID> {

    override fun getId(): UUID = userId

    @Transient
    override fun isNew(): Boolean = createdAt == null

    fun isComplete(): Boolean {
        return !businessName.isNullOrBlank() &&
                !profilePictureUrl.isNullOrBlank() &&
                !idDocumentUrl.isNullOrBlank() &&
                !criminalRecordUrl.isNullOrBlank()
    }

    constructor() : this(
        userId = UUID.randomUUID(),
        user = User(fullName = "", phoneNumber = "")
    )
}
