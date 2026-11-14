package com.thecode007.turboxpress.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "driver_online_sessions",
    indexes = [
        Index(name = "idx_driver_online_sessions_driver", columnList = "driver_id"),
        Index(name = "idx_driver_online_sessions_started", columnList = "started_at"),
        Index(name = "idx_driver_online_sessions_ended", columnList = "ended_at")
    ]
)
data class DriverOnlineSession(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "driver_id", nullable = false, columnDefinition = "VARCHAR(36)")
    val driverId: UUID,

    @Column(name = "started_at", nullable = false)
    var startedAt: Instant,

    @Column(name = "last_heartbeat_at", nullable = false)
    var lastHeartbeatAt: Instant,

    @Column(name = "ended_at")
    var endedAt: Instant? = null,

    @Column(name = "duration_seconds")
    var durationSeconds: Long? = null
)

