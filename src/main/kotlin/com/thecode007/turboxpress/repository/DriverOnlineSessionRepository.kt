package com.thecode007.turboxpress.repository

import com.thecode007.turboxpress.entity.DriverOnlineSession
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface DriverOnlineSessionRepository : JpaRepository<DriverOnlineSession, Long> {

    fun findTopByDriverIdAndEndedAtIsNullOrderByStartedAtDesc(driverId: UUID): DriverOnlineSession?

    @Query("""
        SELECT s FROM DriverOnlineSession s
        WHERE s.driverId = :driverId
          AND s.startedAt < :endInstant
          AND (s.endedAt IS NULL OR s.endedAt > :startInstant)
        ORDER BY s.startedAt ASC
    """)
    fun findOverlappingSessions(
        @Param("driverId") driverId: UUID,
        @Param("startInstant") startInstant: Instant,
        @Param("endInstant") endInstant: Instant
    ): List<DriverOnlineSession>

    @Query("""
        SELECT s FROM DriverOnlineSession s
        WHERE s.driverId IN :driverIds
          AND s.startedAt < :endInstant
          AND (s.endedAt IS NULL OR s.endedAt > :startInstant)
        ORDER BY s.startedAt ASC
    """)
    fun findOverlappingSessionsForDrivers(
        @Param("driverIds") driverIds: List<UUID>,
        @Param("startInstant") startInstant: Instant,
        @Param("endInstant") endInstant: Instant
    ): List<DriverOnlineSession>
}

