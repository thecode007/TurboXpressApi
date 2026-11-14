package com.thecode007.turboxpress.service

import com.thecode007.turboxpress.entity.DriverOnlineSession
import com.thecode007.turboxpress.repository.DriverOnlineSessionRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class DriverOnlineTrackingService(
    private val sessionRepository: DriverOnlineSessionRepository,
    @Value("\${driver.online.stale-threshold-seconds:45}")
    private val staleThresholdSeconds: Long = 45
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun onDriverGoOnline(driverId: UUID, now: Instant = Instant.now()) {
        val openSession = sessionRepository.findTopByDriverIdAndEndedAtIsNullOrderByStartedAtDesc(driverId)
        if (openSession != null) {
            val secondsSinceHeartbeat = Duration.between(openSession.lastHeartbeatAt, now).seconds
            if (secondsSinceHeartbeat <= staleThresholdSeconds * 2) {
                // Still considered active/continuous session
                openSession.lastHeartbeatAt = now
                sessionRepository.save(openSession)
                return
            } else {
                // Heartbeat was stale, close the previous session at lastHeartbeatAt
                openSession.endedAt = openSession.lastHeartbeatAt
                openSession.durationSeconds = Duration.between(openSession.startedAt, openSession.lastHeartbeatAt).seconds.coerceAtLeast(0)
                sessionRepository.save(openSession)
            }
        }

        val newSession = DriverOnlineSession(
            driverId = driverId,
            startedAt = now,
            lastHeartbeatAt = now
        )
        sessionRepository.save(newSession)
        logger.info("[DriverOnlineTracking] Driver $driverId started online session at $now")
    }

    @Transactional
    fun onDriverHeartbeat(driverId: UUID, now: Instant = Instant.now()) {
        val openSession = sessionRepository.findTopByDriverIdAndEndedAtIsNullOrderByStartedAtDesc(driverId)
        if (openSession != null) {
            val secondsSinceHeartbeat = Duration.between(openSession.lastHeartbeatAt, now).seconds
            if (secondsSinceHeartbeat > staleThresholdSeconds * 2) {
                // Prior gap too long, close previous session and start a new one
                openSession.endedAt = openSession.lastHeartbeatAt
                openSession.durationSeconds = Duration.between(openSession.startedAt, openSession.lastHeartbeatAt).seconds.coerceAtLeast(0)
                sessionRepository.save(openSession)

                val newSession = DriverOnlineSession(
                    driverId = driverId,
                    startedAt = now,
                    lastHeartbeatAt = now
                )
                sessionRepository.save(newSession)
            } else {
                openSession.lastHeartbeatAt = now
                sessionRepository.save(openSession)
            }
        } else {
            // No open session, create one
            val newSession = DriverOnlineSession(
                driverId = driverId,
                startedAt = now,
                lastHeartbeatAt = now
            )
            sessionRepository.save(newSession)
        }
    }

    @Transactional
    fun onDriverGoOffline(driverId: UUID, now: Instant = Instant.now()) {
        val openSession = sessionRepository.findTopByDriverIdAndEndedAtIsNullOrderByStartedAtDesc(driverId)
        if (openSession != null) {
            val end = if (Duration.between(openSession.lastHeartbeatAt, now).seconds > staleThresholdSeconds) {
                openSession.lastHeartbeatAt
            } else {
                now
            }
            openSession.endedAt = end
            openSession.durationSeconds = Duration.between(openSession.startedAt, end).seconds.coerceAtLeast(0)
            sessionRepository.save(openSession)
            logger.info("[DriverOnlineTracking] Driver $driverId ended online session: ${openSession.durationSeconds}s")
        }
    }

    @Transactional(readOnly = true)
    fun calculateOnlineDurationSeconds(driverId: UUID, startInstant: Instant, endInstant: Instant): Long {
        val sessions = sessionRepository.findOverlappingSessions(driverId, startInstant, endInstant)
        val now = Instant.now()
        var totalSeconds = 0L

        for (s in sessions) {
            val effectiveEnd = when {
                s.endedAt != null -> s.endedAt!!
                Duration.between(s.lastHeartbeatAt, now).seconds <= staleThresholdSeconds -> now
                else -> s.lastHeartbeatAt
            }

            val overlapStart = if (s.startedAt.isAfter(startInstant)) s.startedAt else startInstant
            val overlapEnd = if (effectiveEnd.isBefore(endInstant)) effectiveEnd else endInstant

            if (overlapEnd.isAfter(overlapStart)) {
                totalSeconds += Duration.between(overlapStart, overlapEnd).seconds
            }
        }
        return totalSeconds
    }

    @Transactional(readOnly = true)
    fun calculateOnlineDurationForDrivers(driverIds: List<UUID>, startInstant: Instant, endInstant: Instant): Map<UUID, Long> {
        if (driverIds.isEmpty()) return emptyMap()
        val sessions = sessionRepository.findOverlappingSessionsForDrivers(driverIds, startInstant, endInstant)
        val now = Instant.now()
        val resultMap = mutableMapOf<UUID, Long>()

        for (s in sessions) {
            val effectiveEnd = when {
                s.endedAt != null -> s.endedAt!!
                Duration.between(s.lastHeartbeatAt, now).seconds <= staleThresholdSeconds -> now
                else -> s.lastHeartbeatAt
            }

            val overlapStart = if (s.startedAt.isAfter(startInstant)) s.startedAt else startInstant
            val overlapEnd = if (effectiveEnd.isBefore(endInstant)) effectiveEnd else endInstant

            if (overlapEnd.isAfter(overlapStart)) {
                val sec = Duration.between(overlapStart, overlapEnd).seconds
                resultMap[s.driverId] = (resultMap[s.driverId] ?: 0L) + sec
            }
        }
        return resultMap
    }

    fun formatDuration(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return "${hours}h ${minutes}m"
    }
}

