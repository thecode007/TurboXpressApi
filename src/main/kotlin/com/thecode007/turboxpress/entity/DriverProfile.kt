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

    @Enumerated(EnumType.STRING)
    @Column(name = "online_status", nullable = false, length = 20)
    var onlineStatus: OnlineStatus = OnlineStatus.OFFLINE,

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

    @Column(name = "current_location", columnDefinition = "geometry(Point, 4326)")
    var currentLocation: org.locationtech.jts.geom.Point? = null,

    @Column(name = "last_seen_at")
    var lastSeenAt: Instant? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: DriverStatus = DriverStatus.IDLE,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "territory_id")
    var territory: Territory? = null,

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

    /**
     * Checks whether the driver is verified, active, online, and has a fresh heartbeat.
     */
    fun isEffectivelyOnline(cutoff: Instant? = null): Boolean {
        val isFresh = cutoff == null || (lastSeenAt != null && !lastSeenAt!!.isBefore(cutoff))
        return user.isActive &&
                verificationStatus == VerificationStatus.APPROVED &&
                onlineStatus == OnlineStatus.ONLINE &&
                isFresh
    }

    /**
     * Checks whether the driver is verified, active, online, idle, and has a fresh heartbeat.
     */
    fun isOnlineAndAvailable(cutoff: Instant? = null): Boolean {
        return isEffectivelyOnline(cutoff) && status == DriverStatus.IDLE
    }

    /**
     * Transitions the driver into the ONLINE state and refreshes their heartbeat timestamp.
     * Ensures task status is reset to IDLE unless the driver is actively ON_DELIVERY.
     */
    fun goOnline(now: Instant = Instant.now()) {
        check(verificationStatus == VerificationStatus.APPROVED) {
            "Only approved drivers can go online (current status: $verificationStatus)"
        }
        check(user.isActive) {
            "Inactive driver account cannot go online"
        }
        onlineStatus = OnlineStatus.ONLINE
        if (status != DriverStatus.ON_DELIVERY) {
            status = DriverStatus.IDLE
        }
        lastSeenAt = now
    }

    /**
     * Transitions the driver into the OFFLINE state while keeping task status valid.
     */
    fun goOffline() {
        onlineStatus = OnlineStatus.OFFLINE
        if (status != DriverStatus.ON_DELIVERY) {
            status = DriverStatus.IDLE
        }
    }

    fun recordHeartbeat(now: Instant = Instant.now()) {
        lastSeenAt = now
    }

    constructor() : this(
        userId = UUID.randomUUID(),
        user = User(fullName = "", phoneNumber = "")
    )
}

enum class DriverStatus {
    IDLE,
    ON_DELIVERY
}

enum class BillingCycle {
    DAILY,
    WEEKLY,
    MONTHLY
}

enum class OnlineStatus {
    ONLINE,
    OFFLINE;

    companion object {
        fun fromString(value: String): OnlineStatus =
            entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
                ?: throw IllegalArgumentException("Invalid online status '$value'. Must be ONLINE or OFFLINE.")
    }
}
