package com.thecode007.turboxpress.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.springframework.data.domain.Persistable
import java.time.Instant
import java.util.UUID

/**
 * System Admin Profile - represents a user who has admin-level access to
 * the backend and desktop admin panel. Must be manually provisioned by an
 * existing admin; self-registration is not supported for this profile type.
 *
 * Verification status defaults to APPROVED since admin profiles are
 * created only by trusted operators, not through a public registration flow.
 */
@Entity
@Table(name = "system_admin_profiles")
data class SystemAdminProfile(

    @Id
    @Column(name = "user_id", columnDefinition = "VARCHAR(36)")
    val userId: UUID,

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    val user: User,

    /** Granularity of admin access: ADMIN, SUPER_ADMIN, READ_ONLY, etc. */
    @Column(name = "admin_level", nullable = false, length = 50)
    var adminLevel: String = "ADMIN",

    /** Scope of data access: GLOBAL, REGIONAL, RESTAURANT_SPECIFIC, etc. */
    @Column(name = "access_scope", nullable = false, length = 50)
    var accessScope: String = "GLOBAL",

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    var verificationStatus: VerificationStatus = VerificationStatus.APPROVED,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null
) : Persistable<UUID> {

    override fun getId(): UUID = userId

    @Transient
    override fun isNew(): Boolean = createdAt == null

    fun isComplete(): Boolean = true

    constructor() : this(
        userId = UUID.randomUUID(),
        user = User(fullName = "", phoneNumber = "")
    )
}
