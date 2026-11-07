package com.thecode007.turboxpress.service

import com.thecode007.turboxpress.entity.OrderStatus
import com.thecode007.turboxpress.exception.ResourceNotFoundException
import com.thecode007.turboxpress.repository.DriverProfileRepository
import com.thecode007.turboxpress.repository.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class OrderAutoAssignmentScheduler(
    private val orderRepository: OrderRepository,
    private val driverProfileRepository: DriverProfileRepository,
    private val orderService: OrderService,
    private val appSettingService: AppSettingService,
    @Value("\${driver.online.stale-threshold-minutes:720}")
    private val staleThresholdMinutes: Long = 720
) {
    private val log = LoggerFactory.getLogger(OrderAutoAssignmentScheduler::class.java)

    /**
     * Periodically sweeps idle drivers whose heartbeat has expired beyond [staleThresholdMinutes]
     * and marks them OFFLINE so abandoned sessions do not remain online forever.
     */
    @Scheduled(fixedDelay = 60000)
    fun sweepStaleOnlineDrivers() {
        val cutoff = Instant.now().minus(staleThresholdMinutes, ChronoUnit.MINUTES)
        val markedOffline = driverProfileRepository.markStaleIdleDriversOffline(cutoff)
        if (markedOffline > 0) {
            log.info("Auto-offlined $markedOffline stale idle driver(s) with no activity since $cutoff")
        }
    }

    @Scheduled(fixedDelay = 15000)
    fun assignDriversToAcceptedOrders() {
        if (!appSettingService.getSettings().isAutoAssignEnabled) {
            return
        }

        val targetStatuses = listOf(OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP)
        val unassignedOrders = orderRepository.findByDriverIsNullAndStatusInOrderByCreatedAtAsc(targetStatuses)

        if (unassignedOrders.isEmpty()) {
            return
        }

        for (order in unassignedOrders) {
            if (order.restaurant?.location == null) continue
            try {
                // autoAssignDriver acquires FOR UPDATE OF d SKIP LOCKED and assigns atomically in one transaction
                orderService.autoAssignDriver(order.id)
            } catch (_: ResourceNotFoundException) {
                // No available idle drivers left; stop iterating remaining orders for this cycle
                break
            } catch (e: Exception) {
                log.warn("Auto-assign failed for order #${order.id}: ${e.message}")
            }
        }
    }

}
