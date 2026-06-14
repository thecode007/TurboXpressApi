package com.thecode007.turboxpress.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.springframework.data.domain.Persistable
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Driver Profile - one per user who registers as a delivery driver.
 *
 * Verification flow:
 *   1. User submits profile via the Driver app (status = PENDING).
 *      Document URLs may be null initially - the admin can approve before upload.
 *   2. Admin reviews - calls AdminProfileService.approveProfile(userId, "COURIER").
 *   3. Status - APPROVED; approvedBy is set for the audit trail.
 */
@Entity
@Table(name = "driver_profiles")
data class DriverProfile(

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

    // --- Document URLs (nullable - admin override before upload) --------------

    /** Government-issued ID scan. May be uploaded after initial registration. */
    @Column(name = "id_document_url", length = 500)
    var idDocumentUrl: String? = null,

    /** Criminal background check certificate. */
    @Column(name = "criminal_record_url", length = 500)
    var criminalRecordUrl: String? = null,

    // --- Vehicle Info ----------------------------------------------------------

    @Column(name = "license_number", length = 100)
    var licenseNumber: String? = null,

    @Column(name = "vehicle_type", length = 50)
    var vehicleType: String? = null,

    @Column(name = "vehicle_plate", length = 50)
    var vehiclePlate: String? = null,

    @Column(name = "is_available", nullable = false)
    var isAvailable: Boolean = true,

    @Column(name = "rating", nullable = false)
    var rating: Double = 0.0,

    // --- Financial / Billing --------------------------------------------------

    @Column(name = "monthly_sub_fee", nullable = false)
    var monthlySubFee: Double = 0.0,

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 20)
    var billingCycle: BillingCycle = BillingCycle.MONTHLY,

    @Column(name = "next_billing_date")
    var nextBillingDate: LocalDate? = null,

    @Column(name = "carried_over_balance", nullable = false)
    var carriedOverBalance: Double = 0.0,

    @Column(name = "admin_debt_balance", nullable = false, precision = 19, scale = 4)
    var adminDebtBalance: BigDecimal = BigDecimal.ZERO,

    @Column(name = "collected_cash_balance", nullable = false, precision = 19, scale = 4)
    var collectedCashBalance: BigDecimal = BigDecimal.ZERO,

    @Column(name = "daily_rate", nullable = false, precision = 19, scale = 4)
    var dailyRate: BigDecimal = BigDecimal.ZERO,

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
        return !displayName.isNullOrBlank() &&
                !profilePictureUrl.isNullOrBlank() &&
                !idDocumentUrl.isNullOrBlank() &&
                !criminalRecordUrl.isNullOrBlank()
    }

    constructor() : this(
        userId = UUID.randomUUID(),
        user = User(fullName = "", phoneNumber = "")
    )
}
