package com.thecode007.turboxpress.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.springframework.data.domain.Persistable
import java.time.Instant
import java.util.UUID

/**
 * Customer Profile - one per user who logs in via the Customer app.
 * Created automatically on first login with targetRole=CUSTOMER.
 * Requires no document verification; auto-approved on creation.
 */
@Entity
@Table(name = "customer_profiles")
data class CustomerProfile(

    /**
     * PK = FK to users.id. One-to-one relationship:
     * one User can have at most one CustomerProfile.
     */
    @Id
    @Column(name = "user_id", columnDefinition = "VARCHAR(36)")
    val userId: UUID,

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    val user: User,

    @Column(name = "display_name", length = 255)
    var displayName: String? = null,

    @Column(name = "profile_picture_url", length = 500)
    var profilePictureUrl: String? = null,

    @Column(name = "default_address_latitude")
    var defaultAddressLatitude: Double? = null,

    @Column(name = "default_address_longitude")
    var defaultAddressLongitude: Double? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    var verificationStatus: VerificationStatus = VerificationStatus.APPROVED,

    @Column(name = "admin_note", columnDefinition = "TEXT")
    var adminNote: String? = null,

    /** UUID of the SystemAdminProfile user who approved/rejected this profile. */
    @Column(name = "approved_by", columnDefinition = "VARCHAR(36)")
    var approvedBy: UUID? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null
) : Persistable<UUID> {

    override fun getId(): UUID = userId

    @Transient
    override fun isNew(): Boolean = createdAt == null

    fun isComplete(): Boolean = !displayName.isNullOrBlank()

    // Required for JPA when using @MapsId
    constructor() : this(
        userId = UUID.randomUUID(),
        user = User(fullName = "", phoneNumber = "")
    )
}
