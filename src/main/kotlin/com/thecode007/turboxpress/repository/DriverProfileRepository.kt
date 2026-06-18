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

    /** Eagerly fetches the related User to avoid LazyInitializationException in service layer. */
    @Query("SELECT p FROM DriverProfile p JOIN FETCH p.user WHERE p.userId = :userId")
    fun findByUserIdWithUser(userId: UUID): Optional<DriverProfile>

    /** Eagerly fetches the related User for all profiles of a given status. */
    @Query("SELECT p FROM DriverProfile p JOIN FETCH p.user WHERE p.verificationStatus = :status")
    fun findAllByVerificationStatusWithUser(status: VerificationStatus): List<DriverProfile>

    /** Finds the nearest idle driver using PostGIS <-> nearest-neighbor operator. */
    @Transactional
    @Query(value = """
        SELECT * FROM driver_profiles d 
        WHERE d.is_available = true 
        AND d.status = 'IDLE'
        AND d.current_location IS NOT NULL 
        ORDER BY d.current_location <-> :restaurantLocation 
        LIMIT 1
        FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
    fun findNearestIdleDriver(@org.springframework.data.repository.query.Param("restaurantLocation") restaurantLocation: org.locationtech.jts.geom.Point): DriverProfile?

    @Modifying
    @Query("UPDATE DriverProfile p SET p.adminDebtBalance = p.adminDebtBalance - p.dailyRate WHERE p.dailyRate > 0 AND p.verificationStatus = 'APPROVED'")
    fun accrueDailySalaries(): Int
}
