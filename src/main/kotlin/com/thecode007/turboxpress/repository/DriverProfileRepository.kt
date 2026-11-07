package com.thecode007.turboxpress.repository

import com.thecode007.turboxpress.entity.DriverProfile
import com.thecode007.turboxpress.entity.VerificationStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.Optional
import java.util.UUID

@Repository
interface DriverProfileRepository : JpaRepository<DriverProfile, UUID> {
    fun findByUserId(userId: UUID): Optional<DriverProfile>
    fun existsByUserId(userId: UUID): Boolean
    fun findAllByVerificationStatus(status: VerificationStatus): List<DriverProfile>
    fun findAllByVerificationStatusAndOnlineStatusAndStatus(
        verificationStatus: VerificationStatus,
        onlineStatus: com.thecode007.turboxpress.entity.OnlineStatus,
        status: com.thecode007.turboxpress.entity.DriverStatus
    ): List<DriverProfile>

    /** Eagerly fetches the related User to avoid LazyInitializationException in service layer. */
    @Query("SELECT p FROM DriverProfile p JOIN FETCH p.user WHERE p.userId = :userId")
    fun findByUserIdWithUser(userId: UUID): Optional<DriverProfile>

    /** Eagerly fetches the related User for all profiles of a given status. */
    @Query("SELECT p FROM DriverProfile p JOIN FETCH p.user WHERE p.verificationStatus = :status")
    fun findAllByVerificationStatusWithUser(status: VerificationStatus): List<DriverProfile>

    /** All APPROVED+ONLINE+IDLE active drivers with their user eagerly loaded and fresh heartbeat. */
    @Query("""
        SELECT p FROM DriverProfile p
        JOIN FETCH p.user u
        WHERE u.isActive = true
          AND p.verificationStatus = com.thecode007.turboxpress.entity.VerificationStatus.APPROVED
          AND p.onlineStatus = com.thecode007.turboxpress.entity.OnlineStatus.ONLINE
          AND p.status = com.thecode007.turboxpress.entity.DriverStatus.IDLE
          AND p.lastSeenAt IS NOT NULL
          AND p.lastSeenAt >= :cutoff
    """)
    fun findAllApprovedOnlineIdleDriversWithUser(
        @org.springframework.data.repository.query.Param("cutoff")
        cutoff: java.time.Instant = java.time.Instant.now().minusSeconds(45)
    ): List<DriverProfile>

    /** Finds the nearest approved, active, online, idle driver using PostGIS <-> nearest-neighbor operator. */
    @Transactional
    @Query(value = """
        SELECT d.* FROM driver_profiles d
        INNER JOIN users u ON u.id = d.user_id
        WHERE u.is_active = true
          AND d.verification_status = 'APPROVED'
          AND d.online_status = 'ONLINE'
          AND d.status = 'IDLE'
          AND d.current_location IS NOT NULL
          AND d.last_seen_at IS NOT NULL
          AND d.last_seen_at >= :cutoff
        ORDER BY d.current_location <-> :restaurantLocation
        LIMIT 1
        FOR UPDATE OF d SKIP LOCKED
    """, nativeQuery = true)
    fun findNearestIdleDriver(
        @org.springframework.data.repository.query.Param("restaurantLocation") restaurantLocation: org.locationtech.jts.geom.Point,
        @org.springframework.data.repository.query.Param("cutoff")
        cutoff: java.time.Instant = java.time.Instant.now().minusSeconds(45)
    ): DriverProfile?

    @Modifying
    @Transactional
    @Query("UPDATE DriverProfile p SET p.lastSeenAt = :now WHERE p.userId = :userId")
    fun touchLastSeenAt(
        @org.springframework.data.repository.query.Param("userId") userId: UUID,
        @org.springframework.data.repository.query.Param("now") now: java.time.Instant
    ): Int

    @Modifying
    @Transactional
    @Query("""
        UPDATE DriverProfile p
        SET p.onlineStatus = com.thecode007.turboxpress.entity.OnlineStatus.OFFLINE
        WHERE p.onlineStatus = com.thecode007.turboxpress.entity.OnlineStatus.ONLINE
          AND (p.lastSeenAt IS NULL OR p.lastSeenAt < :cutoff)
    """)
    fun markStaleIdleDriversOffline(
        @org.springframework.data.repository.query.Param("cutoff") cutoff: java.time.Instant
    ): Int

    @Modifying
    @Query("UPDATE DriverProfile p SET p.adminDebtBalance = p.adminDebtBalance - p.dailyRate WHERE p.dailyRate > 0 AND p.verificationStatus = com.thecode007.turboxpress.entity.VerificationStatus.APPROVED")
    fun accrueDailySalaries(): Int
}
